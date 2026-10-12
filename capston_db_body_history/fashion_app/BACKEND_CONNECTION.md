# 백엔드 연동 안내

앱이 FastAPI 백엔드를 실제로 호출하도록 연동했습니다.
모든 연동은 **서버 우선 → 실패 시 로컬 폴백** 방식이라, 서버가 꺼져 있어도 앱은 정상 동작합니다.

## 연동된 기능
| 기능 | 호출 엔드포인트 | 폴백(서버 없을 때) |
|---|---|---|
| AI 옷 등록 — 옷 종류 분류 | POST /api/clothing/classify | 사용자가 종류 직접 선택 |
| 객체 탐지 | POST /api/object-detection | 색상 기반 로컬 태깅 |
| 실시간 날씨 | GET /api/weather | OpenWeatherMap 직접 호출 |
| 스타일 취향 학습 | GET·POST /api/style-preference | SharedPreferences 로컬 점수 |
| 마이페이지 프로필 | GET /api/profile | 앱 내장 기본 프로필 |
| **옷장 → DB 저장** | POST /api/wardrobe/sync | 폰에만 저장 (다음 변경 때 다시 보냄) |
| **착용 기록 → DB 저장** | POST /api/wear-logs/sync | 폰에만 저장 |
| **체형 분석 결과 → DB 저장** | POST /api/body-measurements (동의 화면에서 '측정 기록 저장'을 켰을 때만) | 저장 안 함 (화면 결과는 그대로) |
| **측정 기록 · 변화 그래프** | GET /api/body-measurements | "서버에 연결되지 않아요" 안내 + 다시 불러오기 |
| **측정 기록 삭제 (마이페이지)** | DELETE /api/body-measurements | "삭제하지 못했어요" 안내 |
| **쇼핑 하트 → DB 저장** | GET /api/products/likes · POST /api/products/{id}/like | 앱을 끄면 하트 초기화 (예전과 같음) |

## DB 연동 (Supabase PostgreSQL) — `data/DbSync.kt`

- **폰이 기준**입니다. 옷장 · 착용 기록은 지금처럼 폰에 먼저 저장하고, 바뀔 때마다 `DbSync`가
  **전체 목록**을 서버로 보내 DB를 똑같이 맞춥니다. (화면 코드는 그대로, `WardrobeStore` · `WearLogStore`의 저장 함수에서 호출)
- 짧은 시간에 여러 번 바뀌면 0.5초 모았다가 마지막 상태만 보냅니다.
- 사진 파일은 보내지 않습니다. 옷은 사진 **주소만**, 체형 분석은 **숫자 결과만**(체형 · 비율 · 키 · cm 추정값) 저장합니다.
- 서버 DB 설정 방법은 `backend/README.md` → "Supabase DB 준비"

## 서버 켜는 법
```bash
cd backend
pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```
(처음 한 번 `backend/.env` 에 Supabase 주소 `DATABASE_URL` 을 넣어야 서버가 켜집니다)

## 접속 주소 설정
- 에뮬레이터: 기본값 `http://10.0.2.2:8000` 그대로 동작
- 실기기: `data/BackendApi.kt` 의 `BASE_URL` 을 PC 내부 IP로 변경
  (예: `http://192.168.0.5:8000`), PC 방화벽 8000 포트 허용 필요

## 연동 확인 방법
서버를 켠 상태로 앱에서 마이페이지/취향학습/날씨를 보면 서버 데이터가 뜨고,
서버를 끄면 자동으로 로컬 데이터로 전환됩니다.

DB 저장 확인: 서버를 켠 상태로 옷 하트 · 옷 등록 · 오늘 입은 옷 기록 · 체형 분석 · 쇼핑 하트를 한 뒤
Supabase 대시보드 **Table Editor** 의 `clothes` · `wear_logs` · `body_measurements` · `product_likes` 표를 보면 바로 반영돼 있습니다.
(또는 브라우저에서 `http://localhost:8000/api/wardrobe`, `/api/wear-logs`, `/api/body-measurements`)

## 참고 — 서버로 옮기지 않은 화면
홈·쇼핑·상품상세 화면의 **상품 목록**은 앱에 번들된 실제 상품 이미지(R.drawable)를 사용하므로 앱 데이터를 그대로 씁니다.
서버 DB의 `products` 표에는 같은 8개 상품(같은 순서 · 이름 · 구매 링크)이 들어 있고, 쇼핑 하트는 상품 이름으로 서버 상품과 연결합니다.
(상품 이름을 바꾸면 `backend/data.py` 의 PRODUCTS 도 같이 바꾸고, 이미 DB에 들어간 뒤라면 Supabase `products` 표에서도 고쳐야 하트가 저장됩니다)
