"""Bounded local experiment runner. Terminates only the direct process it created.
No server, device, network, existing application, or global setting is modified.
"""
import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

def system_memory():
    if os.name != 'nt':
        raise RuntimeError('This resource guard has only been implemented for Windows')
    class Memory(ctypes.Structure):
        _fields_ = [('length',ctypes.c_ulong),('load',ctypes.c_ulong)] + [(n,ctypes.c_ulonglong) for n in
            ('total_ram','free_ram','total_commit','free_commit','total_virtual','free_virtual','extended')]
    item=Memory(); item.length=ctypes.sizeof(item)
    if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(item)):
        raise OSError('Cannot measure resource reserve')
    return {key:int(getattr(item,key)) for key in ('total_ram','free_ram','total_commit','free_commit')}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--python',type=Path,required=True)
    parser.add_argument('--probe',type=Path,required=True)
    parser.add_argument('--log-dir',type=Path,required=True)
    parser.add_argument('--max-seconds',type=int,default=900)
    parser.add_argument('probe_args',nargs=argparse.REMAINDER)
    args=parser.parse_args()
    if not 1 <= args.max_seconds <= 1800: parser.error('Maximum bounded run is 1800 seconds')
    extra=args.probe_args[1:] if args.probe_args[:1]==['--'] else args.probe_args
    folder=args.log_dir.resolve(); folder.mkdir(parents=True,exist_ok=False)
    source=args.probe.read_bytes()
    (folder/'probe-source.py').write_bytes(source)
    (folder/'invocation.json').write_text(json.dumps({'probe_sha256':hashlib.sha256(source).hexdigest(),
        'python':str(args.python),'arguments':extra,'max_seconds':args.max_seconds},indent=2),encoding='utf-8')
    start=time.monotonic()
    try:
        baseline=system_memory()
    except Exception as error:
        (folder/'supervisor.json').write_text(json.dumps({'started':False,
            'reason':'initial_resource_measurement_failed','exception_type':type(error).__name__}),encoding='utf-8')
        return 2
    if baseline['free_commit'] < 2*1024**3:
        (folder/'supervisor.json').write_text(json.dumps({'started':False,'reason':'insufficient_initial_commit_reserve','memory':baseline}))
        return 2
    environment=os.environ.copy()
    environment.update(HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',HF_HUB_DISABLE_TELEMETRY='1',MPLBACKEND='Agg',
                       MPLCONFIGDIR=str(folder/'matplotlib-cache'),PYTHONDONTWRITEBYTECODE='1')
    reason=None; low_samples=0; process=None; code=None; error_type=None
    with (folder/'process.log').open('x',encoding='utf-8') as output, (folder/'resources.jsonl').open('x',encoding='utf-8') as samples:
        try:
            # The probe is standalone stdlib + explicitly pinned source. Execute the
            # captured bytes, so later edits cannot change this run's implementation.
            process=subprocess.Popen([str(args.python),'-u',str(folder/'probe-source.py'),*extra],stdout=output,stderr=subprocess.STDOUT,env=environment)
            while process.poll() is None:
                elapsed=time.monotonic()-start
                memory=system_memory()
                samples.write(json.dumps({'elapsed_seconds':elapsed,**memory})+'\n');samples.flush()
                # Permit normal page-cache reclamation, but do not exhaust the PC's commit limit.
                low_samples = low_samples + 1 if memory['free_commit'] < 768*1024**2 or memory['free_ram'] < 96*1024**2 else 0
                if elapsed > args.max_seconds: reason='bounded_wall_timeout'
                elif low_samples >= 3: reason='system_memory_reserve_reached'
                if reason:
                    process.terminate();break
                time.sleep(2)
            code=process.wait(timeout=15)
        except Exception as error:
            reason='supervisor_or_launch_error'
            error_type=type(error).__name__
        finally:
            if process is not None:
                try:
                    if process.poll() is None:
                        process.kill()
                    code=process.wait(timeout=15)
                except Exception as error:
                    reason='owned_child_cleanup_error'
                    error_type=type(error).__name__
    try:
        final_memory=system_memory()
    except Exception as error:
        final_memory=None
        reason=reason or 'final_resource_measurement_failed'
        error_type=error_type or type(error).__name__
    record={'started':process is not None,'exit_code':code,'stop_reason':reason,
            'exception_type':error_type,'elapsed_seconds':time.monotonic()-start,
            'baseline_memory':baseline,'final_memory':final_memory,'owned_process_only':True,
            'note':'Process exit success alone is not model inference or image quality proof; inspect probe evidence.'}
    (folder/'supervisor.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
    print(json.dumps(record),flush=True)
    return 0 if code == 0 and reason is None else 1

if __name__=='__main__': raise SystemExit(main())
