# APK 업데이트 진단 및 Preview 배포 인수인계

작업 브랜치: `codex/apk-update-diagnostics-20261026`. 기준 HEAD는 `ff58d48165bd25020dd941ddce467a1b677865f0`이며 기존 실행 코드 `6776aee019aad9be28804ceb6c5b1c0a2904a697`의 P0~P6를 유지한다. 이 문서의 마지막 검증 절에 실제 APK/CI 코드 SHA를 기록한다.

## 설치 실패 원인 — 읽기 전용 실기기 진단

S25+의 설치본은 패키지 `com.jeiu.fashionproject`, versionCode **1**, versionName **1.0**, 마지막 업데이트 **2026-10-18 13:22:18 KST**다. 설치 APK만 `adb pull`로 읽었고 삭제·설치·초기화·강제 중지는 하지 않았다.

| 항목 | 설치본 | 이전 최신 GitHub APK |
|---|---|---|
| versionCode / versionName | 1 / 1.0 | 3 / 1.0-presentation |
| 인증서 SHA-256 | `3d540a7a4c5a9d9e5e9dfa81286337d3c10a74a37c1ddeb5f6e916610a26f3b3` | `10f5756afb7e717c7e575e59bd34993250558ee3383b0027d31a0a8868146a81` |
| APK SHA-256 | `1a90bea6d8b964adcdf845c6c32cf42481c5f87162038ffe27c17bc8c44223a3` | `bd89bffc741cc70b3096f2d28c347fc63125bf7843d1f79f042f6f2b4def5767` |
| 3D 기본/비율 아바타·숫자 입력·AiTryOnScreen | 없음 | DEX에 있음 |
| 구형 DB 연결 문구 | 있음 | 없음 |

- 설치본의 전체 APK 해시는 [최초 Actions 39095124206](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/39095124206)의 APK와 **완전히 일치**한다. 해당 소스는 `a0cb59a456d85736f3f90483e4ab7819c17d5e8a`다.
- 최초 워크플로와 Gradle에는 사용자 지정 서명 구성·서명 Secret·키 보관 단계가 없다. GitHub 호스팅 임시 실행 환경의 기본 Debug 키를 사용한 것으로 판단한다. APK에는 공개 인증서만 있어 개인키를 복구할 수 없다.
- 이전 최신 [Actions 40207874127](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/40207874127)도 서명키 캐시 미발견 뒤 `..`가 포함된 경로 때문에 캐시 저장이 거절됐다. 실제 현재 캐시 목록에도 서명키가 없다.
- 사용자 폴더에서 `.jks`·`.keystore`를 검색했다. Android용 키 `C:/Users/USER/.android/debug.keystore`의 인증서는 `a84bf80dff502b0c0ee303ebaff86ab3404e211b7da787336c26cd19a372b8fb`로 설치본과 다르다. Android Studio 2025.3.2 설정에서 다른 서명 경로를 찾지 못했다. OneDrive 서비스 영역의 같은 확장자 파일은 Android 서명 설정과 연결되지 않았고 개인키 추출 대상에서 제외했다. `ElevatedDiagnostics`는 접근 거부여서 전체 사용자 폴더를 완전히 조사했다고 주장하지 않는다.
- 마이페이지 자체도 `1.0.0`을 하드코딩하던 버그가 있었다. 이제 실제 PackageManager 버전·code·Git SHA·공개 서명 지문을 표시한다.

