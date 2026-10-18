"""Selective DB/history integration. Existing image and recommendation endpoints stay in main.py."""
import base64
import binascii
import hashlib
import re
import uuid
from datetime import date as date_cls, datetime, timedelta, timezone
from typing import Optional
from fastapi import APIRouter, Depends, Header, HTTPException, Response
from sqlalchemy import delete, func, select
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session, undefer
from sqlalchemy import inspect as sa_inspect
import data
from database import get_db, schema_has
from db_models import (BodyMeasurement, Clothing, ClothingPhoto, OutfitPlan, Product, ProductInterest, ProductLike,
    RecommendationLog, SavedOutfit, SizeChart, StyleRating, TasteEvent, User, WearLog)
from schemas import (AddClothingRequest, ClothingItem, WardrobeResponse, WardrobeSyncRequest,
    SyncResult, WearLogSyncRequest, WearLogResponse, BodyMeasurementIn, BodyMeasurementOut,
    BodyMeasurementHistory, SizeChartResponse, Profile, UpdateStylesRequest,
    StylePreferenceResponse, RatePreferenceRequest, GenderRequest, ProductInterestSyncRequest,
    ProductInterestResponse, PopularInterestResponse, TasteEventSyncRequest, TasteSummary,
    ClothingPhotoIn, ClothingPhotoOut, RecommendationLogSyncRequest, RecommendationStats,
    TasteEventListResponse, DeleteMyDataResult, SavedOutfitSyncRequest, SavedOutfitResponse,
    OutfitPlanSyncRequest, OutfitPlanResponse, OverviewStats)

app = APIRouter()

def current_user(
    db: Session = Depends(get_db),
    x_device_id: Optional[str] = Header(
        None, description="앱이 생성한 기기 번호. DB API에 필수이며 로그인 인증을 대신하지는 않음"),
) -> User:
    """
    로그인 기능이 생기기 전까지 사용자 구분 방법:
    - 앱은 폰마다 만든 기기 번호를 X-Device-Id 헤더로 보낸다 → 폰마다 따로 저장 (팀원끼리 덮어쓰지 않음)
    - 헤더가 없으면 공유 데모 계정 대신 요청을 거절한다.
    로그인을 붙이면 이 함수만 '토큰 → 사용자' 로 바꾸면 된다.
    """
    if not x_device_id or not re.fullmatch(r"[A-Za-z0-9-]{1,64}", x_device_id):
        raise HTTPException(400, "X-Device-Id is required")
    cond = User.device_id == x_device_id
    user = db.scalar(select(User).where(cond))
    if user is None:
        user = User(device_id=x_device_id, nickname="나의 스타일", preferred_styles=[], sample_records_added=True)
        db.add(user)
        try:
            db.commit()
        except IntegrityError:
            db.rollback()
            user = db.scalar(select(User).where(cond))
            if user is None:
                raise
    return user


def to_dt(ms: Optional[int]) -> Optional[datetime]:
    """앱의 Unix 밀리초 → DB 시각 (소수 계산 없이 밀리초까지 정확히)"""
    if not ms:
        return None
    return datetime.fromtimestamp(ms // 1000, tz=timezone.utc) + timedelta(milliseconds=ms % 1000)


def to_ms(dt: Optional[datetime]) -> Optional[int]:
    """DB 시각 → 앱의 Unix 밀리초"""
    if dt is None:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return int(round(dt.timestamp() * 1000))


def cut(text: Optional[str], n: int) -> Optional[str]:
    """DB 칸 길이를 넘는 글자는 잘라서 저장 (저장 실패 방지)"""
    return text[:n] if text else text


def loaded(obj, name: str):
    """deferred 칸을 DB 에 다시 묻지 않고, 이미 읽어 둔 값만 돌려준다 (안 읽었으면 None)"""
    return None if name in sa_inspect(obj).unloaded else getattr(obj, name)


def require_table(db: Session, table: str) -> None:
    """마이그레이션을 아직 적용하지 않아 표가 없으면 503 + 안내 (앱은 대기열에 두었다가 나중에 다시 보낸다)"""
    if not schema_has(db, table):
        raise HTTPException(503, f"DB 에 {table} 표가 없어요 — 서버 PC 에서 python apply_migrations.py 를 실행해 주세요")


def has_prices(db: Session) -> bool:
    return schema_has(db, "clothes", "price") and schema_has(db, "clothes", "brand")


def clothing_out(c: Clothing, has_photo: bool = False) -> dict:
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
        "originalUri": c.original_uri,
        "cutoutUri": c.cutout_uri,
        "garmentUri": c.garment_uri,
        "hasPhoto": has_photo,
        "brand": loaded(c, "brand"),
        "price": loaded(c, "price"),
    }


