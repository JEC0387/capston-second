# 실기기 오류 수정 최종 보고서

검증일: 2026-10-18 (Asia/Seoul). 저장소 `jimnnieo-svg/capstone-integration-backup`, 기존 브랜치 `fix/body-analysis-20261018`.
작업 시작 시 로컬/원격 최신 HEAD는 `a0cb59a456d85736f3f90483e4ab7819c17d5e8a`로 같았고 working tree는 clean이었다. 연결된 옛 integration 코드나 ZIP을 기준으로 다시 구현하지 않았다.

**SAVEPOINT 01~03의 코드·자동 검증·push를 완료했다. SAVEPOINT 04는 로컬 검증/APK/복구 자료를 저장하지만 GitHub Actions 원격 실행·아티팩트 생성 확인은 네트워크 차단으로 보류다.** 실제 삼성폰·정상 사진 native ML·유료 FASHN 생성/결과 저장은 미검증이며 이를 완료로 표시하지 않는다.

## 1. 체형분석 버튼의 코드상 원인과 수정

`BodyAnalysisViewModel.heightInput` 초기값이 빈 문자열이고, 기존 화면은 `enabled = validHeight`로 촬영/사진 선택을 모두 비활성화했다. 큰 업로드 영역도 `clickable(enabled = validHeight)`여서 키를 입력하지 않으면 안내·동의·권한 요청으로 진입할 방법 없이 회색 버튼만 보였다. 저장 동의를 선택하지 않은 것이 버튼 enabled의 직접 조건은 아니었다. 삼성 기기의 런타임 로그는 제공되지 않았으므로 모든 기기별 원인을 확인했다고 단정하지 않는다.

수정 후 버튼과 큰 영역은 탭할 수 있고, 키가 유효하지 않으면 입력 Dialog를 먼저 보여준다. 100~230cm 검사·기존 숫자/소수점 정책은 유지한다. 유효한 키 → 사진 이용 동의(숫자 기록 저장 기본 OFF) → 원래 탭한 촬영 또는 Android Photo Picker 순서로 이어진다. 보류한 작업을 한 번만 실행하고 동의/키 입력 취소는 실행하지 않는다. 외부 화면 복귀·선택 취소·중복 탭을 처리한다.

카메라는 기존 AppNavigation → CameraPreviewScreen → CAMERA 권한 → 촬영 → ViewModel.analyzeImage → 뒤로 복귀 흐름을 사용한다. 갤러리는 PickVisualMedia와 구형 기기 fallback을 사용하고 선택 URI를 같은 analyzeImage로 전달한다. 사진 URI callback에서도 분석 이용 동의를 확인하며 ViewModel의 높이/동의 가드를 우회하지 않는다. 버튼만 켜고 잘못된 높이나 미동의 사진을 분석하는 변경은 아니다.

CameraPreviewScreen/Manifest/AppNavigation/BodyAnalysisViewModel/BodyTypeClassifier/SilhouetteMeasurer/PoseQualityRules/분석 세션·저장 가드의 기존 핵심 코드는 이번 시작점 대비 보존했다. 네 체형 분류, 불확실 판정 보류, 잘못된 결과 저장/추천 차단을 유지한다.

## 2. 수동 피팅과 AI 생성 분리

기존 내 옷장 VirtualFittingScreen의 마법봉은 ML Kit 관절 기준 위치·너비·기울기 초기화였다. 실제 옷을 생성하는 기능이 아니었고, FASHN은 별도 MusinsaClosetScreen.TryOnView에만 연결돼 있었다.

수동 드래그·확대·회전·투명도·옷별 초기화는 보존하고, 자동 정렬은 `수동 배치 초기화 · 관절 위치에 맞추기` 이름과 별도 아이콘으로 구분했다. 원본에 배경이 있으면 수동 오버레이에도 배경이 보인다는 기존 안내를 유지한다.

새 `AI로 입어보기` 마법봉은 선택한 옷의 스냅샷과 전신 사진으로 AiTryOnScreen을 연다. 실제 생성 버튼은 기존 TryOnRepository.tryOn에 연결된다. 안내는 “원하는 옷을 선택하고 마법봉을 누르면 AI가 입혀줘요 ✨”이며, 드래그 위치가 생성에 반영되지 않는다고 바로 설명한다. 미설정/입력 준비/생성 중/성공/실패를 구분하고 중복 실행을 막는다. 원본 비교·시스템 파일 선택기를 통한 결과 JPEG 저장·재시도 경로를 추가했다. 실제 HTTPS 결과 URL 없이 성공을 만들지 않는다.

