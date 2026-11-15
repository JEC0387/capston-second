# PR #23 최종 검수 · 2026-11-09

## 기준과 범위

- 팀 main: `9e94e0e5bd1c70eec08e596ac5dd30622957153e`.
- 검수 시작 PR HEAD: `8d279ae9a224a1e7249034be897a07b6acf5734e`.
- PR: https://github.com/JEC0387/capston-second/pull/23
- 개인 P56 `9ac1b229f8f1169175526b7fd42fe1d60ad2343f`는 현재 통합 계보의 조상이다.
  기존 PR #19 통합, #20/#21 로그인 및 #22 ZIP 빌드 변경을 다시 덮어쓰지 않았다.
- 원래 24개 변경 파일을 서버 라우터/수명주기, Genlook factory, Android 인증/Navigation,
  회귀 검사, CI 서명 범위 및 문서로 나눠 검토했다. 팀 기능 삭제나 모델 교체는 없다.
- 사용자 기기·운영 DB·Genlook 크레딧·기존 APK는 이번 검사에서 변경하지 않았다.

## 계정 및 데이터 검수

- 새 계정 최초 신원 연결, 동일 계정 재로그인, 다른 빈 계정 전환을 별도 검사한다.
  앞 두 경우에는 기존 로컬 기록을 유지하고 성공하며, 뒤의 경우에는 복원 보류와 동기화 차단을 유지한다.
- Google/Kakao 신원 실패와 기존 Bearer 무효화를 구분하고, 401 본문 유실 시 동일 서버의 status GET만 확인한다.
- 콜백 자기 취소, cold-start 진입, QA/Preview 운영 로그인 차단은 기존 PR 수정과 검사를 유지한다.
- 전체 검사에서 발견한 P57 fixture 두 곳을 수정했다. 공급자 OFF 검사는 운영 서버 상태 대신
  로컬 MockWebServer 응답을 사용한다. 사진 동의 검사는 합성 DataAuth 상태를 테스트 전후 격리한다.
  실제 OAuth 또는 사진 업로드 성공을 뜻하지 않는다.
- Windows에서 사진 URI 경로 `/C:\...`와 File 절대 경로 `C:\...`의 직접 비교로 합성 옷장 사진을
  미참조로 오인한 실패를 재현했다. 정리 전에 참조 URI를 File 절대 경로로 정규화한다.
  기존 사진 보존 assertion 및 수정 전 실패 XML을 유지한다. Android 저장 형식은 바꾸지 않는다.

## 로컬 검증

- Backend 전체: Windows 666 PASS / 35 FAIL / 37 SKIP, subtests 10 PASS.
  실패 35개는 Unix 파일 잠금/fsync 의존 `test_tryon_proxy.py`의 Windows 실행 제약이다.
- 동일 최신 파일을 기존 승인된 WSL 가상환경에서 실행: 39 PASS / 0 FAIL / 0 SKIP,
  6.30초, 최대 RSS 111060 KiB. 앞 실행과 중복되는 4개를 별도 성과로 합산하지 않는다.
- Backend 미실행 범위: PostgreSQL 서비스 미설정 3개, 실제 CUDA/입력 승인 1개,
  torch 26개, onnx 2개, gradio_client 1개, symlink 권한 1개, collection skip 3개.
  모델 연구 검사를 통과했다고 주장하지 않는다. 운영 DB에는 접속하지 않았다.
- 릴리스/서명/CI/네이티브 검사 도구: 31 PASS + subtests 4 PASS.
- Android 첫 전체: 720개 중 715 PASS / 3 FAIL / 2 SKIP.
  수정 후 집중 13개에서 인증 관련 8개 및 옷장 4개 PASS, 경로 문제 1개 FAIL을 보존했다.
- 최종 Android primary 전체(run04): **718 PASS / 0 FAIL / 0 ERROR / 2 SKIP** (128 classes, 720 tests).
  별도 live/loopback 서버 환경을 요구하는 P30/P32 두 검사는 미설정 상태로 건너뛰며 성공으로 계산하지 않는다.
  신규/동일 계정 신원 연결과 기존 계정 전환, 세 실패 회귀가 모두 통과했다. Lint: **0 Error/Fatal / 168 Warning / 3 Hint**, BUILD SUCCESSFUL(16분 26초).
  경고를 무시 설정으로 바꾸지 않았다. QA 인증·동의·옷장 집중 검사(run05): **26 PASS / 0 FAIL / 0 SKIP**.
  QA 로그인 화면·콜백 Compose 검사(run06): **3 PASS / 0 FAIL / 0 SKIP**.
  두 QA 실행은 서로 다른 클래스이며 앱 설치·실제 OAuth·Genlook 호출은 하지 않았다.
