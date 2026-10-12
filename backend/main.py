"""
FashionProject 백엔드 (FastAPI)

앱 화면 ↔ 엔드포인트 매핑
─────────────────────────────────────────────
HomeScreen           → GET  /api/home
WeatherViewModel     → GET  /api/weather             (실시간 날씨 + 조언)
WeatherOutfitScreen  → GET  /api/outfits/weather
WardrobeScreen       → GET  /api/wardrobe
                       GET  /api/wardrobe/favorites  (하트 옷만)
                       POST /api/wardrobe
                       POST /api/wardrobe/{id}/favorite
                       DELETE /api/wardrobe/{id}
AddClothingScreen    → POST /api/clothing/classify   (DeepFashion2 옷 종류 분류)
                       POST /api/object-detection    (YOLOv8 COCO 객체 탐지, 기존)
BodyAnalysisScreen   → POST /api/body-analysis        (전신 사진 업로드)
StylePreferenceScreen→ GET  /api/style-preference
                       POST /api/style-preference/rate (좋아요/싫어요)
VirtualFittingScreen → GET  /api/virtual-fitting/items
StyleSuggestionScreen→ GET  /api/style-suggestions
StyleAnalysisScreen  → GET  /api/style-analysis
ShopScreen           → GET  /api/products
ProductDetailScreen  → GET  /api/products/{id}
MyPageScreen         → GET  /api/profile
OnboardingScreen     → GET  /api/styles
                       POST /api/profile/styles
─────────────────────────────────────────────

실행:  uvicorn main:app --host 0.0.0.0 --port 8000 --reload
문서:  http://localhost:8000/docs  (Swagger 자동 생성)
"""
import io
import shutil
import uuid
from pathlib import Path

import cv2
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from PIL import Image

import data
import detection
import fashion_detection
import weather
from schemas import (
    AddClothingRequest, BodyAnalysisResult, ClothingClassifyResponse, ClothingItem, HomeResponse,
    ObjectDetectionResponse, Product, ProductDetail, Profile,
    RatePreferenceRequest, ShopResponse, StyleAnalysisResponse, StyleScore,
    StylePreferenceResponse, StyleSuggestionResponse, UpdateStylesRequest,
    VirtualFittingResponse, WardrobeResponse, WeatherNowResponse,
    WeatherOutfitResponse,
)

app = FastAPI(
    title="FashionProject API",
    description="AI 기반 개인 맞춤 패션 추천 서비스 백엔드",
    version="1.0.0",
)

# 앱(에뮬레이터/실기기)에서 접근할 수 있도록 CORS 전체 허용 (개발용)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 업로드된 전신 사진 저장 폴더 (정적 서빙)
UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")


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


# ──────────────────────────────────────────────
# 날씨 코디
# ──────────────────────────────────────────────

@app.get("/api/outfits/weather", response_model=WeatherOutfitResponse, tags=["코디 추천"])
def get_weather_outfits():
    """오늘 날씨 기반 AI 추천 코디 3종"""
    return {"weather": data.WEATHER, "outfits": data.WEATHER_OUTFITS}


# ──────────────────────────────────────────────
# 옷장
# ──────────────────────────────────────────────

@app.get("/api/wardrobe", response_model=WardrobeResponse, tags=["옷장"])
def get_wardrobe(category: str = "전체"):
    """내 옷장 조회. ?category=상의 처럼 카테고리 필터 가능"""
    items = data.WARDROBE_ITEMS
    if category != "전체":
        items = [i for i in items if i["category"] == category]
    return {
        "totalCount": len(data.WARDROBE_ITEMS),
        "categories": data.WARDROBE_CATEGORIES,
        "items": items,
    }


@app.post("/api/wardrobe", response_model=ClothingItem, status_code=201, tags=["옷장"])
def add_clothing(req: AddClothingRequest):
    """옷장에 의류 추가 ('+ 의류 추가' 버튼)"""
    new_id = max(i["id"] for i in data.WARDROBE_ITEMS) + 1
    item = {
        "id": new_id,
        "name": req.name,
        "category": req.category,
        "color": req.color,
        "imageUrl": req.imageUrl or f"https://loremflickr.com/300/380/clothing?lock={new_id + 100}",
        "isFavorite": False,
    }
    data.WARDROBE_ITEMS.append(item)
    return item


@app.post("/api/wardrobe/{item_id}/favorite", response_model=ClothingItem, tags=["옷장"])
def toggle_favorite(item_id: int):
    """옷장 아이템 즐겨찾기(하트) 토글"""
    for item in data.WARDROBE_ITEMS:
        if item["id"] == item_id:
            item["isFavorite"] = not item["isFavorite"]
            return item
    raise HTTPException(404, "해당 의류를 찾을 수 없습니다")


@app.delete("/api/wardrobe/{item_id}", status_code=204, tags=["옷장"])
def delete_clothing(item_id: int):
    """옷장에서 의류 삭제"""
    for i, item in enumerate(data.WARDROBE_ITEMS):
        if item["id"] == item_id:
            data.WARDROBE_ITEMS.pop(i)
            return
    raise HTTPException(404, "해당 의류를 찾을 수 없습니다")


