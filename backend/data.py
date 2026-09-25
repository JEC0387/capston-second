"""
인메모리 모의 데이터베이스.
현재 앱 화면(.kt)에 하드코딩된 더미 데이터를 그대로 옮겨,
백엔드 연동 후에도 화면이 지금과 동일하게 보이도록 함.

추후 Firebase/실DB로 교체할 때 이 파일의 데이터 접근 부분만 바꾸면 됨.
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

WARDROBE_ITEMS = [
    {"id": 1,  "name": "베이지 니트",     "category": "상의", "color": "#EDE3D5",
     "imageUrl": "https://loremflickr.com/300/380/knit,sweater?lock=40",   "isFavorite": False},
    {"id": 2,  "name": "화이트 블라우스",  "category": "상의", "color": "#F5F2EE",
     "imageUrl": "https://loremflickr.com/300/380/blouse,clothing?lock=41", "isFavorite": False},
    {"id": 3,  "name": "그래픽 티셔츠",   "category": "상의", "color": "#E8DDD0",
     "imageUrl": "https://loremflickr.com/300/380/tshirt,graphic?lock=42",  "isFavorite": False},
    {"id": 4,  "name": "스트라이프 셔츠",  "category": "상의", "color": "#DDD8D2",
     "imageUrl": "https://loremflickr.com/300/380/shirt,stripe?lock=43",    "isFavorite": False},
    {"id": 5,  "name": "와이드 팬츠",     "category": "하의", "color": "#CDC5BC",
     "imageUrl": "https://loremflickr.com/300/380/pants,wide?lock=44",      "isFavorite": False},
    {"id": 6,  "name": "슬랙스",         "category": "하의", "color": "#B8B0A8",
     "imageUrl": "https://loremflickr.com/300/380/slacks,trousers?lock=45", "isFavorite": False},
    {"id": 7,  "name": "카고 팬츠",      "category": "하의", "color": "#CFCAC0",
     "imageUrl": "https://loremflickr.com/300/380/cargo,pants?lock=46",     "isFavorite": False},
    {"id": 8,  "name": "미니 스커트",     "category": "하의", "color": "#E0D5CC",
     "imageUrl": "https://loremflickr.com/300/380/skirt,mini?lock=47",      "isFavorite": False},
    {"id": 9,  "name": "트렌치 코트",     "category": "아우터", "color": "#D5C8B8",
     "imageUrl": "https://loremflickr.com/300/380/trench,coat?lock=48",     "isFavorite": False},
    {"id": 10, "name": "데님 자켓",      "category": "아우터", "color": "#C5BDB2",
     "imageUrl": "https://loremflickr.com/300/380/denim,jacket?lock=49",    "isFavorite": False},
    {"id": 11, "name": "스니커즈",       "category": "신발", "color": "#F0EBE3",
     "imageUrl": "https://loremflickr.com/300/380/sneakers,shoes?lock=50",  "isFavorite": False},
    {"id": 12, "name": "로퍼",           "category": "신발", "color": "#B8A898",
     "imageUrl": "https://loremflickr.com/300/380/loafers,shoes?lock=51",   "isFavorite": False},
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

# ---------- 쇼핑 상품 (ShopScreen.kt products) ----------
PRODUCTS = [
    {"id": 1, "name": "오버사이즈 베이지 니트", "brand": "ZARA",   "price": 43000,  "originalPrice": 69000,
     "rating": 4.8, "reviewCount": 284, "bgColor": "#EDE3D5", "badge": "AI추천",
     "imageUrl": "https://loremflickr.com/400/500/knit,sweater?lock=30"},
    {"id": 2, "name": "린넨 와이드 팬츠",      "brand": "H&M",    "price": 58000,  "originalPrice": 79000,
     "rating": 4.6, "reviewCount": 156, "bgColor": "#D5CABD", "badge": None,
     "imageUrl": "https://loremflickr.com/400/500/pants,linen?lock=31"},
    {"id": 3, "name": "클래식 코튼 셔츠",      "brand": "UNIQLO", "price": 39000,  "originalPrice": None,
     "rating": 4.9, "reviewCount": 512, "bgColor": "#F0EBE3", "badge": "베스트",
     "imageUrl": "https://loremflickr.com/400/500/shirt,cotton?lock=32"},
    {"id": 4, "name": "미디 플리츠 스커트",    "brand": "COS",    "price": 79000,  "originalPrice": 99000,
     "rating": 4.5, "reviewCount": 98,  "bgColor": "#E0D5CC", "badge": None,
     "imageUrl": "https://loremflickr.com/400/500/skirt,fashion?lock=33"},
    {"id": 5, "name": "카멜 울 코트",         "brand": "MANGO",  "price": 159000, "originalPrice": 229000,
     "rating": 4.7, "reviewCount": 203, "bgColor": "#D5C8B8", "badge": "AI추천",
     "imageUrl": "https://loremflickr.com/400/500/coat,fashion?lock=34"},
    {"id": 6, "name": "데님 스트레이트 팬츠",  "brand": "LEVIS",  "price": 89000,  "originalPrice": 119000,
     "rating": 4.4, "reviewCount": 341, "bgColor": "#C8C0B5", "badge": None,
     "imageUrl": "https://loremflickr.com/400/500/denim,jeans?lock=35"},
]

# ---------- 상품 상세 추가 정보 (ProductDetailScreen.kt) ----------
PRODUCT_DETAIL_EXTRA = {
    1: {
        "name": "오버사이즈 베이지 니트 스웨터",
        "description": "부드러운 터치감의 오버사이즈 니트. 어떤 하의와도 잘 어울리는 베이직 아이템.",
        "colors": ["#EDE3D5", "#F5F2EE", "#C5C5C0", "#3A3A3A"],
        "sizes": ["XS", "S", "M", "L", "XL"],
        "lowestPriceInfo": "최저가: 43,000원 · 네이버쇼핑, 무신사, 29CM 등 비교",
    },
}

# ---------- 프로필 (MyPageScreen.kt) ----------
PROFILE = {
    "nickname": "나의 스타일",
    "email": "style@fashion.ai",
    "wardrobeCount": 12,
    "outfitCount": 8,
    "favoriteCount": 24,
    "bodyType": "모래시계형",
    "preferredStyles": ["미니멀", "캐주얼", "클래식"],
}

# ---------- 온보딩 스타일 선택지 (OnboardingScreen.kt) ----------
STYLE_OPTIONS = ["캐주얼", "미니멀", "스트릿", "페미닌", "빈티지", "스포티", "클래식", "아메카지"]


# ---------- 스타일 취향 학습 (StylePreferenceScreen.kt) ----------
# 앱 StylePreferenceStore 와 동일한 5개 스타일. 서버는 인메모리로 점수를 누적한다.
STYLE_PREFERENCE_STYLES = ["미니멀", "캐주얼", "클래식", "스트릿", "빈티지"]

STYLE_PREFERENCE_STATE = {
    "scores": {s: 0 for s in STYLE_PREFERENCE_STYLES},
    "ratedCount": 0,
}

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
