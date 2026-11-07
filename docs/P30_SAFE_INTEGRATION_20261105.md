# P30 — 안전 통합 및 실제 실행 준비 (2026-11-05)

## 최신 완료 상태 — 로컬 후보 확보, 실제 GPU·실기기·SQL 통합은 미완료

- 실행 SHA: `27945bc1247702c30ea3aa5466700447c962616e` (아래 결과는 이 소스 기준).
- Preview `1.1.86.3-preview / 18603` 로컬 서명 후보와 검증 파일 8개를 새 납품 폴더에 복사하고 원본·사본 해시를 대조했다.
- 납품 폴더: `C:\Users\USER\Documents\ChatGPT\capston\deliveries\P30-Preview-18603-20261105-072418-KST`.
- APK: `fashionai-1.1.86.3-preview-vc18603-27945bc1.apk` (148,748,525 bytes).
- APK SHA256: `efaaed2a811911ec79ad5e5a2154502ded5879c0e439c5cfe9cbec7b3977c20f`.
- 패키지 `com.jeiu.fashionproject.preview`, 기존 인증서 `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`, 버전 증가·embedded source/branch/clean identity·CRC·현재 ZIP 16KB 정렬 통과.
- 네이티브 파일은 이전 P22 LLVM 감사 APK와 바이트 일치 후 해당 ELF 감사를 재사용했다. **실제 16KB 런타임 검증 아님**.
- `LOCAL_VERIFICATION.json`의 최종 `localCandidateChecksPassed=true`, `versionFloorVerified=true`. `BUILD_INFO`의 `distributionVerified=false` 유지: GitHub 배포 Artifact가 아닌 로컬 후보다.
- P24 18601 및 P26 18602의 원본/납품 APK 해시를 다시 확인해 모두 일치했다. P30에서 사용자 데이터의 새 해시 비교나 휴대폰 설치는 하지 않았다.
- 서명 임시 디렉터리/키 파일 제거 확인. 개인 사진·실제 토큰·키·전체 테스트 로그는 Git과 납품 자료에 넣지 않았다.

## 작업 기준 및 보존

- 전용 브랜치: `codex/fashionai-p30-safe-integration-20261105`.
- 시작 SHA: `cdbc6236637e1916f755e0f760c6c877137ac03f`. 기존 P17 작업 브랜치와 팀 main은 변경하지 않는다.
- 실제 담당: 총괄 1명 + 병렬 하위 에이전트 3명. 요청한 10개 전문 역할을 세 트랙으로 묶어 수행했다.
- GitHub Actions 실행·PR 생성·Artifact 업로드 없음. P29의 Push 트리거 제거 및 PR/릴리스 게이트 유지.
- 기존 Preview 18602 및 P24/P26 납품본 유지. P30에서 기기 연결 목록은 비어 있었으며 설치·화면·사진 검증을 수행하지 않았다.

## A. VTON

- CLI/실제 worker가 모델 로드 직전에 manifest를 검증하고 Hugging Face/Transformers를 오프라인으로 고정한다. 누락 캐시는 요청 중 다운로드하지 않는다.
- CLI의 `--overwrite` 미지정 출력은 같은 디렉터리의 완성된 임시 PNG를 원자적 hard-link로 게시한다. 추론 중 생긴 기존 파일을 덮어쓰지 않는다. 지원하지 않는 파일시스템은 안전하게 실패한다.
- 준비 상태 검사에 Human Parser 의존성 포함. 자체 AI 화면에서 2D 자동맞춤과 마법봉 설명을 구분했다.
- 실제 production worker/CUDA용 opt-in 시험을 추가했다. 현재 승인된 GPU·모델·입력이 없어 **skip이며 생성 성공이 아니다**.
- 별도 Android Repository → 실제 loopback FastAPI 계약 시험 추가: 인증 401, 정상 인증, 미준비 모델 차단. 재검사 **1 passed / 0 skipped, 18.468초**. Robolectric의 Android 코드와 실제 Python 서버 간 전송이며, 이미지/POST/모델 추론/에뮬레이터/실기기 E2E 검증이 아니다.
- 가중치 manifest 범위는 FASHN/DWPose 3개 파일. Parser/SegFormer 캐시 무결성·개별 라이선스 확인은 남아 있다. 상업적 사용 가능 판정 없음.

## B. 합성 체형 입력

