# P0 인증 작업 인수인계 — 2026-10-25

## 저장 위치와 범위

- 작업 브랜치: `codex/p0-user-auth-20261025`
- 코드 체크포인트 커밋: `b664e379b81b6eac20b2aac15f98f39882c59f37` (`WIP: checkpoint P0 authenticated private data access`)
- 인수인계 문서 커밋: 이 문서를 포함하는 브랜치 HEAD. 정확한 SHA는 `git rev-parse HEAD`로 확인한다.
- 기준 백업 브랜치/커밋: `backup/security-phase1-20261025` / `745e10d9d7e21d71c63a5c7c195ddf0af11cf53e`
- 이 문서는 **중간 저장**이다. P1 이후 기능 개발, 운영 DB 변경, Railway 배포, 기존 데이터 이전은 수행하지 않았다.

## 변경 파일 전체 목록

코드 체크포인트의 32개 파일:

```text
README.md
backend/.env.example
backend/AUTH_MIGRATION.md
backend/auth_admin.py
backend/auth_api.py
backend/database.py
backend/db_api.py
backend/db_models.py
backend/evaluation/eval_recommend.py
backend/main.py
backend/migrations/005_auth_credentials.sql
backend/tests/__init__.py
backend/tests/support_auth.py
backend/tests/test_auth_api.py
backend/tests/test_backup_api.py
backend/tests/test_db_api.py
backend/tests/test_demo_size_charts.py
backend/tests/test_old_schema.py
backend/tests/test_outfit_book_api.py
backend/tests/test_user_data_api.py
docs/README.md
fashion_app/app/src/main/java/com/jeiu/fashionproject/FashionApp.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/BackendApi.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/BackupRestore.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/DataAuth.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/DbSync.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/ServerSettings.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/BackupRestoreDialog.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/ServerStatusBadge.kt
fashion_app/app/src/main/res/xml/backup_rules.xml
fashion_app/app/src/main/res/xml/data_extraction_rules.xml
fashion_app/app/src/test/java/com/jeiu/fashionproject/BackupAndRecommendationLogTest.kt
```

이 문서 `docs/P0_AUTH_HANDOFF_20261025.md`는 별도의 인수인계 커밋에 추가한다. 기존 백업 브랜치와 다른 작업 디렉터리는 수정하거나 정리하지 않았다.

## 구현된 부분

- 개인 DB API에서 `X-Device-Id` 단독 인증을 제거하고 서버 발급 Bearer 토큰을 요구한다. 옷장·사진·체형 등 개인 데이터 API의 기존 사용자 분리 경로에 적용했다.
- 새 계정은 별도 접속 토큰과 복구 코드를 받는다. 서버에는 두 값의 SHA-256 해시만 저장하며, 복구 시 접속 토큰을 교체한다. 등록은 `AUTH_REGISTRATION_ENABLED=false`가 기본값이다.
- 기존 기기 ID 데이터는 보존한다. 자동 귀속은 금지하고, 운영자가 **기기 ID 외의 독립적 소유권 확인**을 한 뒤 발급한 만료·일회용 티켓으로만 이전할 수 있게 했다. 앱에서 이전 또는 새 계정 시작을 명시적으로 선택한다.
- Android 개인 API 요청에 HTTPS Bearer 인증을 연결했다. 토큰을 발급 서버 주소에 묶고, 서버 주소 변경 시 전송을 차단한다. 토큰·복구 코드는 앱 비공개 저장소에 두고 Android 자동 백업에서 제외했다.
- 이전/복구 중 동기화를 멈추고, 복원에 필요한 원격 응답과 사진을 확보한 뒤 로컬 쓰기를 시작하도록 수정했다. 기존 로컬 데이터와 대기열을 자동 삭제하지 않는다.
- 기존 백업/추천 관련 테스트를 새 인증 흐름에 맞춰 갱신했다. 기존 화면과 기능을 대규모로 재설계하지 않았다.

## 테스트 결과와 미검증 항목