@app.get("/api/wardrobe/favorites", response_model=WardrobeResponse, tags=["옷장"])
def get_favorites():
    """즐겨찾기(하트)한 의류만 조회 — 앱에서 하트 옷을 상단에 모아 보여줄 때 사용"""
    favs = [i for i in data.WARDROBE_ITEMS if i.get("isFavorite")]
    return {
        "totalCount": len(favs),
        "categories": data.WARDROBE_CATEGORIES,
        "items": favs,
    }


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
def get_style_suggestions():
    """체형별 스타일 제안 (추천 스타일 / 피해야 할 스타일 / 추천 상품)"""
    recommended_products = [p for p in data.PRODUCTS if p["badge"] == "AI추천"]
    return {
        "bodyType": data.BODY_ANALYSIS_RESULT["bodyType"],
        "summary": data.BODY_ANALYSIS_RESULT["summary"],
        "recommendedStyles": data.RECOMMENDED_STYLES,
        "avoidStyles": data.AVOID_STYLES,
        "recommendedProducts": recommended_products,
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
# 쇼핑
# ──────────────────────────────────────────────

@app.get("/api/products", response_model=ShopResponse, tags=["쇼핑"])
def get_products(search: str = ""):
    """추천 쇼핑 상품 목록. ?search=니트 처럼 검색 가능"""
    products = data.PRODUCTS
    if search:
        products = [p for p in products if search in p["name"] or search in p["brand"]]
    return {
        "banner": "AI가 내 체형·취향을 분석해 추천한 상품이에요",
        "products": products,
    }


@app.get("/api/products/{product_id}", response_model=ProductDetail, tags=["쇼핑"])
def get_product_detail(product_id: int):
    """상품 상세 (색상/사이즈 옵션, 할인율, 최저가 정보 포함)"""
    product = next((p for p in data.PRODUCTS if p["id"] == product_id), None)
    if product is None:
        raise HTTPException(404, "상품을 찾을 수 없습니다")

    extra = data.PRODUCT_DETAIL_EXTRA.get(product_id, {})
    discount = None
    if product["originalPrice"]:
        discount = round((1 - product["price"] / product["originalPrice"]) * 100)

    return {
        **product,
        "name": extra.get("name", product["name"]),
        "description": extra.get("description", ""),
        "colors": extra.get("colors", ["#EDE3D5", "#F5F2EE", "#C5C5C0", "#3A3A3A"]),
        "sizes": extra.get("sizes", ["XS", "S", "M", "L", "XL"]),
        "discountPercent": discount,
        "lowestPriceInfo": extra.get(
            "lowestPriceInfo",
            f"최저가: {product['price']:,}원 · 네이버쇼핑, 무신사, 29CM 등 비교",
        ),
    }


# ──────────────────────────────────────────────
# 프로필 / 온보딩
# ──────────────────────────────────────────────

@app.get("/api/profile", response_model=Profile, tags=["프로필"])
def get_profile():
    """마이페이지 프로필 (통계 + 체형 + 선호 스타일)"""
    data.PROFILE["wardrobeCount"] = len(data.WARDROBE_ITEMS)
    return data.PROFILE


@app.get("/api/styles", response_model=list[str], tags=["프로필"])
def get_style_options():
    """온보딩 - 선택 가능한 스타일 목록"""
    return data.STYLE_OPTIONS


@app.post("/api/profile/styles", response_model=Profile, tags=["프로필"])
def update_preferred_styles(req: UpdateStylesRequest):
    """온보딩 '시작하기' - 선호 스타일 저장"""
    invalid = [s for s in req.styles if s not in data.STYLE_OPTIONS]
    if invalid:
        raise HTTPException(400, f"유효하지 않은 스타일: {invalid}")
    data.PROFILE["preferredStyles"] = req.styles
    return data.PROFILE


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
# 스타일 취향 학습 (StylePreferenceScreen)
# ──────────────────────────────────────────────

def _preference_payload() -> dict:
    state = data.STYLE_PREFERENCE_STATE
    ranked = sorted(state["scores"].items(), key=lambda kv: kv[1], reverse=True)
    top = ranked[0][0] if ranked else data.STYLE_PREFERENCE_STYLES[0]
    return {
        "scores": [{"style": s, "score": sc} for s, sc in ranked],
        "ratedCount": state["ratedCount"],
        "topStyle": top,
    }


@app.get("/api/style-preference", response_model=StylePreferenceResponse, tags=["AI 분석"])
def get_style_preference():
    """취향 학습 현황(스타일별 점수·누적 평가 수·최상위 스타일) 조회"""
    return _preference_payload()


@app.post("/api/style-preference/rate", response_model=StylePreferenceResponse, tags=["AI 분석"])
def rate_style_preference(req: RatePreferenceRequest):
    """
    코디 카드 좋아요/싫어요 평가 반영.
    like=true → +2점, like=false → -1점 (앱 StylePreferenceStore와 동일 가중치)
    """
    state = data.STYLE_PREFERENCE_STATE
    if req.style not in state["scores"]:
        raise HTTPException(400, f"알 수 없는 스타일: {req.style}")
    state["scores"][req.style] += 2 if req.like else -1
    state["ratedCount"] += 1
    return _preference_payload()


# ──────────────────────────────────────────────
# 가상 피팅 (VirtualFittingScreen)
# ──────────────────────────────────────────────

@app.get("/api/virtual-fitting/items", response_model=VirtualFittingResponse, tags=["가상 피팅"])
def get_fitting_items():
    """가상 피팅에서 입어볼 후보 의류 목록 (옷장 등록 의류 + 샘플)"""
    wardrobe = [
        {"id": i["id"], "name": i["name"], "category": i["category"], "imageUrl": i["imageUrl"]}
        for i in data.WARDROBE_ITEMS
    ]
    return {"items": wardrobe + data.FITTING_SAMPLES}


@app.get("/", include_in_schema=False)
def root():
    return {"service": "FashionProject API", "docs": "/docs"}
