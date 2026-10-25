# AI 기반 옷 추천 쇼핑몰 앱

내 옷장 · 체형 · 날씨 · 취향을 분석해 코디를 추천하고, 부족한 옷은 쇼핑으로 이어 주는 안드로이드 앱입니다.

- 팀: **교수님의 최애들** — 정의찬(팀장), 이지민, 손유리, 황상윤
- 지도교수: 안홍섭

## 주요 기능

| 분야 | 기능 |
|---|---|
| 옷장 | AI 옷 등록(배경 제거 → DeepFashion2 옷 종류 분류 → 옷만 오려내기 → 대표 색), 착용 기록 · 달력 · 통계, 옷값 · 착용당 비용, 안 입는 옷 알림, `전체 / 내 옷 / 관심 옷` 필터, 무신사 랭킹 데모 옷장 |
| 코디 추천 | 옷장 옷 조합 엔진(색 · 날씨 · 상황 · 착용 기록 · 취향 · 체형 점수), 5일 예보 코디, 상황별(면접 · 데이트 · 여행 · 운동) 코디, 추천 범위(내 옷 / 내 옷 + 관심 옷 / 관심 옷), [오늘 이거 입었어요], "이것만 사면 코디 완성" |
| 코디 저장 · 일정 | 🔖 저장한 코디(룩북 · 메모 · 착용 횟수), 📅 코디 일정(날짜별 미리 정하기 → 홈 · 달력 · 내일 날씨 알림에 표시, 일정대로 입었는지 확인) |
| 체형 | 촬영 가이드 + 키 기준 cm 측정, 체형 분류(불확실하면 판정 보류), 신체 치수 직접 입력·선택적 기기 저장, 측정 기록 · 변화 그래프, 기존 사이즈 추천, 개인정보 동의 · 기록 삭제 |
| 취향 | 코디 👍👎 · 취향 카드 · 착용 기록으로 스타일 · 색 · 종류 · 색 조합 방식 학습, 내 취향 리포트, 스타일 분석(옷장 + 착용 기록) |
| 쇼핑 | 추천 상품(실제 구매 링크) · 관심 상품 하트, 네이버 이미지 검색 · 쇼핑인사이트, 성별 맞춤 |
| 기타 | 가상 피팅(수동 배치 + AI로 입어보기 · FASHN, 전송 전 확인), 내일 날씨 푸시 알림(저녁 8시), 서버 연결 상태 표시 |
| 서버 · DB | FastAPI + Supabase(PostgreSQL) — 옷장(+ 사진 백업) · 착용 기록 · 체형 기록 · 취향 기록 · 관심 상품 · 성별 · 추천 기록 · 상품 카탈로그 저장, 백업 코드로 다른 폰에 복원, 서버 데이터 삭제, AI 옷 분류 API |
| AI 성능 | 체형 cm 측정 · 옷 분류 정확도 측정 도구, 추천 성과(👍 · 실제 착용 비율) — 마이페이지 'AI 추천 성과' · `/api/recommendations/stats` |

## 폴더 구조

```
capston-second/
├── fashion_app/   안드로이드 앱 (Kotlin · Jetpack Compose) — Android Studio로 이 폴더를 연다
├── backend/       서버 (Python FastAPI) + DB 연결(db_api.py) + AI 성능 측정 도구(evaluation/)
├── 수정사항.md     실행 방법 · 기능별 수정 내역 · 알려진 한계
├── docs/          개인정보 처리 방식 · 발표 전 점검표 · 통합 · 인계 · 실기기 수정 기록 (목록: docs/README.md)
└── .github/workflows/   GitHub Actions — 올릴 때마다 테스트 · APK 자동 빌드
```

**설치용 APK**: 저장소 **Actions** 탭 → 최근 `Android debug APK` 실행 → Artifacts 의 `fashion-app-debug-apk` (API 키 미포함)

## 실행 방법 (요약)

1. **서버**
   ```bash
   cd backend
   pip install -r requirements.txt
   python -m uvicorn main:app --host 0.0.0.0 --port 8000
   ```
   DB를 쓰려면 `backend/.env.example` 을 `backend/.env` 로 복사해 Supabase 주소(`DATABASE_URL`)를 넣고,
   `python apply_migrations.py`(표 만들기 · 001 ~ 004) → `python seed_catalog.py`(상품) 를 실행합니다.
   DB 없이도 서버와 AI 기능은 동작합니다. → http://localhost:8000/docs
2. **외부 API 키**: 실제 키는 `backend/.env`에만 설정합니다. `backend/.env.example`을 참고하세요.
   ```
   OWM_API_KEY=...        # 날씨
   NAVER_API_KEY_ID=...   # 네이버 이미지 검색 · 쇼핑인사이트
   NAVER_API_KEY=...
   UNSPLASH_ACCESS_KEY=... # 사진 검색(선택)
   ```
   앱에는 이 키를 넣지 않습니다. 해당 기능은 실행 중인 백엔드를 통해 호출합니다.
   개인 DB API는 새 접속 토큰을 요구합니다. 운영 DB 마이그레이션과 기존 백업 코드 이전 절차는
   [`backend/AUTH_MIGRATION.md`](backend/AUTH_MIGRATION.md)를 확인하세요. 승인 전 운영 DB에 적용하지 마세요.
3. **앱**: Android Studio로 `fashion_app` 폴더를 열어 실행
   AI 가상피팅 키는 앱에 넣지 않습니다. 서버에서만 `FASHN_API_KEY`를 설정하고,
   앱의 AI 피팅 화면에서 HTTPS 서버 주소와 사용자별 접속 토큰을 입력합니다.
   유료 생성은 기본 비활성화이며 [서버 설정](backend/AI_TRYON_SETUP.md)을 먼저 확인하세요.
   에뮬레이터는 서버 주소 `http://10.0.2.2:8000` 이 기본값입니다.
   **실제 폰**은 앱 마이페이지 > **서버 주소**에 PC 의 와이파이 IP(예: `192.168.0.10`)를 넣고 [연결 확인] → [저장]
   (폰과 PC 는 같은 와이파이, 서버는 `--host 0.0.0.0` 으로 실행).

자세한 내용: [`수정사항.md`](수정사항.md) · [`backend/DB_INTEGRATION.md`](backend/DB_INTEGRATION.md) ·
[`backend/README.md`](backend/README.md) · [`backend/evaluation/README.md`](backend/evaluation/README.md)

> 실제 키와 비밀번호가 든 `backend/.env`는 공유 ZIP이나 Git에 포함하지 마세요. 이미 앱에 포함됐던 Unsplash 키는 발급처에서 폐기하고 새로 발급하세요.