def photo_ids(db: Session, user: User) -> set[str]:
    """사진 백업이 있는 옷 client_id 목록 (003 을 적용하지 않은 DB 면 빈 목록)"""
    if not schema_has(db, "clothing_photos"):
        return set()
    return set(db.scalars(select(ClothingPhoto.client_id).where(ClothingPhoto.user_id == user.id)))


def measurement_out(m: BodyMeasurement) -> dict:
    return {
        "id": m.id,
        "clientId": m.client_id,
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


def user_clothes(db: Session, user: User, category: str = "전체") -> list[Clothing]:
    """하트 누른 옷이 위(누른 순서대로), 나머지는 등록 순서"""
    q = select(Clothing).where(Clothing.user_id == user.id)
    if has_prices(db):
        q = q.options(undefer(Clothing.brand), undefer(Clothing.price))
    if category != "전체":
        q = q.where(Clothing.category == category)
    q = q.order_by(Clothing.favorited_at.is_(None), Clothing.favorited_at, Clothing.id)
    return list(db.scalars(q))


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
    prices = has_prices(db)
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
        if prices:   # 004 를 적용하지 않은 DB 면 가격 · 브랜드만 건너뛴다
            c.brand = cut((it.brand or "").strip(), 50) or None
            c.price = it.price if it.price is not None and 0 < it.price <= 100_000_000 else None
        saved += 1

    deleted = 0
    for client_id, c in existing.items():
        if client_id not in seen:
            db.delete(c)
            deleted += 1
    # 폰에서 지운 옷의 사진 백업도 함께 지운다 (003 을 적용한 DB 만)
    if schema_has(db, "clothing_photos"):
        for p in db.scalars(select(ClothingPhoto).where(ClothingPhoto.user_id == user.id)):
            if p.client_id not in seen:
                db.delete(p)
    db.commit()
    return {"saved": saved, "deleted": deleted, "total": len(seen)}


@app.get("/api/wardrobe", response_model=WardrobeResponse, tags=["옷장"])
def get_wardrobe(category: str = "전체", db: Session = Depends(get_db),
                 user: User = Depends(current_user)):
    """내 옷장 조회. ?category=상의 처럼 카테고리 필터 가능 (하트 옷이 위)"""
    total = db.scalar(select(func.count()).select_from(Clothing).where(Clothing.user_id == user.id))
    photos = photo_ids(db, user)
    return {
        "totalCount": total,
        "categories": data.WARDROBE_CATEGORIES,
        "items": [clothing_out(c, c.client_id in photos) for c in user_clothes(db, user, category)],
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


@app.post("/api/body-measurements", response_model=BodyMeasurementOut, status_code=201, tags=["AI 분석"])
def save_body_measurement(req: BodyMeasurementIn, db: Session = Depends(get_db),
                          user: User = Depends(current_user)):
    """앱 체형 분석이 끝나면 결과(체형·비율·cm 추정값)를 기록으로 저장"""
    existing = db.scalar(select(BodyMeasurement).where(
        BodyMeasurement.user_id == user.id, BodyMeasurement.client_id == req.clientId))
    if existing is not None:
        return measurement_out(existing)
    m = BodyMeasurement(
        client_id=req.clientId,
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
    user.body_type = m.body_type
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        existing = db.scalar(select(BodyMeasurement).where(
            BodyMeasurement.user_id == user.id, BodyMeasurement.client_id == req.clientId))
        if existing is None:
            raise
        return measurement_out(existing)
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


def _profile_payload(db: Session, user: User) -> dict:
    wardrobe = db.scalar(select(func.count()).select_from(Clothing).where(Clothing.user_id == user.id))
    outfits = db.scalar(select(func.count()).select_from(WearLog).where(WearLog.user_id == user.id))
    fav_clothes = db.scalar(select(func.count()).select_from(Clothing)
                            .where(Clothing.user_id == user.id, Clothing.favorited_at.is_not(None)))
    interests = (db.scalar(select(func.count()).select_from(ProductInterest).where(ProductInterest.user_id == user.id))
                 if schema_has(db, "product_interests") else 0)
    return {
        "nickname": user.nickname,
        "email": user.email or "",
        "wardrobeCount": wardrobe,
        "outfitCount": outfits,                      # 착용 기록 남긴 날 수
        "favoriteCount": fav_clothes + interests,    # 하트 옷 + 관심 상품
        "bodyType": latest_body_type(db, user),
        "preferredStyles": user.preferred_styles or [],
        "gender": user.gender if schema_has(db, "users", "gender") else None,
        "interestCount": interests,
    }


@app.get("/api/profile", response_model=Profile, tags=["프로필"])
def get_profile(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """마이페이지 프로필 (DB 에서 센 옷·착용 기록·하트 개수 + 최근 체형)"""
    return _profile_payload(db, user)


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
# 성별 (마이페이지)
# ──────────────────────────────────────────────

@app.post("/api/profile/gender", response_model=Profile, tags=["프로필"])
def update_gender(req: GenderRequest, db: Session = Depends(get_db), user: User = Depends(current_user)):
    """마이페이지 성별 설정 저장 (FEMALE / MALE, null 이면 해제) — 앱 UserPreferencesViewModel 과 같은 값"""
    if not schema_has(db, "users", "gender"):
        raise HTTPException(503, "DB 에 성별 칸이 없어요 — 서버 PC 에서 python apply_migrations.py 를 실행해 주세요")
    user.gender = req.gender
    db.commit()
    return _profile_payload(db, user)


# ──────────────────────────────────────────────
# 관심 상품 (쇼핑 · 상품 상세 · 옷장 '관심 옷' 하트)
# ──────────────────────────────────────────────

def _catalog_by_key(db: Session, keys: list[str]) -> dict[str, Product]:
    if not keys:
        return {}
    return {p.product_key: p for p in db.scalars(select(Product).where(Product.product_key.in_(keys)))}


@app.post("/api/product-interests/sync", response_model=SyncResult, tags=["쇼핑"])
def sync_product_interests(req: ProductInterestSyncRequest, db: Session = Depends(get_db),
                           user: User = Depends(current_user)):
    """
    폰의 관심 상품 목록(누른 순서)을 받아 DB 를 똑같이 맞춘다 (앱 ProductInterestStore 가 바뀔 때마다 호출).
    새 상품은 추가, 순서는 갱신, 폰에 없는 상품은 삭제. 처음 저장된 시각(added_at)은 유지한다.
    """
    require_table(db, "product_interests")
    existing = {r.product_key: r for r in db.scalars(select(ProductInterest).where(ProductInterest.user_id == user.id))}
    saved = 0
    for position, key in enumerate(req.productIds):
        row = existing.get(key)
        if row is None:
            db.add(ProductInterest(user_id=user.id, product_key=key, position=position,
                                   added_at=datetime.now(timezone.utc)))
            saved += 1
        elif row.position != position:
            row.position = position
            saved += 1
    keep = set(req.productIds)
    deleted = 0
    for key, row in existing.items():
        if key not in keep:
            db.delete(row)
            deleted += 1
    db.commit()
    return {"saved": saved, "deleted": deleted, "total": len(keep)}


@app.get("/api/product-interests", response_model=ProductInterestResponse, tags=["쇼핑"])
def get_product_interests(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """내 관심 상품 (누른 순서). 카탈로그(products)에 있는 상품은 이름 · 가격 · 사진도 함께."""
    require_table(db, "product_interests")
    rows = list(db.scalars(select(ProductInterest).where(ProductInterest.user_id == user.id)
                           .order_by(ProductInterest.position, ProductInterest.id)))
    catalog = _catalog_by_key(db, [r.product_key for r in rows])
    items = []
    for r in rows:
        p = catalog.get(r.product_key)
        items.append({
            "productKey": r.product_key, "position": r.position, "addedAt": to_ms(r.added_at),
            "name": p.name if p else None, "brand": p.brand if p else None,
            "price": p.price if p else None, "imageUrl": (p.image_url or None) if p else None,
            "productUrl": (p.product_url or None) if p else None,
        })
    return {"totalCount": len(items), "items": items}


@app.get("/api/product-interests/popular", response_model=PopularInterestResponse, tags=["쇼핑"])
def popular_interests(limit: int = 10, db: Session = Depends(get_db)):
    """
    여러 사용자가 관심 상품으로 많이 담은 상품 순위 (기기 번호 불필요, 개인 정보 없이 개수만).
    발표 · 쇼핑 화면의 "많이 찜한 상품"용.
    """
    require_table(db, "product_interests")
    rows = db.execute(
        select(ProductInterest.product_key, func.count(func.distinct(ProductInterest.user_id)).label("n"))
        .group_by(ProductInterest.product_key)
        .order_by(func.count(func.distinct(ProductInterest.user_id)).desc(), ProductInterest.product_key)
        .limit(max(1, min(limit, 50)))
    ).all()
    catalog = _catalog_by_key(db, [k for k, _ in rows])
    return {"items": [{
        "productKey": k, "interestCount": n,
        "name": catalog[k].name if k in catalog else None,
        "brand": catalog[k].brand if k in catalog else None,
        "imageUrl": (catalog[k].image_url or None) if k in catalog else None,
    } for k, n in rows]}


# ──────────────────────────────────────────────
# 취향 평가 기록 (색 · 종류 · 색 조합 방식)
# ──────────────────────────────────────────────

@app.post("/api/taste-events/sync", response_model=SyncResult, tags=["취향 학습"])
def sync_taste_events(req: TasteEventSyncRequest, db: Session = Depends(get_db),
                      user: User = Depends(current_user)):
    """
    폰의 취향 평가 기록 전체(코디 👍👎 · 취향 카드 · 착용)를 받아 DB 를 똑같이 맞춘다.
    (평가 시각, 종류)가 같으면 같은 기록 — 새 기록만 추가하고, 폰에서 사라진 기록은 삭제한다.
    (같은 날 착용 기록을 다시 저장하면 앱이 그날 착용 평가를 바꿔 쓰기 때문)
    """
    require_table(db, "taste_events")
    existing = {(e.at_ms, e.source): e for e in db.scalars(select(TasteEvent).where(TasteEvent.user_id == user.id))}
    seen = set()
    saved = 0
    for ev in req.events:
        key = (ev.at, ev.source)
        if key in seen:
            continue
        seen.add(key)
        if key in existing:
            continue
        db.add(TasteEvent(
            user_id=user.id, at_ms=ev.at, source=ev.source, liked=ev.liked,
            colors=[cut(c, 20) for c in ev.colors], kinds=[cut(k, 20) for k in ev.kinds],
            pattern=cut(ev.pattern, 20), style=cut(ev.style, 20),
            item_ids=[cut(i, 64) for i in ev.itemIds], date=ev.date,
        ))
        saved += 1
    deleted = 0
    for key, e in existing.items():
        if key not in seen:
            db.delete(e)
            deleted += 1
    try:
        db.commit()
    except IntegrityError:   # 같은 폰의 동기화 두 개가 동시에 들어온 경우 — 다음 동기화에서 맞춰진다
        db.rollback()
        raise HTTPException(409, "동시에 들어온 동기화 요청이 있어요. 잠시 후 다시 보내 주세요") from None
    return {"saved": saved, "deleted": deleted, "total": len(seen)}


@app.get("/api/taste-events/summary", response_model=TasteSummary, tags=["취향 학습"])
def taste_summary(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """DB 에 쌓인 평가 기록으로 색 · 종류 · 조합 방식 · 스타일 점수를 다시 계산 (앱과 같은 규칙)"""
    require_table(db, "taste_events")
    colors: dict[str, int] = {}
    kinds: dict[str, int] = {}
    patterns: dict[str, int] = {}
    styles: dict[str, int] = {}
    rated = worn = total = 0
    for e in db.scalars(select(TasteEvent).where(TasteEvent.user_id == user.id)):
        total += 1
        if e.source == "worn":
            worn += 1
            delta = 1
        else:
            rated += 1
            delta = 2 if e.liked else -1
        for c in set(e.colors or []):
            colors[c] = colors.get(c, 0) + delta
        for k in set(e.kinds or []):
            kinds[k] = kinds.get(k, 0) + delta
        if e.pattern:
            patterns[e.pattern] = patterns.get(e.pattern, 0) + delta
        if e.style:
            styles[e.style] = styles.get(e.style, 0) + delta

    def ranked(d: dict[str, int]) -> dict[str, int]:
        return dict(sorted(d.items(), key=lambda kv: (-kv[1], kv[0])))

    return {"totalCount": total, "ratedCount": rated, "wornCount": worn, "colors": ranked(colors),
            "kinds": ranked(kinds), "patterns": ranked(patterns), "styles": ranked(styles)}


# ──────────────────────────────────────────────
# 쇼핑 상품 카탈로그 (main.py 의 /api/products 가 사용)
# ──────────────────────────────────────────────

def _catalog_from_data() -> list[dict]:
    return [{
        "id": i + 1, "productKey": p["productKey"], "name": p["name"], "brand": p["brand"],
        "price": p["price"], "originalPrice": p["originalPrice"], "rating": p["rating"],
        "reviewCount": p["reviewCount"], "bgColor": p["bgColor"], "badge": p["badge"],
        "imageUrl": p["imageUrl"], "productUrl": p["productKey"] if p["productKey"].startswith("http") else None,
        "category": p["category"], "mallName": p["mallName"],
    } for i, p in enumerate(data.SHOP_CATALOG)]


def product_out(p: Product) -> dict:
    return {
        "id": p.id, "productKey": p.product_key, "name": p.name, "brand": p.brand, "price": p.price,
        "originalPrice": p.original_price, "rating": p.rating, "reviewCount": p.review_count,
        "bgColor": p.bg_color, "badge": p.badge, "imageUrl": p.image_url or "",
        "productUrl": p.product_url or None, "category": p.category, "mallName": p.mall_name,
    }


def catalog_rows(db: Optional[Session]) -> tuple[list[dict], bool]:
    """
    상품 목록 — DB products 표(seed_catalog.py 로 채움)가 있으면 그 내용, 없으면 data.SHOP_CATALOG.
    두 번째 값은 DB 에서 읽었는지 여부.
    """
    if db is not None:
        try:
            rows = list(db.scalars(select(Product).where(Product.product_key.is_not(None)).order_by(Product.id)))
            if rows:
                return [product_out(p) for p in rows], True
        except SQLAlchemyError:
            db.rollback()
    return _catalog_from_data(), False



# ──────────────────────────────────────────────
# 옷 사진 백업 (백업 코드로 다른 폰에 복원할 때 사용)
# ──────────────────────────────────────────────

PHOTO_MAX_BYTES = 2 * 1024 * 1024
_MAGIC = {
    "image/png": lambda b: b.startswith(b"\x89PNG\r\n\x1a\n"),
    "image/jpeg": lambda b: b.startswith(b"\xff\xd8\xff"),
    "image/webp": lambda b: b[:4] == b"RIFF" and b[8:12] == b"WEBP",
}


@app.post("/api/wardrobe/photos", response_model=ClothingPhotoOut, tags=["옷장"])
def upload_clothing_photo(req: ClothingPhotoIn, db: Session = Depends(get_db),
                          user: User = Depends(current_user)):
    """
    옷장 옷 사진 한 장 백업 (앱이 긴 변 720px 이하로 줄여서 보낸다, 2MB 이하).
    먼저 /api/wardrobe/sync 로 옷이 저장돼 있어야 한다 — 없으면 404 (앱은 나중에 다시 보낸다).
    같은 옷의 사진을 다시 보내면 바꿔 쓴다.
    """
    require_table(db, "clothing_photos")
    try:
        raw = base64.b64decode(req.data, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(422, "사진 데이터(base64)가 올바르지 않아요") from None
    if not raw or len(raw) > PHOTO_MAX_BYTES:
        raise HTTPException(413, "사진은 2MB 이하만 저장할 수 있어요")
    if not _MAGIC[req.mime](raw):
        raise HTTPException(422, "사진 형식이 mime 과 맞지 않아요")
    exists = db.scalar(select(Clothing.id).where(Clothing.user_id == user.id, Clothing.client_id == req.clientId))
    if exists is None:
        raise HTTPException(404, "옷장에 없는 옷이에요 (옷장 동기화 뒤에 다시 보내 주세요)")
    digest = hashlib.sha256(raw).hexdigest()
    row = db.scalar(select(ClothingPhoto).where(ClothingPhoto.user_id == user.id,
                                                ClothingPhoto.client_id == req.clientId))
    if row is None:
        row = ClothingPhoto(user_id=user.id, client_id=req.clientId)
        db.add(row)
    row.mime, row.data, row.size, row.sha256 = req.mime, raw, len(raw), digest
    row.updated_at = datetime.now(timezone.utc)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "같은 사진이 동시에 들어왔어요. 잠시 후 다시 보내 주세요") from None
    return {"clientId": req.clientId, "mime": req.mime, "size": len(raw), "sha256": digest}


@app.get("/api/wardrobe/photos/{client_id}", tags=["옷장"],
         responses={200: {"content": {"image/png": {}, "image/jpeg": {}, "image/webp": {}}}})
def get_clothing_photo(client_id: str, db: Session = Depends(get_db), user: User = Depends(current_user)):
    """백업한 옷 사진 받기 (복원용). 내 기기 번호의 사진만 받을 수 있다."""
    require_table(db, "clothing_photos")
    row = db.scalar(select(ClothingPhoto).where(ClothingPhoto.user_id == user.id,
                                                ClothingPhoto.client_id == client_id))
    if row is None:
        raise HTTPException(404, "사진 백업이 없어요")
    return Response(content=row.data, media_type=row.mime, headers={"Cache-Control": "no-store"})


# ──────────────────────────────────────────────
# 취향 평가 기록 목록 (복원용)
# ──────────────────────────────────────────────

@app.get("/api/taste-events", response_model=TasteEventListResponse, tags=["취향 학습"])
def list_taste_events(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """취향 평가 기록 전체 (오래된 순) — 백업 코드로 복원할 때 폰에 다시 넣는다"""
    require_table(db, "taste_events")
    rows = list(db.scalars(select(TasteEvent).where(TasteEvent.user_id == user.id)
                           .order_by(TasteEvent.at_ms, TasteEvent.id)))
    return {"totalCount": len(rows), "events": [{
        "at": e.at_ms, "source": e.source, "liked": e.liked, "colors": e.colors or [], "kinds": e.kinds or [],
        "pattern": e.pattern, "style": e.style, "itemIds": e.item_ids or [], "date": e.date,
    } for e in rows]}


# ──────────────────────────────────────────────
# 추천 기록 · 추천 성능 (AI 성능 측정)
# ──────────────────────────────────────────────

def item_key(ids: list[str]) -> str:
    return "+".join(sorted(set(ids)))


@app.post("/api/recommendations/sync", response_model=SyncResult, tags=["추천 기록"])
def sync_recommendations(req: RecommendationLogSyncRequest, db: Session = Depends(get_db),
                         user: User = Depends(current_user)):
    """
    폰이 보여 준 추천 코디 기록을 받아 새 기록만 더한다.
    폰은 최근 기록만 들고 있으므로, 폰에서 사라진 오래된 기록도 DB 에는 남겨 둔다 (지우지 않음).
    """
    require_table(db, "recommendation_logs")
    existing = set(db.execute(select(RecommendationLog.date, RecommendationLog.context, RecommendationLog.item_key)
                              .where(RecommendationLog.user_id == user.id)).tuples())
    saved = 0
    for log in req.logs:
        key = (log.date, log.context, cut(item_key(log.itemIds), 512))
        if key in existing:
            continue
        existing.add(key)
        db.add(RecommendationLog(user_id=user.id, date=log.date, context=log.context, scope=cut(log.scope, 12),
                                 rank=log.rank, item_ids=sorted(set(log.itemIds)), item_key=key[2],
                                 shown_at=to_dt(log.shownAt)))
        saved += 1
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "동시에 들어온 동기화 요청이 있어요. 잠시 후 다시 보내 주세요") from None
    total = db.scalar(select(func.count()).select_from(RecommendationLog).where(RecommendationLog.user_id == user.id))
    return {"saved": saved, "deleted": 0, "total": total}


WORN_WINDOW_DAYS = 2   # 추천한 날 ~ 2일 뒤까지 입으면 "추천대로 입음"


def _group(shown: int, liked: int, disliked: int, worn: int, partial: int) -> dict:
    rate = lambda n, d: round(n / d, 3) if d else 0.0
    return {"shown": shown, "liked": liked, "disliked": disliked, "worn": worn, "wornPartial": partial,
            "likeRate": rate(liked, shown), "ratedLikeRate": rate(liked, liked + disliked) if liked + disliked else None,
            "wornRate": rate(worn, shown), "wornPartialRate": rate(partial, shown)}


def recommendation_stats(db: Session, user_id: Optional[int] = None) -> dict:
    """
    추천 기록을 👍👎(taste_events, 코디 평가) · 실제 착용(wear_logs)과 맞춰 본다.
    - liked / disliked: 같은 옷 조합에 마지막으로 누른 평가
    - worn: 추천한 날 ~ 2일 뒤 착용 기록에 코디 옷이 모두 있음, wornPartial: 절반 이상 있음
    """
    def where(model):
        return [model.user_id == user_id] if user_id is not None else []

    logs = list(db.scalars(select(RecommendationLog).where(*where(RecommendationLog))))
    ratings: dict[tuple[int, frozenset], tuple[int, bool]] = {}
    for e in db.scalars(select(TasteEvent).where(TasteEvent.source == "outfit", *where(TasteEvent))):
        k = (e.user_id, frozenset(e.item_ids or []))
        if k not in ratings or e.at_ms >= ratings[k][0]:
            ratings[k] = (e.at_ms, e.liked)
    wear: dict[tuple[int, str], set] = {(w.user_id, w.date): set(w.item_ids or [])
                                         for w in db.scalars(select(WearLog).where(*where(WearLog)))}

    totals = [0, 0, 0, 0, 0]
    by_ctx: dict[str, list[int]] = {}
    for log in logs:
        items = frozenset(log.item_ids or [])
        if not items:
            continue
        rating = ratings.get((log.user_id, items))
        worn = partial = False
        try:
            start = date_cls.fromisoformat(log.date)
        except ValueError:
            continue
        for d in range(WORN_WINDOW_DAYS + 1):
            got = wear.get((log.user_id, (start + timedelta(days=d)).isoformat()))
            if not got:
                continue
            hit = len(items & got)
            worn = worn or hit == len(items)
            partial = partial or hit * 2 >= len(items)
        row = [1, int(bool(rating and rating[1])), int(bool(rating and not rating[1])), int(worn), int(partial)]
        ctx = log.context.split(":")[0] if log.context.startswith("situation") else log.context
        bucket = by_ctx.setdefault(ctx, [0, 0, 0, 0, 0])
        for i, v in enumerate(row):
            totals[i] += v
            bucket[i] += v
    dates = sorted(log.date for log in logs)
    return {
        "users": len({log.user_id for log in logs}),
        "since": dates[0] if dates else None,
        "until": dates[-1] if dates else None,
        "total": _group(*totals),
        "byContext": {k: _group(*v) for k, v in sorted(by_ctx.items())},
    }


@app.get("/api/recommendations/stats", response_model=RecommendationStats, tags=["추천 기록"])
def my_recommendation_stats(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """내 추천 성능 — 보여 준 코디 중 👍 · 실제로 입은 비율 (홈 / 오늘 / 5일 예보 / 상황별)"""
    require_table(db, "recommendation_logs")
    return recommendation_stats(db, user.id)


@app.get("/api/recommendations/stats/all", response_model=RecommendationStats, tags=["추천 기록"])
def all_recommendation_stats(db: Session = Depends(get_db)):
    """모든 사용자를 합친 추천 성능 (발표 · AI 성능 측정표용, 개인 정보 없이 개수와 비율만)"""
    require_table(db, "recommendation_logs")
    return recommendation_stats(db)


# ──────────────────────────────────────────────
# 서버에 저장된 내 데이터 모두 지우기 (개인정보 삭제 요청)
# ──────────────────────────────────────────────

@app.delete("/api/me", response_model=DeleteMyDataResult, tags=["프로필"])
def delete_my_data(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """
    이 기기 번호로 서버 DB 에 저장된 내 기록을 모두 지운다 (옷장 · 사진 백업 · 착용 · 체형 · 취향 · 관심 상품 · 추천 기록 · 사용자).
    폰 안의 데이터는 지우지 않는다. 앱은 지운 뒤 서버 백업을 끈다.
    """
    deleted: dict[str, int] = {}
    for model in (ClothingPhoto, Clothing, WearLog, BodyMeasurement, StyleRating, TasteEvent,
                  ProductInterest, ProductLike, RecommendationLog, SavedOutfit, OutfitPlan):
        if not schema_has(db, model.__tablename__):   # 아직 만들지 않은 표는 지울 것도 없음
            deleted[model.__tablename__] = 0
            continue
        result = db.execute(delete(model).where(model.user_id == user.id))
        deleted[model.__tablename__] = result.rowcount or 0
    db.execute(delete(User).where(User.id == user.id))
    deleted["users"] = 1
    db.commit()
    return {"deleted": deleted}


# ──────────────────────────────────────────────
# 저장한 코디 (룩북) · 코디 일정
# ──────────────────────────────────────────────

@app.post("/api/saved-outfits/sync", response_model=SyncResult, tags=["코디 저장 · 일정"])
def sync_saved_outfits(req: SavedOutfitSyncRequest, db: Session = Depends(get_db),
                       user: User = Depends(current_user)):
    """폰의 저장한 코디 목록(최근 저장 순)과 DB 를 똑같이 맞춘다. 같은 옷 조합은 하나로 본다."""
    require_table(db, "saved_outfits")
    existing = {r.client_key: r for r in db.scalars(select(SavedOutfit).where(SavedOutfit.user_id == user.id))}
    seen: set[str] = set()
    saved = 0
    for position, o in enumerate(req.outfits):
        key = item_key(o.itemIds)
        if key in seen:
            continue
        seen.add(key)
        row = existing.get(key)
        if row is None:
            row = SavedOutfit(user_id=user.id, client_key=key)
            db.add(row)
        row.item_ids = sorted(set(o.itemIds))
        row.style, row.pattern = cut(o.style, 20), cut(o.pattern, 20)
        row.note = cut(o.note, 100)
        row.position = position
        row.saved_at = to_dt(o.savedAt) or row.saved_at or datetime.now(timezone.utc)
        saved += 1
    deleted = 0
    for key, row in existing.items():
        if key not in seen:
            db.delete(row)
            deleted += 1
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "동시에 들어온 동기화 요청이 있어요. 잠시 후 다시 보내 주세요") from None
    return {"saved": saved, "deleted": deleted, "total": len(seen)}


@app.get("/api/saved-outfits", response_model=SavedOutfitResponse, tags=["코디 저장 · 일정"])
def get_saved_outfits(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """저장한 코디 (최근 저장 순)"""
    require_table(db, "saved_outfits")
    rows = list(db.scalars(select(SavedOutfit).where(SavedOutfit.user_id == user.id)
                           .order_by(SavedOutfit.position, SavedOutfit.id)))
    return {"totalCount": len(rows), "outfits": [{
        "id": r.client_key, "itemIds": r.item_ids or [], "style": r.style, "pattern": r.pattern,
        "note": r.note or "", "savedAt": to_ms(r.saved_at),
    } for r in rows]}


@app.post("/api/outfit-plans/sync", response_model=SyncResult, tags=["코디 저장 · 일정"])
def sync_outfit_plans(req: OutfitPlanSyncRequest, db: Session = Depends(get_db),
                      user: User = Depends(current_user)):
    """폰의 코디 일정 전체(날짜마다 하나)와 DB 를 똑같이 맞춘다."""
    require_table(db, "outfit_plans")
    existing = {r.date: r for r in db.scalars(select(OutfitPlan).where(OutfitPlan.user_id == user.id))}
    saved = 0
    for p in req.plans:
        row = existing.get(p.date)
        if row is None:
            row = OutfitPlan(user_id=user.id, date=p.date)
            db.add(row)
        row.item_ids = list(dict.fromkeys(p.itemIds))
        row.note = cut(p.note, 100)
        row.created_at = to_dt(p.createdAt) or row.created_at or datetime.now(timezone.utc)
        saved += 1
    keep = {p.date for p in req.plans}
    deleted = 0
    for d, row in existing.items():
        if d not in keep:
            db.delete(row)
            deleted += 1
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "동시에 들어온 동기화 요청이 있어요. 잠시 후 다시 보내 주세요") from None
    return {"saved": saved, "deleted": deleted, "total": len(keep)}


@app.get("/api/outfit-plans", response_model=OutfitPlanResponse, tags=["코디 저장 · 일정"])
def get_outfit_plans(db: Session = Depends(get_db), user: User = Depends(current_user)):
    """코디 일정 (날짜 순)"""
    require_table(db, "outfit_plans")
    rows = list(db.scalars(select(OutfitPlan).where(OutfitPlan.user_id == user.id).order_by(OutfitPlan.date)))
    return {"totalCount": len(rows), "plans": [{
        "date": r.date, "itemIds": r.item_ids or [], "note": r.note or "", "createdAt": to_ms(r.created_at),
    } for r in rows]}


# ──────────────────────────────────────────────
# 전체 통계 (발표용 — 개인 정보 없이 숫자만)
# ──────────────────────────────────────────────

def _count(db: Session, model) -> int:
    if not schema_has(db, model.__tablename__):
        return 0
    return db.scalar(select(func.count()).select_from(model)) or 0


def _top(rows, n: int = 5) -> list[dict]:
    counts: dict[str, int] = {}
    for name in rows:
        if name:
            counts[name] = counts.get(name, 0) + 1
    return [{"name": k, "count": v} for k, v in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))[:n]]


@app.get("/api/stats/overview", response_model=OverviewStats, tags=["상태"])
def overview_stats(db: Session = Depends(get_db)):
    """
    모든 사용자를 합친 DB 현황 — 발표 · 보고서용 (기기 번호 불필요, 개인 정보 없이 개수 · 순위만).
    옷 종류 · 색 순위, 선호 스타일 순위, 가격을 넣은 옷 평균 가격, 코디 일정대로 입은 비율.
    """
    priced = ([p for p in db.scalars(select(Clothing.price).where(Clothing.price.is_not(None))) if p and p > 0]
              if has_prices(db) else [])
    styles = [s for row in db.scalars(select(User.preferred_styles)) for s in (row or [])]
    wear = {(w.user_id, w.date): set(w.item_ids or []) for w in db.scalars(select(WearLog))}
    today = datetime.now(timezone.utc).date().isoformat()
    plan_days = followed = 0
    plans = db.scalars(select(OutfitPlan).where(OutfitPlan.date < today)) if schema_has(db, "outfit_plans") else []
    for p in plans:
        got = wear.get((p.user_id, p.date))
        if got is None:
            continue
        plan_days += 1
        followed += int(all(i in got for i in (p.item_ids or [])))
    return {
        "users": _count(db, User), "clothes": _count(db, Clothing), "clothingPhotos": _count(db, ClothingPhoto),
        "wearLogs": _count(db, WearLog), "savedOutfits": _count(db, SavedOutfit), "outfitPlans": _count(db, OutfitPlan),
        "productInterests": _count(db, ProductInterest), "tasteEvents": _count(db, TasteEvent),
        "recommendations": _count(db, RecommendationLog), "bodyMeasurements": _count(db, BodyMeasurement),
        "topCategories": _top(db.scalars(select(Clothing.category))),
        "topColors": _top(db.scalars(select(Clothing.color_name))),
        "topPreferredStyles": _top(styles),
        "pricedClothes": len(priced),
        "averagePrice": round(sum(priced) / len(priced)) if priced else None,
        "planDays": plan_days, "planFollowed": followed,
    }
