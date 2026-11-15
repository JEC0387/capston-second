# 기존 Genlook 운영 연결 복구 · 2026-11-09

## 기준과 범위

- 팀 main `9e94e0e5bd1c70eec08e596ac5dd30622957153e`에서
  `codex/fashionai-genlook-restore-20261109`를 생성했다. PR #19는 이미 병합되었으므로 새 PR로 검토한다.
- P57 후속 `9d462b72beb3d094775c71ab35e384262537fb26`의 서버 라우트·배포·보안 검사를
  cherry-pick한 커밋은 `a6e718f8e1fbe8479debe7fffc9423f06abd6195`다.
- 팀 PR #21의 Google/Kakao Bearer 연결과 PR #22의 ZIP 빌드 지원을 보존한다.
- 신규 모델·GPU 구축은 필요하지 않다. 이번 작업은 기존 Genlook 연결 복구다.
- 운영 담당자/프로젝트 접근 권한 미확인으로 Railway 배포는 사용자의 지시에 따라 보류한다.
  생성·과금·계정 생성·API 키 교체·기기 설치·운영 DB 변경은 수행하지 않았다.

## P54 실제 성공 기록 (재실행 아님)

`P54_S25_GENLOOK_E2E_20261106.md`에 기록된 2026-11-06 결과:

- S25+ 마법봉 → Genlook 상의+하의 → 중간 검수 → 신발 → 비교·PNG 저장·재열기·재실행 PASS.
- 크레딧 6→3. API 엔진은 외부 Genlook이며, 스마트폰 내부 모델 추론이 아니다.
- 저장 PNG 896×1184 / 619142 bytes,
  SHA256 `2e55f86c8b9d08395cea184380209b52578a6d688d329d9d14846f6de6b2d90d`.
- 상품 단추·주머니·허리·신발 세부 재현은 FAIL. 연결 복구로 품질 PASS로 변경하지 않는다.

## 현재 운영 주소와 로컬 코드의 차이

대상은 `https://fashion-backend-production-aba0.up.railway.app`이다.
TLS 검증을 유지한 인증 없는 읽기 전용 GET 결과:

| 경로 | 운영 응답 | 의미 |
|---|---|---|
| `/` | 200 | 기존 FashionProject API 응답; 서버 전체 부재가 아님 |
| `/health` | 404 | 이번 liveness 경로가 배포되었는지 확인되지 않음 |
| `/api/outfit-vton/config` | 404 | outfit 라우트가 현재 운영 응답에 없음 |
| `/api/auth/providers` | 200 | google/kakao=true, facebook/apple=false; 실제 OAuth 성공을 뜻하지 않음 |

통합한 `main:app` 로컬 검사는 `/health` 200, outfit 설정 미인증/잘못된 Bearer/X-Device-Id
401, 유효 테스트 Bearer 설정 조회 200을 확인한다. 생성 미승인 상태는 ready=false/503이며
사진 본문을 읽지 않는다. liveness는 DB·Genlook 준비 완료를 보증하지 않는다.

## 실제 코드 연결

- `backend/main.py`: 기존 인증/옷장 API와 동일한 ASGI 앱에 outfit 라우터 및 수명주기를 연결.
  `.env`는 해당 backend 디렉터리만 읽고, 배포 환경변수를 덮어쓰지 않는다.
- `backend/outfit_vton_api.py`: 독립 QA 앱과 운영 앱이 동일한 시작/정리/종료 수명주기 사용.
- `backend/Dockerfile`: Python 3.11, `requirements-genlook.txt` 설치,
  `uvicorn main:app`, 단일 worker, access log 비활성, 명시된 프록시만 신뢰.
- `backend/railway.json`: `/health` 배포 검사. 서비스 root가 backend인지 관리자가 확인해야 한다.
- Android `SERVER_URL` 기본값은 위 운영 주소. `SERVER_OUTFIT_APPROVED`는 기본 false이며
  primary 빌드의 `-PfashionOutfitServerApproved=true`에 별도 운영 승인이 필요하다.
  Preview는 운영 업로드 금지, QA는 별도 검증된 세션 경로만 허용한다.
  Bearer는 DataAuth의 서버 바인딩을 유지하고, 이미지 입력 최대 변은 2048이다.

