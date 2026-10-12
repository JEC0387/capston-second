"""
API 응답/요청 스키마 정의.
앱(Compose)의 각 화면 data class와 1:1로 대응되도록 설계.
"""
from pydantic import BaseModel
from typing import Optional


# ---------- 공통 ----------

class Weather(BaseModel):
    condition: str          # 예: "맑음"
    temperature: int        # 섭씨
    humidity: int           # %
    message: str            # 예: "가벼운 아우터를 걸치기 좋은 날씨예요"


# ---------- 홈 화면 ----------

class OutfitCard(BaseModel):
    """홈 - 오늘의 AI 코디 카드"""
    id: int
    title: str              # 예: "캐주얼 데일리룩"
    desc: str               # 예: "베이지 니트 + 와이드 팬츠"
    bgColor: str            # 예: "#EDE3D5"  (앱 Color(0xFF...) 대응)
    imageUrl: str


class PopularItem(BaseModel):
    """홈 - 인기 상품"""
    id: int
    name: str
    price: int              # 원 단위 숫자 (표시 포맷은 앱에서)
    bgColor: str
    imageUrl: str


class HomeResponse(BaseModel):
    greeting: str
    weather: Weather
    todayOutfits: list[OutfitCard]
    popularItems: list[PopularItem]


# ---------- 날씨 코디 화면 ----------

class OutfitItem(BaseModel):
    label: str              # 예: "베이지 니트"
    color: str              # 예: "#EDE3D5"
    imageUrl: Optional[str] = None


class OutfitSet(BaseModel):
    id: int
    title: str              # 예: "캐주얼 데일리룩"
    tag: str                # 예: "날씨 최적" / "내 스타일" / "트렌디"
    top: OutfitItem
    bottom: OutfitItem
    shoes: OutfitItem


class WeatherOutfitResponse(BaseModel):
    weather: Weather
    outfits: list[OutfitSet]


# ---------- 옷장 화면 ----------

class ClothingItem(BaseModel):
    id: int                 # DB 번호
    name: str
    category: str           # 상의 / 하의 / 아우터 / 신발
    color: str
    imageUrl: str
    isFavorite: bool = False
    # 앱 WardrobeItem 과 같은 추가 정보 (DB 연동 후 추가, 없어도 됨)
    clientId: Optional[str] = None      # 앱의 옷 id (sample_01, UUID …)
    colorName: Optional[str] = None
    subCategory: Optional[str] = None
    detailType: Optional[str] = None
    season: Optional[str] = None
    style: Optional[str] = None
    isCutout: bool = False
    aiTagged: bool = False
    addedAt: Optional[int] = None       # Unix 밀리초
    favoritedAt: Optional[int] = None
    keptAt: Optional[int] = None


class WardrobeResponse(BaseModel):
    totalCount: int
    categories: list[str]
    items: list[ClothingItem]


class AddClothingRequest(BaseModel):
    name: str
    category: str
    color: str = "#EDE3D5"
    imageUrl: str = ""


# ---------- 옷장 동기화 (앱 WardrobeStore → DB) ----------

class WardrobeSyncItem(BaseModel):
    """앱 WardrobeItem 과 필드 이름·뜻이 같다. 시각은 모두 Unix 밀리초."""
    id: str
    name: str
    category: str
    colorName: str = ""
    colorValue: int = 0xFFEDE3D5        # ARGB (앱 Color 값)
    imageUri: str = ""
    aiTagged: bool = False
    subCategory: Optional[str] = None
    detailType: Optional[str] = None
    season: Optional[str] = None
    addedAt: Optional[int] = None
    favoritedAt: Optional[int] = None
    isCutout: bool = False
    keptAt: Optional[int] = None
    originalUri: Optional[str] = None
    cutoutUri: Optional[str] = None
    garmentUri: Optional[str] = None
    style: Optional[str] = None


class WardrobeSyncRequest(BaseModel):
    """폰 옷장 전체 목록. 서버는 이 목록과 똑같아지도록 추가·수정·삭제한다."""
    items: list[WardrobeSyncItem]