동일 패키지의 서명이 달라 정상 덮어쓰기가 불가능하다. 다른 package로 바꾸면 별도 앱이 되며 기존 앱의 저장소·URI 권한·Android Keystore를 자동으로 이어받지 않는다. 삭제나 데이터 초기화로 우회하지 않는다. [Android 서명 정책](https://developer.android.com/studio/publish/app-signing).

## 기존 데이터 백업과 이전 조건

- 암호화 백업: `C:/Users/USER/Documents/ChatGPT/capston/fashionai-s25-backup-20261026.zip`.
- 현재 Windows 사용자용 DPAPI로 **평문을 디스크에 쓰지 않고** 암호화했다. 복호화 대조와 ZIP CRC 검사를 통과했다. Git에서 제외됐고 외부로 업로드하지 않았다.
- 백업 범위: `files`, `shared_prefs`, `databases`, `no_backup`. 파일 1개, SharedPreferences 10개, DB 파일 2개이며 대기열 3건을 포함한다. 개인 데이터는 SharedPreferences 중심이고 앱의 Room 사용은 확인되지 않는다. 라이브러리 DB나 암호화 인증 파일을 새 앱에 그대로 복사하는 이전은 검증되지 않았다.
- 카메라 캐시와 공유 갤러리 원본은 이 백업의 범위가 아니다. 갤러리 원본은 변경하지 않았다. 저장된 URI가 있다면 새 앱의 읽기 권한과 경로를 다시 확인해야 한다. Android Keystore의 비추출 키 자체는 백업되지 않는다.
- 실행 중 파일의 읽기 전용 스냅샷이다. 앱을 정지하거나 DB를 수정하지 않았으므로 원자적 시점 스냅샷·완전 복구를 보장하지 않는다. 실제 복원·이전은 실행하지 않았다.
- **DPAPI 복구 조건:** 동일 Windows 사용자 및 해당 DPAPI 마스터키/프로필이 필요하다. 사용자 이름이나 암호만 같은 새 계정, PC 교체, Windows 재설치로는 복구가 보장되지 않는다. 이 파일만 외장 디스크로 복사하는 것은 휴대 가능한 백업이 아니다.
- PC 교체용 백업은 별도 단계에서 사용자만 아는 암호로 AES-256-GCM 같은 인증 암호화를 적용한 별도 아카이브로 재암호화하고, 암호를 독립적인 암호 관리자/오프라인 보관소에 보관해야 한다. 생성 후 다른 환경에서 복호화 검증하되 실제 앱에 쓰지 않는다. 현재는 사용자의 독립 보관 암호가 마련되지 않아 이 휴대용 백업은 만들지 않았다. 암호나 개인키를 채팅·Git에 올리지 않는다.

### 동기화 대기 3건의 이전 계획 (미실행)

백업에서 값은 출력하지 않고 경로/필드 이름만 확인했다.

1. `/api/profile/styles` — `styles`.
2. `/api/wardrobe/sync` — `items`.
3. `/api/wear-logs/sync` — `logs`.

현재 API는 옷장을 clientId, 착용 기록을 날짜로 맞추는 전체 스냅샷 방식이다. 같은 내용을 반복하면 같은 행을 갱신하지만, 서버에만 있는 항목을 삭제할 수 있으므로 **두 앱에서 같은 계정을 쓰며 자동 재생하면 안 된다**. 기존 앱의 백업 끄기도 서버 삭제를 동반할 수 있어 임의로 누르지 않는다.

승인 후 이전할 때는 (1) 독립적인 계정 소유권 확인, (2) 원본·대상 서버 스냅샷과 대기 3건의 암호화 보관, (3) clientId/날짜를 보존한 충돌 비교와 사용자 확인, (4) 임시 대상에서 3건을 한 번씩 반영하고 반복 반영의 동일성 검증, (5) 승인된 단일 앱만 쓰기 권한을 갖도록 전환, (6) 조회 결과·개수·내용 해시 검증 후에만 대상 대기열 완료 처리가 필요하다. 원본 대기열은 검증 전에 비우지 않는다. 이전 도구와 원자적 서버 지원은 아직 구현하지 않았다.

## 패키지와 서명 정책

| 구분 | package / 앱 이름 | 서명·서버 정책 |
|---|---|---|
| 기존 S25+ | `com.jeiu.fashionproject` | 원본 유지, 설치/삭제 안 함 |
| 새 Preview | `com.jeiu.fashionproject.preview` / **FashionAI Preview** | 새 고정 Preview 키, 서버 개인 데이터 접근 차단 |
| 향후 정식 Release | `com.jeiu.fashionproject` | 별도의 정식 서명·데이터 이전 승인 필요. 현재 Release 배포 없음 |

- Preview 인증서 SHA-256: `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`.
- Preview 키 로컬 암호화 백업: `C:/Users/USER/Documents/ChatGPT/capston/fashionai-preview-signing-20261026.zip`. 사용자 DPAPI 조건은 위와 같다. 원본 키나 비밀번호는 출력하지 않았다.
- 공식 `gh`가 저장소 공개키로 암호화해 새 Preview 키를 GitHub Secrets에 저장했다. Secret 이름은 `ANDROID_DEBUG_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`다. 공개 변수는 `ANDROID_KEY_ALIAS`, `ANDROID_SIGNING_CERT_SHA256`다. 이 키는 Preview 전용이고 정식 Release 키로 사용하지 않는다. Secrets는 다시 평문 조회하는 보관소가 아니므로 독립적인 키 백업 계획도 필요하다.
- Actions는 키를 RUNNER_TEMP에만 복원하고 완료 후 제거한다. 키를 캐시/산출물/Git에 저장하지 않는다. APK 내부에 키 파일·서명 암호가 없는지 검사하며 예상 공개 지문과 다르면 게시를 차단한다.
- 주 패키지 Debug는 CI 검증만 하고 배포하지 않는다. 게시 도구는 Preview 외의 패키지 게시를 거절한다. 정식 배포와 앱 삭제/이전은 별도 승인 대상이다.

## 구현과 검증 경계

- 버전: 로컬 검증용 4 / `1.1-local`; CI는 `10000 + run_number*100 + run_attempt`, 이름 `1.1.<run>.<attempt>[-preview]`. 100회 이상의 재시도는 새 실행을 요구한다. 과거 실행·게시 아티팩트를 조회해 낮거나 같은 배포 code를 거절하고, 브랜치 간 게시를 직렬화한다.
- APK 내부 `assets/build_identity.json`과 manifest 버전·실제 checkout SHA·브랜치·빌드 번호를 검증한다. `BUILD_INFO.txt/json`, APK SHA-256, 공개 서명을 함께 제공하고 파일명·artifact명에 버전을 넣는다.
- `-PfashionPreview=true`로 별도 패키지 빌드. Preview는 백업 대기열 전송뿐 아니라 개인 API Authorization, 등록·복구·이전 요청도 차단한다. 기존 서버 계정의 복구 코드를 Preview에 입력하지 않는다. 앱 저장소·Android Keystore는 UID가 달라 분리된다.
- 3D는 사진/치수 없이 체형 분석의 **3D 기본 모델**, 입력/분석 후 **3D 비율 아바타 보기**에서 진입한다. 작은 뷰포트 진입 테스트와 APK DEX 포함 여부를 검사한다. 이는 인체 비율 참고 모델이며 3D 옷감 시뮬레이션/정확한 실측 복원은 아니다. 실기기 GPU·터치는 미검증이다.
- 마법봉은 원본 사진·옷 선택 후 기존 `AiTryOnScreen`으로 이동한다. 자동 정렬/스티커 합성은 별도다. AI 서버 준비 확인은 GET `/api/tryon/config`만 쓰며 생성하지 않는다. AI용 토큰·전송 동의·생성 요청은 별도이고 백업 로그인으로 대신하지 않는다. 유료 API는 호출하지 않았다.
- Railway 공개 GET 결과: 루트 `FashionProject API`, DB `ready=true`/`status=available`지만 **syncReady/authProtocol 누락**. 최신 앱의 인증 프로토콜 2를 만족하지 않아 실제 동기화 준비 상태가 아니다. 앱 안내를 구분했으며 인증 보안을 낮추는 호환 우회는 하지 않았다. 운영 서버 업데이트와 SQL 005/006 적용 확인은 승인 후 필요하다.
- 추가 읽기 전용 확인에서 `/api/auth/status`와 `/api/tryon/config`가 모두 404였으며 공개 OpenAPI에도 해당 경로가 없었다. 현재 운영 주소에는 최신 인증·AI 프록시 API가 배포되지 않은 상태로 확인된다. 토큰만 입력해서 해결되지 않는다. 운영 변경은 하지 않았다.
- 날씨·검색·옷 등록·추천의 기존 코드는 유지했다. 자동 테스트를 실제 사진의 AI 정확도·외부 서비스 품질 검증으로 표시하지 않는다.

## 실행 방법과 안전한 설치 순서

1. 성공한 최신 CI의 **Preview** artifact를 받는다. `BUILD_INFO`의 package가 `.preview`, 버전/code/SHA·서명 지문이 위 정책과 일치하는지 확인한다.
2. 기존 앱을 지우지 않고 Preview를 별도 설치할 수 있다. 이번 작업에서는 휴대폰에 설치하지 않는다. 사용자가 설치 후에도 기존 앱은 원래 아이콘으로 남아 있어야 한다.
3. Preview 마이페이지의 실제 버전/code/Git SHA를 `BUILD_INFO`와 대조한다. 서버 개인 동기화 차단 표시를 확인하고 원래 복구 코드를 사용하지 않는다.
4. 이전 Preview를 업데이트할 때는 `tools/check_apk_update.py NEW.apk --installed-apk OLD_PREVIEW.apk`로 package/서명/code를 검사한다. 원래 S25+ 앱과 비교하면 패키지 불일치로 차단되는 것이 정상이다. 도구는 설치/삭제를 실행하지 않는다.

## 초기 로컬 검증 기록

- 로컬 첫 전체 Android 실행: 289개 중 실패 5개. 2개는 과도한 버튼 세로 배치로 생긴 기존 갤러리 회귀여서 배치를 원복했다. 1개는 서버 안내 변경에 따른 기존 테스트 갱신이다. 나머지는 Windows Robolectric 임시 경로 오류와 Mock HTTPS 복구 테스트 실패다. 해당 실패를 숨기지 않으며 원격 전체 결과와 구분한다.
- 로컬 재검증은 Lint 자체의 `ConfigurationScreenWidthHeightDetector` / FIR 예외로 중단됐다. 검사 무시 설정은 추가하지 않았다. Linux CI의 Lint 결과를 따로 확인한다.
- 로컬 백엔드: 59 passed, 3 skipped, 8 subtests passed, 7 warnings. Windows 미지원 피팅 테스트 2파일은 제외했으며 PostgreSQL 3건은 건너뛰었다. 전체 회귀는 CI의 Linux/PostgreSQL 결과로 확인한다.
- 배포 안전성 단위 테스트 3개 통과. `actionlint` 정적 검사 통과. 초기 CI의 중복 `if` 오류와 AGP 9 `resValues` 설정 누락은 단계별 수정했다.
- 원본 브랜치 보존, 데이터 삭제/이전, 운영 배포, 유료 생성, Merge/Force Push는 수행하지 않았다.

## Railway 추가 점검

- 실제 첫 Preview APK DEX에서 기본 `https://fashion-backend-production-aba0.up.railway.app`를 확인했다. `FashionApp.onCreate`는 `ServerSettings.load`와 `ServerConnection.start`를 호출한다. 신규 설치에는 주소 입력이 필요 없다. 명시적으로 저장했던 주소는 보존하고 설정 화면에서 기본/현재 주소 차이와 되돌리기를 안내한다. Preview는 새 저장소이므로 구 앱의 주소 설정을 자동 승계하지 않는다.
- 최신 GitHub `main.py`는 auth/tryon 라우터를 조건 없이 등록한다. FASHN 키가 없어도 라우터가 사라지지 않고 인증 거절/설정 오류로 응답해야 한다. 운영 OpenAPI에 두 경로가 없고 404이므로 **현재 주소의 앱에 최신 라우터가 등록되지 않은 것**은 확인됐다. 구 코드·다른 서비스/브랜치·다른 진입점 중 어느 배포 설정이 원인인지는 운영 메타데이터 없이는 단정할 수 없다.
- 공식 gh로 이 저장소의 deployments와 실행 코드의 배포 status를 조회했지만 연결된 Railway 배포 커밋 증거는 없었다. Railway 전용 인증 도구가 없고 브라우저 읽기 시 URL 확인 안전 제어로 Computer Use가 중단돼, 정확한 운영 브랜치/커밋과 실제 환경변수 등록 여부는 **미확인**이다.
- [computer-use 스킬](C:/Users/USER/.codex/plugins/cache/openai-bundled/computer-use/26.1021.31345/skills/computer-use/SKILL.md)의 [중단 지침](C:/Users/USER/.codex/plugins/cache/openai-bundled/computer-use/26.1021.31345/docs/guidance.md): “If Computer Use reports that the turn ended or that the user stopped Computer Use, stop issuing app input.” 도구가 브라우저 URL을 안전하게 확인할 수 없어 이번 turn의 Computer Use 중단을 명시했으므로 추가 UI 입력은 하지 않았다. 비UI CI·코드 검증은 계속했다.
- 로컬 현재 작업 디렉터리에는 `backend/.env`가 없고 프로세스의 DB/FASHN/OWM/NAVER/Unsplash/TRYON 관련 환경변수도 설정되지 않았다. 최신 `.env.example`은 비밀 값이 비어 있다. 과거 `capston-second-main (4).zip`과 `capston-second-phase1-security-modified-20261025.zip`에는 실제 `.env`/`local.properties`가 없고 `.env.example`만 있다. 과거 템플릿에는 비어 있지 않은 필드가 있으나, 예시인지 실제 유효 키인지 검증하지 않았으며 값을 출력·복사·전송하지 않았다. 로컬 미설정을 운영 미설정으로 해석하면 안 된다.
- 승인 후 운영자가 확인할 항목: 연결 저장소/브랜치/커밋, 서비스 root와 `uvicorn main:app` 진입점, auth/tryon 라우터 포함, SQL 005/006, `DATABASE_URL`, `AUTH_REGISTRATION_ENABLED`, `TRYON_ENABLED`, `FASHN_API_KEY`, `TRYON_CLIENT_TOKEN_HASHES`, 양의 사용자/전체 일일 한도, 지속 가능한 `TRYON_JOURNAL_PATH`, `OWM_API_KEY`, `NAVER_API_KEY_ID`/`NAVER_API_KEY`, `UNSPLASH_ACCESS_KEY`. 키 값은 보고서나 APK에 넣지 않는다. 운영 배포·변수 변경은 미실행이다.

## 변경 파일

```text
.github/workflows/android-debug-apk.yml
.gitignore
README.md
fashion_app/app/build.gradle.kts
fashion_app/app/src/main/AndroidManifest.xml
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/AppBuildInfo.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/BackendApi.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/DataAuth.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/DbSync.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/ServerSettings.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/BackupRestoreDialog.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/ServerAddressDialog.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/ServerStatusBadge.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/AiTryOnScreen.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/MyPageScreen.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/AppBuildInfoTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyPhotoEntryUiTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/PreviewIsolationTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/ServerSettingsTest.kt
tools/apk_release.py
tools/backup_installed_app.py
tools/check_apk_update.py
tools/manage_preview_signing.py
tools/test_apk_release.py
tools/windows_private_archive.py
docs/APK_UPDATE_HANDOFF_20261026.md
docs/README.md
docs/APK_DOWNLOAD.md
```

## 최종 전달·검증 확정

- APK 실행 코드 SHA: **`041d4f811005de77b645d0bd0ac32dc30ec8325f`**. 이 문서를 확정하는 후속 커밋은 문서만 변경하며 실제 최종 브랜치 HEAD는 `git rev-parse HEAD`로 확인한다.
- 첫 독립 빌드 [40217873661](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/40217873661): **7개 작업 전체 성공**, 코드 `659c50e443772283642194a9aa249b90b01c8cfa`, `1.1.11.1-preview` / code **11101**.
- 두 번째 독립 빌드 [40219084167](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/40219084167): **7개 작업 전체 성공**, 코드 `041d4f811005de77b645d0bd0ac32dc30ec8325f`, `1.1.12.1-preview` / code **11201**.
- 두 APK를 실제 다운로드해 `apksigner`로 확인했다. 인증서는 둘 다 **`f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`**로 동일하고 code가 증가한다. Preview 11101→11201의 package/서명/version 사전 검사는 통과했다. 실기기 설치 성공으로 해석하지 않는다.
- 최종 Android 전체 **290개 / 40개 클래스, 실패·오류·건너뜀 0**. 별도 Preview 격리·빌드 정보 검사 **3개 / 2개 클래스, 실패·오류·건너뜀 0**. 전체 결과가 Preview 검사에 덮어써지지 않도록 두 보고서를 보존했다.
- Debug APK·기기 테스트 Kotlin 컴파일·Lint 성공. **Lint Fatal/Error 0, Warning 165**. 경고를 모두 해결한 상태는 아니다. Windows에서 발생했던 실패/도구 예외는 위 초기 기록에 남겼고 Linux 전체 회귀 및 Lint는 성공했다. 로컬 Windows에서 모든 실패가 재검증 완료됐다고 주장하지 않는다.
- 백엔드 Linux/PostgreSQL 회귀 **105 passed, 8 subtests passed, 7 warnings**. 배포 안전성 도구 테스트 **3개 통과**. Windows Python 3.11/3.12/3.13/3.14와 Linux Python 3.12 서버 기동 확인 전부 성공했다. 전용 임시 테스트 DB만 사용했다.
- [최신 Preview APK artifact](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/40219084167/artifacts/12609271808), [전체 테스트·Lint 보고서](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/40219084167/artifacts/12608622648). 저장소 접근 권한으로 로그인해야 한다.
- 로컬 APK: `C:/Users/USER/Documents/ChatGPT/capston/apk-update-20261026/deliverables/fashionai-1.1.12.1-preview-vc11201-041d4f81.apk`.
- APK와 BUILD_INFO.txt/json·SHA256SUMS.txt 묶음: **`C:/Users/USER/Documents/ChatGPT/capston/fashionai-preview-1.1.12.1-vc11201-041d4f81.zip`**. ZIP CRC 검사 성공.
- APK SHA-256: **`f473eddcd42f1196aba87bb96a50c274e48b42d0e8755bbbcdc20c2ce334d228`**. manifest·내부 build_identity·BUILD_INFO와 실제 package/서명/code/SHA를 대조해 일치했다.
- 실제 최종 DEX에서 3D 기본/비율 아바타·숫자 입력·BodyDimensions·SizeFeedback·옷장·코디 추천·AiTryOnScreen 및 기본 Railway URL을 확인했다. 사진·직접 입력 없는 기본 모델 진입은 작은 뷰포트 자동 테스트로 확인했다. 실제 AI 생성·자동 분류 정확도·사이즈 정확도·GPU/카메라/터치는 검증하지 않았다. 3D 의류 착용 시뮬레이션은 기존 구현 범위가 아니다.
- 새 서명 암호/키가 변경 소스와 CI 로그에 포함되지 않았음을 값 출력 없이 검사했고, CI의 APK 비밀 파일·서명 암호 검사도 통과했다. 두 암호화 로컬 백업은 Git/Actions artifact에서 제외됐다.
- USB 재승인 후 S25+가 `device`로 연결됐다. 원래 앱은 여전히 code 1 / 1.0 / 10월18일 업데이트이며 대기열도 **3건 그대로**다. 두 암호화 백업 ZIP CRC도 다시 통과했다. Preview 설치·기존 앱 삭제/초기화·데이터 이전·운영 서버/DB 변경은 실행하지 않았다.
- 마지막 읽기 전용 대조에서 대기 3건의 전체 내용이 암호화 백업과 정확히 일치했다. 최종 CI 로그와 변경 소스에서도 새 서명키·암호의 실제 값 일치 항목은 0개였다.

### 다음 순서

1. 이 문서와 위 실행 코드의 성공 CI를 확인하고, 사용자가 승인하면 **별도 Preview 설치만** 진행해 버전/서명·3D GPU·터치·카메라·옷 등록·추천을 실기기에서 확인한다. 기존 앱과 복구 코드를 Preview로 옮기지 않는다.
2. 사용자가 독립 보관할 백업 암호/보관 수단을 마련하면 DPAPI 데이터·서명키 백업을 휴대 가능한 인증 암호화 형식으로 별도 복제하고 다른 환경에서 복호화만 검증한다. 현재 DPAPI ZIP만으로 Windows 재설치에 대비됐다고 보고하지 않는다.
3. Railway에 읽기 전용 접근이 가능한 세션에서 배포 저장소/브랜치/커밋·실행 경로·변수 이름별 등록 여부를 확인한다. 승인 전 배포/변수/SQL을 변경하지 않는다. 실제 키 값은 출력하지 않는다.
4. 운영 API·SQL 준비와 독립 소유권 확인 및 안전한 단일 작성자 이전 계획이 승인된 뒤에만 기존 대기 3건의 충돌 검증·이전 도구를 구현/실행한다. 기존 백업과 원래 앱은 검증 완료 때까지 보존한다.
5. 향후 정식 Release는 Preview와 별도의 서명 정책을 확정한다. 새 키로 기존 code 1 설치본을 직접 업데이트할 수 없으므로 삭제나 자동 귀속으로 우회하지 않는다.
