"""
FashionProject 백엔드 (FastAPI)

앱 화면 ↔ 엔드포인트 매핑
─────────────────────────────────────────────
HomeScreen           → GET  /api/home
WeatherViewModel     → GET  /api/weather             (실시간 날씨 + 조언)
                       GET  /api/weather/forecast    (5일 예보, 서버가 날씨 키를 대신 씀)
WeatherOutfitScreen  → GET  /api/outfits/weather
WardrobeScreen       → GET  /api/wardrobe
                       GET  /api/wardrobe/favorites  (하트 옷만)
                       POST /api/wardrobe
                       POST /api/wardrobe/{id}/favorite
                       DELETE /api/wardrobe/{id}
AddClothingScreen    → POST /api/clothing/classify   (DeepFashion2 옷 종류 분류)
                       POST /api/object-detection    (YOLOv8 COCO 객체 탐지, 기존)
BodyAnalysisScreen   → 기기 내 사진 분석; /api/body-analysis는 410으로 종료
StylePreferenceScreen→ GET  /api/style-preference
                       POST /api/style-preference/rate (좋아요/싫어요)
VirtualFittingScreen → GET  /api/virtual-fitting/items
StyleSuggestionScreen→ GET  /api/style-suggestions
StyleAnalysisScreen  → GET  /api/style-analysis
ShopScreen           → GET  /api/products
                       GET  /api/naver/image-search  (네이버 이미지 검색, 서버가 네이버 키를 대신 씀)
ProductDetailScreen  → GET  /api/products/{id}
MyPageScreen         → GET  /api/profile
OnboardingScreen     → GET  /api/styles
                       POST /api/profile/styles
─────────────────────────────────────────────

실행:  uvicorn main:app --host 0.0.0.0 --port 8000 --reload
문서:  http://localhost:8000/docs  (Swagger 자동 생성)
"""
import base64
import io
from contextlib import asynccontextmanager
from database import initialize_database, database_status, optional_db
from db_api import app as db_router, catalog_rows
from auth_api import router as auth_router
from auth_provider_gate import router as provider_router
from tryon_proxy import create_router as tryon_router, close_service as close_tryon

import cv2
from fastapi import Depends, FastAPI, File, Form, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image

import data
import detection
import fashion_detection
import naver
import unsplash
import weather
from schemas import (
    BodyAnalysisResult, ClothingClassifyResponse, HomeResponse, NaverImageSearchResponse,
    ObjectDetectionResponse, ProductDetail, ShopResponse, StyleAnalysisResponse, StyleSuggestionResponse, VirtualFittingResponse, WeatherNowResponse,
    WeatherOutfitResponse,
)

MAX_ANALYSIS_UPLOAD = 10 * 1024 * 1024


async def read_analysis_photo(photo: UploadFile) -> Image.Image:
    raw = await photo.read(MAX_ANALYSIS_UPLOAD + 1)
    if len(raw) > MAX_ANALYSIS_UPLOAD:
        raise HTTPException(413, "이미지 파일이 너무 큽니다")
    try:
        image = Image.open(io.BytesIO(raw))
        if image.width * image.height > 20_000_000:
            raise HTTPException(413, "이미지 해상도가 너무 큽니다")
        image.load()
        return image
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(400, "이미지 파일을 해석할 수 없습니다") from None

@asynccontextmanager
async def lifespan(_):
    initialize_database()
    # DB 에 아직 적용하지 않은 마이그레이션이 있으면 서버 창에 알려 준다 (그 기능만 저장이 안 됨)
    status = database_status()
    if status.get("missing"):
        print(f"[DB] 빠진 표 · 칸: {', '.join(status['missing'])} → 적용 이력·스키마 검토 필요: MIGRATION_PREFLIGHT.md")
    try:
        yield
    finally:
        await close_tryon()


