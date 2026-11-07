"""Create a development credential outside this repository; never print the raw token."""
import argparse
import hashlib
import os
from pathlib import Path
import secrets

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('private_directory',type=Path)
    args=parser.parse_args()
    directory=args.private_directory.expanduser().resolve()
    if Path(__file__).resolve().parent.parent in (directory,*directory.parents):
        parser.error('Choose a private directory outside the repository')
    directory.mkdir(parents=True,exist_ok=True,mode=0o700)
    token=secrets.token_urlsafe(32)
    with os.fdopen(os.open(directory/'local-vton-token.txt',os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as stream:
        stream.write(token)
    print('LOCAL_VTON_TOKEN_HASHES='+hashlib.sha256(token.encode()).hexdigest())
    print('Raw token saved to the private directory only. Do not upload/share it or put it in shell history.')
