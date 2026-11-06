"""Run manually on the trusted server; never put the emitted token in an APK."""
import hashlib
import secrets
if __name__ == '__main__':
    token = secrets.token_urlsafe(32)
    print('Personal app access token (deliver privately, shown only now):', token)
    print('TRYON_CLIENT_TOKEN_HASHES entry:', hashlib.sha256(token.encode()).hexdigest())
