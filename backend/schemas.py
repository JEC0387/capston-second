"""
API 응답/요청 스키마 정의.
앱(Compose)의 각 화면 data class와 1:1로 대응되도록 설계.
"""
from pydantic import BaseModel, Field, ConfigDict, field_validator, model_validator
from typing import Literal, Optional
from datetime import date


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
    # 복원용 (백업 코드로 다른 폰에 옷장을 되살릴 때)
    originalUri: Optional[str] = None
    cutoutUri: Optional[str] = None
    garmentUri: Optional[str] = None
    hasPhoto: bool = False              # 사진 백업(clothing_photos)이 있는지
    brand: Optional[str] = None
    price: Optional[int] = None


class WardrobeResponse(BaseModel):
    totalCount: int
    categories: list[str]
    items: list[ClothingItem]


class AddClothingRequest(BaseModel):
    name: str
    category: str
    color: str = "#EDE3D5"
    imageUrl: str = ""


# ---------- AI 체형 분석 ----------

class BodyProportion(BaseModel):
    label: str              # 예: "어깨 너비"
    ratio: float            # 0.0 ~ 1.0


class BodyAnalysisResult(BaseModel):
    bodyType: str           # 예: "모래시계형"
    summary: str            # 예: "어깨·힙 균형 · 허리 라인 뚜렷"
    proportions: list[BodyProportion]
    features: list[str]     # 체형 특징 bullet


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
    # DB 카탈로그 연동 후 추가 (없어도 됨)
    productKey: Optional[str] = None # 앱 ShopCatalog 상품 id (구매 링크) — 관심 상품과 같은 값
    productUrl: Optional[str] = None
    category: Optional[str] = None
    mallName: Optional[str] = None


class ShopResponse(BaseModel):
    banner: str             # 예: "AI가 내 체형·취향을 분석해 추천한 상품이에요"
    products: list[Product]


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
    gender: Optional[str] = None     # FEMALE / MALE (마이페이지 성별 설정)
    interestCount: int = 0           # 관심 상품 수


class GenderRequest(BaseModel):
    """마이페이지 성별 설정 (null 이면 설정 해제)"""
    gender: Optional[Literal["FEMALE", "MALE"]] = None


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


class WardrobeSyncItem(BaseModel):
    """앱 WardrobeItem 과 필드 이름·뜻이 같다. 시각은 모두 Unix 밀리초."""
    id: str = Field(min_length=1, max_length=64)
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
    brand: Optional[str] = None         # 50자까지 저장
    price: Optional[int] = None         # 원 (1 ~ 1억 원만 저장, 그 밖은 비움)


class WardrobeSyncRequest(BaseModel):
    """폰 옷장 전체 목록. 서버는 이 목록과 똑같아지도록 추가·수정·삭제한다."""
    items: list[WardrobeSyncItem]

    @field_validator('items')
    @classmethod
    def unique_ids(cls, items):
        if len({item.id for item in items}) != len(items):
            raise ValueError('Duplicate wardrobe ids')
        return items


class SyncResult(BaseModel):
    saved: int              # 추가·수정된 개수
    deleted: int            # 폰에 없어서 지운 개수
    total: int              # 동기화 후 DB 에 있는 개수


class WearLogItem(BaseModel):
    date: str               # yyyy-MM-dd
    itemIds: list[str]      # 앱 옷 id 목록
    memo: str = ""


    @field_validator('date')
    @classmethod
    def valid_date(cls, value):
        if len(value) != 10 or date.fromisoformat(value).isoformat() != value:
            raise ValueError('Expected yyyy-MM-dd')
        return value


class WearLogSyncRequest(BaseModel):
    logs: list[WearLogItem]

    @field_validator('logs')
    @classmethod
    def valid_logs(cls, logs):
        if len({log.date for log in logs}) != len(logs) or any(not log.itemIds for log in logs):
            raise ValueError('Duplicate dates or empty log')
        return logs


class WearLogResponse(BaseModel):
    totalCount: int
    logs: list[WearLogItem]


class BodyMeasurementIn(BaseModel):
    clientId: str = Field(min_length=1, max_length=64)
    model_config = ConfigDict(allow_inf_nan=False)
    """앱 체형 분석 결과 저장 (사진은 보내지 않음 — 숫자만)."""
    bodyType: str
    confidence: int = Field(default=0, ge=0, le=100)
    shoulderRatio: Optional[float] = Field(default=None, gt=0, le=2)
    waistRatio: Optional[float] = Field(default=None, gt=0, le=2)
    hipRatio: Optional[float] = Field(default=None, gt=0, le=2)
    legRatio: Optional[float] = Field(default=None, gt=0, le=2)
    shoulderToHip: Optional[float] = Field(default=None, gt=0.099, le=5)
    waistToHip: Optional[float] = Field(default=None, gt=0.099, le=5)
    heightCm: Optional[float] = Field(default=None, gt=99.999, le=230)
    shoulderCm: Optional[float] = Field(default=None, gt=14.999, le=80)    # 정면 너비 추정값 (둘레 아님)
    waistCm: Optional[float] = Field(default=None, gt=9.999, le=100)
    hipCm: Optional[float] = Field(default=None, gt=14.999, le=100)
    legCm: Optional[float] = Field(default=None, gt=19.999, le=150)
    torsoCm: Optional[float] = Field(default=None, gt=14.999, le=100)
    measuredAt: Optional[int] = Field(default=None, ge=1, le=253402300799000)      # Unix 밀리초 (없으면 서버 시각)


    @model_validator(mode='after')
    def consistent_cm(self):
        widths = [self.shoulderCm, self.waistCm, self.hipCm]
        if any(v is not None for v in widths + [self.legCm, self.torsoCm]):
            if self.heightCm is None or any(v is None for v in widths):
                raise ValueError('cm measurements require height and all three widths')
        return self


