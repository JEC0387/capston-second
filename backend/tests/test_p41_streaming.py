import hashlib
import json
import struct

import pytest

from p41_streaming import TensorReader, install_streamed_weights


def test_real_bf16_disk_read_and_streamed_forward_match_eager_fp32(tmp_path):
    torch=pytest.importorskip('torch')
    save_file=pytest.importorskip('safetensors.torch').save_file
    model=torch.nn.Sequential(torch.nn.Linear(3,2),torch.nn.GELU(),torch.nn.Linear(2,1,bias=False)).bfloat16().eval()
    with torch.no_grad():
        model[0].weight.copy_(torch.tensor([[0.5,-0.25,0.75],[-0.5,0.25,0.5]]))
        model[0].bias.copy_(torch.tensor([0.125,-0.25]))
        model[2].weight.copy_(torch.tensor([[0.5,-1.0]]))
    path=tmp_path/'model.safetensors'
    save_file(model.state_dict(),path)
    checksum=hashlib.sha256(path.read_bytes()).hexdigest()
    reader=TensorReader(path,checksum)
    try:
        eager=model.float()
        with torch.device('meta'):
            streamed=torch.nn.Sequential(torch.nn.Linear(3,2),torch.nn.GELU(),torch.nn.Linear(2,1,bias=False))
        stats=install_streamed_weights(streamed,reader)
        assert stats['disk_linear_modules']==2
        assert sum(p.numel() for p in streamed.parameters())==0
        values=torch.tensor([[1.,2.,-1.],[-1.,0.,1.]])
        with torch.inference_mode():
            expected=eager(values)
            assert torch.equal(streamed(values),expected)
            first_reads=reader.calls
            assert torch.equal(streamed(values),expected)
        assert reader.calls==2*first_reads  # Re-read, not whole-model cache.
        assert reader.stats()['unique_tensors_read']==3
        with pytest.raises(ValueError,match='fp32'):
            streamed(values.bfloat16())
    finally:
        reader.close()


def test_hash_and_overlap_rejected_before_tensor_use(tmp_path):
    path=tmp_path/'bad.safetensors'
    header=json.dumps({'a':{'dtype':'BF16','shape':[2],'data_offsets':[0,4]},
                       'b':{'dtype':'BF16','shape':[2],'data_offsets':[2,6]}}).encode()
    path.write_bytes(struct.pack('<Q',len(header))+header+b'000000')
    with pytest.raises(ValueError,match='sha'):
        TensorReader(path,'0'*64)
    with pytest.raises(ValueError,match='overlap'):
        TensorReader(path,hashlib.sha256(path.read_bytes()).hexdigest())


def test_missing_tensor_schema_cannot_silently_use_random_weights(tmp_path):
    torch=pytest.importorskip('torch')
    save_file=pytest.importorskip('safetensors.torch').save_file
    path=tmp_path/'model.safetensors'
    save_file({'wrong':torch.ones(1,dtype=torch.bfloat16)},path)
    reader=TensorReader(path,hashlib.sha256(path.read_bytes()).hexdigest())
    try:
        with torch.device('meta'): model=torch.nn.Linear(2,2)
        with pytest.raises(ValueError,match='schema_keys'): install_streamed_weights(model,reader)
    finally: reader.close()
