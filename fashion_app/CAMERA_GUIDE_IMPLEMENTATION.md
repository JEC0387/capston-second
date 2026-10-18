# 인체분석 카메라 1차 구현

## 프로젝트 구조 및 기존 흐름

- `fashion_app/`: Android Kotlin + Compose, 단일 `app` 모듈. `MainActivity` → `AppNavigation`에서 화면과 ViewModel 연결.
- `ui/screens/`: 홈, 옷장, 날씨 코디, 체형 분석, 스타일 추천, 쇼핑, 가상 피팅 등 Compose 화면.
- `ui/viewmodel/`: 체형 분석, 의류 등록, 날씨 상태 관리. `data/`에는 서버 API, 옷장/스타일 저장 및 데이터 공급 코드.
- `backend/`: Python FastAPI. 옷장/날씨/추천/객체 탐지 등의 API. `/api/body-analysis`는 현재 고정 결과를 반환하는 별도 구현이며, Android 체형 분석 화면은 로컬 ML Kit 분석을 사용한다.
- `capston_supabase_changes.zip`: 함께 들어 있던 별도 변경 묶음. 이번 촬영 가이드에 적용하지 않음.

기존 체형 분석 경로:

1. `BodyAnalysisScreen.kt`에서 사진 선택 또는 카메라 화면 진입.
2. `CameraPreviewScreen.kt`에서 후면 CameraX 카메라로 cacheDir에 JPEG 촬영.
3. `AppNavigation.kt`가 이전 화면의 savedStateHandle에 `photo_uri` 전달 후 카메라 화면 닫기.
4. `BodyAnalysisViewModel.analyzeImage()`가 Accurate Pose Detection SINGLE_IMAGE_MODE 실행.
5. 기존 전신/정면 검증 후 MediaPipe 측정을 시도하고, 실패하면 포즈 관절 너비 추정으로 폴백.
6. 기존 분석 결과 화면에서 사진, 체형, 신체 비율, 특징 및 스타일 조언 표시.

`BodySegmenter.kt`: IMAGE 모드 ImageSegmenter와 `assets/selfie_segmenter.tflite` 사용. ZIP에 모델 파일이 실제 포함되어 있다. 어깨/허리/힙 높이의 마스크 가로폭을 ±2% 범위에서 평균한다. 이 값은 픽셀 폭이며 cm가 아니다. 기존 코드의 마스크 카테고리 해석과 모델 품질은 이번 단계에서 바꾸거나 검증하지 않았다.

`BodyAnalysisViewModel.kt`: 어깨/힙 비율 >=1.08은 역삼각형, <=0.92는 삼각형, 나머지는 허리/(어깨·힙 평균) <=0.80이면 모래시계형, 그 외 직사각형. 세그멘테이션 폴백 허리는 작은 너비의 78%로 근사한다. 기존 비율 계산/분류 로직과 결과 화면은 유지했다.

## 수정 및 추가 파일

| 파일 | 변경 |
| --- | --- |
| app/src/main/java/com/jeiu/fashionproject/ui/screens/CameraPreviewScreen.kt | 기존 Preview·ImageCapture에 ImageAnalysis 및 실시간 ML Kit 스트림 추가. 가이드 영역/안내/진행률/수동 촬영 표시. 권한과 생명주기 처리, 중복 촬영 잠금, 오류 표시, 미지원 기기의 수동 촬영 폴백. |
| app/src/main/java/com/jeiu/fashionproject/ui/camera/CaptureGuide.kt | UI/Android에 독립적인 촬영 조건 평가와 자동 촬영 상태 제어. |
| app/src/test/java/com/jeiu/fashionproject/CaptureGuideTest.kt | 정상/누락/저신뢰도/거리/잘림/중앙/정면/팔/비정상 수치 및 촬영 유지 시간·잠금 검증. |
| app/src/main/AndroidManifest.xml | 기존 CAMERA 권한에 대응하는 선택 하드웨어 uses-feature 선언 추가. Lint의 PermissionImpliesUnsupportedChromeOsHardware 오류 해결. |
| CAMERA_GUIDE_IMPLEMENTATION.md | 구조, 기준, 제약, 테스트 방법 문서. |

Gradle 의존성, BodyAnalysisScreen, BodyAnalysisViewModel, BodySegmenter, AppNavigation은 변경하지 않았다. 이미 있던 CAMERA 권한, CameraX 1.3.4, ML Kit accurate 18.0.0-beta5를 사용한다.

