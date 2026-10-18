# 인체분석 2차 작업: 키 기준 cm 추정

정면 사진의 픽셀을 입력 키로 보정한 **cm 단위 추정값**을 추가했다. 줄자 실측, 정밀 계측 또는 검증된 오차율을 의미하지 않는다. 1차 CameraPreviewScreen / CaptureGuide / 자동 촬영 테스트는 변경하지 않았다.

## 1. 작업 전 체형 측정 구조

Android 단일 app 모듈의 MainActivity → AppNavigation → BodyAnalysisScreen 흐름이다. 촬영 사진은 CameraPreviewScreen이 URI를 반환하고 AppNavigation이 기존 BodyAnalysisViewModel.analyzeImage(context, uri)를 호출한다. 갤러리 사진도 같은 분석 함수를 사용한다.

기존 BodyAnalysisViewModel은 ML Kit AccuratePoseDetector의 SINGLE_IMAGE_MODE로 관절 좌표·신뢰도를 얻고, BodySegmenter는 어깨/허리/힙 높이의 마스크 폭을 얻었다. 폭 측정이 없으면 관절 가로폭과 허리 78% 근사로 기존 체형 분류를 수행했다. 결과는 BodyAnalysisResult, 상태는 BodyAnalysisUiState로 관리했다.

확인한 문제/제약:

- ServerProfile, MyPageScreen 및 로컬 저장 코드에 사용자 키 필드가 없었다.
- 포즈와 분할이 서로 다른 이미지 로더를 사용했다. 특히 Android 24~27의 BitmapFactory 경로는 EXIF 방향을 따로 보정하지 않았다.
- 기존 BodyWidths는 마스크 픽셀 폭만 가지고 있었다. 원본 이미지의 세로 픽셀과 무조건 혼합하면 안 된다.
- 기존 코의 y~발목 y는 다리 비율을 위한 근사값으로, 실제 키 스케일의 분모로 재사용할 수 없다.
- 번들 selfie_segmenter.tflite의 내장 labels.txt는 단일 `selfie`이다. 기존 다중 카테고리 주석/비제로 카테고리 가정을 cm 측정의 근거로 삼지 않고, 해당 단일 사람 채널의 confidence mask를 읽도록 했다.

## 2. 수정한 파일 목록

기존 파일 3개 수정:

- app/src/main/java/com/jeiu/fashionproject/ui/screens/BodyAnalysisScreen.kt
- app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/BodyAnalysisViewModel.kt
- app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/BodySegmenter.kt

추가 파일 5개:

- app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/BodyMeasurementCalculator.kt
- app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/SilhouetteMeasurer.kt
- app/src/test/java/com/jeiu/fashionproject/BodyMeasurementCalculatorTest.kt
- app/src/test/java/com/jeiu/fashionproject/SilhouetteMeasurerTest.kt
- BODY_MEASUREMENT_PHASE2.md

CameraPreviewScreen, CaptureGuide, AppNavigation, Manifest, Gradle 의존성, 모델 파일, backend는 이번 작업에서 변경하지 않았다.

## 3. 각 파일의 변경 내용

| 파일 | 변경 내용 |
| --- | --- |
| BodyAnalysisScreen | 분석 전 키 입력/검증 및 촬영·갤러리 시작 조건. 기존 결과 카드 뒤에 cm 추정 카드 추가. 기존 특징·스타일 조언·비율 막대·다시 분석 흐름 유지. cm 불가 시 재시도 버튼 제공. |
| BodyAnalysisViewModel | SavedStateHandle에 키 입력 보관. 이미지 하나를 방향 보정하여 두 모델에 공유. 백그라운드 디코딩/마스크 측정, 작업 취소 및 native 자원 정리. 기존 BodyAnalysisResult에 measurements, measurementIssue, waistToHip 추가. |
| BodySegmenter | 기존 모델/클래스를 재사용하고 단일 사람 confidence mask 추출. 순수 Kotlin 측정기에 좌표와 마스크 전달. 마스크·MPImage 정리 및 동시 실행/close 직렬화. |
| BodyMeasurementCalculator | 기존 BodyWidths를 같은 패키지로 이동·확장. BodyMeasurements 값 객체, 키 검증, cm 변환과 수치 범위 검사. Android/UI 의존성 없음. |
| SilhouetteMeasurer | 사람 연결 영역, 몸통 폭 밴드, 머리~발 전체 높이, 신뢰도·잘림·정면·팔·곧게 선 다리 검사. 마스크/비트맵 해상도 변환. Android/UI 의존성 없음. |
| 두 테스트 파일 | cm 환산/잘못된 값 10개, 합성 마스크·포즈 11개 테스트. |

