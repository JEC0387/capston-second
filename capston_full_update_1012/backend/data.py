"""
고정 데이터 + DB 초기 데이터(seed).

- 옷장·착용 기록·체형 기록·취향 평가·상품·하트·사이즈표는 Supabase DB 에 저장된다 (db_models.py).
- 이 파일의 WARDROBE_SAMPLES / PRODUCTS / PROFILE 은 DB 가 비어 있을 때 처음 한 번만
  넣는 초기 데이터다 (seed.py). 앱의 SampleWardrobe.kt / ShopScreen.kt 와 같은 내용.
- 홈 카드·스타일 분석 문구처럼 아직 DB 로 옮기지 않은 화면용 고정 데이터도 여기 있다.
"""

# ---------- 홈: 오늘의 AI 코디 (HomeScreen.kt outfitCards) ----------
TODAY_OUTFITS = [
    {"id": 1, "title": "캐주얼 데일리룩", "desc": "베이지 니트 + 와이드 팬츠",
     "bgColor": "#EDE3D5", "imageUrl": "https://loremflickr.com/400/500/fashion,casual?lock=10"},
    {"id": 2, "title": "미니멀 오피스룩", "desc": "화이트 블라우스 + 슬랙스",
     "bgColor": "#E8E0D8", "imageUrl": "https://loremflickr.com/400/500/fashion,office?lock=11"},
    {"id": 3, "title": "스트릿 캐주얼", "desc": "후드집업 + 카고 팬츠",
     "bgColor": "#DDD5C8", "imageUrl": "https://loremflickr.com/400/500/streetwear,fashion?lock=12"},
]

# ---------- 홈: 인기 상품 (HomeScreen.kt popularItems) ----------
POPULAR_ITEMS = [
    {"id": 1, "name": "오버사이즈 니트", "price": 43000, "bgColor": "#F0E8DC",
     "imageUrl": "https://loremflickr.com/300/380/knit,sweater?lock=20"},
    {"id": 2, "name": "린넨 와이드 팬츠", "price": 58000, "bgColor": "#E8DDD0",
     "imageUrl": "https://loremflickr.com/300/380/pants,fashion?lock=21"},
    {"id": 3, "name": "베이직 코튼 티", "price": 22000, "bgColor": "#EDE5D8",
     "imageUrl": "https://loremflickr.com/300/380/tshirt,clothing?lock=22"},
    {"id": 4, "name": "데님 자켓", "price": 89000, "bgColor": "#DDD5C8",
     "imageUrl": "https://loremflickr.com/300/380/denim,jacket?lock=23"},
]

# ---------- 날씨 (HomeScreen / WeatherOutfitScreen 상단 카드) ----------
WEATHER = {
    "condition": "맑음",
    "temperature": 23,
    "humidity": 45,
    "message": "가벼운 아우터를 걸치기 좋은 날씨예요",
}

# ---------- 날씨 코디 3종 (WeatherOutfitScreen.kt outfits) ----------
WEATHER_OUTFITS = [
    {
        "id": 1, "title": "캐주얼 데일리룩", "tag": "날씨 최적",
        "top":    {"label": "베이지 니트",   "color": "#EDE3D5"},
        "bottom": {"label": "와이드 팬츠",   "color": "#D5CABD"},
        "shoes":  {"label": "스니커즈",     "color": "#F0EBE3"},
    },
    {
        "id": 2, "title": "미니멀 오피스룩", "tag": "내 스타일",
        "top":    {"label": "화이트 블라우스", "color": "#F5F2EE"},
        "bottom": {"label": "슬랙스",        "color": "#CDC5BC"},
        "shoes":  {"label": "로퍼",          "color": "#B8A898"},
    },
    {
        "id": 3, "title": "라이트 스트릿", "tag": "트렌디",
        "top":    {"label": "그래픽 티셔츠",  "color": "#E8DDD0"},
        "bottom": {"label": "카고 팬츠",     "color": "#CFCAC0"},
        "shoes":  {"label": "청키 스니커즈",  "color": "#DDD8D0"},
    },
]

# ---------- 옷장 (WardrobeScreen.kt clothingItems) ----------
WARDROBE_CATEGORIES = ["전체", "상의", "하의", "아우터", "신발"]

# 앱 SampleWardrobe.kt 와 같은 샘플 옷 12벌 (client_id 가 같아서 앱과 서버가 같은 옷으로 인식)
# 사진은 앱 안(res/drawable)에 들어 있다.
_RES = "android.resource://com.jeiu.fashionproject/drawable/"


