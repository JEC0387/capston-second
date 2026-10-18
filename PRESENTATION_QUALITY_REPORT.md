# 발표 전 최종 품질 점검 보고서

2026-10-18 (Asia/Seoul). 기존 `jimnnieo-svg/capstone-integration-backup` / `fix/body-analysis-20261018`, 시작 기준 `5de350b741177ef1c85c08581280d09c54d7c3f1`. 작업 전 로컬·원격 HEAD 일치와 clean tree를 확인했다. 기존 SAFEPOINT/SAVEPOINT를 보존했고 새 브랜치·main/팀 저장소 수정·merge/force push/사용자 데이터 삭제를 하지 않았다.

## 추가 수정

### P0 입력·촬영

키 입력(필수) 제목, cm 환산에 키가 필요한 이유, 입력 전/후 다음 행동 안내를 표시했다. 기존처럼 버튼을 눌러 키 입력→이용 동의→원래 촬영/사진 선택으로 이어질 수 있으며 invalid height로 분석하지 않는다. 선택적 숫자 저장은 기본 OFF다. Done 또는 촬영/선택 시 키보드/초점을 정리하고 IME 패딩·Activity adjustResize·스크롤 가능한 입력 Dialog를 적용했다.

기존7개 버튼 화면 테스트에3개를 추가해 도움말/Done/유효 키 후 촬영 callback, 분석 오류 후 촬영 재시도와 저장OFF 보존,360dp 높이에서 입력/두 버튼 접근을 실제 Compose JVM으로 검증했다. 실제 삼성 키보드나 카메라를 실행한 검증은 아니다.

### P1 가상피팅

AI 미설정인데도 자연스럽게 생성한다고 안내하던 문구를 수정했다. 설정/입력 조건을 검사하고 미설정은 수동 배치·연결 안내를 제공하며 AI 성공을 만들지 않는다. 옷을 선택하지 않고 AI 화면에 왔을 때 옷 선택으로 돌아갈 수 있다. 수동 자동 정렬과 AI 마법봉을 계속 구분한다.

작은 화면에서 크기/투명도/옷 선택 영역이 많은 높이를 차지하던 구조를 확인했다. 조절 패널만 가용 높이에 따라 제한해 별도로 스크롤하고, 캔버스 의류 드래그와 분리했다. 작은 높이에서는 안내를 간결하게 해 사진 공간을 확보한다. 기존 드래그·회전·확대·투명도·의류별 선택/초기화·팀원 화면을 삭제하지 않았다. 늦은 자세 검출이 새 사진을 덮지 않도록 요청 세대를 확인하고 일반 검출 실패는 수동 배치로 안내한다.

P1 Compose3개를 추가해 미설정 시 생성 약속 없음/실행 차단·수동 선택 복귀, 신발 미지원 안내,360dp 높이에서 사진 선택 후 크기/투명도/의류 선택 패널 접근을 실제 실행했다. 실제 인물/native 자세 검출/생성 결과 저장 검증은 아니다.

### 배포 실패 복구

GitHub API 조회가 이번 작업에서는 실제 성공했다. 이전5de350b 및 초기 점검 커밋은 Actions failure·jobs0이었고 실행 로그가 없었다. 공식 SHA 검증한 actionlint1.7.12로 `jobs.build.env`에서 runner.temp context가 허용되지 않는 오류를 재현했다. 허용되는 github.workspace 기반 경로로1줄을 수정했고 actionlint0오류, 실제 원격 작업 시작과 SDK 설치 성공을 확인했다. 기존 워크플로 이름/브랜치 필터/job 조건/읽기 권한/디버그 키 캐시/아티팩트는 유지했다.

이 문서를 포함하는 발표 커밋은 push 후 같은 SHA의 Actions 성공·아티팩트·다운로드를 확인한다. 최종 실행 URL/다운로드 검증은 최종 응답 및 전달 manifest가 기준이며, 이전 실패 상태와 혼동하지 않는다. 이 문서 자체가 원격 성공을 가정하는 것은 아니다.

## 회귀와 전체 사용자 흐름 점검

기존191개 테스트를 삭제/skip/약화하지 않고 유지했다. 분류기·실루엣·자세 품질·native 파이프라인·카메라·세션/저장/추천 가드/옷장 저장소/관심/착용/날씨/홈/쇼핑/Navigation 등 핵심 파일19개와 Backend는 시작 커밋과 바이트 단위로 같음을 확인했다. 관절 거리 강제 판정/가짜 허리 제거, 네 유형, 다중 행 측정, 경계 보류 및 기존 임계값은 그대로다.