## 4. 사용자 키를 가져오는 위치

BodyAnalysisScreen의 `키 (cm)` 입력 → BodyAnalysisViewModel.heightInput → SavedStateHandle["body_height_cm"].

지원 범위는 100~230cm, 소수점 첫째 자리까지다. 이 패션 앱 시제품의 청소년/성인 대상 검증 범위이며 의학적인 정상 키 범위가 아니다. 아동·범위 밖 신장에 대한 보정은 별도 검증이 필요하므로 임의로 통과시키지 않는다. 이 이유는 계산기 코드에 주석으로 남겼다.

빈 값, 문자, 지수 표기, 음수, NaN, Infinity, 범위 밖 값을 거부한다. 숫자 입력 중 `170.` 같은 미완성 문자열은 편집할 수 있으나 분석은 시작되지 않는다. UI에서 촬영·사진 선택을 비활성화하고, ViewModel과 계산기에서도 다시 검증한다.

화면 회전/카메라 이동/시스템 saved state 복원에서 키가 유지된다. DB·프로필·SharedPreferences에 영구 저장하지 않는다. 분석을 시작할 때 키를 스냅샷으로 읽어 도중 변경과 섞이지 않게 한다.

## 5. 전체 신체 높이(pixel)와 좌표계

1. 사진을 한 번 디코딩한다. Android 28+는 ImageDecoder의 방향 처리를 사용하고, 24~27은 기존 의존성에 포함된 AndroidX ExifInterface와 Matrix로 EXIF 회전·반전 8방향을 처리한다.
2. 메모리 부담을 줄이기 위해 긴 변 약 2048px 이하로 샘플링한다. 이후 사용되는 모든 pose 좌표는 이 **방향 보정된 분석 비트맵**의 픽셀이다. PreviewView/Compose 크기는 전혀 사용하지 않는다.
3. ML Kit에는 InputImage.fromBitmap(bitmap, 0), MediaPipe에는 바로 그 bitmap을 전달한다. EXIF 회전을 두 번 적용하지 않는다.
4. 단일 selfie confidence mask의 확률 >=0.70을 전경으로 사용한다. 분석용 마스크 순회는 긴 변 최대 512칸으로 제한한다.
5. 어깨/힙 사이 몸통 중앙에서 시작해 4방향으로 연결된 사람 영역만 선택한다. 떨어진 작은 잡음은 전체 높이에 포함하지 않는다. 다른 전경이 본체 면적의 5%를 넘으면 cm 계산을 거부한다.
6. 해당 영역의 가장 위 행과 가장 아래 행의 경계가 머리 위~발바닥 후보이다. `bodyHeightPx = (maxY + 1 - minY) × bitmapHeight / maskHeight`.
7. 포즈의 코, 어깨, 힙, 무릎, 발목, 발끝이 같은 영역에 대응하는지 확인한다. 마스크 머리 경계와 코, 발끝 경계와 포즈 발끝도 일치해야 한다.

가로폭은 `widthPx = widthInMask × bitmapWidth / maskWidth`. 가로와 세로 스케일을 각각 적용하므로 마스크가 정사각형이거나 이미지와 다른 크기여도 단위를 혼합하지 않는다. 전체 높이와 각 폭은 모두 동일 분석 비트맵 좌표계로 반환한다.

## 6. pixel → cm 공식

```
cmPerPixel = heightCm / bodyHeightPx
shoulderWidthCm = shoulderWidthPx * cmPerPixel
waistWidthCm = waistWidthPx * cmPerPixel
hipWidthCm = hipWidthPx * cmPerPixel
legLengthCm = (ankleCenterY - hipCenterY) * cmPerPixel
torsoLengthCm = (hipCenterY - shoulderCenterY) * cmPerPixel
```