## 운영 담당자에게 필요한 설정 (값/키 원문 제출 금지)

1. Railway 프로젝트·서비스 ID/콘솔 링크, 관리자와 배포 권한, 현재 연결 저장소/브랜치/SHA,
   root `backend`, Dockerfile 및 start command override 여부, 현재 요금/리소스 범위.
   이 PR 승인·병합과 운영 배포는 서로 다른 승인이다.
2. Python 3.11 + `main:app` + 단일 worker. `PORT`는 Railway 값 사용.
   `FASHION_TRUSTED_PROXY_IPS`는 실제 플랫폼 프록시 범위를 확인해 설정한다.
   신뢰 범위를 확인하지 않은 `*` 또는 HTTPS 검사 우회는 금지한다.
3. `DATABASE_URL`은 관리자만 비밀변수로 보관. `DB_AUTO_CREATE=false` 유지.
   현재 적용 이력/스키마 백업, 기존 005 충돌 및 이력 검사 후 승인된 마이그레이션만 적용.
   이번 작업에서는 운영 마이그레이션을 실행하지 않았다.
4. Genlook 선택은 `OUTFIT_VTON_PROVIDER=genlook`.
   `GENLOOK_API_KEY`의 존재·활성 계정 여부만 확인하고 값을 출력/커밋하지 않는다.
   새 승인 전 `OUTFIT_VTON_ENABLED=false`, `GENLOOK_ENABLED=false`,
   `GENLOOK_PAID_CALLS_APPROVED=false`로 두어 연결 검사와 생성 승인을 분리한다.
5. 운영 승인 후 필요한 설정명:
   `OUTFIT_VTON_DEPLOYMENT_APPROVED`, `OUTFIT_VTON_POLICY_VERSION`,
   `OUTFIT_VTON_RETENTION_SECONDS`(1~86400), `OUTFIT_VTON_STORAGE`,
   `OUTFIT_VTON_MAX_JOBS_PER_DAY`, `OUTFIT_VTON_TIMEOUT_SECONDS`;
   `GENLOOK_DEPLOYMENT_APPROVED`, `GENLOOK_PRIVACY_APPROVED`,
   `GENLOOK_PREPAID_CONFIRMED`, `GENLOOK_AUTO_TOPUP_DISABLED_CONFIRMED`,
   `GENLOOK_TERMS_REVIEW_ID`, `GENLOOK_PRIVACY_REVIEW_ID`, `GENLOOK_COST_REVIEW_ID`,
   `GENLOOK_LEDGER_DIR`, `GENLOOK_MAX_TOTAL_CREDITS`, `GENLOOK_UNIT_PRICE_MICROUSD`,
   `GENLOOK_MAX_TOTAL_MICROUSD`, `GENLOOK_TIMEOUT_SECONDS`.
   비용 값과 승인 ID를 임의로 만들지 않는다. 저널/결과는 접근 제한된 지속 볼륨에 두며
   재배포 시 예산 저널을 새로 만들어 사용량 제한을 초기화하지 않는다.
6. P54의 2회 경로는 `GENLOOK_MODE=clothing-pair-then-shoes`.
   기존 private operator review gate를 `GENLOOK_OPERATOR_REVIEW_SECONDS`로 factory에 연결했다.
   예: 검수 300초라면 provider와 service timeout도 전체 생성+검수에 충분한 900초 등으로 함께 검토한다.
   검수 값은 기본 0(비활성), 명시할 때 1~1800이며 paired mode 및 provider timeout 이내여야 한다.
   잘못된 값/모드/시간은 자동 무검수 실행으로 넘어가지 않고 BLOCKED다. 계약 SHA도 달라져
   이전 승인 저널을 조용히 재사용하지 않는다. 외부 관리자 승인 API를 새로 열지 않는다.
   다음 승인 시험에서는 이 값을 명시하고 기존 private 검수 파일로 중간 결과를 확인한 후에만
   신발 요청을 허용한다. `crop=false`, 원격 1일 보관 계약은 기존 어댑터에 유지된다.
   P56의 특정 상품 reference 파일을 일반 사용자의 상품에 자동 적용하지 않는다.
   원격 보관 정책과 별도 서버 로그/백업 삭제 정책은 각각 관리자 확인이 필요하다.
