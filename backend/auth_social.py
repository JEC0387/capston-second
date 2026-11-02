"""
카카오 · 페이스북 · Apple 로그인 — 브라우저(OAuth 인가 코드) 방식.

    앱 → POST /api/auth/start {provider}        → 로그인 주소 (state 에 이 폰 기기 번호를 묶어 둠)
    앱 → 브라우저로 그 주소 열기 → 카카오 · 페이스북 · Apple 로그인 화면
    각 회사 → 우리 서버 /api/auth/{provider}/callback?code=…  (Apple 은 POST)
    서버 → 각 회사에 code 로 사용자 고유 번호 확인 → 1회용 로그인 코드 → fashionai://login?code=… 로 앱 열기
    앱 → POST /api/auth/exchange {code} → 계정 번호(accountKey) — 구글 로그인과 같은 결과

앱에 SDK · 비밀 값을 넣지 않고, 비밀 값(클라이언트 시크릿 · Apple 키)은 서버 환경변수에만 둔다.

환경변수 (Railway Variables · backend/.env)
    PUBLIC_BASE_URL          서버 주소 (예: https://fashion-backend-production-aba0.up.railway.app) — 각 회사에 등록할 Redirect URI 의 앞부분
    KAKAO_REST_API_KEY       카카오 디벨로퍼스 > 앱 키 > REST API 키
    KAKAO_CLIENT_SECRET      (선택) 카카오 로그인 > 보안 > Client Secret 을 켰을 때
    FACEBOOK_APP_ID / FACEBOOK_APP_SECRET    메타 개발자 > 앱 설정 > 기본 설정
    APPLE_SERVICE_ID         Apple Developer > Identifiers > Services ID (예: com.jeiu.fashionproject.signin)
    APPLE_TEAM_ID / APPLE_KEY_ID / APPLE_PRIVATE_KEY   Sign in with Apple 키 (.p8 파일 내용, 줄바꿈은 \\n 으로)
"""
import base64
import json
import os
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Optional

PROVIDERS = ("kakao", "facebook", "apple")
LABELS = {"kakao": "카카오", "facebook": "페이스북", "apple": "Apple", "google": "구글"}

STATE_TTL = 600        # 로그인 화면에서 10분 안에 끝내야 함
CODE_TTL = 300         # 앱으로 돌아와 5분 안에 교환해야 함

_states: dict = {}     # state → (provider, device_id, 만든 시각)
_codes: dict = {}      # 1회용 로그인 코드 → (provider, subject, email, name, device_id, 만든 시각)


class ProviderNotConfigured(Exception):
    pass


class ProviderFailed(Exception):
    """각 회사가 로그인을 확인해 주지 않음 (취소 · 만료 · 설정 오류)"""


def env(name: str) -> str:
    return os.environ.get(name, "").strip()


def is_configured(provider: str) -> bool:
    if provider == "kakao":
        return bool(env("KAKAO_REST_API_KEY"))
    if provider == "facebook":
        return bool(env("FACEBOOK_APP_ID") and env("FACEBOOK_APP_SECRET"))
    if provider == "apple":
        return all(env(k) for k in ("APPLE_SERVICE_ID", "APPLE_TEAM_ID", "APPLE_KEY_ID", "APPLE_PRIVATE_KEY"))
    return False


def redirect_uri(provider: str, base_url: str) -> str:
    base = (env("PUBLIC_BASE_URL") or base_url).rstrip("/")
    return f"{base}/api/auth/{provider}/callback"


def _cleanup(now: float):
    for store, ttl in ((_states, STATE_TTL), (_codes, CODE_TTL)):
        for k in [k for k, v in store.items() if now - v[-1] > ttl]:
            store.pop(k, None)


# ── ① 로그인 시작 ────────────────────────────────────────────────────────