app = FastAPI(
    lifespan=lifespan,
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

# Legacy files are deliberately left on disk, but no upload directory is publicly served.


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











# ──────────────────────────────────────────────
# AI 체형 분석
# ──────────────────────────────────────────────

@app.post("/api/body-analysis", tags=["AI 분석"])
async def analyze_body():
    """The current app analyzes photos on-device; the old server stub is retired."""
    raise HTTPException(410, "체형 사진 서버 분석은 지원하지 않습니다. 앱의 기기 내 분석을 이용해주세요.")


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

    image = await read_analysis_photo(photo)

    detections, counts, result_img = detection.detect_objects(
        image, conf_threshold=conf_threshold
    )

    bgr = result_img[:, :, ::-1]  # RGB → BGR (cv2.imwrite용)
    ok, encoded = cv2.imencode(".jpg", bgr)
    if not ok or encoded.size > 4 * 1024 * 1024:
        raise HTTPException(500, "결과 이미지를 준비하지 못했습니다")

    return {
        "totalCount": len(detections),
        "detections": detections,
        "counts": counts,
        "resultImageUrl": "data:image/jpeg;base64," + base64.b64encode(encoded.tobytes()).decode("ascii"),
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

    image = await read_analysis_photo(photo)

    try:
        return fashion_detection.classify_clothing(image, conf_threshold=conf_threshold)
    except FileNotFoundError as e:
        raise HTTPException(503, str(e))


@app.get("/api/style-suggestions", response_model=StyleSuggestionResponse, tags=["AI 분석"])
def get_style_suggestions(db=Depends(optional_db)):
    """체형별 스타일 제안 (추천 스타일 / 피해야 할 스타일 / 추천 상품 — 쇼핑 카탈로그의 AI추천 상품)"""
    recommended_products = [p for p in catalog_rows(db)[0] if p["badge"] == "AI추천"]
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
def get_products(search: str = "", db=Depends(optional_db)):
    """
    쇼핑 상품 목록 (앱 ShopCatalog 와 같은 상품). ?search=셔츠 처럼 상품명 · 브랜드 검색.
    DB products 표(seed_catalog.py 로 채움)가 있으면 DB 에서, 없으면 data.SHOP_CATALOG 에서 읽는다.
    """
    products, _ = catalog_rows(db)
    if search:
        products = [p for p in products if search in p["name"] or search in p["brand"]]
    return {
        "banner": "AI가 내 체형·취향을 분석해 추천한 상품이에요",
        "products": products,
    }


@app.get("/api/products/{product_id}", response_model=ProductDetail, tags=["쇼핑"])
def get_product_detail(product_id: int, db=Depends(optional_db)):
    """상품 상세 (할인율 · 구매 링크). id 는 /api/products 응답의 id."""
    products, _ = catalog_rows(db)
    product = next((p for p in products if p["id"] == product_id), None)
    if product is None:
        raise HTTPException(404, "상품을 찾을 수 없습니다")
    discount = None
    if product["originalPrice"]:
        discount = round((1 - product["price"] / product["originalPrice"]) * 100)
    return {
        **product,
        "description": "",
        "colors": [product["bgColor"]],
        "sizes": [],
        "discountPercent": discount,
        "lowestPriceInfo": f"판매가: {product['price']:,}원 · {product.get('mallName') or '쇼핑몰'}",
    }


# ──────────────────────────────────────────────
# 프로필 / 온보딩
# ──────────────────────────────────────────────



@app.get("/api/styles", response_model=list[str], tags=["프로필"])
def get_style_options():
    """온보딩 - 선택 가능한 스타일 목록"""
    return data.STYLE_OPTIONS




# ──────────────────────────────────────────────
# 실시간 날씨 (WeatherViewModel)
# ──────────────────────────────────────────────

@app.get("/api/weather", response_model=WeatherNowResponse, tags=["날씨"])
def get_weather(lat: float = weather.DEFAULT_LAT, lon: float = weather.DEFAULT_LON):
    """
    위도·경도로 실시간 날씨 조회 → 온도·습도·상태 + 옷차림 조언.

    앱 WeatherViewModel이 기기 위치(lat/lon)로 호출.
    서버 환경변수 OWM_API_KEY 로 실제 OpenWeatherMap을 호출한다.
    키가 없거나 날씨를 못 받으면 503을 반환한다 (가짜 기본값을 주지 않음).
    """
    w = weather.fetch_weather(lat, lon)
    if w is None:
        raise HTTPException(503, "서버가 날씨를 받아오지 못했어요 (OWM_API_KEY 확인)")
    advice = weather.make_advice(w["temp"], w["humidity"], w["conditionId"])
    return {**w, "advice": advice}


@app.get("/api/weather/forecast", tags=["날씨"])
def get_weather_forecast(lat: float = weather.DEFAULT_LAT, lon: float = weather.DEFAULT_LON):
    """
    5일 예보(3시간 간격 40개) — OpenWeatherMap /forecast 응답을 그대로 돌려준다.

    앱 WeatherRepository.fetchForecast 가 호출해서 기존 parseForecast 로 날짜별로 묶는다.
    키는 서버에만 두므로 앱(APK)에는 날씨 키가 없어도 된다. 같은 위치는 10분 동안 다시 묻지 않음.
    """
    f = weather.fetch_forecast(lat, lon)
    if f is None:
        raise HTTPException(503, "서버가 날씨 예보를 받아오지 못했어요 (OWM_API_KEY 확인)")
    return f


# ──────────────────────────────────────────────
# 네이버 이미지 검색 (쇼핑 탭 · 홈 · 코디 카드 사진)
# ──────────────────────────────────────────────

@app.get("/api/naver/image-search", response_model=NaverImageSearchResponse, tags=["쇼핑"])
def naver_image_search(
    query: str = Query(..., min_length=1, max_length=100),
    display: int = Query(30, ge=1, le=100),
):
    """
    네이버 이미지 검색을 서버가 대신 호출한다 (키는 서버 환경변수 NAVER_API_KEY_ID · NAVER_API_KEY).

    앱 NaverImageSearchRepository.search 가 호출. 같은 검색어는 1시간 동안 다시 묻지 않음.
    키가 없으면 503, 네이버 오류면 502를 반환한다.
    """
    try:
        return {"items": naver.search_images(query.strip(), display)}
    except naver.NaverNotConfigured:
        raise HTTPException(503, "서버에 네이버 키가 없어요 (NAVER_API_KEY_ID · NAVER_API_KEY 확인)")
    except naver.NaverUnavailable:
        raise HTTPException(502, "네이버 이미지 검색이 응답하지 않아요")


@app.get("/api/naver/shopping-insight", tags=["쇼핑"])
def naver_shopping_insight():
    try:
        return naver.shopping_insight()
    except naver.NaverNotConfigured:
        raise HTTPException(503, "서버에 네이버 키가 없어요")
    except naver.NaverUnavailable:
        raise HTTPException(502, "쇼핑인사이트가 응답하지 않아요")


@app.get("/api/unsplash/photo", tags=["쇼핑"])
def unsplash_photo(query: str = Query(..., min_length=1, max_length=100)):
    try:
        return {"url": unsplash.photo_url(query.strip())}
    except unsplash.UnsplashUnavailable:
        raise HTTPException(503, "사진 서비스를 사용할 수 없어요")


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


app.include_router(auth_router)
app.include_router(provider_router)
app.include_router(db_router)
app.include_router(tryon_router())


@app.middleware("http")
async def private_response_cache_policy(request, call_next):
    response = await call_next(request)
    private = ("/api/auth", "/api/wardrobe", "/api/wear-logs", "/api/body-measurements",
               "/api/profile", "/api/style-preference", "/api/product-interests",
               "/api/taste-events", "/api/recommendations", "/api/saved-outfits",
               "/api/outfit-plans", "/api/me", "/api/backup")
    private += ("/api/stats",)
    if any(request.url.path == p or request.url.path.startswith(p + "/") for p in private):
        response.headers["Cache-Control"] = "no-store"
        response.headers["Pragma"] = "no-cache"
    return response

@app.get("/api/health/database", tags=["상태"])
def database_health():
    return database_status()