7. 로그인: `SOCIAL_LOGIN_ENABLED`, `GOOGLE_CLIENT_ID`와 앱 `GOOGLE_WEB_CLIENT_ID`의 aud 일치,
   실제 패키지/서명에 대한 Google 콘솔 등록, `KAKAO_REST_API_KEY`,
   필요 시 `KAKAO_CLIENT_SECRET`, `PUBLIC_BASE_URL` 및 Kakao 등록 callback
   `https://fashion-backend-production-aba0.up.railway.app/api/auth/kakao/callback` 확인.
   변수 존재만으로 실제 로그인 PASS를 판정하지 않는다.
8. 배포 후 `/health` 200 및 outfit 설정 미인증 401, 테스트 Bearer 200/ready=false부터 확인.
   **그 이후 새 크레딧 승인과 해당 버전 설치 승인을 확보한 경우에만** S25+ 실생성/로그인 검증.

## 검증 기록

### 로그인 결함 수정

- 빈 서버 계정으로 전환 시 `AuthStore.complete`가 `completeRestore()`를 호출해 이전 계정의
  로컬 기록/대기열을 새 계정으로 전송할 수 있었다. 수정 전 실제 로컬 HTTPS 회귀가
  `canSync` 검사에서 실패했다. 빈 계정은 이제 성공으로 처리하지 않고 기존 기록을 유지하며
  복원 보류를 지속한다. 기록 이전을 자동 승인하지 않는다.
- Google `invalid_identity`, Kakao `login_expired` 401은 기존 Bearer 자체의 거절과 다르다.
  `authentication_required`일 때만 재인증 상태로 전환하며, 소셜 신원 오류는 기존 자격정보를 유지한다.
- QA/Preview에서는 운영 로그인 공급자 조회 및 Google 계정 선택을 시작하지 않고 격리 이유를 표시한다.
  primary의 Google/Kakao 연결은 보존한다.
- `LoginScreen`의 `LaunchedEffect(redirect)`가 콜백 소비 → null 발행에 의해 자기 인증 교환을
  취소하는 결함을 Compose 검사로 재현했다. 안정된 effect의 순차 collect로 수정했다.
- 새 브라우저 콜백은 저장된 화면 상태 유무와 무관하게 검증하고, 실제 수락된 Intent 데이터만 비운다.
  초기 화면/다른 경로에 복귀해도 로그인 경로로 singleTop 이동하며, 교환 소비자는 한 곳이다.
  서버의 일회용 코드/계정 바인딩을 유지한다. OS의 모든 프로세스 종료·네트워크 단절 복구나
  실제 OAuth 성공까지 검증했다고 해석하면 안 된다.
- 기존 로그인 UI 검사는 공개 운영 서버에 의존하지 않도록 로컬 공급자 응답을 사용한다.
  검사 기대값을 낮추거나 실제 로그인 실패를 숨기지 않는다.

### 실행 결과

- 최신 팀 main과 서버 통합 후 Backend 집중 검사: **71 PASS**, 외부 생성/OAuth/운영 DB 호출 없음.
  `test_main_outfit_deployment`, `test_social_bridge`, `test_auth_api`,
  `test_clothing_consent`, `test_outfit_vton_api`.