기존 `tryon-v1.6` / `POST https://api.fashn.ai/v1/run` / `GET /v1/status/{id}` 구조를 재사용한다. 앱의 상의·아우터/하의/원피스는 tops/bottoms/one-pieces에 대응한다. 신발·액세서리는 생성에서 제외하고 수동 배치로 남긴다. 상의+하의는 순차 최대 2회 생성 요청이며 앞 결과가 다음 인물 입력이다. 한 번의 요청으로 두 벌을 생성한다고 가정하지 않는다. 상의·아우터 충돌/원피스와 다른 의류 충돌은 선택 정리를 안내한다.

생성 전 별도의 외부 사진 전송 동의에서 대상·목적·생성 요청 수·중간 결과 재전송·비용·얼굴/핏 한계를 안내한다. 거부 시 전송하지 않는다. 선택한 옷을 재시도 시에도 유지하지만 새 전송 확인을 받는다. 생성 POST 자동 재시도는 없고 앞 단계 실패 시 뒤 요청을 중단한다. 실제 가격·크레딧 차감은 현행 정책/계정에서 확인해야 하며 기존 1벌=1크레딧 문구를 확정 요금으로 유지하지 않았다.

유료 생성 요청은 **0회**다. 공식 문서는 proxy CONNECT 403으로 확인하지 못했고 현행 API 호환성/요금/생성 품질을 새로 검증했다고 주장하지 않는다. BuildConfig FASHN_API_KEY 구조의 APK 추출 위험이 남아 있으며 Backend에 기존 FASHN proxy는 없다. 운영 인증/속도 제한/자격증명 없이 서버 proxy를 배포했다고 주장하지 않는다. Actions APK에는 외부 서비스 키를 넣지 않아 미설정 안내가 정상이다. 상세 내용은 AI_TRYON_STATUS.md.

## 3. 옷장 Collapsing Header

기존 고정 배너 3개를 상품과 같은 LazyColumn의 첫 항목으로 옮겼다. 아래로 스크롤하면 배너가 실제 레이아웃에서 위로 이동하고, 상품이 그 공간을 채운다. 중간 방향 전환 시 나타나는 조건이나 alpha/높이 토글이 없으므로 목록 중간에서 배너가 갑자기 펼쳐지지 않는다. 최상단으로 돌아오면 스크롤에 맞춰 자연스럽게 나타난다.

필터 두 줄은 stickyHeader에 고정하고, 상품은 stable key가 있는 2열 Row와 행 배치 애니메이션으로 유지했다. 기존 Compose 버전의 LazyGrid에는 stickyHeader API가 없어 전체 UI 라이브러리 업그레이드 대신 기존 LazyColumn API를 사용했다. 스크롤 위치는 rememberLazyListState, 범위/카테고리는 rememberSaveable로 보존한다.

배너의 미착용 알림/보관·삭제 확인/부족 옷 추천/무신사 이동 callback, 내 옷/관심 옷/카테고리 데이터, 즐겨찾기/관심 해제, 헤더의 달력·오늘 입은 옷, 하단 등록/피팅/코디 버튼은 유지한다. 하단 내비게이션은 변경하지 않았다. 비동기 배너 데이터는 스크롤 밖에서도 화면 상태에 보관해 불필요한 재계산/깜빡임을 줄였다.

## 4. 실제 자동 검증

| 항목 | 실제 결과 |
|---|---|
| Android 테스트 | **191 성공 / 실패 0 / 오류 0 / 건너뜀 0** |
| 기존/순수 Kotlin | 180개: 시작점158 + 버튼 진입8 + AI 계획/순차 실행14 |
| Compose JVM UI | **11개 실제 실행 성공**: 버튼·동의·키·선택 취소·URI 분석 오류7, 스크롤·필터·클릭·상태 복원4 |
| 실행 환경 | Robolectric4.16.1, Android API35 framework, JDK21. 실기기 실행과 구분 |
| Android instrumentation | 7개 컴파일 성공, 기기에서 미실행 |
| assembleDebug | 성공 |
| lintDebug | Error/Fatal0, Warning100, Hint2 |
| Backend | 11개 성공 / 실패·오류0. 소스 변경 없음, 격리 SQLite/테스트 앱 사용 |
| 유료 FASHN | 호출0, 실서비스 미검증 |
| GitHub Actions | YAML/셸 문법·동일 명령의 로컬 빌드는 검증, 원격 실행·아티팩트 생성 미확인 |

