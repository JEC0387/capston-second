# 백엔드 연동 안내

앱이 FastAPI 백엔드를 실제로 호출하도록 연동했습니다.
모든 연동은 **서버 우선 → 실패 시 로컬 폴백** 방식이라, 서버가 꺼져 있어도 앱은 정상 동작합니다.

## 연동된 기능
| 기능 | 호출 엔드포인트 | 폴백(서버 없을 때) |
|---|---|---|
| 객체 탐지(AI 옷 등록) | POST /api/object-detection | 색상 기반 로컬 태깅 |
| 실시간 날씨 | GET /api/weather | OpenWeatherMap 직접 호출 |
| 스타일 취향 학습 | GET·POST /api/style-preference | SharedPreferences 로컬 점수 |
| 마이페이지 프로필 | GET /api/profile | 앱 내장 기본 프로필 |

## 서버 켜는 법
```bash
cd backend
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

## 접속 주소 설정
- 에뮬레이터: 기본값 `http://10.0.2.2:8000` 그대로 동작
- 실기기: `data/BackendApi.kt` 의 `BASE_URL` 을 PC 내부 IP로 변경
  (예: `http://192.168.0.5:8000`), PC 방화벽 8000 포트 허용 필요

## 연동 확인 방법
서버를 켠 상태로 앱에서 마이페이지/취향학습/날씨를 보면 서버 데이터가 뜨고,
서버를 끄면 자동으로 로컬 데이터로 전환됩니다.

## 참고 — 서버로 옮기지 않은 화면
홈·쇼핑·상품상세 화면은 앱에 번들된 실제 무신사 상품 이미지(R.drawable)를 사용하므로,
이미지 손실을 막기 위해 의도적으로 로컬 데이터를 유지했습니다.
(백엔드에는 해당 엔드포인트가 준비돼 있어 필요 시 전환 가능)