def start(provider: str, device_id: Optional[str], base_url: str) -> str:
    """각 회사 로그인 화면 주소. state 는 위조 방지 + 어느 폰에서 시작했는지 기억"""
    if provider not in PROVIDERS or not is_configured(provider):
        raise ProviderNotConfigured()
    now = time.time()
    _cleanup(now)
    state = secrets.token_urlsafe(24)
    _states[state] = (provider, device_id, now)
    uri = redirect_uri(provider, base_url)
    if provider == "kakao":
        q = {"client_id": env("KAKAO_REST_API_KEY"), "redirect_uri": uri, "response_type": "code", "state": state}
        return "https://kauth.kakao.com/oauth/authorize?" + urllib.parse.urlencode(q)
    if provider == "facebook":
        q = {"client_id": env("FACEBOOK_APP_ID"), "redirect_uri": uri, "state": state,
             "response_type": "code", "scope": "public_profile,email"}
        return "https://www.facebook.com/dialog/oauth?" + urllib.parse.urlencode(q)
    q = {"client_id": env("APPLE_SERVICE_ID"), "redirect_uri": uri, "response_type": "code",
         "scope": "name email", "response_mode": "form_post", "state": state}
    return "https://appleid.apple.com/auth/authorize?" + urllib.parse.urlencode(q, quote_via=urllib.parse.quote)


def take_state(provider: str, state: Optional[str]) -> Optional[str]:
    """콜백의 state 확인 (한 번만 쓸 수 있음) → 시작한 폰의 기기 번호. 맞지 않으면 ProviderFailed"""
    entry = _states.pop(state or "", None)
    if entry is None or entry[0] != provider or time.time() - entry[2] > STATE_TTL:
        raise ProviderFailed("state")
    return entry[1]


# ── ② 각 회사에 사용자 확인 ───────────────────────────────────────────────

def _http_json(url: str, data: Optional[dict] = None, headers: Optional[dict] = None) -> dict:
    body = urllib.parse.urlencode(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, headers=headers or {})
    if body is not None:
        req.add_header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raise ProviderFailed(f"HTTP {e.code}") from None
    except (urllib.error.URLError, TimeoutError, ValueError) as e:
        raise ProviderFailed(type(e).__name__) from None


def kakao_user(code: str, uri: str) -> dict:
    data = {"grant_type": "authorization_code", "client_id": env("KAKAO_REST_API_KEY"), "redirect_uri": uri, "code": code}
    if env("KAKAO_CLIENT_SECRET"):
        data["client_secret"] = env("KAKAO_CLIENT_SECRET")
    token = _http_json("https://kauth.kakao.com/oauth/token", data).get("access_token")
    if not token:
        raise ProviderFailed("token")
    me = _http_json("https://kapi.kakao.com/v2/user/me", headers={"Authorization": f"Bearer {token}"})
    if not me.get("id"):
        raise ProviderFailed("id")
    account = me.get("kakao_account") or {}
    email = account.get("email") if account.get("is_email_verified") else ""
    name = (account.get("profile") or {}).get("nickname") or (me.get("properties") or {}).get("nickname") or ""
    return {"sub": str(me["id"]), "email": email or "", "name": name}


def facebook_user(code: str, uri: str) -> dict:
    q = {"client_id": env("FACEBOOK_APP_ID"), "client_secret": env("FACEBOOK_APP_SECRET"), "redirect_uri": uri, "code": code}
    token = _http_json("https://graph.facebook.com/oauth/access_token?" + urllib.parse.urlencode(q)).get("access_token")
    if not token:
        raise ProviderFailed("token")
    me = _http_json("https://graph.facebook.com/me?" + urllib.parse.urlencode(
        {"fields": "id,name,email", "access_token": token}))
    if not me.get("id"):
        raise ProviderFailed("id")
    return {"sub": str(me["id"]), "email": me.get("email") or "", "name": me.get("name") or ""}


