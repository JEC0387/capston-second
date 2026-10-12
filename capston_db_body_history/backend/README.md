# FashionProject 백엔드 (FastAPI + Supabase PostgreSQL)

앱과 같은 데이터를 제공하고, 사용자 데이터(옷장 · 착용 기록 · 체형 측정 기록 · 취향 평가 · 상품 하트)를
**Supabase(PostgreSQL) DB에 저장**하는 서버입니다. AI 옷 분류(DeepFashion2) · 객체 탐지(YOLOv8) · 실시간 날씨도 제공합니다.

## 실행 방법

### 1) Supabase DB 준비 (처음 한 번)

1. [supabase.com](https://supabase.com) 로그인 → **New project** (Region: Northeast Asia (Seoul))
   - 이때 정한 **Database Password** 를 꼭 메모 (팀장만 보관, 팀원에게는 개인톡으로)
2. 프로젝트 화면 상단 **Connect** → **Session pooler** 탭의 주소를 복사
   ```
   postgresql://postgres.abcdefgh:[YOUR-PASSWORD]@aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres
   ```
3. `backend/.env.example` 을 복사해서 같은 폴더에 **`.env`** 로 저장
   ```bash
   # Windows
   copy .env.example .env
   # Mac / Linux
   cp .env.example .env
   ```
4. `.env` 를 메모장으로 열고 `DATABASE_URL=` 뒤에 주소를 붙여넣은 뒤 **`[YOUR-PASSWORD]` 를 실제 비밀번호로 교체** (대괄호도 지우기)

> `.env` 는 `.gitignore` 에 들어 있어 깃허브에 **올라가지 않습니다.** 비밀번호를 코드나 단톡방에 올리지 마세요.
> 비밀번호에 `@ # / ?` 같은 특수문자가 있으면 주소가 깨지므로, Supabase에서 비밀번호를 영문+숫자로 다시 정하는 게 편합니다.

### 2) 서버 실행

```bash
cd backend
pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

처음 켜면 서버가 알아서
- Supabase에 **테이블 8개를 만들고** (이미 있으면 그대로 둠)
- 비어 있을 때만 **데모 사용자 · 샘플 옷 12벌 · 상품 8개**를 넣습니다.
- 예전 버전 코드로 만든 테이블이 이미 있으면, 새로 필요한 칸만 자동으로 덧붙입니다 (데이터는 지우지 않음).

터미널에 `[DB] 연결 대상: Supabase PostgreSQL (aws-0-ap-northeast-2.pooler.supabase.com:5432)` 이 보이면 성공입니다.
브라우저에서 **http://localhost:8000/docs** 를 열면 모든 API를 직접 눌러서 테스트할 수 있고,
Supabase 대시보드 왼쪽 **Table Editor** 에서 저장된 데이터를 표로 볼 수 있습니다.

`DATABASE_URL` 이 없으면 서버가 켜지지 않고 설정 방법을 안내하는 메시지를 띄웁니다.

## 실시간 날씨 API 키 (선택)

`/api/weather`는 OpenWeatherMap을 사용합니다. `backend/.env` 에 한 줄을 넣으면 실제 날씨를,
없으면 기본값(맑음 22도)을 반환합니다.

```
OWM_API_KEY=발급받은_키
```

## 안드로이드 앱에서 접속할 주소

| 환경 | Base URL |
|---|---|
| 에뮬레이터 | `http://10.0.2.2:8000` |
| 실기기 (같은 와이파이) | `http://<PC의 IP>:8000` (예: `http://192.168.0.5:8000`) |

> 실기기 테스트 시 PC 방화벽에서 8000 포트를 허용해야 합니다.
> Windows: `설정 > 방화벽 > 인바운드 규칙`에서 TCP 8000 허용.

## DB 테이블 (db_models.py)

| 테이블 | 내용 | 앱에서 채우는 곳 |
|---|---|---|
| `users` | 사용자 (폰마다 1명 + 데모 사용자) | 앱이 처음 연결할 때 자동 생성 |
| `clothes` | 옷장 옷 — 이름 · 종류 · 색 · 계절 · 스타일 · 하트 시각 · 사진 주소 | 옷 등록 · 수정 · 삭제 · 하트 · [계속 보관] |
| `wear_logs` | 착용 기록 — 날짜 · 입은 옷 id 목록 · 메모 | [오늘 입은 옷] · 달력 |
| `body_measurements` | 체형 분석 결과 — 체형 · 신뢰도 · 비율 · cm 추정값 (**사진은 저장 안 함**) | 체형 분석 성공 시 |
| `style_ratings` | 취향 학습 좋아요/싫어요 기록 | 취향 학습 · 홈 코디 👍👎 |
| `products` | 쇼핑 상품 (구매 링크 포함) | 서버 초기 데이터 |
| `product_likes` | 상품 하트 | 쇼핑 화면 하트 |
| `size_charts` | 브랜드 사이즈표 (사이즈 추천용) | `import_size_charts.py` 로 CSV 넣기 |

### 동기화 방식 — "폰이 기준, 서버는 따라 저장"

- 앱은 지금처럼 폰 안(SharedPreferences)에 먼저 저장하고, 바뀔 때마다 **전체 목록**을 서버로 보냅니다
  (`/api/wardrobe/sync`, `/api/wear-logs/sync`). 서버는 그 목록과 똑같아지도록 추가 · 수정 · 삭제합니다.
- 서버가 꺼져 있어도 앱은 그대로 동작하고, 다음에 무언가 바뀌거나 앱을 다시 켤 때 맞춰집니다.
- **사용자 구분**: 로그인 기능이 생기기 전까지, 앱은 처음 실행 때 만든 **기기 번호**를 모든 요청의 `X-Device-Id` 헤더로 보냅니다.
  서버는 번호마다 `users` 에 사용자를 따로 만들어서, 팀원 여러 명이 같은 Supabase DB를 써도 서로의 옷장을 덮어쓰지 않습니다.
- Swagger(`/docs`)에서 `X-Device-Id` 칸을 비우면 **데모 사용자**(샘플 옷 12벌), 폰의 번호를 넣으면 그 폰의 데이터가 보입니다.
  폰의 번호는 Supabase `users` 표의 `device_id` 칸에서 확인할 수 있습니다.
- 그래서 Swagger에서 `POST /api/wardrobe` 로 폰 사용자에게 넣은 옷은, 폰에 없는 옷이라 **다음 앱 동기화 때 지워집니다.**
- 옷 사진 파일은 올리지 않고 **폰 안의 주소만** 저장합니다 (사진 서버 업로드는 다음 단계).

## 사이즈표 넣기

1. `size_charts_template.csv` 를 엑셀로 열어 쇼핑몰 '실측 사이즈' 표를 옮겨 적고 CSV(UTF-8)로 저장
2. `python import_size_charts.py 내사이즈표.csv` (기존 표를 지우고 새로 넣으려면 `--replace`)
3. `GET /api/size-charts?brand=무신사스탠다드&category=하의` 로 확인

## 엔드포인트 ↔ 앱 화면 매핑

(DB) = Supabase에 저장 · 조회

| 앱 화면 | 메서드 | 경로 | 설명 |
|---|---|---|---|
| HomeScreen | GET | `/api/home` | 날씨 + 오늘의 코디 + 인기 상품 |
| (예시) | GET | `/api/outfits/weather` | 날씨 기반 예시 코디 3종 |
| WardrobeStore(앱 저장소) | POST | `/api/wardrobe/sync` | (DB) 폰 옷장 전체를 서버에 맞춤 |
| WardrobeScreen | GET | `/api/wardrobe?category=상의` | (DB) 옷장 목록 (하트 옷이 위) |
| (Swagger 시연) | POST | `/api/wardrobe` | (DB) 의류 추가 |
| (Swagger 시연) | POST | `/api/wardrobe/{id}/favorite` | (DB) 하트 토글 |
| (Swagger 시연) | GET | `/api/wardrobe/favorites` | (DB) 하트한 옷만 |
| (Swagger 시연) | DELETE | `/api/wardrobe/{id}` | (DB) 의류 삭제 |
| WearLogStore(앱 저장소) | POST | `/api/wear-logs/sync` | (DB) 착용 기록 전체를 서버에 맞춤 |
| WearCalendarScreen | GET | `/api/wear-logs` | (DB) 착용 기록 조회 |
| BodyAnalysisScreen | POST | `/api/body-measurements` | (DB) 체형 분석 결과 숫자 저장 |
| BodyAnalysisScreen | GET | `/api/body-measurements` | (DB) 측정 기록 (변화 그래프용) |
| BodyAnalysisScreen | DELETE | `/api/body-measurements` | (DB) 측정 기록 전부 삭제 |
| (예전 방식) | POST | `/api/body-analysis` | 전신 사진 업로드 → 고정 결과 |
| AddClothingScreen | POST | `/api/clothing/classify` | 옷 사진 → DeepFashion2 옷 종류 분류 + 윤곽 |
| (객체 탐지) | POST | `/api/object-detection` | 이미지 업로드 → YOLOv8 객체 탐지 |
| WeatherViewModel | GET | `/api/weather?lat=&lon=` | 실시간 날씨 + 옷차림 조언 |
| StylePreferenceScreen | GET | `/api/style-preference` | (DB) 취향 학습 현황(점수·순위) |
| StylePreferenceScreen | POST | `/api/style-preference/rate` | (DB) 코디 좋아요/싫어요 기록 |
| VirtualFittingScreen | GET | `/api/virtual-fitting/items` | (DB) 가상 피팅 후보 의류 |
| StyleSuggestionScreen | GET | `/api/style-suggestions` | (DB) 최근 체형 기준 추천/회피 스타일 |
| StyleAnalysisScreen | GET | `/api/style-analysis` | 스타일 분포 + 키워드 + AI 코멘트 |
| ShopScreen | GET | `/api/products?search=셔츠` | (DB) 상품 목록 (검색, 하트 여부 포함) |
| ShopScreen | GET | `/api/products/likes` | (DB) 하트 누른 상품 (누른 순서) |
| ShopScreen | POST | `/api/products/{id}/like` | (DB) 하트 켜기/끄기 `{"liked": true}` |
| ProductDetailScreen | GET | `/api/products/{id}` | (DB) 상품 상세 (색상/사이즈/할인율/구매 링크) |
| (사이즈 추천 예정) | GET | `/api/size-charts?brand=&category=` | (DB) 브랜드 사이즈표 |
| MyPageScreen | GET | `/api/profile` | (DB) 프로필 + 옷 · 착용 기록 · 하트 개수 |
| OnboardingScreen | GET | `/api/styles` | 스타일 선택지 목록 |
| OnboardingScreen | POST | `/api/profile/styles` | (DB) 선호 스타일 저장 |

## 응답 예시

`GET /api/products` :

```json
{
  "banner": "AI가 내 체형·취향을 분석해 추천한 상품이에요",
  "products": [
    {
      "id": 1,
      "name": "CKS 스탠다드 슬림핏 반팔 셔츠 네이비",
      "brand": "CHICKS",
      "price": 39000,
      "originalPrice": null,
      "rating": 4.7,
      "reviewCount": 184,
      "bgColor": "#D5E0EC",
      "badge": "AI추천",
      "imageUrl": "",
      "productUrl": "https://www.musinsa.com/products/6405693",
      "category": "상의",
      "mallName": "무신사",
      "isLiked": false
    }
  ]
}
```

`POST /api/body-measurements` (앱이 체형 분석 성공 시 자동 호출) :

```json
{
  "bodyType": "역삼각형",
  "confidence": 82,
  "shoulderRatio": 1.0, "waistRatio": 0.71, "hipRatio": 0.83, "legRatio": 0.47,
  "shoulderToHip": 1.2, "waistToHip": 0.86,
  "heightCm": 172.0, "shoulderCm": 42.3, "waistCm": 29.1, "hipCm": 34.8,
  "legCm": 80.5, "torsoCm": 52.0,
  "measuredAt": 1760000000000
}
```

## 파일 구조

```
backend/
├── main.py               # FastAPI 앱 + 엔드포인트 (화면별로 주석 구분)
├── database.py           # Supabase 연결 (.env 의 DATABASE_URL 사용)
├── db_models.py          # DB 테이블 8개 정의
├── seed.py               # 테이블 만들기 + 처음 한 번 초기 데이터 넣기
├── import_size_charts.py # 사이즈표 CSV → DB
├── size_charts_template.csv
├── .env.example          # .env 양식 (.env 자체는 깃허브에 안 올라감)
├── detection.py          # YOLOv8 객체 탐지 모듈 (object_detect 프로젝트 통합)
├── fashion_detection.py  # DeepFashion2 옷 종류 분류 + COCO 가방류 합치기
├── models/               # deepfashion2_yolov8s-seg.pt (처음 요청 때 자동 다운로드, git에는 넣지 않음)
├── schemas.py            # 응답/요청 JSON 스키마 (앱 data class와 1:1)
├── data.py               # 고정 화면 데이터 + DB 초기 데이터 (샘플 옷 · 상품)
├── weather.py            # OpenWeatherMap 실시간 날씨
└── requirements.txt
```

> `db_models.py` 는 DB 테이블 정의, 폴더 `models/` 는 AI 모델 파일 보관 장소입니다.

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

## 옷 종류 분류 API (DeepFashion2)

AI 옷 등록 화면(AddClothingScreen)이 사용합니다. `POST /api/clothing/classify`

- 두 모델을 함께 실행하고 결과를 합칩니다 (`fashion_detection.py`)
  - **DeepFashion2 YOLOv8-seg**: 옷 13종류 + 옷 윤곽(마스크)
  - **COCO YOLOv8** (`yolov8n.pt`, 기존 객체 탐지 모델): 가방류(handbag/backpack/suitcase)만 사용
- 같은 옷에 이름이 겹쳐 잡히면(박스 IoU ≥ 0.7) 확신도가 높은 쪽만 남기고, 크게 잡힌 순서로 반환합니다.
- 요청: `multipart/form-data` — `photo`, `conf_threshold`(선택, 기본 0.3)
- 응답 예시 (`mask`는 이미지와 같은 크기의 흑백 PNG를 base64로 인코딩한 값, 흰색 = 옷):

```json
{
  "imageWidth": 720, "imageHeight": 900,
  "candidates": [
    {"label": "short_sleeved_shirt", "source": "deepfashion2", "confidence": 0.81,
     "bbox": [90, 120, 610, 520], "area": 0.43, "mask": "iVBORw0KGgo..."},
    {"label": "trousers", "source": "deepfashion2", "confidence": 0.88,
     "bbox": [120, 480, 590, 900], "area": 0.23, "mask": "iVBORw0KGgo..."}
  ]
}
```

### 모델 파일 (자동 다운로드)

모델 파일(약 24MB)은 용량과 라이선스 때문에 저장소에 넣지 않습니다(`.gitignore`의 `backend/models/`).
서버에서 옷 분류를 **처음 요청할 때** `backend/models/`에 파일이 없으면 허깅페이스에서 자동으로 내려받습니다.
(첫 요청은 다운로드 때문에 조금 느리고, 인터넷이 필요합니다.)

자동 다운로드가 안 되는 환경이면 직접 받아 주세요.

```bash
mkdir models
curl -L -o models/deepfashion2_yolov8s-seg.pt https://huggingface.co/Bingsu/adetailer/resolve/main/deepfashion2_yolov8s-seg.pt
```

- 출처: [Bingsu/adetailer](https://huggingface.co/Bingsu/adetailer) (저장소 라이선스 Apache-2.0, mAP50 0.849)
- 학습 데이터 [DeepFashion2](https://github.com/switchablenorms/DeepFashion2)는 **비상업 연구용** → 캡스톤 시연 용도로만 사용
- 파일이 없고 자동 다운로드에도 실패하면 이 API는 503 오류를 돌려주고, 앱은 사용자가 종류를 직접 고르는 방식으로 동작합니다.

### ultralytics 설치 시 주의 (Windows)

`ultralytics`는 `opencv-python`을 함께 설치하려 하는데, 이미 설치된 `opencv-python-headless`와
같은 `cv2` 폴더를 써서 충돌합니다(서버가 켜져 있으면 "액세스 거부" 오류). 이럴 때는 cv2를 건드리지 않고 설치하세요.

```bash
pip install --no-deps ultralytics ultralytics-thop polars cloudpickle
```

## 다음 단계 (확장 포인트)

1. **로그인** — `main.py` 의 `current_user()` 를 "기기 번호 → 사용자" 에서 "로그인 토큰 → 사용자" 로 바꾸면 됨 (Supabase Auth 활용 가능)
2. **옷 사진 서버 저장** — 지금은 폰 안 주소만 저장. Supabase Storage 에 올리고 주소를 `clothes.image_url` 에 저장
3. **사이즈 추천** — `body_measurements` 의 cm 추정값과 `size_charts` 를 비교 (단면 × 2 ≈ 둘레, 여유분 고려)
4. **클라우드 배포** — 서버를 Render / Railway 등에 올리고 앱 `BASE_URL` 을 그 주소로 변경