예: 키 170cm, 전체 높이 850px → 0.2cm/px. 어깨 209px → 41.8cm, 허리 156px → 31.2cm, 엉덩이 198px → 39.6cm.

## 7. cm 항목과 데이터 모델

기존 BodyAnalysisResult를 확장했으며 별도의 중복 체형 분석 결과 모델을 만들지 않았다.

```
BodyAnalysisResult
  기존 bodyTypeKo, shoulderRatio, waistRatio, hipRatio, legRatio,
       characteristics, styleAdvice, confidence, shoulderToHip 유지
  waistToHip: Float
  measurements: BodyMeasurements?  // 검증 실패 시 null
  measurementIssue: String?

BodyMeasurements
  heightCm
  shoulderWidthCm
  waistWidthCm
  hipWidthCm
  legLengthCm?       // 힙 관절 중심~발목 중심의 세로 투영 길이
  torsoLengthCm?     // 어깨 중심~힙 중심의 세로 투영 길이
  cmPerPixel
  measuredBodyHeightPx
  measuredAt        // Unix epoch milliseconds
```

허리 높이는 기존과 같은 어깨~힙의 60% 지점이다. 어깨/허리/힙 높이 주변 ±1%, ±0.5%, 중앙의 최대 5행을 평균한다. 각 행은 몸통 중심을 포함하는 연속 전경만 측정해 떨어진 팔·손을 폭에 합치지 않는다. 3행 미만 또는 최대/최소 폭 비율 >1.35이면 마스크 측정을 거부한다.

UI는 소수점 첫째 자리 cm, 비율은 둘째 자리로 표시한다. 허리둘레/엉덩이둘레 값은 생성하지 않는다. 다리·상체 길이는 재단 치수나 관절을 따라 잰 길이가 아닌 명시된 두 지점의 세로 투영 길이다.

## 8. 기존 분류와의 연결

calculateBodyType의 분기/비율 계산은 유지했다:

- 어깨/힙 >=1.08: 역삼각형.
- 어깨/힙 <=0.92: 삼각형.
- 나머지 중 허리/(어깨·힙 평균) <=0.80: 모래시계형.
- 나머지: 직사각형.
- 기존 최대 폭 기준 비율 막대와 코~발목 기반 다리 비율 계산도 유지.

다만 분류에 공급되는 세그멘테이션 폭은 올바른 사람 확률 채널과 몸통 구간에서 얻으므로, 기존 카테고리/팔 포함 방식과 **결과 숫자가 같다고 보장하지 않는다**. 분류 알고리즘을 교체한 것은 아니다. 분할 폭 자체가 없으면 기존 포즈 기반 분류 폴백을 유지한다.

cm 계산은 이 폴백 값을 사용하지 않는다. 분류에 충분하지만 cm 보정에는 부족한 사진에서는 체형·비율·조언을 유지하면서 cm 카드에 재촬영 안내를 표시한다. 기존 포즈 검증부터 실패하면 기존 Error 화면으로 이동한다. 기존 confidence는 체형 분석의 포즈 기반 점수로, cm 오차율이나 계측 신뢰도로 재정의하지 않았다.

## 9. 신뢰할 수 없는 결과 차단

