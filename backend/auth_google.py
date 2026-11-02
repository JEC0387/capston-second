"""
구글 로그인 확인 — 앱이 보낸 구글 ID 토큰이 진짜인지, 우리 앱용으로 발급된 것인지 확인한다.

    앱(구글 계정 선택) → 구글 ID 토큰 → 우리 서버 /api/auth/google → 구글 tokeninfo 로 확인 → 계정 연결

환경변수 GOOGLE_CLIENT_ID (Railway Variables · backend/.env) — Google Cloud 콘솔에서 만든
"웹 애플리케이션" OAuth 클라이언트 ID. 여러 개면 쉼표로 구분한다. 앱 local.properties 의
GOOGLE_WEB_CLIENT_ID 와 같은 값이어야 한다 (안드로이드 앱이 받는 토큰의 aud 가 이 값).
비밀번호 · 클라이언트 보안 비밀은 필요 없다 (ID 토큰 확인에는 클라이언트 ID 만 쓴다).
"""
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo"
ISSUERS = {"accounts.google.com", "https://accounts.google.com"}


class GoogleNotConfigured(Exception):
    """서버에 GOOGLE_CLIENT_ID 가 없음"""


class InvalidGoogleToken(Exception):
    """토큰이 가짜 · 만료 · 다른 앱용"""


class GoogleUnavailable(Exception):
    """구글 확인 서버에 연결하지 못함"""


def client_ids() -> set[str]:
    return {c.strip() for c in os.environ.get("GOOGLE_CLIENT_ID", "").split(",") if c.strip()}


def is_configured() -> bool:
    return bool(client_ids())


def _tokeninfo(id_token: str) -> dict:
    url = f"{TOKENINFO_URL}?{urllib.parse.urlencode({'id_token': id_token})}"
    try:
        with urllib.request.urlopen(url, timeout=8) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        if e.code == 400:            # 구글이 "올바르지 않은 토큰" 이라고 답함
            raise InvalidGoogleToken() from None
        raise GoogleUnavailable(type(e).__name__) from None
    except (urllib.error.URLError, TimeoutError, ValueError) as e:
        raise GoogleUnavailable(type(e).__name__) from None


def verify(id_token: str) -> dict:
    """
    확인된 사용자 정보 {sub, email, name, picture} — sub 는 구글 계정마다 바뀌지 않는 고유 번호.
    토큰 내용은 로그에 남기지 않는다.
    """
    allowed = client_ids()
    if not allowed:
        raise GoogleNotConfigured()
    if not id_token or len(id_token) > 4096:
        raise InvalidGoogleToken()
    info = _tokeninfo(id_token)
    if info.get("aud") not in allowed:
        raise InvalidGoogleToken()
    if info.get("iss") not in ISSUERS:
        raise InvalidGoogleToken()
    try:
        if int(info.get("exp", 0)) < time.time():
            raise InvalidGoogleToken()
    except (TypeError, ValueError):
        raise InvalidGoogleToken() from None
    if str(info.get("email_verified", "")).lower() != "true" or not info.get("sub"):
        raise InvalidGoogleToken()
    return {
        "sub": str(info["sub"]),
        "email": info.get("email") or "",
        "name": info.get("name") or (info.get("email") or "").split("@")[0],
        "picture": info.get("picture") or "",
    }
