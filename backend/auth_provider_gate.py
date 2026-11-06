"""Team OAuth entry points retained without the unsafe device-ID account bridge.

Provider credentials alone cannot enable login. A verified identity-to-Bearer
bridge, approved account migration and Android recovery contract are required.
No request here contacts an OAuth provider or reads/writes account data.
"""
from fastapi import APIRouter, HTTPException
from schemas import (ExchangeLoginRequest, GoogleLoginRequest,
                     LoginProvidersResponse, StartLoginRequest)

router = APIRouter(prefix="/api/auth", tags=["인증"])
_UNAVAILABLE = "소셜 로그인 연동이 준비되지 않았습니다. 기기의 기존 기록을 유지하고 게스트로 계속 이용해주세요."


@router.get("/providers", response_model=LoginProvidersResponse)
def providers():
    return {"google": False, "kakao": False, "facebook": False, "apple": False}


def unavailable():
    raise HTTPException(503, detail={"code": "social_auth_bridge_unavailable", "message": _UNAVAILABLE})


@router.post("/google")
def google_login(request: GoogleLoginRequest):
    unavailable()


@router.post("/start")
def start_login(request: StartLoginRequest):
    unavailable()


@router.post("/exchange")
def exchange_login(request: ExchangeLoginRequest):
    unavailable()


@router.get("/{provider}/callback")
def provider_callback(provider: str):
    if provider not in ("kakao", "facebook", "apple"):
        raise HTTPException(404, detail="Unknown login provider")
    unavailable()


@router.post("/apple/callback")
def apple_callback():
    unavailable()