최종 명령: fashion_app에서 `bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --init-script gradle/test-runtime.init.gradle.kts --console=plain --max-workers=2`.
실제 BUILD SUCCESSFUL을 확인했다. Backend는 `.venv/bin/python -m unittest discover -s tests -v`로 11개를 실행했다. 기존 테스트를 삭제/비활성화하지 않았다.

실제 Compose 테스트는 초기 배너 클릭, 목록 중간에서 역방향 스크롤해도 배너 숨김 유지, 필터 고정·작동, 최상단 복귀, 스크롤/필터 상태 복원, 빈 목록을 검증한다. 격리된 카드/데이터로 실제 production 스크롤 컴포넌트를 실행하며 기존 사용자 옷장 데이터를 지우지 않는다. 전체 앱 화면 전환·실제 상품/배너 데이터 조합은 실기기 리허설 대상이다.

처음 AI 컴파일의 타임아웃 return 레이블 오류는 수정했다. 헤더 최초 컴파일은 미지원 grid stickyHeader 때문에 실패해 지원되는 LazyColumn으로 수정했다. Robolectric 최초 실행기의 읽기 전용 홈 오류는 Gradle 캐시 안의 테스트 전용 JVM user.home과 기존 proxy/CA를 사용하는 init script로 해결했다. URI 오류 UI 테스트는 가상 MainLooper 구동을 보완했다. 실패 검사를 삭제하지 않고 최종 전체 검증을 다시 통과했다.

## 5. APK 및 업데이트 설치

- applicationId: `com.jeiu.fashionproject` (유지).
- versionCode: **2**, versionName: **1.0-real-device-fix**.
- minSdk24(Android7.0 이상), targetSdk36, compileSdk37.
- APK 크기: 169481398 bytes.
- APK SHA-256: `470644eb622874b8194b6b506f22f2f3da8a893284303904aa27969c9afac81a`.
- apksigner verify 성공. 인증서 SHA-256: `16126e22392895bb1daa95525d13189a7dc07cee0c79d7e062ae275891d7e57f`.
- 이전 클라우드 로컬 APK와 인증서 동일: True.
- 휴대폰의 이전 GitHub Actions APK 인증서는 알 수 없어 업데이트 호환을 보장하지 않는다. 같은 서명이면 versionCode1→2 업데이트 설치가 가능하다. 실제 설치 검증은 미실행이다.
- Actions debug.keystore 캐시를 추가해 이후 캐시가 유지되는 빌드의 서명을 보존한다. 이번 첫 캐시 생성/캐시 소실/다른 머신 빌드에서는 서명이 달라질 수 있다. 충돌 시 기존 앱을 삭제하거나 데이터를 초기화하지 말고 기존 키/백업 방법을 확인한다.
- APK 로컬 파일: `fashion_app/app/build/outputs/apk/debug/app-debug.apk`; 전달 복사본: `/workspace/real-device-fix-work/artifacts/body-analysis-stabilized-debug.apk`.

## 6. GitHub 배포·다운로드 상태

기존 `Body analysis debug APK` 워크플로를 유지하고 해당 브랜치 필터/job 조건을 보존했다. unit/build/lint/UI 컴파일 후에 APK·해시·서명·빌드 커밋을 아티팩트에 보관하도록 보강했다. `real-device-fix-source`에는 커밋 기준 SAVEPOINT_04_FINAL.zip과 인수인계/재개 문서를 보관하고 `real-device-fix-test-reports`에는 테스트 XML·Lint 보고서를 보관한다. 아티팩트 보관 기간은 30일이다.

원격 Git read/push는 실제 성공했지만 GitHub API 조회는 기존 GH_TOKEN 바인딩으로 호출해도 Forbidden이다. api.github.com과 docs.fashn.ai의 허용 **초안만 저장**했으며 환경 설정에서 검토/저장 후 Publish해야 적용된다. 기존 package_managers preset은 보존했다. 현재 원격 Actions 성공/아티팩트 존재/PC 다운로드 성공을 확인한 것은 아니다.

PC 다운로드: GitHub 로그인 → https://github.com/jimnnieo-svg/capstone-integration-backup/actions?query=branch%3Afix%2Fbody-analysis-20261018 → 최종 커밋의 Body analysis debug APK → 녹색 성공 → Artifacts의 body-analysis-stabilized-debug → ZIP 해제 → body-analysis-stabilized-debug.apk. 소스는 real-device-fix-source를 선택한다. 기존 실패/WIP 실행이나 오래된 SAFEPOINT APK와 혼동하지 않는다. 자세한 내용은 APK_DOWNLOAD.md.