- 키: 유한한 숫자, 100~230cm.
- 포즈: 코/양 눈·어깨·팔꿈치·손목·힙·무릎·발목·발끝 총 17점, 유한한 좌표와 0.75~1.0 신뢰도. 비트맵 안에 있어야 한다.
- 분할: 단일 사람 확률 채널, 유효한 버퍼/확률, 몸통 중앙 연결 영역 및 주요 관절의 전경 대응.
- 잘림: 사람 연결 영역이 네 가장자리에서 최소 2칸 또는 1%만큼 떨어져 있어야 한다. 다른 전경이 본체의 5%를 넘으면 거부.
- 높이: 마스크 높이 80칸 이상, 분석 이미지 높이의 50% 이상, 분석 비트맵 200px 이상. 0·음수·NaN·Infinity 거부.
- 머리~발 대응: 코는 마스크 위에서 전체 높이의 2.5~16% 아래, 마스크 바닥은 포즈 발끝과 약 2칸~전체 높이의 5% 범위에서 일치.
- 정면/직립: 어깨 폭/몸통 >=0.55, 힙 폭/몸통 >=0.25, 어깨·힙 기울기 <=어깨 폭의 20%, 코 중심과 눈 간격, 몸통 좌우 기울기 검사.
- 팔: 팔꿈치·손목은 어깨 아래로, 몸통 선보다 어깨 폭의 12% 이상 바깥으로 벌려야 함. 머리 위로 올린 팔은 허용하지 않음.
- 다리: 무릎이 힙~발목 길이의 25~75% 위치에 있고, 힙~발목 선에서 가로로 몸통 길이의 12% 이상 벗어나면 거부. 과도한 다리 벌림/기울기 거부.
- 폭 산출: 어깨 마스크 폭은 포즈 어깨 폭의 0.8~1.5배. 행별 폭 변동이 크면 거부.
- cm 범위: 어깨 15~75, 허리 12~80, 힙 15~85cm. 키 대비 폭도 각각 0.12~0.40, 0.09~0.45, 0.12~0.45 범위여야 함.
- 선택 길이: 다리/키 0.25~0.65, 상체/키 0.15~0.40. 명시된 길이가 음수/NaN/Infinity 등 잘못된 수치이면 결과 전체를 거부.
- 값을 임의로 clamp하거나 cm 허리를 78%로 만들어 내지 않는다. 내부 검증 후 UI도 유한한 양수만 포맷해 NaN cm/Infinity cm/음수 cm 표시를 차단한다.

이 기준은 시제품의 보수적 거부 규칙이며 실측으로 검증된 정확도 기준은 아니다. 높이 정보만으로 원근 왜곡·의복 두께·카메라 기울기·머리카락·신발 높이를 완전히 제거할 수 없다. 번들 selfie 모델이 전신/발 분할에 실패하면 cm를 숨긴다. 여러 사람이 겹쳐 연결된 경우까지 완전하게 구분하는 기능은 아니다.

## 10. 새 단위 테스트

BodyMeasurementCalculatorTest: 10개.

- 정상 170cm / 850px 환산 및 측정 시각.
- 키 변경 시 전체 길이의 비례 변화.
- 전체 높이 0·음수·작은 값·NaN·Infinity.
- 각 너비 0·음수·작은 값·큰 값·NaN·Infinity.
- 세그멘테이션 없음/불신뢰 및 포즈 폴백의 cm 사용 방지.
- 잘못된 사용자 키와 입력 형식/지원 경계.
- 잘못된 선택 길이와 선택 길이 누락.
- 비트맵 축소 후 cm 불변.
- 극단적으로 크거나 작은 유한한 수치 방어.

SilhouetteMeasurerTest: 11개.

- 머리 위~발바닥 높이를 비트맵 픽셀로 환산하고 별도 팔을 폭에서 제외.
- 비트맵 해상도 변경 및 비정사각형 마스크의 x/y 스케일 검증.
- 머리/발 잘림, 잡음, 두 번째 전경, 발 누락/저신뢰도.
- 마스크·포즈 불일치, 빈 마스크/역전된 전경/잘못된 버퍼/NaN.
- 옆모습·팔 붙임·구부린 다리 거부.

기존 CaptureGuideTest 9개와 ExampleUnitTest 1개도 함께 실행한다. 합성 마스크 테스트는 계산/거부 로직 테스트이며 실제 모델 정확도를 검증한 것은 아니다.

## 11. 빌드 / 테스트 / Lint 결과

2026-10-05 최종 소스에서 다음 명령이 BUILD SUCCESSFUL로 완료되었다.

`gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`

- assembleDebug: 성공. `app/build/outputs/apk/debug/app-debug.apk` 생성.
- testDebugUnitTest: 총 31개 통과, 실패/오류 0개. 새 계산 10개 + 마스크 11개 + 기존 가이드 9개 + 기존 예제 1개.
- lintDebug: 성공, 오류 0개. 상세 경고는 `app/build/reports/lint-results-debug.html` 참조.
- 기존 AndroidX ExifInterface 의존성을 재사용해 별도의 라이브러리 추가 없이 EXIF 처리를 컴파일했다.
- 실제 Android 기기의 카메라/갤러리/EXIF 통합 동작 및 실제 사람의 cm 오차는 아직 검증하지 않았다.