- 증거는 Git 제외 `fashion_app/build/genlook-restore-private/`에 보존한다.
- Genlook 선택·출처/소유자·중복 및 재시작 경계 집중 검사 **2 PASS**.
  합성 provider 검사이며 신규 실제 AI 생성 성공 증거가 아니다.
- Android RED 01: 계정 전환 회귀 1개, 예상한 동기화 보류 실패 1개.
- Android RED 02: 9개 중 4개 실패. 1개는 예상한 Compose 자기 취소 결함,
  3개는 테스트 간 DataAuth 초기화가 재사용되는 fixture 문제였다.
  기존 P23의 싱글턴 격리 방식으로 테스트 전후 합성 상태를 보존/복원하고 재검증했다.
  초기 `canSync`·자료 보존·인증 거절 assertions는 유지했다.
- GREEN 03은 37개 중 35 PASS / 2 FAIL이었다. 자기 취소/cold-start UI와 계정 전환 보존은 통과했다.
  남은 원인은 기존 P23 시험 인증서의 숫자 loopback SAN 누락과, JDK의 고정 길이 POST 401 오류 본문 유실이다.
  후자는 별도 localhost JDK 실행에서도 고정 길이 요청은 오류 본문 0 bytes, 버퍼 요청은 36 bytes로 재현됐다.
  POST 재전송 동작을 변경하지 않도록 기존 streaming 요청은 유지한다. 오류 본문이 없어 인증 거절을
  구별할 수 없을 때만 동일 서버·동일 Bearer의 읽기 전용 `/api/auth/status`를 한 번 확인하는 수정과
  focused 회귀를 통과했다. 생성 요청 재시도와는 무관하며 실제 계정에는 호출하지 않았다.
- GREEN 04: AuthStoreBearer 11, 계정 복구 보존 1, 기존 P23 기록 보존 1 — **13 PASS / 0 FAIL**.
  GREEN 03의 실패 2건을 해결했으며, 나머지 통과 결과는 보존했다.
- Genlook factory의 중간 검수 설정 누락은 1개 실패로 재현 후 최소 연결 수정.
  main ASGI 및 Genlook 연결 회귀 **27 PASS / 0 FAIL**. 앞선 71/2개와 중복되는 검사 수는 합산하지 않는다.
- 기존 P54 검수 승인/거절/변조/취소/시간 초과/중복·예산 경계: 합성 HTTP **11 PASS**.
- PostgreSQL 테스트 대상 URL의 host/dbname/query 우회 차단 **9 PASS**; 실제 DB 연결·마이그레이션은 미수행.
- CI 서명 범위·실행 정책·릴리스 가드: **10 PASS**.
  최초 실행의 2개 오류는 샌드박스가 임시 합성 Git 저장소 생성을 차단한 환경 오류였으며,
  동일 검사를 허용된 로컬 실행으로 확인했다. 최초 오류 로그도 보존한다.
- primary Android 클래스별 최신 결과는 중복을 제외해 **40 PASS**이다.
  GREEN 03의 변경 없는 통과 클래스 27개와 GREEN 04의 최종 클래스 13개를 합친 결과이며,
  단일 전체 CI 실행 결과가 아니다. Kotlin 컴파일 성공, 실제 Google/Kakao 로그인·S25+ 검증은 미수행.
- QA GREEN 05: QA 조건의 AuthStoreBearer 11개 + 로그인 화면 1개 **12 PASS / 0 FAIL / 0 SKIP**,
  `BUILD SUCCESSFUL` (8분 10초). QA/Preview에서 실제 운영 계정 선택·요청이 차단되는 경로를 검사했다.
  primary 로그인 성공을 뜻하지 않는다. 새 APK 제작·설치·사용자 데이터 조작은 하지 않았다.
- 기존 P54 실기기 성공과 이번 로컬 검사, 운영 배포 대기는 별개의 결과다.
- Release 서명 승인/운영 개인정보 처리/상품 재현 품질/최신 앱 OAuth와 생성 실기기 검증이
  아직 충족되지 않았으므로 **정식 출시 NO-GO**.