## 촬영 조건의 기준

기준은 실제 거리/신체 치수가 아닌 화면상 비율이며, 실기기 데이터로 보정할 초기 휴리스틱이다. 실패 조건은 아래 순서로 우선 표시한다.

| 조건 | 기준 |
| --- | --- |
| 사람 검출 | 포즈 랜드마크가 없으면 사람 안내. |
| 전신 신뢰도 | 코, 양 눈·어깨·팔꿈치·손목·힙·무릎·발목·발끝 총 17점이 모두 존재하고 inFrameLikelihood >=0.65. 좌표/신뢰도는 유한한 값이어야 함. |
| 머리 여유 | 머리 꼭대기 랜드마크가 없어 코 위로 몸통 길이의 35% 여유를 추정. 몸통 길이는 어깨 평균 y~힙 평균 y. |
| 너무 가까움 | 추정 머리 위~아래쪽 발끝 높이가 미리보기 높이의 86% 초과. |
| 프레임 포함 | 핵심 관절 및 추정 머리 위/발끝이 화면 경계 내에 있어야 함. |
| 너무 멂 | 전신 높이 비율 60% 미만. |
| 가이드 정렬 | 가이드 x=12~88%, y=6~94%. 머리/발끝은 가이드 세로 경계 내, 모든 관절은 가이드 가로 경계 내. 힙 중심은 화면 중앙 ±10% 이내. |
| 정면 근사 | 어깨 폭/몸통 길이 >=0.55, 힙 폭/몸통 길이 >=0.25. 어깨·힙 좌우 높이 차이/어깨 폭 <=0.20. 코와 어깨 중심의 x 차이/어깨 폭 <=0.25. 양 눈 폭/어깨 폭 >=0.12. |
| 양팔 간격 | 양 팔꿈치와 손목이 각각 같은 쪽 어깨~힙 선보다 바깥으로 어깨 폭의 12% 이상 떨어져야 함. |
| 준비 완료 | 위 조건 모두 통과. 가이드가 초록색으로 바뀌고 준비 완료 문구/진행률 표시. |

Preview, ImageAnalysis, ImageCapture를 같은 ViewPort로 묶고 ImageProxyTransformFactory 및 CoordinateTransform으로 ML Kit의 회전된 이미지 좌표를 PreviewView 좌표로 변환한다. 화면에 보이지 않는 잘린 영역의 관절로 준비 판정하지 않도록 한다. PreviewView는 COMPATIBLE 모드를 사용한다.

## 자동 촬영 및 자원 관리

- 준비 조건을 연속 1,500ms 만족한 새 포즈 결과에서만 촬영한다. 독립적인 타이머 만료만으로 촬영하지 않는다.
- 조건 실패, 프레임 간격 700ms 초과, 분석 처리 700ms 초과, 앱 일시 정지/화면 크기 변경 시 누적 시간을 초기화한다.
- 200ms 주기로 최신 결과가 700ms 넘게 없으면 준비 UI를 초기화한다.
- 수동·자동 촬영은 같은 `tryLock()`을 통과해야 한다. 사진 저장 중 및 저장 완료 후 잠금을 유지해 중복 촬영을 막는다.
- 저장 실패 시 잠금을 풀되 오류 안내를 유지하여 자동 재시도를 반복하지 않는다. 수동 버튼으로 재시도하거나 카메라를 다시 열 수 있다.
- 앱이 일시 정지된 동안 성공한 사진은 복귀 시 한 번 전달한다. 화면을 닫은 뒤 도착한 콜백은 탐색하지 않고 해당 임시 사진을 정리한다.
- KEEP_ONLY_LATEST로 분석 프레임이 쌓이지 않게 한다. ML Kit Task 완료 후 ImageProxy를 닫는다. null 입력 및 동기 예외에서도 닫는다.
- 화면 종료 시 해당 화면의 use case만 unbind, analyzer 해제, detector 해제. 진행 중 ML Task가 있다면 완료 후 detector를 닫는다.
- 일부 기기에서 Preview+Capture+Analysis 조합 바인딩이 실패하면 기존 Preview+Capture 조합으로 수동 촬영을 제공한다.

## 범위 및 한계