class BodyMeasurementOut(BodyMeasurementIn):
    clientId: Optional[str] = None  # Records created before idempotent sync have no client id.
    id: int
    isSample: bool = False                # 시연용 샘플 기록 (실제 측정 아님)


class BodyMeasurementHistory(BaseModel):
    totalCount: int
    measurements: list[BodyMeasurementOut]   # 최근 것부터


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


# ---------- 관심 상품 (앱 ProductInterestStore → DB) ----------

class ProductInterestSyncRequest(BaseModel):
    """폰의 관심 상품 id 목록 (누른 순서). 서버는 이 목록과 똑같아지도록 추가 · 삭제한다."""
    productIds: list[str] = Field(max_length=500)

    @field_validator('productIds')
    @classmethod
    def valid_ids(cls, ids):
        cleaned = []
        for value in ids:
            value = (value or '').strip()
            if not value or len(value) > 512:
                raise ValueError('Product id must be 1~512 characters')
            if value not in cleaned:
                cleaned.append(value)
        return cleaned


class ProductInterestItem(BaseModel):
    productKey: str
    position: int
    addedAt: Optional[int] = None    # Unix 밀리초 (서버에 처음 저장된 시각)
    # 카탈로그(products)에 있는 상품이면 함께 채움
    name: Optional[str] = None
    brand: Optional[str] = None
    price: Optional[int] = None
    imageUrl: Optional[str] = None
    productUrl: Optional[str] = None


class ProductInterestResponse(BaseModel):
    totalCount: int
    items: list[ProductInterestItem]


class PopularInterest(BaseModel):
    productKey: str
    interestCount: int               # 이 상품을 관심 상품으로 담은 사용자 수
    name: Optional[str] = None
    brand: Optional[str] = None
    imageUrl: Optional[str] = None


class PopularInterestResponse(BaseModel):
    items: list[PopularInterest]


# ---------- 취향 평가 기록 (앱 TastePreferenceStore → DB) ----------

class TasteEventItem(BaseModel):
    at: int = Field(gt=0)            # Unix 밀리초 (평가한 시각)
    source: Literal["outfit", "card", "worn"]
    liked: bool
    colors: list[str] = Field(default_factory=list, max_length=20)
    kinds: list[str] = Field(default_factory=list, max_length=20)
    pattern: Optional[str] = None
    style: Optional[str] = None
    itemIds: list[str] = Field(default_factory=list, max_length=20)
    date: Optional[str] = None       # 착용 기록이면 yyyy-MM-dd

    @field_validator('date')
    @classmethod
    def valid_date(cls, value):
        if value is not None and (len(value) != 10 or date.fromisoformat(value).isoformat() != value):
            raise ValueError('Expected yyyy-MM-dd')
        return value


class TasteEventSyncRequest(BaseModel):
    """폰의 취향 평가 기록 전체. (평가 시각, 종류)가 같으면 같은 기록으로 본다."""
    events: list[TasteEventItem] = Field(max_length=5000)


class TasteSummary(BaseModel):
    """DB 기록으로 다시 계산한 취향 점수 (👍 +2 / 👎 −1 / 착용 +1, 한 기록 안의 같은 색 · 종류는 한 번만)"""
    totalCount: int
    ratedCount: int
    wornCount: int
    colors: dict[str, int]
    kinds: dict[str, int]
    patterns: dict[str, int]
    styles: dict[str, int]



# ---------- 옷 사진 백업 (복원용) ----------

class ClothingPhotoIn(BaseModel):
    """앱이 줄여서 올리는 옷 사진 한 장 (base64). 사람 · 체형 사진은 받지 않는다 — 옷장 옷 사진만."""
    clientId: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_.:-]+$")
    mime: Literal["image/png", "image/jpeg", "image/webp"]
    data: str = Field(min_length=8, max_length=2_900_000)   # base64 (원본 약 2MB 이하)


class ClothingPhotoOut(BaseModel):
    clientId: str
    mime: str
    size: int
    sha256: str


# ---------- 추천 기록 (추천 성능 측정) ----------