class SyncResult(BaseModel):
    saved: int              # 추가·수정된 개수
    deleted: int            # 폰에 없어서 지운 개수
    total: int              # 동기화 후 DB 에 있는 개수


# ---------- 착용 기록 (앱 WearLogStore → DB) ----------

class WearLogItem(BaseModel):
    date: str               # yyyy-MM-dd
    itemIds: list[str]      # 앱 옷 id 목록
    memo: str = ""


class WearLogSyncRequest(BaseModel):
    logs: list[WearLogItem]


class WearLogResponse(BaseModel):
    totalCount: int
    logs: list[WearLogItem]


# ---------- AI 체형 분석 ----------

class BodyProportion(BaseModel):
    label: str              # 예: "어깨 너비"
    ratio: float            # 0.0 ~ 1.0


class BodyAnalysisResult(BaseModel):
    bodyType: str           # 예: "모래시계형"
    summary: str            # 예: "어깨·힙 균형 · 허리 라인 뚜렷"
    proportions: list[BodyProportion]
    features: list[str]     # 체형 특징 bullet


class BodyMeasurementIn(BaseModel):
    """앱 체형 분석 결과 저장 (사진은 보내지 않음 — 숫자만)."""
    bodyType: str
    confidence: int = 0
    shoulderRatio: Optional[float] = None
    waistRatio: Optional[float] = None
    hipRatio: Optional[float] = None
    legRatio: Optional[float] = None
    shoulderToHip: Optional[float] = None
    waistToHip: Optional[float] = None
    heightCm: Optional[float] = None
    shoulderCm: Optional[float] = None    # 정면 너비 추정값 (둘레 아님)
    waistCm: Optional[float] = None
    hipCm: Optional[float] = None
    legCm: Optional[float] = None
    torsoCm: Optional[float] = None
    measuredAt: Optional[int] = None      # Unix 밀리초 (없으면 서버 시각)


class BodyMeasurementOut(BodyMeasurementIn):
    id: int


class BodyMeasurementHistory(BaseModel):
    totalCount: int
    measurements: list[BodyMeasurementOut]   # 최근 것부터


# ---------- 객체 탐지 (YOLOv8) ----------

class Detection(BaseModel):
    label: str               # 예: "person", "handbag"
    confidence: float        # 0.0 ~ 1.0
    bbox: list[int]          # [x1, y1, x2, y2] (전처리된 이미지 좌표 기준)


class ObjectDetectionResponse(BaseModel):
    totalCount: int                  # 탐지된 객체 총 개수
    detections: list[Detection]      # 객체별 상세
    counts: dict[str, int]           # 라벨별 개수 통계 (예: {"person": 2})
    resultImageUrl: str              # 바운딩 박스가 그려진 결과 이미지 URL


# ---------- 옷 종류 분류 (DeepFashion2 + COCO 가방류) ----------

class ClothingCandidate(BaseModel):
    label: str               # DeepFashion2 종류(예: "trousers") 또는 COCO 가방(예: "handbag")
    source: str              # "deepfashion2" / "coco"
    confidence: float        # 0.0 ~ 1.0
    bbox: list[int]          # [x1, y1, x2, y2] (전처리된 이미지 좌표 기준)
    area: float              # 박스 면적 / 이미지 면적 (0~1)
    mask: Optional[str] = None  # 옷 윤곽 흑백 PNG(base64), 이미지 크기와 같음 (COCO는 없음)


class ClothingClassifyResponse(BaseModel):
    imageWidth: int                      # 전처리된 이미지 크기 (마스크 크기와 같음)
    imageHeight: int
    candidates: list[ClothingCandidate]  # 크게 잡힌 순서


# ---------- 체형별 스타일 제안 ----------

class StyleCard(BaseModel):
    id: int
    title: str              # 예: "허리 강조 원피스"
    desc: str
    color: str


class StyleSuggestionResponse(BaseModel):
    bodyType: str
    summary: str
    recommendedStyles: list[StyleCard]
    avoidStyles: list[str]
    recommendedProducts: list["Product"]


# ---------- 스타일 분석 결과 ----------

