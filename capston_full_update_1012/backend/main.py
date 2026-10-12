"""
FashionProject 백엔드 (FastAPI + Supabase PostgreSQL)

앱 화면 ↔ 엔드포인트 매핑                                   (DB) = Supabase 에 저장·조회
─────────────────────────────────────────────────────────────
HomeScreen           → GET  /api/home
                       GET  /api/outfits/weather          (예시 코디 3종)
WeatherViewModel     → GET  /api/weather                  (실시간 날씨 + 조언)
WardrobeStore(앱)    → POST /api/wardrobe/sync            (DB) 폰 옷장 전체를 서버에 맞춰 저장
WardrobeScreen       → GET  /api/wardrobe                 (DB)
                       GET  /api/wardrobe/favorites       (DB) 하트 옷만
                       POST /api/wardrobe                 (DB)
                       POST /api/wardrobe/{id}/favorite   (DB)
                       DELETE /api/wardrobe/{id}          (DB)
WearLogStore(앱)     → POST /api/wear-logs/sync           (DB) 착용 기록 전체 저장
WearCalendarScreen   → GET  /api/wear-logs                (DB)
AddClothingScreen    → POST /api/clothing/classify        (DeepFashion2 옷 종류 분류)
                       POST /api/object-detection         (YOLOv8 COCO 객체 탐지)
BodyAnalysisScreen   → POST /api/body-measurements        (DB) 분석 결과 숫자만 저장 (사진 X)
                       GET  /api/body-measurements        (DB) 측정 기록
                       DELETE /api/body-measurements      (DB) 측정 기록 전부 삭제
                       POST /api/body-analysis            (사진 업로드 — 예전 방식, 고정 결과)
StylePreferenceScreen→ GET  /api/style-preference         (DB)
                       POST /api/style-preference/rate    (DB) 좋아요/싫어요
VirtualFittingScreen → GET  /api/virtual-fitting/items    (DB)
StyleSuggestionScreen→ GET  /api/style-suggestions        (DB)
StyleAnalysisScreen  → GET  /api/style-analysis
ShopScreen           → GET  /api/products                 (DB)
                       GET  /api/products/likes           (DB) 하트 누른 상품
                       POST /api/products/{id}/like       (DB) 하트 켜기/끄기
ProductDetailScreen  → GET  /api/products/{id}            (DB)
사이즈 추천(예정)    → GET  /api/size-charts              (DB) 브랜드 사이즈표
MyPageScreen         → GET  /api/profile                  (DB)
OnboardingScreen     → GET  /api/styles
                       POST /api/profile/styles           (DB)
─────────────────────────────────────────────────────────────

준비:  backend/.env 에 DATABASE_URL (Supabase Session pooler 주소) — README 참고
실행:  python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
문서:  http://localhost:8000/docs  (Swagger 자동 생성)
"""
import io
import re
import shutil
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

import cv2
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from PIL import Image
from sqlalchemy import delete, func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

import data
import detection
import fashion_detection
import weather
from database import db_label, get_db
from db_models import (
    BodyMeasurement, Clothing, Product as ProductRow, ProductLike, SizeChart,
    StyleRating, User, WearLog,
)
from schemas import (
    AddClothingRequest, BodyAnalysisResult, BodyMeasurementHistory, BodyMeasurementIn,
    BodyMeasurementOut, ClothingClassifyResponse, ClothingItem, HomeResponse,
    ObjectDetectionResponse, Product, ProductDetail, ProductLikeRequest,
    ProductLikesResponse, Profile, RatePreferenceRequest, ShopResponse,
    SizeChartResponse, StyleAnalysisResponse, StylePreferenceResponse,
    StyleSuggestionResponse, SyncResult, UpdateStylesRequest, VirtualFittingResponse,
    WardrobeResponse, WardrobeSyncRequest, WearLogResponse, WearLogSyncRequest,
    WeatherNowResponse, WeatherOutfitResponse,
)
from seed import DEMO_EMAIL, ensure_sample_measurements, init_db


@asynccontextmanager
async def lifespan(_: FastAPI):
    # 서버가 켜질 때 한 번: 테이블 만들기 + 비어 있으면 초기 데이터 넣기
    init_db()
    print(f"[DB] 연결 대상: {db_label()}")
    yield


app = FastAPI(
    title="FashionProject API",
    description="AI 기반 개인 맞춤 패션 추천 서비스 백엔드 (Supabase PostgreSQL)",
    version="2.0.0",
    lifespan=lifespan,
)

# 앱(에뮬레이터/실기기)에서 접근할 수 있도록 CORS 전체 허용 (개발용)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 업로드된 사진·탐지 결과 이미지 저장 폴더 (정적 서빙, 깃허브에는 안 올라감)
UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")