## 12. 구현하지 않은 기능

Supabase/DB 저장, FastAPI·로그인/JWT 수정, 측면 사진, 허리/가슴둘레 추정, 사이즈 추천, 쇼핑몰 연동, 새 AI 모델, 1차 카메라 가이드 재작성은 하지 않았다. 모델·라이브러리 버전도 변경하지 않았다. cm 정확도 보정, 체형별 오차 통계, 실기기 EXIF/카메라 통합 테스트는 후속 검증이 필요하다.

## 13. 실제 Android 기기 테스트 절차

1. Android Studio에서 fashion_app 폴더를 열고 기존 환경(compileSdk 37, Gradle daemon JDK 21)으로 Sync/실행한다.
2. 키 빈 값일 때 촬영·갤러리 선택이 비활성화되는지 확인. 99/231은 거부, 159/170/170.5는 허용되는지 확인. 문자/NaN/Infinity/음수 붙여넣기 시 시작할 수 없어야 한다.
3. 키 170 입력 후 촬영 화면 진입. 1차 가이드·1.5초 자동 촬영·수동 촬영·중복 방지·닫기·회전·백그라운드 복귀가 그대로인지 확인.
4. 밝은 배경에서 몸에 붙는 옷을 입고, 맨발로 정면·직립·팔 벌림 상태를 유지한다. 머리와 발 아래에 여백을 둔다. 성공 시 어깨/허리/엉덩이 cm와 입력 키가 표시되어야 한다.
5. 동일한 갤러리 사진을 키 170과 159로 각각 분석한다. cm 값은 약 159/170배가 되고 기존 비율과 체형은 동일해야 한다(같은 이미지 추론의 미세 차이는 별도 관찰).
6. 머리/발을 자른 사진, 옆모습, 어두운 사진, 팔 붙임, 구부린 무릎, 두 사람이 있는 사진을 시험한다. 부적합 포즈는 기존 Error, cm 근거 부족은 cm 안내/재시도이며 숫자를 억지로 표시하지 않아야 한다.
7. 세로/가로 촬영 JPEG와 EXIF 회전·반전 샘플을 갤러리에서 분석한다. Android 24~27과 28+ 기기에서 각각 확인. 사진 방향이 잘못되거나 이중 회전되면 안 된다. 동일 이미지의 방향 정규화/축소본에서 cm가 크게 달라지지 않아야 한다.
8. 모델 누락 검증이 필요하면 별도 테스트 복사본에서 모델 파일을 임시 제외하고 빌드한다. cm는 표시되지 않고 기존 포즈 기반 분류와 재시도 안내가 동작해야 한다. 검증 후 원본 모델을 복원한다.
9. 분석 중 화면 회전/앱 종료/빠른 재분석을 시험해 오래된 작업 결과가 새 상태를 덮거나 카메라를 점유하지 않는지 확인. 카메라에서 복귀한 뒤 키가 유지되어야 한다.
10. 최소 3개 거리에서 같은 인물을 촬영하고 cm 변동을 기록한다. 정면 투영 너비와 비교할 수 있는 실측 기준으로 여러 사람/의복 조건의 오차를 확인한다. 줄자로 잰 허리둘레와 비교하지 않는다.
11. 결과의 기존 비율 막대, 체형 특징, 스타일 조언, 다시 분석하기가 유지되는지 확인한다. 모든 숫자에 NaN/Infinity/음수가 없어야 한다.

## 참고 API 자료

- MediaPipe confidence mask 및 출력 좌표: https://developers.google.com/edge/api/mediapipe/java/com/google/mediapipe/tasks/vision/imagesegmenter/ImageSegmenter
- 모델 출력 확률 마스크: https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MediaPipe%20Selfie%20Segmentation.pdf
- ImageDecoder: https://developer.android.com/reference/android/graphics/ImageDecoder

## 1차 카메라 보존 확인

작업 전후 SHA-256이 동일하다.

- CameraPreviewScreen.kt: BB403CB38AAECEF7629C1DE8BDFF3AB760F39D44406257263966CD94F8D12556
- CaptureGuide.kt: A15D543479A91160E4BD0B56D4777ED42416E8EDAEE399DF7428D3D950F84572

