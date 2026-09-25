# FashionProject 백엔드 (FastAPI)

앱 화면을 하나도 안 바꿔도 되도록, 현재 앱에 하드코딩된 더미 데이터를
그대로 API로 제공하는 백엔드입니다. 나중에 이 데이터를 실제 DB/AI 분석으로
교체하면 됩니다.

## 실행 방법

```bash
cd backend
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

실행 후 브라우저에서 **http://localhost:8000/docs** 를 열면
Swagger 문서에서 모든 API를 직접 눌러서 테스트할 수 있습니다.

## 실시간 날씨 API 키 (선택)

`/api/weather`는 OpenWeatherMap을 사용합니다. 환경변수로 키를 주면 실제 날씨를,
키가 없으면 기본값(맑음 22도)을 반환합니다.

```bash
# Windows (PowerShell)
$env:OWM_API_KEY="발급받은_키"
uvicorn main:app --host 0.0.0.0 --port 8000 --reload

# macOS / Linux
OWM_API_KEY=발급받은_키 uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

## 안드로이드 앱에서 접속할 주소

| 환경 | Base URL |
|---|---|
| 에뮬레이터 | `http://10.0.2.2:8000` |
| 실기기 (같은 와이파이) | `http://<PC의 IP>:8000` (예: `http://192.168.0.5:8000`) |

> 실기기 테스트 시 PC 방화벽에서 8000 포트를 허용해야 합니다.
> Windows: `설정 > 방화벽 > 인바운드 규칙`에서 TCP 8000 허용.

## 엔드포인트 ↔ 앱 화면 매핑

| 앱 화면 | 메서드 | 경로 | 설명 |
|---|---|---|---|
| HomeScreen | GET | `/api/home` | 날씨 + 오늘의 코디 + 인기 상품 |
| WeatherOutfitScreen | GET | `/api/outfits/weather` | 날씨 기반 추천 코디 3종 |
| WardrobeScreen | GET | `/api/wardrobe?category=상의` | 옷장 목록 (카테고리 필터) |
| WardrobeScreen | POST | `/api/wardrobe` | 의류 추가 |
| WardrobeScreen | POST | `/api/wardrobe/{id}/favorite` | 즐겨찾기(하트) 토글 |
| WardrobeScreen | GET | `/api/wardrobe/favorites` | 하트한 옷만 조회 |
| WardrobeScreen | DELETE | `/api/wardrobe/{id}` | 의류 삭제 |
| WeatherViewModel | GET | `/api/weather?lat=&lon=` | 실시간 날씨 + 옷차림 조언 |
| StylePreferenceScreen | GET | `/api/style-preference` | 취향 학습 현황(점수·순위) |
| StylePreferenceScreen | POST | `/api/style-preference/rate` | 코디 좋아요/싫어요 평가 |
| VirtualFittingScreen | GET | `/api/virtual-fitting/items` | 가상 피팅 후보 의류 |
| BodyAnalysisScreen | POST | `/api/body-analysis` | 전신 사진 업로드 → 체형 분석 |
| (객체 탐지) | POST | `/api/object-detection` | 이미지 업로드 → YOLOv8 객체 탐지 |
| StyleSuggestionScreen | GET | `/api/style-suggestions` | 체형별 추천/회피 스타일 |
| StyleAnalysisScreen | GET | `/api/style-analysis` | 스타일 분포 + 키워드 + AI 코멘트 |
| ShopScreen | GET | `/api/products?search=니트` | 상품 목록 (검색) |
| ProductDetailScreen | GET | `/api/products/{id}` | 상품 상세 (색상/사이즈/할인율) |
| MyPageScreen | GET | `/api/profile` | 프로필 + 통계 |
| OnboardingScreen | GET | `/api/styles` | 스타일 선택지 목록 |
| OnboardingScreen | POST | `/api/profile/styles` | 선호 스타일 저장 |

## 응답 예시

`GET /api/products` :

```json
{
  "banner": "AI가 내 체형·취향을 분석해 추천한 상품이에요",
  "products": [
    {
      "id": 1,
      "name": "오버사이즈 베이지 니트",
      "brand": "ZARA",
      "price": 43000,
      "originalPrice": 69000,
      "rating": 4.8,
      "reviewCount": 284,
      "bgColor": "#EDE3D5",
      "badge": "AI추천",
      "imageUrl": "https://..."
    }
  ]
}
```

`POST /api/body-analysis` (multipart/form-data, 필드명 `photo`) :

```json
{
  "bodyType": "모래시계형",
  "summary": "어깨·힙 균형 · 허리 라인 뚜렷",
  "proportions": [
    {"label": "어깨 너비", "ratio": 0.82},
    {"label": "허리 라인", "ratio": 0.65},
    {"label": "힙 너비", "ratio": 0.80},
    {"label": "다리 길이", "ratio": 0.75}
  ],
  "features": ["어깨와 힙의 너비가 비슷해요", "허리 라인이 뚜렷하게 들어가요", "상하체 균형이 잘 맞아요"]
}
```

## 파일 구조

```
backend/
├── main.py          # FastAPI 앱 + 엔드포인트 (화면별로 주석 구분)
├── detection.py     # YOLOv8 객체 탐지 모듈 (object_detect 프로젝트 통합)
├── schemas.py       # 응답/요청 JSON 스키마 (앱 data class와 1:1)
├── data.py          # 인메모리 모의 DB (앱 더미 데이터를 그대로 이전)
└── requirements.txt
```

## 객체 탐지 API (object_detect 통합)

기존 Streamlit 프로젝트(`object_detect`)의 YOLOv8 탐지 기능을
`POST /api/object-detection` 엔드포인트로 통합했습니다.

- 요청: `multipart/form-data`
  - `photo` — 이미지 파일 (jpg/png/webp/bmp)
  - `conf_threshold` — 신뢰도 임계값 (선택, 기본 0.25, 범위 0.1~0.9)
- 응답 예시:

```json
{
  "totalCount": 2,
  "detections": [
    {"label": "person", "confidence": 0.91, "bbox": [120, 40, 480, 920]},
    {"label": "handbag", "confidence": 0.62, "bbox": [300, 500, 420, 700]}
  ],
  "counts": {"person": 1, "handbag": 1},
  "resultImageUrl": "/uploads/detect_3fa8...c1.jpg"
}
```

- `resultImageUrl`은 바운딩 박스가 그려진 결과 이미지로,
  `http://<서버주소>:8000/uploads/...`로 바로 접근할 수 있습니다.
- 최초 요청 시 `yolov8n.pt` 모델이 자동 다운로드되며, 이후에는 캐시에서 재사용됩니다.
- 추론은 CPU에서 동작하며, 입력 이미지는 최대 960px로 리사이즈 후 처리됩니다.

## 다음 단계 (확장 포인트)

1. **실제 체형 분석** — `main.py`의 `analyze_body()` 안 TODO 위치에
   OpenCV/MediaPipe 분석 코드를 호출하도록 교체 (기획서 기술 스택 그대로)
2. **실제 날씨** — `data.WEATHER` 대신 기상청/OpenWeatherMap API 호출
3. **DB 영속화** — `data.py`의 리스트를 Firebase 또는 SQLite로 교체
4. **앱 연동** — 앱에 Retrofit + INTERNET 권한 추가 후 더미 리스트를 API 호출로 교체
