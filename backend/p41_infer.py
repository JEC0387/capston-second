"""P41 offline, separated-generation process. No app/API integration or download.

Original BF16 checkpoint values are promoted to official CPU FP32 per Linear.
Sequential CFG/query chunks preserve full resolution and original conditions.
Short schedules are explicitly diagnostic, never a naturalness/release PASS.
"""
import argparse
import hashlib
import importlib
import json
from pathlib import Path
import sys

from p40_flatlay_probe import Evidence, NAMESPACE, WEIGHTS, namespace_source, offline_network_guard, sha256, verify_source
from p41_preprocess import load_prepared
from p41_streaming import TensorReader, install_streamed_weights
from p41_attention import patched_query_attention, sequential_cfg
from p41_sampling_state import save_state, load_state


def validate_prepared_source(metadata, manifest):
    current=json.loads(manifest.read_text(encoding='utf-8-sig'))
    prepared=metadata['source']
    if prepared['manifest_sha256']!=sha256(manifest) or prepared['files']!=current['files']:
        raise ValueError('prepared_source_does_not_match_inference_source')


def adapter_digest():
    names=('p40_flatlay_probe.py','p41_infer.py','p41_streaming.py','p41_preprocess.py','p41_attention.py','p41_sampling_state.py')
    hashes={name:sha256(Path(__file__).with_name(name)) for name in names}
    return hashlib.sha256(json.dumps(hashes,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def sampling_contract(args, metadata, torch_version):
    return dict(model_sha256=WEIGHTS['model.safetensors'],source_manifest_sha256=sha256(args.source_manifest),
        prepared_manifest_sha256=sha256(args.prepared_dir/'prepared-manifest.json'),
        person_sha256=metadata['inputs']['person']['sha256'],garment_sha256=metadata['inputs']['garment']['sha256'],
        adapter_sha256=adapter_digest(),source_revision=metadata['source']['revision'],seed=args.seed,steps=args.steps,
        category=metadata['category'],precision='float32',sampler='euler_rf',time_shift_mu=1.5,guidance_scale=1.5,
        skip_cfg_last_n_steps=1,query_chunk=args.query_chunk,cfg_execution='sequential_conditional_then_unconditional',
        torch_version=str(torch_version),algorithm='p41-streamed-fp32-v1')


def parse_args(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source-dir',type=Path,required=True)
    p.add_argument('--source-manifest',type=Path,required=True)
    p.add_argument('--weights-dir',type=Path,required=True)
    p.add_argument('--prepared-dir',type=Path,required=True)
    p.add_argument('--output-dir',type=Path,required=True)
    p.add_argument('--steps',type=int,choices=(1,2,4,20,30,50),default=20)
    p.add_argument('--diagnostic',action='store_true')
    p.add_argument('--threads',type=int,default=2)
    p.add_argument('--query-chunk',type=int,default=128)
    p.add_argument('--seed',type=int,default=42)
    p.add_argument('--resume',type=Path)
    p.add_argument('--stop-after-step',type=int)
    args=p.parse_args(argv)
    if args.steps<20 and not args.diagnostic: p.error('short schedules require --diagnostic')
    if not 1<=args.threads<=4: p.error('threads must be 1..4')
    if not 1<=args.query_chunk<=256: p.error('query-chunk must be 1..256')
    if (args.resume or args.stop_after_step is not None) and (args.steps<20 or args.diagnostic):
        p.error('checkpoint/resume requires the standard schedule without --diagnostic')
    if args.stop_after_step is not None and not 0<args.stop_after_step<args.steps:
        p.error('stop-after-step must be a nonfinal completed step')
    return args


def main(argv=None):
    args=parse_args(argv)
    evidence=Evidence(args.output_dir)
    reader=None
    try:
        evidence.event('source_verification')
        verify_source(args.source_dir,args.source_manifest)
        with offline_network_guard():
            import torch
            torch.set_num_threads(args.threads); torch.set_num_interop_threads(1)
            evidence.event('runtime_loaded',torch_version=torch.__version__,cuda_available=torch.cuda.is_available(),precision='float32')
            namespace_source(args.source_dir)
            evidence.event('prepared_input_loading')
            inputs,metadata=load_prepared(args.prepared_dir)
            validate_prepared_source(metadata,args.source_manifest)
            contract=sampling_contract(args,metadata,torch.__version__)
            start_step=0
            if args.resume:
                images,start_step,rng=load_state(args.resume,contract)
                if args.stop_after_step is not None and args.stop_after_step<=start_step:
                    raise ValueError('stop_target_already_completed')
                evidence.event('checkpoint_resumed',completed_steps=start_step,requested_steps=args.steps,
                               checkpoint_manifest_sha256=sha256(args.resume/'sampling-state.json'))
            evidence.event('prepared_inputs_verified',manifest_sha256=sha256(args.prepared_dir/'prepared-manifest.json'),
                           category=metadata['category'],pose_runtime_loaded=False,parser_loaded=False,
                           shapes={name:list(value.shape) for name,value in inputs.items()})
            module=importlib.import_module(NAMESPACE+'.tryon_mmdit')
            utils=importlib.import_module(NAMESPACE+'.utils')
            evidence.event('checkpoint_hash_and_header')
            reader=TensorReader(args.weights_dir/'model.safetensors',WEIGHTS['model.safetensors'])
            evidence.event('core_meta_initialization')
            with torch.device('meta'): model=module.TryOnModel()
            evidence.event('streaming_schema_install')
            stats=install_streamed_weights(model,reader)
            evidence.event('streaming_model_prepared',**stats,**reader.stats(),
                           full_checkpoint_resident=False,forward_verified=False,
                           dwpose_imported=any(k.startswith(NAMESPACE+'.dwpose') for k in sys.modules))
            for name,block in model.named_modules():
                if len(name.split('.'))==2 and name.split('.')[0] in {'x_patch_mixer','double_blocks','single_blocks'}:
                    def entering(_module,_args,block_name=name):
                        evidence.event('block_started',block=block_name,**reader.stats())
                    block.register_forward_pre_hook(entering)
            if args.resume:
                torch.set_rng_state(rng)
            else:
                torch.manual_seed(args.seed)
                images=torch.randn((1,3,864,576),dtype=torch.float32)
            schedule=utils.get_rf_schedule(num_steps=args.steps,mu=1.5)
            completed=start_step
            with torch.inference_mode(), patched_query_attention(module,query_chunk_size=args.query_chunk):
                for index,(current,previous) in enumerate(zip(schedule[:-1],schedule[1:])):
                    if index<start_step: continue
                    evidence.event('denoise_step_started',index=index,requested_steps=args.steps,diagnostic=args.diagnostic)
                    times=torch.full((1,),current,dtype=torch.float32)
                    if index==args.steps-1:
                        # Official sampler discards the last unconditional result.
                        # Avoid that unused forward without changing its velocity.
                        velocity=model(images,times,**inputs)['x']
                    else:
                        pred=sequential_cfg(model,images,times,**inputs)
                        velocity=pred['v_u']+1.5*(pred['v_c']-pred['v_u'])
                        del pred
                    images=images+(previous-current)*velocity
                    del velocity
                    if not bool(torch.isfinite(images).all()): raise RuntimeError('nonfinite_model_output')
                    completed+=1
                    evidence.event('denoise_step_completed',index=index,requested_steps=args.steps,**reader.stats())
                    if not args.diagnostic and completed<args.steps:
                        checkpoint=evidence.folder/f'checkpoint-{completed:03d}'
                        save_state(checkpoint,images,completed,contract,torch.get_rng_state())
                        evidence.event('checkpoint_saved',completed_steps=completed,requested_steps=args.steps,
                                       checkpoint_manifest_sha256=sha256(checkpoint/'sampling-state.json'))
                    if args.stop_after_step==completed:
                        evidence.finish(success=True,model_inference_completed=False,completed_steps=completed,
                            requested_steps=args.steps,png_created=False,checkpoint_only=True,
                            checkpoint_path=checkpoint.name,**reader.stats())
                        return 0
                result=utils.tensor_to_pil(images[0].clamp(-1,1),unnormalize=True).convert('RGB')
            left,top,right,bottom=metadata['padding_ltrb']
            result=result.crop((left,top,result.width-right,result.height-bottom))
            result.info.clear()
            pending=evidence.folder/'unverified-result.partial'
            with pending.open('xb') as output: result.save(output,format='PNG')
            from PIL import Image
            with Image.open(pending) as check: check.verify()
            with Image.open(pending) as check:
                check.load()
                if check.format!='PNG' or check.mode!='RGB' or check.size!=result.size: raise RuntimeError('png_verification')
            name='diagnostic-model-result.png' if args.diagnostic else 'new-model-result.png'
            target=evidence.folder/name
            pending.rename(target)
            evidence.event('png_verified',width=result.width,height=result.height,sha256=sha256(target),diagnostic=args.diagnostic)
            evidence.finish(success=True,model_inference_completed=True,completed_steps=completed,
                            requested_steps=args.steps,diagnostic=args.diagnostic,quality='NOT_REVIEWED',
                            png_created=True,output_file=name,output_sha256=sha256(target),
                            seed=args.seed,precision='float32',quantized=False,**reader.stats())
            return 0
    except Exception as error:
        failed=evidence.stage
        evidence.event('failure',failed_stage=failed,exception_type=type(error).__name__,reason=str(error)[:500])
        actual_png=any((evidence.folder/name).exists() for name in ('diagnostic-model-result.png','new-model-result.png'))
        evidence.finish(success=False,failed_stage=failed,model_inference_completed=False,png_created=actual_png,
                        incomplete_file_exists=(evidence.folder/'unverified-result.partial').exists(),
                        diagnostic=args.diagnostic,**(reader.stats() if reader else {}))
        return 1
    finally:
        if reader: reader.close()


if __name__=='__main__': raise SystemExit(main())
