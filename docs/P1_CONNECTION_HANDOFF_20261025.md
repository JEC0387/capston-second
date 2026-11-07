# P1 서버·DB 자동 연결 인수인계 — 2026-10-25

## 작업 위치와 범위

- 저장소: `jimnnieo-svg/capstone-integration-backup`
- 후속 브랜치: `codex/p0-hardening-p1-connect-20261025`
- 시작 커밋: `273319be70b7838bff5c82c21f89234cdde2f24f`
- P0 중간 저장: `7f477d4` / 스냅샷·서버 바인딩 보완: `2b01552`
- 기존 백업·P0 브랜치와 시작 환경의 `work` 브랜치를 보존했다.

## 구현한 기본 사용자 경험

1. 기본 서버 주소는 기존 BuildConfig의 Railway HTTPS 주소를 그대로 사용한다. 저장된
   사용자 지정 주소도 유지한다. 새 설치에서 주소를 수동으로 입력할 필요가 없다.
2. `FashionApp` 시작 시 비동기로 서버 식별 → DB/스키마 준비 → 개인 인증 상태 확인 →
   기존 동기화 대기열 재시도를 진행한다. 화면과 로컬 기능은 서버 응답을 기다리지 않는다.
3. 네트워크 복귀 알림과 주기 재시도를 지원한다. 실패가 이어지면 5초부터 최대 5분까지
   간격을 늘리고, 연결 성공 후에는 1분마다 확인한다. 서버 변경/상태 배지 터치로 재시도한다.
4. 서버 백업을 끈 경우 신규 등록·자동 업로드를 하지 않는다. 복원 중, 기존 데이터 이전
   판단 대기, 보안 저장소 오류, 서버 주소 불일치, 인증 만료 때에도 자동 전송을 보류한다.
5. 서버 상태 배지는 연결 실패/DB 점검/인증/복원/이전/대기 건수를 구분한다. 단순히 DB가
   응답한다는 이유로 모든 기록이 저장됐다고 표시하지 않는다.

## DB 저장·조회·복원 경로

- 기존 FastAPI/SQLAlchemy 개인 저장 API와 Supabase PostgreSQL 연결 경로를 유지했다.
  앱에는 DB 비밀번호나 Supabase 서비스 키를 넣지 않는다.
- `/api/health/database`에 `authProtocol=2`, `authReady`, `syncReady`를 추가했다.
  기존 `ready` 의미는 보존한다. 필수 인증/동기화 스키마가 빠지면 `syncReady=false`여서
  앱이 대기열을 유지한다. 앱이 마이그레이션이나 DB 생성을 실행하지 않는다.
- `/api/backup/snapshot`은 계정의 쓰기 요청과 직렬화한 단일 스냅샷을 반환한다.
  사진은 같은 계정에서 받아 스냅샷의 SHA-256과 일치하는지 확인한다.
- 로컬 PostgreSQL 16.2에서 옷/가격/브랜드/사진/착용 기록/스타일 저장과 조회, 토큰 복구,
  스냅샷 및 사용자 간 접근 차단을 실제 DB로 검증했다. 이는 운영 Supabase 접속 확인을
  대신하지 않는다. 실제 Railway/Supabase 연결·쓰기·배포는 수행하지 않았다.

## 장애와 원본 보존

- 네트워크/서버/스키마 오류에는 인증 정보, 로컬 기록, 미전송 대기열을 자동 삭제하지 않는다.
- 개인 API는 HTTPS와 발급 서버에 묶인 Bearer 토큰이 없으면 요청 본문도 전송하지 않는다.
  인증 요청과 개인 요청은 리다이렉트를 따르지 않는다. HTTP 개발 서버에서 개인 API를
  검증하려면 별도의 로컬 HTTPS 구성이 필요하다.
- 복구 응답 손실 시 요청한 복구 코드와 서버를 암호화해 보관한다. 이어하기는 그 계정과
  서버의 인증부터 재시도하며, 기존 계정의 스냅샷으로 잘못 복원하지 않는다.
- 늦게 도착한 이전 토큰의 401 응답이 새 토큰을 무효화하지 않도록 실제 요청에 사용한
  토큰을 대조한다. 토큰/서버 바인딩은 하나의 불변 값으로 교체한다.
- 복원 저널이 있으면 업로드와 복원 취소를 차단한다. 재시작 시 모든 저장소에 다시 적용한
  다음 인증 상태를 저장하고 저널을 마무리한다. 두 작업 사이에 종료돼도 다음 시작에서
  다시 마무리할 수 있다. 원본 preferences/대기열과 기존 사진은 보존한다.
- 상세한 승인 절차와 원본 보존 위치는 `backend/AUTH_MIGRATION.md`를 따른다.

## 검증 방법

```text
# backend 디렉터리, 로컬 전용 테스트 DB. 테스트는 비로컬 주소를 거부한다.
DATABASE_URL='' DB_AUTO_CREATE=false \
TEST_POSTGRES_URL=postgresql+psycopg2://<test-user>@127.0.0.1:<port>/fashionai_test \
python -m pytest -q

# fashion_app 디렉터리, Java 21 및 Android SDK 필요
bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin \
  --init-script gradle/test-runtime.init.gradle.kts --no-daemon --console=plain --max-workers=2
```

- PostgreSQL URL이 없으면 PostgreSQL 전용 테스트만 skip한다. 전체 검증에는 로컬 테스트 DB가
  필요하다. GitHub Actions는 격리 PostgreSQL 서비스와 함께 실행한다.
- HTTPS 모의 서버 테스트는 복구 응답 손실, 요청 대상 서버 변경, 재시도, 늦은 401,
  HTTP 개인 데이터 전송 차단을 검증한다. 운영 API와 유료 AI 제공자는 호출하지 않는다.
- 테스트 수·마지막 CI 결과는 `P0_AUTH_HANDOFF_20261025.md`의 최신 절을 참조한다.

## 승인 전에 남은 확인

- 실제 Android 기기의 Keystore, 프로세스 강제 종료/재시작, 저장 공간 부족, 화면 이동,
  카메라/사진 선택과 네트워크 전환은 아직 실기기로 검증하지 않았다.
- Railway 프록시 피어 식별과 등록 시도 제한을 스테이징에서 확인해야 한다. 사용자 입력
  X-Forwarded-For를 신뢰하지 않는다. 프록시 뒤에서 모든 사용자가 하나의 예산을 공유하는
  구성이라면 출시 전에 신뢰 프록시/에지 제한을 검증한다.
- 승인된 환경에 005/006 SQL 적용, 등록 설정, 서버·앱 출시 순서, 기존 사용자 이전 안내와
  독립적인 승인 기록/티켓 전달 담당자를 확정해야 한다. 자료가 부족하면 이전을 보류한다.
- 운영 DB 변경, Railway 배포, 유료 API 호출, Merge, Force Push는 실행하지 않았다.


## 최종 검증 결과

- 코드 `9731363037a7a30b6ebd0c48cfdc7eb70854de51`의
  [GitHub Actions](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/40199469609)는
  **7개 job 모두 성공**했고 실패 step은 없다.
- Android 256개 / 31개 클래스, 실패·오류·건너뜀 0. APK 생성, instrumented test 컴파일, Lint 성공.
- 백엔드 101 passed / 8 subtests passed / 7 warnings. PostgreSQL 전용 3개 테스트도 실행했다.
- Linux와 Windows의 서버 시작 검증을 통과했다. 실제 운영 연결/쓰기·배포나 실기기 검증을
  수행한 결과는 없으며, 운영 적용은 위 승인 전 확인 항목을 해결한 뒤 별도로 진행한다.