def apple_client_secret() -> str:
    """Apple 은 비밀번호 대신 .p8 키로 서명한 JWT(ES256)를 client_secret 으로 쓴다 (5분짜리)"""
    import jwt   # PyJWT[crypto] — Apple 을 켤 때만 필요
    key = env("APPLE_PRIVATE_KEY").replace("\\n", "\n")
    now = int(time.time())
    return jwt.encode(
        {"iss": env("APPLE_TEAM_ID"), "iat": now, "exp": now + 300, "aud": "https://appleid.apple.com",
         "sub": env("APPLE_SERVICE_ID")},
        key, algorithm="ES256", headers={"kid": env("APPLE_KEY_ID")})


def _jwt_payload(token: str) -> dict:
    try:
        part = token.split(".")[1]
        return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))
    except (IndexError, ValueError):
        raise ProviderFailed("id_token") from None


def apple_user(code: str, uri: str, user_json: Optional[str] = None) -> dict:
    data = {"client_id": env("APPLE_SERVICE_ID"), "client_secret": apple_client_secret(), "code": code,
            "grant_type": "authorization_code", "redirect_uri": uri}
    id_token = _http_json("https://appleid.apple.com/auth/token", data).get("id_token")
    if not id_token:
        raise ProviderFailed("token")
    # Apple 서버에서 우리 키로 직접 받은 토큰이라 내용(발급자 · 대상)만 확인한다
    claims = _jwt_payload(id_token)
    if claims.get("iss") != "https://appleid.apple.com" or claims.get("aud") != env("APPLE_SERVICE_ID") or not claims.get("sub"):
        raise ProviderFailed("claims")
    name = ""
    if user_json:   # 이름은 처음 로그인할 때 한 번만 온다
        try:
            n = json.loads(user_json).get("name") or {}
            name = f"{n.get('lastName', '')}{n.get('firstName', '')}".strip()
        except (ValueError, AttributeError):
            pass
    return {"sub": str(claims["sub"]), "email": claims.get("email") or "", "name": name}


def fetch_user(provider: str, code: str, uri: str, user_json: Optional[str] = None) -> dict:
    if provider == "kakao":
        return kakao_user(code, uri)
    if provider == "facebook":
        return facebook_user(code, uri)
    return apple_user(code, uri, user_json)


# ── ③ 앱으로 돌아가기 ────────────────────────────────────────────────────

def issue_code(provider: str, user: dict, device_id: Optional[str]) -> str:
    now = time.time()
    _cleanup(now)
    code = secrets.token_urlsafe(24)
    _codes[code] = (provider, user["sub"], user.get("email", ""), user.get("name", ""), device_id, now)
    return code


def take_code(code: str) -> Optional[tuple]:
    entry = _codes.pop(code or "", None)
    if entry is None or time.time() - entry[-1] > CODE_TTL:
        return None
    return entry


def back_to_app_html(query: dict) -> str:
    """브라우저에서 앱(fashionai://login)을 다시 여는 페이지 — 자동으로 열리고, 안 되면 버튼"""
    q = urllib.parse.urlencode(query)
    app_url = f"fashionai://login?{q}"
    intent_url = f"intent://login?{q}#Intent;scheme=fashionai;package=com.jeiu.fashionproject;end"
    ok = "code" in query
    msg = "로그인했어요. 앱으로 돌아가요…" if ok else "로그인하지 못했어요. 앱으로 돌아가 다시 시도해 주세요."
    return f"""<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>STYLE AI 로그인</title>
<style>body{{font-family:sans-serif;background:#FAF8F5;color:#1A1A1A;display:flex;flex-direction:column;
align-items:center;justify-content:center;min-height:90vh;margin:0 16px;text-align:center}}
a{{display:inline-block;margin-top:20px;padding:14px 28px;border-radius:12px;background:#C4956A;color:#fff;
text-decoration:none;font-weight:bold}}</style></head>
<body><h2>STYLE AI</h2><p>{msg}</p><a href="{intent_url}">앱으로 돌아가기</a>
<script>setTimeout(function(){{location.href={json.dumps(app_url)};}},150);</script></body></html>"""