def _sample(cid, name, category, sub, detail, season, color_name, argb, kind, original=None):
    image = _RES + cid
    return {
        "clientId": cid, "name": name, "category": category,
        "subCategory": sub, "detailType": detail, "season": season,
        "colorName": color_name, "color": "#%06X" % (argb & 0xFFFFFF),
        "imageUrl": image,
        "isCutout": kind != "photo",
        "originalUri": _RES + original if kind == "garment" else (image if kind == "photo" else None),
        "cutoutUri": image if kind == "bg" else None,
        "garmentUri": image if kind == "garment" else None,
    }


WARDROBE_SAMPLES = [
    _sample("sample_01", "베이지 니트", "상의", "긴팔 상의", "니트", "사계절", "베이지", 0xFFB5A38F, "garment", "casual_daily_look"),
    _sample("sample_02", "스트라이프 블라우스", "상의", "반팔 상의", "블라우스", "여름", "그레이", 0xFFA6A7A9, "garment", "summer_blouse"),
    _sample("sample_03", "화이트 반팔티", "상의", "반팔 상의", "반팔티", "여름", "화이트", 0xFFCFC8C3, "garment", "popular_crop_tshirt"),
    _sample("sample_04", "체크 셔츠", "상의", "긴팔 상의", "셔츠", "사계절", "그레이", 0xFF838892, "photo"),
    _sample("sample_05", "와이드 청바지", "하의", "긴바지", "청바지", "사계절", "블루", 0xFF67777C, "photo"),
    _sample("sample_06", "블랙 슬랙스", "하의", "긴바지", "슬랙스", "사계절", "블랙", 0xFF181C1E, "garment", "shop_officer_pants"),
    _sample("sample_07", "카고 팬츠", "하의", "긴바지", "면바지", "사계절", "블랙", 0xFF313233, "garment", "popular_cargo_pants"),
    _sample("sample_08", "블랙 미니 스커트", "하의", "치마", "미니스커트", "사계절", "블랙", 0xFF282521, "photo"),
    _sample("sample_09", "베이지 블레이저", "아우터", "긴팔 아우터", "자켓", "봄·가을", "베이지", 0xFFCDB6A1, "photo"),
    _sample("sample_10", "데님 반팔 셔츠", "상의", "반팔 상의", "셔츠", "여름", "블루", 0xFF515A69, "garment", "shop_denim_shirt"),
    _sample("sample_11", "스니커즈", "신발", "운동화", "스니커즈", "사계절", "화이트", 0xFFDAD4D5, "photo"),
    _sample("sample_12", "로퍼", "신발", "구두", "로퍼", "사계절", "블랙", 0xFF242728, "bg"),
]

# ---------- AI 체형 분석 결과 (BodyAnalysisScreen.kt) ----------
BODY_ANALYSIS_RESULT = {
    "bodyType": "모래시계형",
    "summary": "어깨·힙 균형 · 허리 라인 뚜렷",
    "proportions": [
        {"label": "어깨 너비", "ratio": 0.82},
        {"label": "허리 라인", "ratio": 0.65},
        {"label": "힙 너비",   "ratio": 0.80},
        {"label": "다리 길이", "ratio": 0.75},
    ],
    "features": [
        "어깨와 힙의 너비가 비슷해요",
        "허리 라인이 뚜렷하게 들어가요",
        "상하체 균형이 잘 맞아요",
    ],
}

# ---------- 시연용 체형 측정 기록 (측정 기록 · 변화 그래프 화면) ----------
# 새 사용자(폰)마다 한 번만 넣는다. is_sample=True 로 저장되어 앱에 "샘플"로 표시되고,
# 앱의 [샘플 기록 지우기] 또는 DELETE /api/body-measurements?samplesOnly=true 로 지울 수 있다.
# 키 165cm 가상 인물, 약 3개월 동안 허리가 조금씩 줄어드는 흐름 (cm 는 정면 폭 추정값)
SAMPLE_HEIGHT_CM = 165.0
SAMPLE_MEASUREMENTS = [
    # (며칠 전, 체형, 신뢰도, 어깨cm, 허리cm, 엉덩이cm, 다리cm, 상체cm)
    (84, "직사각형", 78, 38.6, 31.0, 36.8, 77.4, 49.8),
    (63, "직사각형", 81, 38.5, 30.6, 36.6, 77.6, 49.6),
    (42, "모래시계형", 83, 38.7, 30.1, 36.7, 77.5, 49.9),
    (21, "모래시계형", 85, 38.4, 29.7, 36.5, 77.5, 49.7),
    (7, "모래시계형", 86, 38.5, 29.4, 36.6, 77.6, 49.8),
]