class RecommendationLogItem(BaseModel):
    date: str                                     # yyyy-MM-dd (추천한 날)
    context: str = Field(min_length=1, max_length=30, pattern=r"^[A-Za-z0-9_:-]+$")
    scope: str = Field(default="OWNED", max_length=12)
    rank: int = Field(default=0, ge=0, le=50)
    itemIds: list[str] = Field(min_length=1, max_length=8)
    shownAt: int = Field(gt=0)                    # Unix 밀리초

    @field_validator('date')
    @classmethod
    def valid_date(cls, value):
        if len(value) != 10 or date.fromisoformat(value).isoformat() != value:
            raise ValueError('Expected yyyy-MM-dd')
        return value

    @field_validator('itemIds')
    @classmethod
    def valid_ids(cls, value):
        if any(not i or len(i) > 64 for i in value):
            raise ValueError('item id must be 1-64 chars')
        return value


class RecommendationLogSyncRequest(BaseModel):
    """폰이 기억하는 최근 추천 기록. 이미 있는 기록은 그대로 두고 새 기록만 더한다 (지우지 않음)."""
    logs: list[RecommendationLogItem] = Field(max_length=2000)


class RecommendationStatsGroup(BaseModel):
    shown: int            # 보여 준 코디 수
    liked: int            # 그 코디에 👍
    disliked: int         # 그 코디에 👎
    worn: int             # 추천한 날 ~ 2일 뒤 안에 그 코디를 그대로 입음 (착용 기록에 코디 옷이 모두 있음)
    wornPartial: int      # 코디 옷의 절반 이상을 입음 (worn 포함)
    likeRate: float       # liked / shown
    ratedLikeRate: Optional[float] = None   # liked / (liked + disliked) — 평가한 것 중 👍 비율
    wornRate: float       # worn / shown
    wornPartialRate: float


class RecommendationStats(BaseModel):
    users: int
    since: Optional[str] = None
    until: Optional[str] = None
    total: RecommendationStatsGroup
    byContext: dict[str, RecommendationStatsGroup]


class TasteEventListResponse(BaseModel):
    totalCount: int
    events: list[TasteEventItem]


class DeleteMyDataResult(BaseModel):
    deleted: dict[str, int]   # 표 이름 → 지운 줄 수


# ---------- 저장한 코디 · 코디 일정 ----------

def _check_ids(value: list[str]) -> list[str]:
    if any(not i or len(i) > 64 for i in value):
        raise ValueError('item id must be 1-64 chars')
    return value


class SavedOutfitItem(BaseModel):
    id: Optional[str] = Field(default=None, max_length=600)   # 없으면 서버가 옷 id 로 만든다
    itemIds: list[str] = Field(min_length=1, max_length=8)
    style: Optional[str] = None
    pattern: Optional[str] = None
    note: str = Field(default="", max_length=100)
    savedAt: Optional[int] = None

    @field_validator('itemIds')
    @classmethod
    def valid_ids(cls, value):
        return _check_ids(value)


class SavedOutfitSyncRequest(BaseModel):
    """폰의 저장한 코디 목록 (최근 저장 순). 서버는 이 목록과 똑같아지도록 추가 · 수정 · 삭제한다."""
    outfits: list[SavedOutfitItem] = Field(max_length=200)


class SavedOutfitResponse(BaseModel):
    totalCount: int
    outfits: list[SavedOutfitItem]


class OutfitPlanItem(BaseModel):
    date: str
    itemIds: list[str] = Field(min_length=1, max_length=8)
    note: str = Field(default="", max_length=100)
    createdAt: Optional[int] = None

    @field_validator('itemIds')
    @classmethod
    def valid_ids(cls, value):
        return _check_ids(value)

    @field_validator('date')
    @classmethod
    def valid_date(cls, value):
        if len(value) != 10 or date.fromisoformat(value).isoformat() != value:
            raise ValueError('Expected yyyy-MM-dd')
        return value


class OutfitPlanSyncRequest(BaseModel):
    """폰의 코디 일정 전체 (날짜마다 하나). 서버는 이 목록과 똑같아지도록 맞춘다."""
    plans: list[OutfitPlanItem] = Field(max_length=400)

    @field_validator('plans')
    @classmethod
    def unique_dates(cls, plans):
        if len({p.date for p in plans}) != len(plans):
            raise ValueError('Duplicate plan dates')
        return plans


class OutfitPlanResponse(BaseModel):
    totalCount: int
    plans: list[OutfitPlanItem]


# ---------- 전체 통계 (발표용) ----------

class NameCount(BaseModel):
    name: str
    count: int


class OverviewStats(BaseModel):
    """모든 사용자를 합친 개수 · 순위 (개인 정보 없이 숫자만)"""
    users: int
    clothes: int
    clothingPhotos: int
    wearLogs: int
    savedOutfits: int
    outfitPlans: int
    productInterests: int
    tasteEvents: int
    recommendations: int
    bodyMeasurements: int
    topCategories: list[NameCount]
    topColors: list[NameCount]
    topPreferredStyles: list[NameCount]
    pricedClothes: int                 # 가격을 넣은 옷 수
    averagePrice: Optional[int] = None
    planDays: int                      # 지난 일정 중 착용 기록이 있는 날
    planFollowed: int                  # 그중 일정대로 입은 날