- 최종 CI는 이 검증된 변경을 커밋·Push한 후 PR 이벤트로 1회 실행한다. 실제 CI 결과와
  정확한 대상 SHA는 PR Checks/검증 댓글 및 로컬 인수 receipt에 기록하며, CI 전 병합하지 않는다.

## 운영·병합·Release 경계

- 운영 읽기 GET 재확인: `/` 200, `/health` 404, outfit config 404,
  auth providers 200(google/kakao=true). 실제 OAuth 성공 증거는 아니다.
- 상윤의 기존 Railway 서비스 확인·배포 승인은 아직 없다. `backend/RAILWAY_HANDOFF.md`에
  기존 프로젝트 CLI 배포, DB 계보 사전 검증, 비용 저널 지속 보존, 먼저 생성 OFF 연결 검증을 정리했다.
- `fashion_app/LOGIN_SETUP.md`의 '테이블 존재만으로 마이그레이션 불필요' 안내를 수정했다.
- 실제 main은 보호 플래그 false / rulesets 빈 목록 / reviews 없음으로 조회됐다.
  보호가 없더라도 사용자 지시의 팀 리뷰·승인 단계를 생략하지 않는다.
- PR 병합 전 ZIP은 REVIEW 전용이다. 최종 `FashionAI_Railway_Backend_<mainSHA>.zip`은 실제 병합 SHA에서만 만든다.
- 공식 Release 서명키/운영 활성화/실제 OAuth/최신 앱 Genlook E2E/상품 세부 품질이 미충족이다.
  기존 QA/Preview 키를 정식 키로 전용하지 않으며 정식 APK·새 설치는 수행하지 않았다.
- P54 S25+ 생성·비교·저장·재열기 PASS는 보존한다. 상품 단추·주머니·허리·신발 세부 FAIL도 유지한다.
- 정식 출시 **NO-GO**. 상윤: 운영 배포·정책·DB 확인, 의찬: 실제 OAuth/계정 격리 검증,
  Release 키 보유자: 공식 서명/패키지 승인, 팀 리뷰어: PR 검토·병합 승인,
  사용자: 다음 생성 크레딧·신규 APK 설치 승인.

## 증거 보존

실제 로그/XML은 Git 제외 `fashion_app/build/pr23-final-private/20261109/`에 보존한다.
디스크 정리는 별도 `integration-logs/cache-cleanup-deleted-files-20261109.csv`에 파일별 절대 경로와
크기를 기록했다. 검증된 사용 종료 임시 파일만 총 3,592,042,224 bytes 정리했고,
정리 직후 여유 4.39 GiB로 최소 목표를 충족했다. 프로젝트/사진/모델/APK/증거/DB/키/SDK/현재 Gradle은 보존했다.

## 최종 CI #45 실패와 최소 수정

- 실행: https://github.com/JEC0387/capston-second/actions/runs/42558042172
- PR HEAD `4aab81516bb1bb56faa22376c52f7baf08a5f867`, 검사 merge ref
  `f33db38a99b375e8d37c6e2038b1cad37c5c500f`.
- Windows Python 3.11/3.12/3.13/3.14 및 Ubuntu 3.12 서버 시작 5개 job PASS.
- Backend: 702 PASS / 33 SKIP / 3 ERROR / subtests 10 PASS.
  PostgreSQL 3개가 접속 전에 `explicit_local_test_postgres_url_required`로 거절됐다.
  Android build job은 backend 실패 의존성 때문에 SKIPPED이며 CI Android PASS가 아니다.
- 원인: workflow의 `TEST_POSTGRES_URL`에는 SQLAlchemy driver qualifier가 있었지만
  보안 target parser는 libpq `postgresql://` 입력만 허용한다. 파서를 완화하지 않고
  workflow의 합성 localhost URL 한 줄만 `postgresql://`로 수정했다.
- 실제 workflow 값을 fixture에 전달하는 회귀를 추가해 같은 오류로 RED를 확인했다.
  수정 후 URL 경계/마이그레이션/CI·서명 회귀 **82 PASS**. 실제 DB 재실행 결과는 아니다.
- 사용자가 승인한 전체 CI 1회는 이미 실행했으므로 추가 CI를 자동 시작하지 않는다.
  이 설정 수정은 `[skip ci]`로 보존하며, 추가 CI 승인 후 새 최종 SHA를 검증해야 한다.
  기존 runtime 코드는 변경되지 않아 primary 718/QA29/Lint 결과를 재사용한다.
- CI 미통과 상태를 유지하고 Draft 해제/main 병합/운영 배포를 보류한다.