- 원래 코드는 큰 상부 배경 영역이 한 픽셀 폭 연결부로 인물에 연결되면 동일 연결요소로 받아들였다.
- RED02: `connectedExtraUpperForegroundCannotChangeHeightCalibration` 1개 실행/1개 실패. 기대 `EXTRA_FOREGROUND`, 실제 `null`로 재현했다.
- RED01은 테스트 JVM의 `Redirect.DISCARD` 컴파일 호환성 문제이며 앱 결함 증거로 계산하지 않는다. 표준 null 출력으로 테스트 실행 환경만 수정했다.
- 기존 5% 추가 영역 허용 기준과 랜드마크 ±2픽셀 범위를 사용해 어깨 위에서 머리와 분리된 큰 영역을 거절한다. 작은 잡음과 넓은 연결된 목 윤곽은 유지하는 독립 기대값 회귀를 추가했다.
- 실제 사람 수 검출·유효 전신 실사진·실측 MAE 검증이 아니다. P18 합성 방향/좌표 결과와 P22 사진 Fit 변환을 대체하거나 약화하지 않았다.

## C. 팀 통합 및 데이터 보존

- 팀 main 확인 SHA: `411d008ebf542ec04c4966936303a686b4f784e5`.
- 기존 P19 상품/체형 제안 및 P23 게스트 안전 기능 유지. 이번에 새 팀 화면이나 소셜 인증을 이식하지 않았다.
- 팀의 동기화 실패 후 로컬 삭제, `X-Device-Id` 인증, 계약이 다른 ProductRecommender 의존성 일괄 이식은 제외했다.
- `005_auth_credentials.sql`과 팀 `005_user_logins.sql` 동시 적용 위험: 중복 번호를 DB import/접속 전에 차단한다. dry-run은 DB 설정도 로드하지 않는다.
- 선택적 독립 적용 이력 manifest의 파일명/체크섬/번호 충돌을 검사한다. 이력이 없으면 `pendingFiles=null`, `schemaValidated=false`; 비어 있는 DB라고 가정하지 않는다.
- SQL 파일·번호·운영 스키마는 변경하지 않았다. 실제 적용 이력과 격리 PostgreSQL 복사본이 없어 **SQL 통합 완료 아님**. 로컬 Docker 정보 확인도 제한 시간 내 응답하지 않았다.
- 자세한 절차: `backend/MIGRATION_PREFLIGHT.md`. 기존 Bearer/계정 소유권/복구 저널/대기열/Preview 격리 유지.

## 검증 기록

- Backend 최종 집중 묶음: **68 passed, 1 skipped**, exit 0. VTON/manifest/server/실행 가드/마이그레이션 preflight 및 기존 계정 격리·복구 3개 포함. 실제 CUDA 시험 1개 skip.
- 계정 테스트는 격리 SQLite, SQL preflight는 임시 파일 기반이다. PostgreSQL 실행이나 운영 데이터 보존 검증으로 확대하지 않는다.
- Android Preview 최종 범위: **142개 통과**. 동일 실행 SHA에서 첫 실행의 통과 141개를 보존하고, 환경을 복구한 실제 FastAPI 검사 1개만 재실행해 합쳤다. 중복 실행 수를 더하지 않았다.
- 첫 실행은 143개 중 141개 통과/2개 실패였다. `DataAuthRecoverySafetyTest`는 primary용 복구 성공 검사를 Preview에 잘못 포함한 실행 범위 오류: `PREVIEW_BUILD=true`에서 `DataAuth.call()`의 의도된 격리로 차단된다. 원래 primary 테스트·CI 선택·production 격리를 수정/삭제하지 않았으며 **primary 성공 경로는 이번에 재실행하지 않았다**. Preview의 실제 보존 계약은 통과한 기존 `P23DataPreservationTest` 등으로 검증했다.
- 다른 실패는 기존 Python 환경의 `uvicorn` 미설치였다. 이미 `backend/requirements.txt`에 고정된 `uvicorn==0.34.0`과 필요한 click 8.5.0을 로컬 캐시에서 설치한 뒤 해당 1개만 재실행했다. 모델/가중치 다운로드 없음.
- 재실행에서 컴파일 캐시 재사용, 실제 FastAPI 검사·Lint·assemble 성공(exit 0, 17분 1초). Lint **Error/Fatal 0, Warning 164, Hint 2**. 처음 시도는 서명 전에 차단됐으며 실패 로그/XML은 보존했다.
- Backend/Android 전체 CI를 실행한 결과가 아니다. 기존 P16 CI를 새 코드 성공 근거로 사용하지 않았다. Actions 실행·Artifact 업로드 없음.
- 실패 로그/최종 XML은 Git 제외 `fashion_app/build/p30-private/`에 별도 보존한다. 사진·토큰·기기 식별값·전체 로그는 커밋하지 않는다.