# ──────────────────────────────────────────────
# 공용 도우미
# ──────────────────────────────────────────────

def current_user(
    db: Session = Depends(get_db),
    x_device_id: Optional[str] = Header(
        None, description="앱 기기 번호. 앱이 자동으로 보낸다. 비워 두면 데모 사용자(샘플 옷 12벌)로 처리"),
) -> User:
    """
    로그인 기능이 생기기 전까지 사용자 구분 방법:
    - 앱은 폰마다 만든 기기 번호를 X-Device-Id 헤더로 보낸다 → 폰마다 따로 저장 (팀원끼리 덮어쓰지 않음)
    - 헤더가 없으면(Swagger 등) 데모 사용자로 처리
    로그인을 붙이면 이 함수만 '토큰 → 사용자' 로 바꾸면 된다.
    """
    device_id = re.sub(r"[^A-Za-z0-9-]", "", x_device_id or "")[:64]
    cond = (User.device_id == device_id) if device_id else (User.email == DEMO_EMAIL)
    user = db.scalar(select(User).where(cond))
    if user is None:  # 처음 연결한 폰이거나, 누가 DB 에서 지웠을 때 새로 만든다
        user = User(
            email=None if device_id else DEMO_EMAIL,
            device_id=device_id or None,
            nickname=data.PROFILE["nickname"],
            preferred_styles=list(data.PROFILE["preferredStyles"]),
        )
        db.add(user)
        try:
            db.commit()
        except IntegrityError:  # 같은 폰의 요청 두 개가 동시에 만들려고 한 경우
            db.rollback()
            user = db.scalar(select(User).where(cond))
            if user is None:
                raise
    if not user.sample_records_added:
        # 처음 연결한 폰(또는 예전 버전 사용자)에게 시연용 측정 기록을 한 번 넣어 준다
        ensure_sample_measurements(db, user)
        try:
            db.commit()
        except IntegrityError:
            db.rollback()
    return user


def to_dt(ms: Optional[int]) -> Optional[datetime]:
    """앱의 Unix 밀리초 → DB 시각"""
    return datetime.fromtimestamp(ms / 1000, tz=timezone.utc) if ms else None


def to_ms(dt: Optional[datetime]) -> Optional[int]:
    """DB 시각 → 앱의 Unix 밀리초"""
    if dt is None:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return int(dt.timestamp() * 1000)


def cut(text: Optional[str], n: int) -> Optional[str]:
    """DB 칸 길이를 넘는 글자는 잘라서 저장 (저장 실패 방지)"""
    return text[:n] if text else text


def clothing_out(c: Clothing) -> dict:
    return {
        "id": c.id,
        "name": c.name,
        "category": c.category,
        "color": c.color,
        "imageUrl": c.image_url or "",
        "isFavorite": c.favorited_at is not None,
        "clientId": c.client_id,
        "colorName": c.color_name,
        "subCategory": c.sub_category,
        "detailType": c.detail_type,
        "season": c.season,
        "style": c.style,
        "isCutout": c.is_cutout,
        "aiTagged": c.ai_tagged,
        "addedAt": to_ms(c.added_at),
        "favoritedAt": to_ms(c.favorited_at),
        "keptAt": to_ms(c.kept_at),
    }


def product_out(p: ProductRow, liked_ids: set[int] = frozenset()) -> dict:
    return {
        "id": p.id,
        "name": p.name,
        "brand": p.brand,
        "price": p.price,
        "originalPrice": p.original_price,
        "rating": p.rating,
        "reviewCount": p.review_count,
        "bgColor": p.bg_color,
        "badge": p.badge,
        "imageUrl": p.image_url or "",
        "productUrl": p.product_url or "",
        "category": p.category,
        "mallName": p.mall_name,
        "isLiked": p.id in liked_ids,
    }


def measurement_out(m: BodyMeasurement) -> dict:
    return {
        "id": m.id,
        "bodyType": m.body_type,
        "confidence": m.confidence,
        "shoulderRatio": m.shoulder_ratio,
        "waistRatio": m.waist_ratio,
        "hipRatio": m.hip_ratio,
        "legRatio": m.leg_ratio,
        "shoulderToHip": m.shoulder_hip_ratio,
        "waistToHip": m.waist_hip_ratio,
        "heightCm": m.height_cm,
        "shoulderCm": m.shoulder_cm,
        "waistCm": m.waist_cm,
        "hipCm": m.hip_cm,
        "legCm": m.leg_cm,
        "torsoCm": m.torso_cm,
        "measuredAt": to_ms(m.measured_at),
        "isSample": bool(m.is_sample),
    }


