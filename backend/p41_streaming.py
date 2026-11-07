"""Research-only FP32 disk-streamed Linear weights from verified safetensors.

No quantization, whole-file mmap, full state_dict copy, download or app adapter.
Official layout: https://github.com/safetensors/safetensors#format
The single model execution owner must retain this reader until inference ends.
"""
from pathlib import Path
import hashlib
import json
import math
import struct
import sys


def unique_object(pairs):
    out = {}
    for key, value in pairs:
        if key in out:
            raise ValueError('duplicate_header_key')
        out[key] = value
    return out


class TensorReader:
    def __init__(self, path: Path, expected_sha256: str):
        if sys.byteorder != 'little':
            raise ValueError('only_little_endian_tested')
        self.path = Path(path)
        self.file = self.path.open('rb', buffering=0)
        try:
            digest = hashlib.sha256()
            for block in iter(lambda: self.file.read(1024*1024), b''):
                digest.update(block)
            if digest.hexdigest() != expected_sha256:
                raise ValueError('checkpoint_sha_mismatch')
            self.file.seek(0)
            prefix = self.file.read(8)
            if len(prefix) != 8:
                raise ValueError('truncated_header')
            size = struct.unpack('<Q', prefix)[0]
            if not 2 <= size <= 1024*1024:
                raise ValueError('unsupported_header_size')
            raw = self.file.read(size)
            if len(raw) != size or raw[:1] != b'{':
                raise ValueError('invalid_header')
            entries = json.loads(raw, object_pairs_hook=unique_object)
            metadata = entries.pop('__metadata__', {})
            if not isinstance(metadata, dict) or any(not isinstance(k,str) or not isinstance(v,str) for k,v in metadata.items()):
                raise ValueError('invalid_metadata')
            self.base = 8+size
            self.entries = entries
            spans = []
            for name, item in entries.items():
                if not isinstance(name,str) or not isinstance(item,dict) or set(item) != {'dtype','shape','data_offsets'}:
                    raise ValueError('invalid_tensor_descriptor')
                widths = {'BF16':2, 'F32':4, 'I64':8}
                shape = item['shape']
                offsets = item['data_offsets']
                if item['dtype'] not in widths or not isinstance(shape,list) or len(shape)>8 or any(type(d) is not int or d<0 for d in shape):
                    raise ValueError('unsupported_tensor_shape_or_dtype')
                if not isinstance(offsets,list) or len(offsets)!=2 or any(type(v) is not int for v in offsets):
                    raise ValueError('invalid_offsets')
                start,end = offsets
                byte_count=math.prod(shape)*widths[item['dtype']]
                if start<0 or end-start!=byte_count or byte_count>128*1024**2:
                    raise ValueError('invalid_tensor_span')
                spans.append((start,end))
            cursor=0
            for start,end in sorted(spans):
                if start!=cursor:
                    raise ValueError('overlap_or_hole')
                cursor=end
            if self.base+cursor!=self.path.stat().st_size or not entries:
                raise ValueError('trailing_or_truncated_data')
            self.calls=0; self.bytes_read=0; self.max_tensor_bytes=0; self.seen=set(); self.max_linear_output_bytes=0
        except BaseException:
            self.file.close()
            raise

    def tensor(self, name):
        import torch
        entry=self.entries[name]
        start,end=entry['data_offsets']
        self.file.seek(self.base+start)
        raw=bytearray(end-start)
        if self.file.readinto(raw)!=len(raw):
            raise ValueError('tensor_changed_or_truncated')
        dtype={'BF16':torch.bfloat16,'F32':torch.float32,'I64':torch.int64}[entry['dtype']]
        tensor=(torch.frombuffer(raw,dtype=dtype).reshape(entry['shape']) if raw else torch.empty(entry['shape'],dtype=dtype))
        self.calls+=1; self.bytes_read+=len(raw); self.max_tensor_bytes=max(self.max_tensor_bytes,len(raw)); self.seen.add(name)
        # This conversion allocates only one tensor, never the whole checkpoint.
        return tensor.float() if tensor.is_floating_point() else tensor

    def stats(self):
        return {'tensor_reads':self.calls,'bytes_read':self.bytes_read,
                'largest_single_read_bytes':self.max_tensor_bytes,'unique_tensors_read':len(self.seen),
                'largest_linear_output_bytes':self.max_linear_output_bytes,
                'checkpoint_tensor_count':len(self.entries)}

    def close(self):
        self.file.close()


def install_streamed_weights(model, reader: TensorReader):
    import torch
    original=model.state_dict()
    if set(original)!=set(reader.entries):
        raise ValueError('checkpoint_schema_keys')
    for name,tensor in original.items():
        if list(tensor.shape)!=reader.entries[name]['shape']:
            raise ValueError('checkpoint_schema_shape:'+name)
    del original
    class DiskLinear(torch.nn.Module):
        def __init__(self, prefix, child):
            super().__init__()
            self.prefix=prefix; self.in_features=child.in_features; self.out_features=child.out_features
            self.has_bias=child.bias is not None
        def forward(self, value):
            if value.dtype!=torch.float32 or value.device.type!='cpu':
                raise ValueError('streamed_linear_requires_cpu_fp32')
            weight=reader.tensor(self.prefix+'.weight')
            bias=reader.tensor(self.prefix+'.bias') if self.has_bias else None
            reader.max_linear_output_bytes=max(reader.max_linear_output_bytes,math.prod(value.shape[:-1])*self.out_features*4)
            # Weight temporaries die after this invocation. No cache of packed
            # weights or autograd history is kept by the module.
            return torch.nn.functional.linear(value,weight,bias)
    counts={'disk_linear_modules':0,'resident_parameter_elements':0,'precision':'float32','quantized':False}
    replacements={}
    def visit(parent, prefix=''):
        for name,child in list(parent.named_children()):
            key=f'{prefix}.{name}' if prefix else name
            if type(child) is torch.nn.Linear:
                replacement=DiskLinear(key,child)
                parent._modules[name]=replacement
                replacements[id(child)]=replacement
                counts['disk_linear_modules']+=1
            else:
                visit(child,key)
        for name,param in list(parent.named_parameters(recurse=False)):
            key=f'{prefix}.{name}' if prefix else name
            value=reader.tensor(key)
            parent._parameters[name]=torch.nn.Parameter(value,requires_grad=False)
            counts['resident_parameter_elements']+=value.numel()
        for name,buffer in list(parent.named_buffers(recurse=False)):
            if name in parent._non_persistent_buffers_set:
                if buffer.is_meta: raise ValueError('unsupported_nonpersistent_meta_buffer')
                continue
            key=f'{prefix}.{name}' if prefix else name
            parent._buffers[name]=reader.tensor(key)
    # Pinned upstream has no shared Linear aliases; fail explicitly rather than
    # silently loading a different alias key in a future architecture.
    seen=set()
    for name,module in model.named_modules(remove_duplicate=False):
        if isinstance(module,torch.nn.Linear):
            if id(module) in seen: raise ValueError('shared_linear_requires_explicit_schema')
            seen.add(id(module))
    visit(model)
    if any(p.is_meta for p in model.parameters()) or any(b.is_meta for b in model.buffers()):
        raise ValueError('unmaterialized_nonstream_tensor')
    model.eval()
    return counts