## 7. 복구·중간 저장

| 단계 | 실제 전체 SHA | push |
|---|---|---|
| INITIAL_BACKUP | `93af258e4e5dd54774aa64a16397837335f984de` | confirmed |
| SAVEPOINT_01 | `754bc4c82a47f68807014483b2f81c1c2cb167fe` | confirmed |
| SAVEPOINT_02 | `76a9ac8125463f457ef09d7e424b8797b39b8539` | confirmed |
| SAVEPOINT_03 | `4028c4885996f48451e94854cef6d404580f8c22` | confirmed |
| SAVEPOINT_04 | 이 문서를 포함하는 SAVEPOINT_04 커밋 | 커밋 후 같은 브랜치 일반 push 및 SHA 비교 |

중간 WIP e9f01c3도 같은 브랜치에 push했고 검증된 SAVEPOINT와 구분했다. 현재 단계 자기 SHA는 포함할 수 없으므로 `git rev-parse HEAD`와 Git log/최종 응답/별도 DELIVERY_MANIFEST.json으로 확인한다. main·다른 브랜치·팀 저장소 수정, 새 브랜치 생성, merge/force push/reset/기존 DB 삭제는 수행하지 않았다.

초기·단계별 소스 ZIP은 `/workspace/real-device-fix-work/backups/`에 실제 생성하고 CRC를 확인한다. .git/빌드·캐시/.env/local.properties/로컬 도구를 제외하며 필요한 소스·Gradle·리소스·문서를 포함한다. 이 채팅에는 파일 첨부 다운로드 도구가 없어 로컬 경로를 다운로드 검증으로 표시하지 않는다. GitHub 특정 커밋의 Code → Download ZIP으로도 복구할 수 있고 Actions의 소스 아티팩트는 원격 성공 후 다운로드할 수 있다.

## 8. 아직 남은 작업

- 네트워크 설정 적용 후 최종 Actions 실행/아티팩트/다운로드 확인 (SAVEPOINT04 원격 배포 확인 보류).
- 삼성폰에 데이터 보존 업데이트 설치 후 촬영·권한 허용/거부·사진 선택/취소·동의/저장OFF·연속 요청·실패 재시도 확인.
- 정상 전신 사진/EXIF 회전·반전/native ML 및 실제 사람 분류 정확도 확인. 합성/Compose 테스트를 실제 정확도로 환산하지 않는다.
- 유료 호출 사전 승인·실제 FASHN 설정/현행 문서/비용·API 호환성·생성 결과·원본 비교·결과 저장 검증. 서버 proxy는 미구현이다.
- 실제 옷장 데이터/작은 화면/화면 이탈 후 복귀/내비게이션에서 배너·필터·상품 흐름 리허설.

## 변경 파일
- `.github/workflows/body-analysis-debug-apk.yml`
- `capston-second-main/AI_TRYON_STATUS.md`
- `capston-second-main/BODY_ANALYSIS_TEST_CHECKLIST.md`
- `capston-second-main/HANDOFF.md`
- `capston-second-main/REAL_DEVICE_FIX_HANDOFF.md`
- `capston-second-main/REAL_DEVICE_FIX_REQUIREMENTS.md`
- `capston-second-main/RESUME_PROMPT.md`
- `capston-second-main/fashion_app/app/build.gradle.kts`
- `capston-second-main/fashion_app/app/src/androidTest/java/com/jeiu/fashionproject/BodyGalleryConsentTest.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/data/TryOnPlan.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/data/TryOnRepository.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/WardrobeContentGrid.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/AiTryOnScreen.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/BodyAnalysisScreen.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/MusinsaClosetScreen.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/VirtualFittingScreen.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/WardrobeScreen.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/BodyPhotoEntryFlow.kt`
- `capston-second-main/fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyPhotoEntryFlowTest.kt`
- `capston-second-main/fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyPhotoEntryUiTest.kt`
- `capston-second-main/fashion_app/app/src/test/java/com/jeiu/fashionproject/TryOnPlanTest.kt`
- `capston-second-main/fashion_app/app/src/test/java/com/jeiu/fashionproject/WardrobeContentGridTest.kt`
- `capston-second-main/fashion_app/gradle/libs.versions.toml`
- `capston-second-main/fashion_app/gradle/test-runtime.init.gradle.kts`
- REAL_DEVICE_FIX_REPORT.md / REAL_DEVICE_FIX_HANDOFF.md / RESUME_PROMPT.md / APK_DOWNLOAD.md (최종 문서).