def latest_body_type(db: Session, user: User) -> Optional[str]:
    """가장 최근 체형 분석 결과 (없으면 사용자 정보의 체형)"""
    latest = db.scalar(
        select(BodyMeasurement.body_type)
        .where(BodyMeasurement.user_id == user.id, BodyMeasurement.is_sample.is_not(True))  # 샘플 제외
        .order_by(BodyMeasurement.measured_at.desc(), BodyMeasurement.id.desc())
        .limit(1)
    )
    return latest or user.body_type


def liked_product_ids(db: Session, user: User) -> set[int]:
    return set(db.scalars(select(ProductLike.product_id).where(ProductLike.user_id == user.id)))


def user_clothes(db: Session, user: User, category: str = "전체") -> list[Clothing]:
    """하트 누른 옷이 위(누른 순서대로), 나머지는 등록 순서"""
    q = select(Clothing).where(Clothing.user_id == user.id)
    if category != "전체":
        q = q.where(Clothing.category == category)
    q = q.order_by(Clothing.favorited_at.is_(None), Clothing.favorited_at, Clothing.id)
    return list(db.scalars(q))


# ──────────────────────────────────────────────
# 홈
# ──────────────────────────────────────────────

@app.get("/api/home", response_model=HomeResponse, tags=["홈"])
def get_home():
    """홈 화면에 필요한 데이터 일괄 조회 (날씨 + 오늘의 코디 + 인기 상품)"""
    return {
        "greeting": "안녕하세요",
        "weather": data.WEATHER,
        "todayOutfits": data.TODAY_OUTFITS,
        "popularItems": data.POPULAR_ITEMS,
    }


@app.get("/api/outfits/weather", response_model=WeatherOutfitResponse, tags=["코디 추천"])
def get_weather_outfits():
    """오늘 날씨 기반 추천 코디 3종 (고정 예시)"""
    return {"weather": data.WEATHER, "outfits": data.WEATHER_OUTFITS}


# ──────────────────────────────────────────────
# 옷장 (DB)
# ──────────────────────────────────────────────

@app.post("/api/wardrobe/sync", response_model=SyncResult, tags=["옷장"])
def sync_wardrobe(req: WardrobeSyncRequest, db: Session = Depends(get_db),
                  user: User = Depends(current_user)):
    """
    폰 옷장 전체 목록을 받아 DB 를 똑같이 맞춘다 (앱 WardrobeStore 가 바뀔 때마다 호출).

    - 폰에 있는 옷: 없으면 추가, 있으면 내용 갱신 (앱의 옷 id = clientId 로 구분)
    - 폰에 없는 옷: DB 에서 삭제
    → 폰이 기준이고 서버는 그대로 따라 저장한다. 사진 파일은 올리지 않고 주소만 저장.
    """
    existing = {c.client_id: c for c in db.scalars(select(Clothing).where(Clothing.user_id == user.id))}
    seen: set[str] = set()
    saved = 0
    for it in req.items:
        if not it.id or it.id in seen:
            continue
        seen.add(it.id)
        c = existing.get(it.id)
        if c is None:
            c = Clothing(user_id=user.id, client_id=cut(it.id, 64))
            db.add(c)
        c.name = cut(it.name, 100) or "이름 없음"
        c.category = cut(it.category, 20) or "기타"
        c.sub_category = cut(it.subCategory, 30)
        c.detail_type = cut(it.detailType, 30)
        c.season = cut(it.season, 20)
        c.style = cut(it.style, 20)
        c.color = "#%06X" % (it.colorValue & 0xFFFFFF)
        c.color_name = cut(it.colorName, 20)
        c.image_url = it.imageUri
        c.original_uri = it.originalUri
        c.cutout_uri = it.cutoutUri
        c.garment_uri = it.garmentUri
        c.is_cutout = it.isCutout
        c.ai_tagged = it.aiTagged
        c.added_at = to_dt(it.addedAt)
        c.favorited_at = to_dt(it.favoritedAt)
        c.kept_at = to_dt(it.keptAt)
        saved += 1

    deleted = 0
    for client_id, c in existing.items():
        if client_id not in seen:
            db.delete(c)
            deleted += 1
    db.commit()
    return {"saved": saved, "deleted": deleted, "total": len(seen)}


@app.get("/api/wardrobe", response_model=WardrobeResponse, tags=["옷장"])
def get_wardrobe(category: str = "전체", db: Session = Depends(get_db),
                 user: User = Depends(current_user)):
    """내 옷장 조회. ?category=상의 처럼 카테고리 필터 가능 (하트 옷이 위)"""
    total = db.scalar(select(func.count()).select_from(Clothing).where(Clothing.user_id == user.id))
    return {
        "totalCount": total,
        "categories": data.WARDROBE_CATEGORIES,
        "items": [clothing_out(c) for c in user_clothes(db, user, category)],
    }