| 흐름 | 이번 증거와 범위 |
|---|---|
| 온보딩→홈→마이페이지→체형분석→결과→사이즈/스타일 추천 | Navigation callback 연결을 실제 소스로 점검, P0 화면/데이터 테스트 실행. 전체 실기기 E2E는 미실행 |
| 동의/저장OFF·ON→세션·히스토리 | 기존 privacy/completion/persistence 테스트 실행. 서버 기록·실제 폰·운영 DB는 미검증/변경 없음 |
| 재분석/취소/이전 결과·늦은 완료·타임아웃 | 기존 request gate·session·measurement 안전성 테스트, URI 오류/선택 취소·촬영 재시도 UI 실행. 정상 사진 native 반복/EXIF 실행은 미검증 |
| 옷 등록→저장소→관심/내 옷→추천 | 기존 OutfitSources/RecommendationIntegration/TasteLearning/BodyAware/Backend roundtrip 테스트와 소스 연결 점검. 실제 사진 등록·외부 네이버 응답은 미검증 |
| 날씨/TPO/취향/착용 입력 | 기존 추천/취향 테스트와 error/fallback 소스 점검. 외부 날씨 실응답·위치 권한은 미검증 |
| 옷장 헤더/필터/카드 | 기존 스크롤·최상단 복귀·중간 방향 전환·고정 필터·빈 목록·복원 Compose4개 실행. WardrobeScreen/카드/내비게이션을 변경하지 않음. 실제 데이터·회전/탭 E2E는 미검증 |
| 인터넷 실패 | 네이버/날씨/FASHN 기능별 예외 경로 및 로컬 데이터·추천의 독립성을 코드/격리 테스트로 점검. 실제 비행기모드·API 인증/통신 장애 테스트는 리허설 대상 |

## FASHN 입력·지원·비용