## 남은 외부 조건

1. 승인된 CUDA 환경, 고정 모델 revision, 기존 가중치/Parser 캐시·라이선스, 승인된 비개인 입력이 있어야 실제 자체 추론 시험 가능. `backend/LOCAL_VTON_SERVER.md`의 명시적 opt-in 절차를 따른다.
2. 실제 전신 분석·측정 정확도는 지정 입력과 실측 기준, 기기 접근이 필요하다. 개인 사진 외부 전송은 승인되지 않았다.
3. 팀 인증/SQL 통합은 독립 적용 이력·스키마와 보호된 PostgreSQL 테스트 복사본, OAuth 설정/별도 승인 필요.
4. 새 APK가 검증돼도 설치 승인은 별도다. P30은 실제 AI 생성·실기기 전체 검증·팀 전체 통합 완료가 아니다.

## 실제 변경 파일 (17개)

- `backend/local_vton.py`: 오프라인 로딩, 직전 manifest 검사, 출력 파일 덮어쓰기 경쟁 방지.
- `backend/local_vton_worker.py`: 실제 worker의 오프라인/체크섬 검증.
- `backend/local_vton_server.py`: Human Parser 의존성 준비 검사.
- `backend/tests/test_local_vton.py`: 기존 CLI 시험의 manifest 계약 반영.
- `backend/tests/test_local_vton_execution_guards.py`: 출력 경쟁·오프라인·manifest 회귀.
- `backend/tests/test_local_vton_real_model.py`: 실제 production worker/CUDA opt-in 시험(현재 skip).
- `backend/LOCAL_VTON_SERVER.md`, `backend/LOCAL_VTON_SETUP.md`: 실제 실행·승인·의존성 및 라이선스 한계.
- `backend/apply_migrations.py`, `backend/migration_plan.py`: DB import 전 번호 충돌/이력 manifest 검사.
- `backend/tests/test_migration_plan.py`, `backend/MIGRATION_PREFLIGHT.md`: 오프라인 회귀 및 SQL 미완료 범위.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/LocalVtonScreen.kt`: 2D 자동맞춤과 AI 설명 구분.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/SilhouetteMeasurer.kt`: 연결된 추가 상부 전경 거절.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/SilhouetteMeasurerTest.kt`: 독립 합성 반례/정상 대조.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P30LocalVtonLiveContractTest.kt`: 실제 loopback FastAPI 인증/준비 상태 계약.
- `docs/P30_SAFE_INTEGRATION_20261105.md`: 소스 체크포인트·최종 결과·남은 조건.

## 후속 인수

- 납품 폴더의 `P30_DELIVERY_VERIFICATION.json`에 클래스별 통과 수, 최초 실패 사유, Lint 수, 원본 보존 해시, 사본별 SHA가 있다. 개인 식별값이나 전체 XML/로그는 제외했다.
- 같은 후보를 재빌드하지 않는다. 새 코드를 수정하면 새 소스 SHA/증가 버전과 관련 검증을 다시 확보한다. 향후 배포 versionCode는 18603보다 높아야 한다.
- 설치는 별도 승인 후 실제 설치 패키지/서명/버전과 새 데이터 보존 기준을 확인해 삭제 없이 Preview만 업데이트한다. QA 패키지로 대체하거나 데이터 초기화하지 않는다.
- 실제 CUDA 시험은 승인된 환경·고정 가중치·Parser 캐시/개별 라이선스·비개인 입력이 준비된 뒤에만 명시적 opt-in으로 시작한다. 새 PNG·GPU·VRAM·품질 결과는 현재 모두 없음/미확인이다.
- 팀 main 통합은 별도 승인 및 실제 이력/격리 PostgreSQL 검증 후 진행한다. 기존 DataAuth/Bearer/DbSync/복구 저널을 팀 구현으로 교체하지 않는다.