# ---------- 체형별 스타일 제안 (StyleSuggestionScreen.kt) ----------
RECOMMENDED_STYLES = [
    {"id": 1, "title": "허리 강조 원피스",     "desc": "모래시계형에 잘 맞는 허리 라인 원피스", "color": "#EDE3D5"},
    {"id": 2, "title": "피트 앤 플레어",      "desc": "상하체 균형을 살려주는 핏",           "color": "#E8E0D8"},
    {"id": 3, "title": "크롭 탑 + 미디스커트", "desc": "허리 라인을 자연스럽게 강조",          "color": "#DDD5C8"},
    {"id": 4, "title": "랩 스타일",          "desc": "체형을 돋보이게 하는 클래식 스타일",     "color": "#E0D8D0"},
]

AVOID_STYLES = [
    "오버사이즈 티셔츠 + 와이드 팬츠 조합",
    "허리 라인이 없는 박시한 원피스",
    "너무 길고 풍성한 레이어드 스타일",
]

# ---------- 스타일 분석 (StyleAnalysisScreen.kt) ----------
STYLE_BREAKDOWN = [
    {"name": "미니멀", "percent": 35, "color": "#C18D5A"},
    {"name": "캐주얼", "percent": 25, "color": "#8FAE8B"},
    {"name": "클래식", "percent": 20, "color": "#8FA8C8"},
    {"name": "스트릿", "percent": 12, "color": "#C88FA8"},
    {"name": "빈티지", "percent": 8,  "color": "#B0A860"},
]

STYLE_KEYWORDS = ["깔끔한", "심플한", "모던한", "자연스러운", "세련된", "편안한"]

STYLE_AI_COMMENT = (
    "깔끔하고 심플한 미니멀 스타일을 선호하시네요. "
    "불필요한 장식 없이 기본에 충실한 코디를 잘 소화하실 수 있어요. "
    "베이직 아이템을 활용한 다양한 코디 조합이 잘 어울릴 거예요."
)

