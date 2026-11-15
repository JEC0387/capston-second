# P67 — FashionFit 팀 통합 후보 인수 기록 (2026-11-12)

## 통합 기준

- 팀 저장소: `JEC0387/capston-second`
- 출발점: `sangyoon` / `6bbb0a5f84244d91e89f1e4a5096a02c29244ace`
- 사용자 최신 원격: `jimnnieo-svg/capstone-integration-backup`의
  `codex/fashionfit-p65-ai-tryon-ux-20261111` / `09dcad00df62df8a01e05a394e96583acb4b1270`
- 원본 Git 트리를 재귀적으로 확인한 결과 두 커밋 모두 파일 913개이며,
  내용이 다른 일반 파일은 정확히 18개였다.
- 차이 중 Android 앱·검증·Release 도구 17개는 P65의 정확한 Git blob을 반영했다.
- 나머지 `fashion_app/gradle.properties`는 기본 승인 스위치를 `false`로 유지했다.

## 채택한 정책

- 기존 P65의 최신 사진/상의/하의/신발 선택 검증과 가상피팅 간결 UI 보존.
- 기존 서버 핵심 소스(`backend/main.py`, `outfit_vton_api.py`,
  `outfit_vton_provider.py`, `Dockerfile`)는 두 브랜치가 동일하므로 재작성하지 않음.
- 기존 로그인·옷장·데이터 보존 코드는 변경하지 않음.
- 상윤 브랜치의 `fashionOutfitServerApproved=true` 기본 활성화는 보류.
  인증·사진 처리·유료 호출·Railway 실제 준비 상태가 승인되지 않은 동안
  `SERVER_OUTFIT_APPROVED=false`를 기본값으로 유지하고, Preview/QA 격리를 보존.
- 프로덕션 승인 스위치는 추후 운영 조건 검증 뒤 명시적인 별도 빌드 설정으로만 변경.
- 현재 진행 중인 P53/P54 로컬 DB·Genlook 시연 작업과 QA APK는 별도 보존.

## 이 작업에서 확인한 범위

- `git diff --check` 통과.
- 오프라인 Python `unittest` 기반 Release·CI·서명·Artifact 보호 검사 **24 PASS**.
- 백엔드 Python 소스 문법 컴파일(`python3 -m compileall -q backend`) 통과.
- Android SDK·서명키·ADB가 이 작업 환경에 없으므로 Android 컴파일,
  새 서명 APK 생성, 실기기 설치, OAuth, Railway 및 Genlook 생성은 **미실행**.
- GitHub Actions 비용 보호를 위해 신규 전체 CI 실행은 요청하지 않았으며,
  이 후보는 검증 완료/출시 승인이라는 뜻이 아니다.

## `main` 반영 전 필수 단계

1. Windows Android SDK가 있는 검증 환경에서 이 통합 브랜치를 받아
   Android 전체 테스트·`lintRelease`·`assembleRelease`를 수행한다.
2. 통합 코드의 백엔드 API·인증·DB 테스트와 실제 Railway readiness를 검증한다.
3. 운영 승인 여부와 과금/동의/보관·삭제 정책 확인 후에만 승인된 QA에서
   합성 사진 Genlook 실제 생성을 별도로 수행한다.
4. S25+ 및 별도 Android 기기에 **동일 서명·패키지·버전·해시 APK**를
   설치해 로그인, 앱 실행, 가상피팅, 데이터 보존을 확인한다.
5. 팀원의 코드 리뷰와 필요한 GitHub 보호 검사를 만족하면 PR을 `main`에 병합한다.
6. 병합된 `main` 커밋에서 정식 서명 APK 하나를 만들고 버전코드를 증가시켜
   SHA-256 및 다운로드 QR을 단일 기준으로 공유한다.

> 별도 QA `com.jeiu.fashionproject.preview.qa.p65` / 18622와
> 실제 정식 패키지 `com.jeiu.fashionproject`는 서로 다른 설치 앱이다.
> QA 설치 성공만으로 정식 Release E2E 성공을 주장하지 않는다.