## 추가 승인 CI #46 — 실패 상태 보존

- 사용자의 추가 1회 승인으로 `b120c13df833554fd81981cdec6ecada5617ad68`에서
  https://github.com/JEC0387/capston-second/actions/runs/42559250033 을 실행했다.
  `4111da6`의 PostgreSQL URL 수정은 포함되며 이후 앱 실행 코드 변경은 없다.
- 공개 저장소의 표준 runner에서 manual `full / validation_only=true`를 사용했다.
  전체 검사 명령은 유지하고 Artifact 업로드, Gradle 캐시 쓰기, 배포 서명 secret 로드를
  하지 않았다. 실제 Artifact 0개. 계정 전체 사용량/청구 잔액은 조회하지 못했다.
- Backend **706 PASS / 0 FAIL / 0 ERROR / 33 SKIP**, subtests 10 PASS.
  PostgreSQL 16의 동시 요청 제한·일회용 복구 티켓·사진/기록 복구 및 계정 격리 3개도 통과했다.
  Windows 3.11/3.12/3.13/3.14와 Ubuntu 3.12 서버 시작 5개 job PASS.
- Android P23 집중 검사와 기본 APK 컴파일/assemble PASS. 전체 단위 검사
  **717 PASS / 1 FAIL / 2 SKIP**. `P57PhotoConsentTest`의
  `productionRequiresBearerAndPurposeHeaderWhilePreviewSendsNothing`에서
  총 요청 수 `expected 1 / actual 2`로 실패했다. 이후 계측 컴파일/Lint/Preview/정렬은
  SKIPPED이며 이전 로컬 결과를 이번 CI의 성공으로 대체하지 않는다.
- CI 로그에는 추가 요청의 경로가 없어, 실제 중복 업로드인지 테스트 간 백그라운드
  요청 간섭인지 아직 확정할 수 없다. 동일 동의의 재사용 거절 assertion 자체는 통과한 뒤
  총 요청 수 assertion에서 실패했다. 기대값을 유지하고 추가 요청의 method/path만
  실패 메시지에 포함하도록 진단을 보강했다. 인증 헤더·사진 본문은 기록하지 않는다.
- 자동 CI 재실행은 하지 않았다. PR Draft 유지, 팀 리뷰/병합 준비 완료로 판정하지 않는다.
  main 병합·운영 배포·기기 설치·Genlook 호출은 수행하지 않았다.
- 로컬 사진 동의/인증 21개는 PASS하여 단독 재현되지 않았다. 이후 저장 fixture 2개가
  실제 `FashionApp.onCreate`를 실행함을 새 assertion으로 재현했다(7개 모두
  expected `android.app.Application`, actual `FashionApp` 실패). 이 경로는 DbSync와
  ServerConnection의 주기적 네트워크 작업을 시작하며 저장 단위 검사의 범위를 벗어난다.
- `ManualBodyMeasurementsTest`, `P34ManualBodyPersistenceTest`에 테스트 전용 Application을
  명시했다. 사진 동의 검사에는 ServerConnection 미시작 assertion을 추가하고 기존
  요청 수 1회/인증/동의/원본 바이트 검증은 유지했다. 실제 CI의 두 번째 요청 경로는
  로그에 없어 이 fixture 결함과의 인과관계 전체를 CI에서 확인한 것으로 주장하지 않는다.
- 로컬 진단 run08은 SDK 환경변수 누락으로 테스트 시작 전 실패했다. 잘못 복사된 이전
  XML에는 `NOT_THIS_RUN.txt`를 남겼고 집계하지 않는다. run09는 640MiB Gradle heap의
  GC thrashing으로 중단됐으며 테스트 결과가 아니다. run10은 명령 전용 1024MiB heap,
  단일 worker로 실행해 21 PASS. 전역 JVM/시스템 설정은 바꾸지 않았다.
- fixture 수정 후 로컬 전체 run12: **718 PASS / 0 FAIL / 0 ERROR / 2 SKIP**,
  128 classes / 720 tests, BUILD SUCCESSFUL(9분 57초). 저장 fixture 7개, 사진 동의 4개 및
  재접속 worker 미시작 assertion을 포함한다. 기존 두 live/loopback skip만 유지됐다.
  이 로컬 결과는 Ubuntu CI 재검증 성공을 뜻하지 않는다. 이번 추가 CI 승인 1회는
  소진됐으므로 자동 재실행 없이 `[skip ci]`로 테스트 환경 수정과 기록만 보존한다.
  앱 실행 코드는 그대로이며 APK 재제작/설치·Lint 반복은 수행하지 않았다.