# ---------- 쇼핑 상품 (ShopScreen.kt products 와 같은 8개, 순서도 같음) ----------
# imageUrl 이 빈 상품은 앱 안 사진(res/drawable)을 쓴다. productUrl = 실제 구매 페이지.
_SIZES = ["S", "M", "L", "XL"]
PRODUCTS = [
    {"name": "CKS 스탠다드 슬림핏 반팔 셔츠 네이비", "brand": "CHICKS", "mallName": "무신사",
     "price": 39000, "originalPrice": None, "rating": 4.7, "reviewCount": 184,
     "bgColor": "#D5E0EC", "badge": "AI추천", "category": "상의", "imageUrl": "",
     "productUrl": "https://www.musinsa.com/products/6405693"},
    {"name": "사이드 핀턱 빈티지 워싱 벌룬핏 데님 팬츠", "brand": "미스터제이슨", "mallName": "4910",
     "price": 8522, "originalPrice": 22000, "rating": 4.5, "reviewCount": 231,
     "bgColor": "#A8B8C8", "badge": None, "category": "하의",
     "imageUrl": "https://d3ha2047wt6x28.cloudfront.net/hEmVfMXLFDQ/pr:GOODS_DETAIL/czM6Ly9hYmx5LWltYWdlLWxlZ2FjeS9kYXRhL2dvb2RzLzIwMjYwMjI2XzE3NzIwNjgyMzQ2NTQ5NjRtLmpwZw",
     "productUrl": "https://4910.kr/desktop/goods/51479882"},
    {"name": "[made]썸머크루즈가디건", "brand": "어텀", "mallName": "지그재그",
     "price": 26560, "originalPrice": 35000, "rating": 4.6, "reviewCount": 98,
     "bgColor": "#EDE3D5", "badge": "AI추천", "category": "아우터",
     "imageUrl": "https://cf.product-image.s.zigzag.kr/original/c/17/026/333/170263336-3421640085957955899.jpeg?width=400&height=400&quality=80&format=webp",
     "productUrl": "https://zigzag.kr/catalog/products/170263336"},
    {"name": "아치스 조리샌들_블랙", "brand": "ARCHIES", "mallName": "무신사",
     "price": 35000, "originalPrice": None, "rating": 4.8, "reviewCount": 342,
     "bgColor": "#3A3A3A", "badge": "베스트", "category": "신발", "imageUrl": "",
     "productUrl": "https://www.musinsa.com/products/3142059"},
    {"name": "백 사틴 오피서 팬츠 [블랙]", "brand": "무신사스탠다드", "mallName": "무신사",
     "price": 59900, "originalPrice": None, "rating": 4.8, "reviewCount": 512,
     "bgColor": "#4A4A4A", "badge": None, "category": "하의", "imageUrl": "",
     "productUrl": "https://www.musinsa.com/products/3304653"},
    {"name": "[MADE]러블린셔링블라우스 - 4color", "brand": "Ni:some", "mallName": "지그재그",
     "price": 32200, "originalPrice": 42900, "rating": 4.9, "reviewCount": 284,
     "bgColor": "#F5EEE8", "badge": "AI추천", "category": "상의",
     "imageUrl": "https://cf.product-image.s.zigzag.kr/original/d/2026/4/27/5875_202604271053470543_53810.jpeg?width=400&height=400&quality=80&format=webp",
     "productUrl": "https://zigzag.kr/catalog/products/161164288"},
    {"name": "바이오워싱 크롭 데님 반팔 셔츠", "brand": "DIMITRI BLACK", "mallName": "무신사",
     "price": 49000, "originalPrice": None, "rating": 4.4, "reviewCount": 156,
     "bgColor": "#A8C0D0", "badge": None, "category": "상의", "imageUrl": "",
     "productUrl": "https://www.musinsa.com/products/6317950"},
    {"name": "8부 원턱 버뮤다 스웨트 팬츠 3color", "brand": "NANDE", "mallName": "무신사",
     "price": 49000, "originalPrice": None, "rating": 4.5, "reviewCount": 203,
     "bgColor": "#D5D0C8", "badge": None, "category": "하의", "imageUrl": "",
     "productUrl": "https://www.musinsa.com/products/6068184"},
]
for _p in PRODUCTS:
    _p.setdefault("description", "")
    _p.setdefault("colors", [_p["bgColor"]])
    _p.setdefault("sizes", _SIZES)

# ---------- 프로필 (MyPageScreen.kt) ----------
# 데모 사용자 (로그인 기능 전까지 모든 요청이 이 사용자로 저장된다). 개수는 DB 에서 센다.
PROFILE = {
    "nickname": "나의 스타일",
    "email": "style@fashion.ai",
    "preferredStyles": ["미니멀", "캐주얼", "클래식"],
}

# ---------- 온보딩 스타일 선택지 (OnboardingScreen.kt) ----------
STYLE_OPTIONS = ["캐주얼", "미니멀", "스트릿", "페미닌", "빈티지", "스포티", "클래식", "아메카지"]


# ---------- 스타일 취향 학습 (StylePreferenceScreen.kt) ----------
# 앱 StylePreferenceStore 와 동일한 5개 스타일. 평가 기록은 DB(style_ratings)에 쌓이고
# 점수는 그 기록으로 매번 다시 계산한다 (좋아요 +2, 싫어요 -1).
STYLE_PREFERENCE_STYLES = ["미니멀", "캐주얼", "클래식", "스트릿", "빈티지"]

# ---------- 가상 피팅 후보 의류 (VirtualFittingScreen.kt) ----------
# 옷장에 등록된 옷 + 아래 샘플이 피팅 후보로 제공된다.
FITTING_SAMPLES = [
    {"id": 901, "name": "베이지 니트",   "category": "상의",
     "imageUrl": "https://loremflickr.com/300/380/knit,sweater?lock=40"},
    {"id": 902, "name": "화이트 블라우스", "category": "상의",
     "imageUrl": "https://loremflickr.com/300/380/blouse,clothing?lock=41"},
    {"id": 903, "name": "데님 자켓",     "category": "아우터",
     "imageUrl": "https://loremflickr.com/300/380/denim,jacket?lock=49"},
    {"id": 904, "name": "베이지 코트",   "category": "아우터",
     "imageUrl": "https://loremflickr.com/300/380/coat,beige?lock=48"},
]