- Android: `:app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain` 성공. JVM 단위 테스트 **240개 / 26개 클래스, 실패 0, 오류 0, 건너뜀 0**. Debug APK 생성 확인. Kotlin의 `LocalClipboardManager` 사용 중단 경고와 일부 네이티브 라이브러리 strip 경고는 빌드를 실패시키지 않았다.
- 백엔드: 인증 관련 신규 테스트 **4개 통과**. Windows에서 `tests/test_tryon_proxy.py`를 제외한 회귀 테스트 **50 passed, 8 subtests passed, 7 warnings**. 마지막 백엔드 코드 수정 이후 전체 회귀 테스트를 다시 실행하지 않았으며, 그 이후 변경은 사용하지 않는 import 정리와 평가 스크립트 조정이었다.
- Linux 전용 `test_tryon_proxy.py`는 실행하지 못했다. 이 환경에서 WSL 접근이 거부됐고 Docker 데몬이 실행되지 않았다.
- GitHub Actions는 이 브랜치에서 실행되지 않았다. `android-debug-apk.yml`의 push 대상은 `main`, `merge/**`, `feature/**`여서 `codex/**` push만으로 자동 실행되지 않는다.
- 실기기 설치·카메라·사진 선택·화면 이동·네트워크 장애·앱 재시작, Railway/Supabase 통합, 운영 데이터 이전은 미검증이다.
- 변경 파일 32개의 경로 및 고신뢰 비밀키 패턴을 검사했고 `.env`, API 키, 토큰, 사용자 사진, APK, 키스토어, 빌드 결과물이 Git 포함 대상에 없음을 확인했다. `.env.example`에는 빈 예시 값과 등록 비활성 설정만 있다. 이는 모든 형태의 비밀정보 부재를 증명하는 검사는 아니다.

## 미완성 및 주의사항

1. **배포 불가 상태:** 운영 DB에 `005_auth_credentials.sql`을 적용하지 않았고 Railway를 변경하지 않았다. 등록도 기본 비활성이다. 구 앱은 새 백엔드 개인 API에서 401을 받으므로 앱·서버 출시 순서와 이전 안내를 설계해야 한다. 기존 폰 로컬 데이터는 이 작업으로 삭제되지 않는다.
2. **기존 데이터 이전:** 옛 `X-Device-Id`는 소유권 증명이 아니다. 독립적 확인 절차, 승인 기록, 코드의 안전한 전달 방법이 운영상 아직 정해지지 않았다. 이를 마련하기 전에는 티켓 발급과 클라우드 이전을 해서는 안 된다. `backend/AUTH_MIGRATION.md` 참조.
3. **복원 원자성:** 네트워크/사진 수집 실패 전 로컬 쓰기를 막지만, 여러 로컬 저장소에 쓰는 중 앱이 종료되면 부분 복원이 가능하다. 다음 단계에서 임시 저장 후 교체나 롤백을 검토한다.
4. **인증 강화:** 앱 토큰·복구 코드는 앱 전용 SharedPreferences에 있고 자동 백업은 제외했지만 Android Keystore 기반 암호화는 아직 없다. 등록·복구·티켓 시도 횟수 제한과 운영 모니터링도 필요하다.
5. **연결:** P1의 Railway 자동 연결/동기화 검증은 시작하지 않았다. 인증 정보의 서버 주소 묶음으로 주소 변경 시 재인증이나 명시적 복구가 필요하다. 인증 요청은 HTTPS만 허용하므로 로컬 HTTP 서버 개발 흐름은 별도로 점검해야 한다.
6. **추가 기능:** AI 피팅 고도화, 3D, 사이즈 추천, AI 옷장/코디 추천은 이번 P0 체크포인트에서 손대지 않았다.

## 다음 Codex 세션의 정확한 순서

1. `codex/p0-user-auth-20261025`를 체크아웃하고 이 문서와 `backend/AUTH_MIGRATION.md`, 코드 체크포인트 및 브랜치 HEAD를 확인한다. 기준 백업 브랜치와 다른 미커밋 작업 디렉터리를 보존한다.
2. 우선 인증 경계를 코드 리뷰한다. 특히 모든 개인 FastAPI 경로의 Bearer 적용, 토큰의 서버 주소 바인딩, 복구/티켓 경합, 소유권 교차 접근 테스트를 확인한다. Linux에서 `test_tryon_proxy.py`와 GitHub Actions를 실행한다.
3. 운영 변경 전에 독립적인 기존 데이터 소유권 확인 절차와 승인 기록, 티켓 전달/남용 방지, 신규 등록 rate limit을 설계·테스트한다. 기존 데이터를 자동 귀속하거나 삭제하지 않는다.
4. Android 복원 중 로컬 쓰기 원자성과 토큰 저장 강화, 서버/앱 순차 출시 및 구 앱 401 안내를 검증한다. 실기기에서 백업·복원·오프라인·재시작을 테스트한다.
5. P0의 미해결 보안·호환성 항목을 닫은 뒤에만 Railway/Supabase 자동 연결(P1) 및 나머지 기능을 단계별로 진행한다. 운영 DB 변경·배포·유료 AI 호출·Merge는 별도 승인 후 시행한다.