class StyleData(BaseModel):
    name: str               # 예: "미니멀"
    percent: int
    color: str


class StyleAnalysisResponse(BaseModel):
    breakdown: list[StyleData]
    keywords: list[str]     # 예: ["깔끔한", "심플한", ...]
    aiComment: str


# ---------- 쇼핑 / 상품 ----------

class Product(BaseModel):
    id: int
    name: str
    brand: str
    price: int
    originalPrice: Optional[int] = None
    rating: float
    reviewCount: int
    bgColor: str
    badge: Optional[str] = None      # "AI추천" / "베스트" / None
    imageUrl: str
    productUrl: str = ""             # 구매 페이지 (무신사/지그재그 …)
    category: Optional[str] = None
    mallName: Optional[str] = None
    isLiked: bool = False            # 하트 여부


class ShopResponse(BaseModel):
    banner: str             # 예: "AI가 내 체형·취향을 분석해 추천한 상품이에요"
    products: list[Product]


class ProductLikeRequest(BaseModel):
    liked: bool                      # true=하트 켜기, false=끄기 (같은 값을 여러 번 보내도 안전)


class LikedProduct(BaseModel):
    productId: int
    name: str
    likedAt: int                     # Unix 밀리초


class ProductLikesResponse(BaseModel):
    totalCount: int
    items: list[LikedProduct]        # 하트 누른 순서


class SizeChartRow(BaseModel):
    brand: str
    category: str
    sizeLabel: str
    shoulderCm: Optional[float] = None
    chestCm: Optional[float] = None
    waistCm: Optional[float] = None
    hipCm: Optional[float] = None
    lengthCm: Optional[float] = None
    measureType: str = "단면"
    sourceUrl: str = ""


class SizeChartResponse(BaseModel):
    totalCount: int
    rows: list[SizeChartRow]


class ProductDetail(Product):
    description: str = ""
    colors: list[str] = []           # hex 색상 목록
    sizes: list[str] = []            # XS~XL
    discountPercent: Optional[int] = None
    lowestPriceInfo: str = ""        # 예: "최저가: 43,000원 · 네이버쇼핑, 무신사, 29CM 등 비교"


# ---------- 마이페이지 / 프로필 ----------

class Profile(BaseModel):
    nickname: str
    email: str
    wardrobeCount: int
    outfitCount: int
    favoriteCount: int
    bodyType: Optional[str] = None
    preferredStyles: list[str] = []


class UpdateStylesRequest(BaseModel):
    """온보딩 - 선호 스타일 저장"""
    styles: list[str]


# ---------- 실시간 날씨 (WeatherViewModel) ----------

class WeatherNowResponse(BaseModel):
    """앱이 위치(위도/경도)로 실시간 날씨를 조회할 때 응답.
    앱의 WeatherInfo(condition, temp, humidity, conditionId)와 대응."""
    condition: str          # 예: "맑음"
    temp: int               # 섭씨
    humidity: int           # %
    conditionId: int        # OpenWeatherMap 날씨 코드 (200~899)
    advice: str             # 온도·습도·상태에 맞춘 옷차림 한 줄


# ---------- 스타일 취향 학습 (StylePreferenceStore) ----------

class StyleScore(BaseModel):
    style: str              # 예: "미니멀"
    score: int


class StylePreferenceResponse(BaseModel):
    """취향 학습 현황 조회"""
    scores: list[StyleScore]    # 스타일별 누적 점수 (내림차순)
    ratedCount: int             # 누적 평가 횟수
    topStyle: str               # 현재 최상위 선호 스타일


class RatePreferenceRequest(BaseModel):
    """코디 카드 좋아요/싫어요 평가"""
    style: str
    like: bool                  # true=좋아요(+2), false=싫어요(-1)


# ---------- 가상 피팅 (VirtualFittingScreen) ----------

class FittingItem(BaseModel):
    id: int
    name: str
    category: str
    imageUrl: str


class VirtualFittingResponse(BaseModel):
    """가상 피팅에서 입어볼 후보 의류 목록 (옷장 + 샘플)"""
    items: list[FittingItem]


StyleSuggestionResponse.model_rebuild()