- cm 변환, 거리 센서/카메라 보정, DB 저장, 서버 분석 연동은 구현하지 않았다.
- 후면 카메라를 유지했으며 카메라 전환 기능은 추가하지 않았다.
- 자세 조건 유지 여부를 검사한다. 광학적 흔들림/초점 품질을 정량 평가하는 기능은 아니다.
- 단일 인물 포즈 모델이므로 화면 내 여러 사람의 수를 검증하지 못한다. 촬영 대상 한 명만 화면에 두어야 한다.
- 정면/팔 벌림/머리 경계는 랜드마크 기반 근사이다. 등 돌림, 헐렁한 옷, 가림, 어두운 조명에서 오판 가능성이 있다.
- accurate 모델을 재사용하므로 저성능 기기에서 700ms 조건을 만족하지 못할 수 있다. 이런 경우 수동 촬영을 사용할 수 있다. 새 라이브러리를 추가하지 않았다.
- 실제 기기 카메라 동작과 임계값의 체형별 적합성은 별도 검증이 필요하다.

## Android Studio 테스트

1. `fashion_app` 폴더를 Open. 기존 프로젝트 요구 버전은 AGP 9.1.1, Gradle 9.3.1, Gradle daemon JDK 21, compileSdk 37, minSdk 24. SDK 경로를 Android Studio에서 설정하고 Sync.
2. `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 실행.
3. 실제 Android 기기에 설치하고 체형 분석 → 사진 촬영하기 진입. 권한 허용/거부/거부 후 설정에서 허용하고 복귀를 각각 확인.
4. 사람이 없을 때, 발이 잘릴 때, 너무 가까울 때/멀 때, 좌우로 치우쳤을 때 안내와 자동 촬영 방지 확인.
5. 옆으로 서기, 어깨 기울이기, 얼굴 돌리기, 한 팔/양팔 붙이기를 각각 시험. 상태가 적절히 바뀌는지 확인.
6. 가이드 중앙에서 전신/정면/양팔 벌림을 만족하면 초록 가이드 및 진행률 확인. 약 1.5초 유지 시 한 장만 촬영되고 기존 체형 분석 결과로 이동하는지 확인.
7. 약 1초 준비 후 팔을 붙이거나 화면 밖으로 이동. 다시 자세를 맞춰도 이전 시간이 누적되지 않고 처음부터 진행하는지 확인.
8. 자동 촬영 직전 수동 버튼을 연속 터치해도 사진/화면 이동이 중복되지 않는지 확인. 조건 미충족 상태에서도 수동 촬영이 되는지 확인(기존 사진 분석에서 부적합 오류가 날 수 있음).
9. 준비 중 홈 버튼 → 복귀, 화면 회전, 뒤로 가기/닫기 → 재진입을 반복. 백그라운드 촬영, 남은 카메라 점유, 이전 결과에 의한 즉시 촬영이 없어야 함.
10. 촬영 완료 후 기존 비율·체형·스타일 조언 화면과 갤러리 사진 분석이 유지되는지 확인.
11. 세로/가로 화면에서 인물을 가이드 경계로 이동시켜 실제 미리보기 잘림과 안내가 일치하는지 확인. 여러 성능/화면비 기기에서 반복.

## 참고 API 문서

- https://developers.google.com/ml-kit/vision/pose-detection/android
- https://developer.android.com/reference/androidx/camera/view/transform/ImageProxyTransformFactory
- https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform

## 검증 결과

2026-10-05 최종 수정본에서 다음 명령이 BUILD SUCCESSFUL로 완료되었다.

`gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`

- Debug APK 생성: `app/build/outputs/apk/debug/app-debug.apk`
- 단위 테스트: 새 CaptureGuideTest 9개 + 기존 ExampleUnitTest 1개, 총 10개 통과.
- Lint: 오류 0개. 기존 의존성 버전/기타 화면 등 경고 및 상태 boxing 관련 힌트는 보고서 참조.
- Lint 보고서: `app/build/reports/lint-results-debug.html`
- 원본 ZIP의 기존 파일 292개를 SHA-256으로 비교: CameraPreviewScreen.kt와 AndroidManifest.xml만 변경됨. 기존 체형 분석/MediaPipe/Gradle 의존성 파일은 원본과 동일.
- 이 PC의 기존 Android SDK를 사용했고, 프로젝트에 필요한 API 37 플랫폼을 설치했다. PC 전용 local.properties는 배포 소스에 추가하지 않았다.
- 실제 카메라 기기 테스트는 수행하지 않았음. 위 실기기 테스트 절차를 반드시 별도 수행해야 한다.