@app.post("/api/wardrobe", response_model=ClothingItem, status_code=201, tags=["옷장"])
def add_clothing(req: AddClothingRequest, db: Session = Depends(get_db),
                 user: User = Depends(current_user)):
    """
    옷 한 벌 추가 (Swagger 시연·다른 클라이언트용).
    앱은 /api/wardrobe/sync 로 저장하므로, 폰에 없는 이 옷은 다음 앱 동기화 때 지워진다.
    """
    c = Clothing(
        user_id=user.id,
        client_id=f"server-{uuid.uuid4().hex[:12]}",
        name=cut(req.name, 100),
        category=cut(req.category, 20),
        color=cut(req.color, 9),
        image_url=req.imageUrl,
        added_at=datetime.now(timezone.utc),
    )
    db.add(c)
    db.commit()
    db.refresh(c)
    return clothing_out(c)


@app.get("/api/wardrobe/favorites", response_model=WardrobeResponse, tags=["옷장"])
def get_favorites(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """즐겨찾기(하트)한 의류만 조회 — 하트 누른 순서대로"""
    favs = list(db.scalars(
        select(Clothing)
        .where(Clothing.user_id == user.id, Clothing.favorited_at.is_not(None))
        .order_by(Clothing.favorited_at)
    ))
    return {
        "totalCount": len(favs),
        "categories": data.WARDROBE_CATEGORIES,
        "items": [clothing_out(c) for c in favs],
    }


def _own_clothing(db: Session, user: User, item_id: int) -> Clothing:
    c = db.get(Clothing, item_id)
    if c is None or c.user_id != user.id:
        raise HTTPException(404, "해당 의류를 찾을 수 없습니다")
    return c


@app.post("/api/wardrobe/{item_id}/favorite", response_model=ClothingItem, tags=["옷장"])
def toggle_favorite(item_id: int, db: Session = Depends(get_db), user: User = Depends(current_user)):
    """옷장 아이템 즐겨찾기(하트) 토글 — 누른 시각을 저장해 위로 올린다"""
    c = _own_clothing(db, user, item_id)
    c.favorited_at = None if c.favorited_at else datetime.now(timezone.utc)
    db.commit()
    return clothing_out(c)


@app.delete("/api/wardrobe/{item_id}", status_code=204, tags=["옷장"])
def delete_clothing(item_id: int, db: Session = Depends(get_db), user: User = Depends(current_user)):
    """옷장에서 의류 삭제"""
    db.delete(_own_clothing(db, user, item_id))
    db.commit()


# ──────────────────────────────────────────────
# 착용 기록 (DB)
# ──────────────────────────────────────────────

@app.post("/api/wear-logs/sync", response_model=SyncResult, tags=["착용 기록"])
def sync_wear_logs(req: WearLogSyncRequest, db: Session = Depends(get_db),
                   user: User = Depends(current_user)):
    """폰 착용 기록 전체를 받아 DB 를 똑같이 맞춘다 (날짜가 같으면 갱신, 폰에 없는 날짜는 삭제)."""
    existing = {w.date: w for w in db.scalars(select(WearLog).where(WearLog.user_id == user.id))}
    seen: set[str] = set()
    saved = 0
    for log in req.logs:
        if len(log.date) != 10 or log.date in seen or not log.itemIds:
            continue
        seen.add(log.date)
        w = existing.get(log.date)
        if w is None:
            w = WearLog(user_id=user.id, date=log.date)
            db.add(w)
        w.item_ids = list(log.itemIds)
        w.memo = log.memo
        saved += 1

    deleted = 0
    for date, w in existing.items():
        if date not in seen:
            db.delete(w)
            deleted += 1
    db.commit()
    return {"saved": saved, "deleted": deleted, "total": len(seen)}


@app.get("/api/wear-logs", response_model=WearLogResponse, tags=["착용 기록"])
def get_wear_logs(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """착용 기록 조회 (최근 날짜부터)"""
    logs = list(db.scalars(
        select(WearLog).where(WearLog.user_id == user.id).order_by(WearLog.date.desc())
    ))
    return {
        "totalCount": len(logs),
        "logs": [{"date": w.date, "itemIds": w.item_ids or [], "memo": w.memo or ""} for w in logs],
    }


# ──────────────────────────────────────────────
# 체형 측정 기록 (DB) — 사진은 폰 안에서만 분석, 서버에는 숫자만 저장
# ──────────────────────────────────────────────

@app.post("/api/body-measurements", response_model=BodyMeasurementOut, status_code=201, tags=["AI 분석"])
def save_body_measurement(req: BodyMeasurementIn, db: Session = Depends(get_db),
                          user: User = Depends(current_user)):
    """앱 체형 분석이 끝나면 결과(체형·비율·cm 추정값)를 기록으로 저장"""
    m = BodyMeasurement(
        user_id=user.id,
        body_type=cut(req.bodyType, 20),
        confidence=max(0, min(100, req.confidence)),
        shoulder_ratio=req.shoulderRatio,
        waist_ratio=req.waistRatio,
        hip_ratio=req.hipRatio,
        leg_ratio=req.legRatio,
        shoulder_hip_ratio=req.shoulderToHip,
        waist_hip_ratio=req.waistToHip,
        height_cm=req.heightCm,
        shoulder_cm=req.shoulderCm,
        waist_cm=req.waistCm,
        hip_cm=req.hipCm,
        leg_cm=req.legCm,
        torso_cm=req.torsoCm,
        measured_at=to_dt(req.measuredAt) or datetime.now(timezone.utc),
    )
    db.add(m)
    user.body_type = m.body_type   # 마이페이지 체형 표시용
    db.commit()
    db.refresh(m)
    return measurement_out(m)


@app.get("/api/body-measurements", response_model=BodyMeasurementHistory, tags=["AI 분석"])
def get_body_measurements(limit: int = 30, db: Session = Depends(get_db),
                          user: User = Depends(current_user)):
    """체형 측정 기록 (최근 것부터, 기본 30개) — 변화 그래프용"""
    rows = list(db.scalars(
        select(BodyMeasurement)
        .where(BodyMeasurement.user_id == user.id)
        .order_by(BodyMeasurement.measured_at.desc(), BodyMeasurement.id.desc())
        .limit(max(1, min(limit, 200)))
    ))
    total = db.scalar(select(func.count()).select_from(BodyMeasurement)
                      .where(BodyMeasurement.user_id == user.id))
    return {"totalCount": total, "measurements": [measurement_out(m) for m in rows]}


@app.delete("/api/body-measurements", status_code=204, tags=["AI 분석"])
def delete_body_measurements(samplesOnly: bool = False, db: Session = Depends(get_db),
                             user: User = Depends(current_user)):
    """
    내 체형 측정 기록 삭제 (개인정보 보호 — '기록 지우기').
    ?samplesOnly=true 면 시연용 샘플 기록만 지운다. 지운 샘플은 다시 생기지 않는다.
    """
    q = delete(BodyMeasurement).where(BodyMeasurement.user_id == user.id)
    if samplesOnly:
        q = q.where(BodyMeasurement.is_sample.is_(True))
    else:
        user.body_type = None
    db.execute(q)
    db.commit()


# ──────────────────────────────────────────────
# AI 체형 분석
# ──────────────────────────────────────────────

@app.post("/api/body-analysis", response_model=BodyAnalysisResult, tags=["AI 분석"])
async def analyze_body(photo: UploadFile = File(...)):
    """
    전신 사진 업로드 → AI 체형 분석 결과 반환.

    현재는 사진을 서버에 저장만 하고 고정 결과(모래시계형)를 반환.
    추후 이 함수 안에서 OpenCV/MediaPipe 분석 코드를 호출하도록 교체:

        result = analyze_with_mediapipe(saved_path)   # ← 여기만 바꾸면 됨
    """
    if photo.content_type not in ("image/jpeg", "image/png", "image/webp"):
        raise HTTPException(400, "jpg/png/webp 이미지만 업로드할 수 있습니다")

    ext = Path(photo.filename or "photo.jpg").suffix or ".jpg"
    saved_path = UPLOAD_DIR / f"{uuid.uuid4().hex}{ext}"
    with saved_path.open("wb") as f:
        shutil.copyfileobj(photo.file, f)

    # TODO: 여기서 실제 체형 분석 모델 호출 (Python + OpenCV — 기획서 기술 스택)
    return data.BODY_ANALYSIS_RESULT


@app.post("/api/object-detection", response_model=ObjectDetectionResponse, tags=["AI 분석"])
async def detect_objects(
    photo: UploadFile = File(...),
    conf_threshold: float = Form(0.25),
):
    """
    이미지 업로드 → YOLOv8 객체 탐지 결과 반환.

    object_detect 프로젝트(Streamlit 앱)의 탐지 기능을 API로 통합한 엔드포인트.
    - multipart/form-data, 필드명 `photo` (jpg/png/webp/bmp)
    - `conf_threshold` 폼 필드로 신뢰도 임계값 조절 가능 (기본 0.25, 0.1~0.9)
    - 응답: 객체 목록 + 라벨별 개수 통계 + 바운딩 박스가 그려진 결과 이미지 URL

    활용 예: 옷장에 의류 사진 등록 시 의류/액세서리 자동 인식,
    전신 사진에서 인물(person) 검출 후 체형 분석 파이프라인 연결 등.
    """
    if photo.content_type not in ("image/jpeg", "image/png", "image/webp", "image/bmp"):
        raise HTTPException(400, "jpg/png/webp/bmp 이미지만 업로드할 수 있습니다")
    if not 0.1 <= conf_threshold <= 0.9:
        raise HTTPException(400, "conf_threshold는 0.1 ~ 0.9 사이여야 합니다")

    try:
        image = Image.open(io.BytesIO(await photo.read()))
        image.load()
    except Exception:
        raise HTTPException(400, "이미지 파일을 해석할 수 없습니다")

    detections, counts, result_img = detection.detect_objects(
        image, conf_threshold=conf_threshold
    )

    # 결과 이미지를 uploads/에 저장하고 정적 URL로 제공
    result_name = f"detect_{uuid.uuid4().hex}.jpg"
    result_path = UPLOAD_DIR / result_name
    bgr = result_img[:, :, ::-1]  # RGB → BGR (cv2.imwrite용)
    if not cv2.imwrite(str(result_path), bgr):
        raise HTTPException(500, "결과 이미지 저장에 실패했습니다")

    return {
        "totalCount": len(detections),
        "detections": detections,
        "counts": counts,
        "resultImageUrl": f"/uploads/{result_name}",
    }


@app.post("/api/clothing/classify", response_model=ClothingClassifyResponse, tags=["AI 분석"])
async def classify_clothing(
    photo: UploadFile = File(...),
    conf_threshold: float = Form(0.3),
):
    """
    옷 사진 업로드 → 옷 종류 후보 목록 (크게 잡힌 순서).

    - DeepFashion2 YOLOv8-seg: 옷 13종류 + 옷 윤곽 마스크(흑백 PNG, base64)
    - COCO YOLOv8: 가방류(handbag / backpack / suitcase)만 추가
    - multipart/form-data, 필드명 `photo`
    앱은 누끼 결과를 흰 배경으로 채워서 보낸다.
    """
    if photo.content_type not in ("image/jpeg", "image/png", "image/webp", "image/bmp"):
        raise HTTPException(400, "jpg/png/webp/bmp 이미지만 업로드할 수 있습니다")
    if not 0.1 <= conf_threshold <= 0.9:
        raise HTTPException(400, "conf_threshold는 0.1 ~ 0.9 사이여야 합니다")

    try:
        image = Image.open(io.BytesIO(await photo.read()))
        image.load()
    except Exception:
        raise HTTPException(400, "이미지 파일을 해석할 수 없습니다")

    try:
        return fashion_detection.classify_clothing(image, conf_threshold=conf_threshold)
    except FileNotFoundError as e:
        raise HTTPException(503, str(e))


@app.get("/api/style-suggestions", response_model=StyleSuggestionResponse, tags=["AI 분석"])
def get_style_suggestions(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """체형별 스타일 제안 (최근 체형 분석 결과 기준 + AI추천 상품)"""
    liked = liked_product_ids(db, user)
    recommended = db.scalars(select(ProductRow).where(ProductRow.badge == "AI추천").order_by(ProductRow.id))
    body_type = latest_body_type(db, user) or data.BODY_ANALYSIS_RESULT["bodyType"]
    return {
        "bodyType": body_type,
        "summary": data.BODY_ANALYSIS_RESULT["summary"],
        "recommendedStyles": data.RECOMMENDED_STYLES,
        "avoidStyles": data.AVOID_STYLES,
        "recommendedProducts": [product_out(p, liked) for p in recommended],
    }


@app.get("/api/style-analysis", response_model=StyleAnalysisResponse, tags=["AI 분석"])
def get_style_analysis():
    """스타일 분석 결과 (도넛 차트 분포 + 키워드 + AI 코멘트)"""
    return {
        "breakdown": data.STYLE_BREAKDOWN,
        "keywords": data.STYLE_KEYWORDS,
        "aiComment": data.STYLE_AI_COMMENT,
    }


# ──────────────────────────────────────────────
# 쇼핑 (DB)
# ──────────────────────────────────────────────

@app.get("/api/products", response_model=ShopResponse, tags=["쇼핑"])
def get_products(search: str = "", db: Session = Depends(get_db), user: User = Depends(current_user)):
    """추천 쇼핑 상품 목록. ?search=셔츠 처럼 상품명·브랜드 검색 가능"""
    q = select(ProductRow).order_by(ProductRow.id)
    if search:
        like = f"%{search}%"
        q = q.where(ProductRow.name.ilike(like) | ProductRow.brand.ilike(like))
    liked = liked_product_ids(db, user)
    return {
        "banner": "AI가 내 체형·취향을 분석해 추천한 상품이에요",
        "products": [product_out(p, liked) for p in db.scalars(q)],
    }


# ※ /api/products/likes 는 /api/products/{product_id} 보다 먼저 선언해야 한다 (주소 겹침 방지)
@app.get("/api/products/likes", response_model=ProductLikesResponse, tags=["쇼핑"])
def get_product_likes(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """하트 누른 상품 목록 (누른 순서대로) — 앱 쇼핑 화면이 하트 상품을 위로 올릴 때 사용"""
    rows = db.execute(
        select(ProductLike.product_id, ProductRow.name, ProductLike.liked_at)
        .join(ProductRow, ProductRow.id == ProductLike.product_id)
        .where(ProductLike.user_id == user.id)
        .order_by(ProductLike.liked_at, ProductLike.product_id)
    ).all()
    return {
        "totalCount": len(rows),
        "items": [{"productId": pid, "name": name, "likedAt": to_ms(at) or 0} for pid, name, at in rows],
    }


@app.post("/api/products/{product_id}/like", response_model=Product, tags=["쇼핑"])
def set_product_like(product_id: int, req: ProductLikeRequest, db: Session = Depends(get_db),
                     user: User = Depends(current_user)):
    """상품 하트 켜기/끄기 (liked=true/false). 같은 값을 여러 번 보내도 결과는 같다."""
    product = db.get(ProductRow, product_id)
    if product is None:
        raise HTTPException(404, "상품을 찾을 수 없습니다")
    like = db.get(ProductLike, (user.id, product_id))
    if req.liked and like is None:
        db.add(ProductLike(user_id=user.id, product_id=product_id, liked_at=datetime.now(timezone.utc)))
    elif not req.liked and like is not None:
        db.delete(like)
    db.commit()
    return product_out(product, liked_product_ids(db, user))


@app.get("/api/products/{product_id}", response_model=ProductDetail, tags=["쇼핑"])
def get_product_detail(product_id: int, db: Session = Depends(get_db), user: User = Depends(current_user)):
    """상품 상세 (색상/사이즈 옵션, 할인율, 구매 링크)"""
    p = db.get(ProductRow, product_id)
    if p is None:
        raise HTTPException(404, "상품을 찾을 수 없습니다")
    discount = None
    if p.original_price:
        discount = round((1 - p.price / p.original_price) * 100)
    return {
        **product_out(p, liked_product_ids(db, user)),
        "description": p.description or "",
        "colors": p.colors or [p.bg_color],
        "sizes": p.sizes or ["S", "M", "L", "XL"],
        "discountPercent": discount,
        "lowestPriceInfo": f"판매가: {p.price:,}원 · {p.mall_name or '쇼핑몰'}",
    }


@app.get("/api/size-charts", response_model=SizeChartResponse, tags=["쇼핑"])
def get_size_charts(brand: str = "", category: str = "", db: Session = Depends(get_db)):
    """브랜드 사이즈표 조회 (?brand=무신사스탠다드&category=하의). 데이터는 import_size_charts.py 로 넣는다."""
    q = select(SizeChart)
    if brand:
        q = q.where(SizeChart.brand == brand)
    if category:
        q = q.where(SizeChart.category == category)
    rows = list(db.scalars(q.order_by(SizeChart.brand, SizeChart.category, SizeChart.id)))
    return {
        "totalCount": len(rows),
        "rows": [{
            "brand": r.brand, "category": r.category, "sizeLabel": r.size_label,
            "shoulderCm": r.shoulder_cm, "chestCm": r.chest_cm, "waistCm": r.waist_cm,
            "hipCm": r.hip_cm, "lengthCm": r.length_cm,
            "measureType": r.measure_type, "sourceUrl": r.source_url or "",
        } for r in rows],
    }


# ──────────────────────────────────────────────
# 프로필 / 온보딩 (DB)
# ──────────────────────────────────────────────

def _profile_payload(db: Session, user: User) -> dict:
    wardrobe = db.scalar(select(func.count()).select_from(Clothing).where(Clothing.user_id == user.id))
    outfits = db.scalar(select(func.count()).select_from(WearLog).where(WearLog.user_id == user.id))
    fav_clothes = db.scalar(select(func.count()).select_from(Clothing)
                            .where(Clothing.user_id == user.id, Clothing.favorited_at.is_not(None)))
    fav_products = db.scalar(select(func.count()).select_from(ProductLike).where(ProductLike.user_id == user.id))
    return {
        "nickname": user.nickname,
        "email": user.email or data.PROFILE["email"],
        "wardrobeCount": wardrobe,
        "outfitCount": outfits,                      # 착용 기록 남긴 날 수
        "favoriteCount": fav_clothes + fav_products,  # 하트 옷 + 하트 상품
        "bodyType": latest_body_type(db, user),
        "preferredStyles": user.preferred_styles or [],
    }


@app.get("/api/profile", response_model=Profile, tags=["프로필"])
def get_profile(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """마이페이지 프로필 (DB 에서 센 옷·착용 기록·하트 개수 + 최근 체형)"""
    return _profile_payload(db, user)


@app.get("/api/styles", response_model=list[str], tags=["프로필"])
def get_style_options():
    """온보딩 - 선택 가능한 스타일 목록"""
    return data.STYLE_OPTIONS


@app.post("/api/profile/styles", response_model=Profile, tags=["프로필"])
def update_preferred_styles(req: UpdateStylesRequest, db: Session = Depends(get_db),
                            user: User = Depends(current_user)):
    """온보딩 '시작하기' - 선호 스타일 저장"""
    invalid = [s for s in req.styles if s not in data.STYLE_OPTIONS]
    if invalid:
        raise HTTPException(400, f"유효하지 않은 스타일: {invalid}")
    user.preferred_styles = list(req.styles)
    db.commit()
    return _profile_payload(db, user)


# ──────────────────────────────────────────────
# 실시간 날씨 (WeatherViewModel)
# ──────────────────────────────────────────────

@app.get("/api/weather", response_model=WeatherNowResponse, tags=["날씨"])
def get_weather(lat: float = weather.DEFAULT_LAT, lon: float = weather.DEFAULT_LON):
    """
    위도·경도로 실시간 날씨 조회 → 온도·습도·상태 + 옷차림 조언.

    앱 WeatherViewModel이 기기 위치(lat/lon)로 호출.
    서버 환경변수 OWM_API_KEY 가 설정돼 있으면 실제 OpenWeatherMap을 호출하고,
    없으면 기본값(맑음 22도)으로 응답한다.
    """
    w = weather.fetch_weather(lat, lon)
    advice = weather.make_advice(w["temp"], w["humidity"], w["conditionId"])
    return {**w, "advice": advice}


# ──────────────────────────────────────────────
# 스타일 취향 학습 (DB)
# ──────────────────────────────────────────────

def _preference_payload(db: Session, user: User) -> dict:
    """평가 기록(style_ratings)으로 점수를 다시 계산 — 좋아요 +2, 싫어요 -1 (앱과 같은 가중치)"""
    scores = {s: 0 for s in data.STYLE_PREFERENCE_STYLES}
    rated = 0
    for style, liked in db.execute(
        select(StyleRating.style, StyleRating.liked).where(StyleRating.user_id == user.id)
    ):
        rated += 1
        if style in scores:
            scores[style] += 2 if liked else -1
    ranked = sorted(scores.items(), key=lambda kv: kv[1], reverse=True)
    return {
        "scores": [{"style": s, "score": sc} for s, sc in ranked],
        "ratedCount": rated,
        "topStyle": ranked[0][0],
    }


@app.get("/api/style-preference", response_model=StylePreferenceResponse, tags=["AI 분석"])
def get_style_preference(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """취향 학습 현황(스타일별 점수·누적 평가 수·최상위 스타일) 조회"""
    return _preference_payload(db, user)


@app.post("/api/style-preference/rate", response_model=StylePreferenceResponse, tags=["AI 분석"])
def rate_style_preference(req: RatePreferenceRequest, db: Session = Depends(get_db),
                          user: User = Depends(current_user)):
    """코디 카드 좋아요/싫어요 평가를 DB 에 한 줄씩 기록"""
    if req.style not in data.STYLE_PREFERENCE_STYLES:
        raise HTTPException(400, f"알 수 없는 스타일: {req.style}")
    db.add(StyleRating(user_id=user.id, style=req.style, liked=req.like))
    db.commit()
    return _preference_payload(db, user)


# ──────────────────────────────────────────────
# 가상 피팅 (VirtualFittingScreen)
# ──────────────────────────────────────────────

@app.get("/api/virtual-fitting/items", response_model=VirtualFittingResponse, tags=["가상 피팅"])
def get_fitting_items(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """가상 피팅에서 입어볼 후보 의류 목록 (DB 옷장 + 샘플)"""
    wardrobe = [
        {"id": c.id, "name": c.name, "category": c.category, "imageUrl": c.image_url or ""}
        for c in user_clothes(db, user)
    ]
    return {"items": wardrobe + data.FITTING_SAMPLES}


@app.get("/", include_in_schema=False)
def root():
    return {"service": "FashionProject API", "docs": "/docs", "database": db_label()}