공식 [v1.6 문서](https://docs.fashn.ai/api-reference/tryon-v1-6)를 실제 열람했다. 기존 model_name/inputs, model_image/garment_image URL 또는 data URI, tops/bottoms/one-pieces, balanced, JPEG 형식 구조와 일치한다. flat-lay/ghost mannequin/on-model 상품을 문서가 다루고 garment_photo_type 기본은 auto다. 앱은 원본 상품 이미지를 우선 사용하고 배경이 있어도 원본을 전송한다. 드래그 좌표는 보내지 않는다. 실제 제공된 사진이 없어 품질/포즈/배경 적합성을 입증하지 않았다.

문서상 처리 해상도864×1296, 결과1장당1크레딧이다. num_samples=1을 명시해 단일 출력을 고정했다. 상의+하의는 중간/최종2출력의 순차 생성이며 신발·액세서리는 이 모델에서 지원한다고 표시하지 않는다. 다른 유료 모델로 변경하지 않았다. 실제 계정 요금·유효 키/크레딧·실패 과금은 미조회, 유료 요청0회다.

API 미설정/401인증/402크레딧/HTTP 서비스/IOException 통신 오류를 성공으로 표시하지 않는다. 생성 중 중복 방지·원본 비교/결과 저장·새 전송 동의는 유지했다. 실제 API 결과/얼굴 보존·핏·저장 성공은 미검증이다. 앱 BuildConfig 키 추출 위험은 남고 인증된 서버 proxy는 구현/배포하지 않았다. Actions APK는 외부 API 키를 넣지 않으므로 기본 발표에서는 로컬 분석/수동 배치/옷장/일반 추천을 사용한다.

## 최종 로컬 검증

- Android **197 성공, 실패/오류/건너뜀0** = 기존191 + P0 UI3 + P1 UI3.
- Robolectric/API35 Compose **17개 실제 실행** (버튼10, 옷장4, 피팅3). 실제 휴대폰 테스트와 구분한다.
- `assembleDebug`, `lintDebug`, `compileDebugAndroidTestKotlin` 성공. Android instrumentation7개는 컴파일만, 기기 실행0.
- Backend unittest **11개 성공**, 격리 SQLite/테스트 앱. Backend 소스·운영 DB 변경 없음.
- 최종 명령: fashion_app에서 `bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --init-script gradle/test-runtime.init.gradle.kts --console=plain --max-workers=2`.
- 실제 BUILD SUCCESSFUL 후 APK 복사/서명/해시를 확인했다. 로그: /workspace/body-analysis-work/logs/quality-*.log, Backend: /workspace/presentation-quality-work/backend-final.log.

## Lint 검토

최종 Error/Fatal0 · Warning100 · Hint2. 기존100개 경고를 심각도/관련성별로 검토했고 경고를 숨기지 않았다.

| ID | 항목 수(중복 위치/Hint 포함) |
|---|---|
| OldTargetApi | 1 |
| SelectedPhotoAccess | 1 |
| GradleDependency | 2 |
| NewerVersionAvailable | 5 |
| Aligned16KB | 3 |
| StaticFieldLeak | 2 |
| AutoboxingStateCreation | 2 |
| UnusedResources | 15 |
| IconLocation | 35 |
| UseKtx | 36 |

- 우선 검토: Aligned16KB3건은 동일 MediaPipe0.10.14 arm64 native 라이브러리의 중복 진단이다. 실제16KB 기기 호환 미검증이며 벤더 SDK 교체·native 회귀가 필요해 발표 직전 임의 업그레이드/기능 삭제로 해결하지 않았다. 이 제한은 남는다.
- StaticFieldLeak2건은 DbSync가 applicationContext만 보관함을 소스에서 확인했다. Activity를 저장하는 코드가 아니므로 데이터 큐 구조를 불필요하게 바꾸지 않았다.
- SelectedPhotoAccess1건은 기존 READ_MEDIA_IMAGES 선언이다. 현재 Picker/GetContent는 URI 권한을 사용하지만 과거 저장 사진의 호환성을 실기기 확인하지 않고 기존 권한을 제거하지 않았다.
- targetSDK/의존성 새 버전/아이콘 위치/미사용 리소스/KTX/boxing 등은 현행 기능과 별개인 광범위 변경으로 이어질 수 있어 업데이트/일괄 정리를 하지 않았다. 심각한 배포 차단 원인인 workflow context 오류는 별도로 수정했다.

## 발표 APK·서명·다운로드

- applicationId `com.jeiu.fashionproject`, **versionCode3 / versionName1.0-presentation** (직전2에서 증가).
- minSdk24, targetSdk36, compileSdk37. Debug APK 169497810bytes.
- 로컬 APK SHA256: `228ce314e5011fc496dde8a2bc3d7c9aebcef6a56c67e0538aef0f40e9dc6400`.
- apksigner verify 성공, 인증서 SHA256 `16126e22392895bb1daa95525d13189a7dc07cee0c79d7e062ae275891d7e57f`.
- 이전 로컬 버전2와 서명 동일: True. 기존 휴대폰의 Actions 인증서는 알 수 없어 무삭제 업데이트를 보장하지 않는다. 동일 서명이면 `adb install -r`로 데이터를 유지하는 업데이트가 가능하다. 충돌 시 앱을 임의 삭제하지 않는다.
- Actions 디버그 키 캐시를 유지했다. 캐시 첫 생성/소실·다른 머신은 서명이 다를 수 있다. 최종 CI APK의 SIGNATURE.txt가 원격 산출물 기준이다.
- PC: GitHub 로그인→같은 브랜치의 최종 커밋 Actions→녹색 성공→Artifacts의 body-analysis-stabilized-debug→ZIP해제. 소스는 real-device-fix-source, 테스트/Lint는 real-device-fix-test-reports. 정확한 최종 실행/아티팩트 URL은 최종 응답에 제공한다.

## 저장 지점·복구

| 단계 | 실제 전체 SHA | push |
|---|---|---|
| INITIAL | `18cf0a2847102a24b44560a5a48caeb632132bdb` | confirmed |
| P0 | `bcc37fc3d985564728e6baeeee7e7fc34200648b` | confirmed |
| P1 | `fae4c79acbbf0cf613bed6283924208c9008aa34` | confirmed |
| FINAL | 이 보고서를 포함하는 quality: final presentation readiness 커밋 | push 후 원격 SHA/Actions 비교 |

자기 커밋 SHA/실행 결과는 커밋 안에 미리 고정할 수 없으므로 git rev-parse HEAD / 최종 응답 / Actions BUILD_INFO.txt 및 별도 DELIVERY_MANIFEST.json으로 확인한다. 단계 ZIP은 /workspace/presentation-quality-work/backups/에 생성하고 CRC 확인한다. 사용자에게 로컬 경로만 제공하고 PC 다운로드가 확인됐다고 주장하지 않는다.

최신 인수인계는 REAL_DEVICE_FIX_HANDOFF.md, 새 세션 재개는 RESUME_PROMPT.md, 발표 리허설은 PRESENTATION_TEST_CHECKLIST.md다. 실제 기기/사진/native ML/EXIF/서버 히스토리/실제 API를 미검증으로 남긴다.

## 변경 파일
- `.github/workflows/body-analysis-debug-apk.yml`
- `capston-second-main/AI_TRYON_STATUS.md`
- `capston-second-main/REAL_DEVICE_FIX_HANDOFF.md`
- `capston-second-main/RESUME_PROMPT.md`
- `capston-second-main/fashion_app/app/build.gradle.kts`
- `capston-second-main/fashion_app/app/src/main/AndroidManifest.xml`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/data/TryOnRepository.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/AiTryOnScreen.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/BodyAnalysisScreen.kt`
- `capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/VirtualFittingScreen.kt`
- `capston-second-main/fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyPhotoEntryUiTest.kt`
- `capston-second-main/fashion_app/app/src/test/java/com/jeiu/fashionproject/PresentationFittingUiTest.kt`
- capston-second-main/PRESENTATION_QUALITY_REPORT.md
- capston-second-main/PRESENTATION_TEST_CHECKLIST.md
