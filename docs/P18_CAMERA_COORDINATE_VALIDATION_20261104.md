# P18 사진 방향·좌표 검증 — 2026-11-04

## SAFEPOINT 01 — 완료

- P17 브랜치 `codex/fashionai-p17-camera-personal-vton-20261104`와 기존 `p16-device-20261104` worktree를 그대로 사용한다. 재개 HEAD `71ad5367db68d7fe173582831b9cf4bcfd6108c0`, Git clean, C: 23,255,166,976 bytes. 적용되는 AGENTS.md를 상위 경로/작업 폴더에서 찾지 못했다. P17 인수인계와 비공개 증거 폴더는 보존했다.
- 사용자가 이번 단계는 **합성 좌표 검증만** 선택했다. 카메라·갤러리를 열거나 실기기 사진을 선택하지 않는다. 유효 전신 분석·촬영 사진 방향·실측 정확도는 미검증이다.
- 실행 코드 기준은 `94e4e3a7b5b0a5017f934e50ab840260b067fba7`, Preview 17901. P17에서 완료한 촬영·기존 AI 결과 조회/저장을 반복하지 않는다.

| 경계 | 실제 연결 코드 | 기존 근거 | 추가 검증 공백 |
|---|---|---|---|
| 촬영/미리보기 | CameraPreviewScreen: DEFAULT_BACK_CAMERA, 공통 ViewPort, capture/analysis/preview의 targetRotation | P17 가로 UI·실촬영·복귀 | 실제 센서/EXIF 사진의 픽셀 방향은 별도 승인 입력 필요. 전면 카메라 미지원 |
| 실시간 관절 → 화면 | ML Kit에 imageInfo.rotationDegrees, ImageProxyTransformFactory(rotation=true/crop=false) → CoordinateTransform → CaptureGuide | 동일 PoseQualityRules 및 crop 정책 코드 | 90/270도 전체 회전 좌표, 비대칭 crop 경계와 바깥 점의 계약 검사 추가 필요 |
| 저장 사진 → 분석 bitmap | BodyAnalysisViewModel.loadBitmap: API28+ ImageDecoder EXIF 처리, 이전 API BitmapFactory+ExifInterface Matrix; 최대 축 기준 sampling | TryOnImagePreparationTest는 별도 피팅 저장 경로만 검사 | 체형 분석의 실제 디코더에서 90/270·EXIF 미러·이미 회전된 픽셀·sampling 검증 필요 |
| bitmap → ML/실루엣 | InputImage.fromBitmap(bitmap,0); BodySegmenter는 같은 bitmap, mask를 별도 X/Y 비율로 환산 | SilhouetteReliabilityTest의 대칭/반복/품질 검사 | 실제 인물 검출·경계 정확도는 합성 좌표 시험으로 대체 불가 |
| 결과 사진/측정선 표시 | Coil AsyncImage Fit, BodyMeasurementOverlay의 bodyImageRect, 정규화된 guides | P7MeasurementTest: 세로/가로 letterbox·잘못된 크기; 기존 guides 검증 | Coil 표시용 디코딩과 분석 디코딩의 EXIF 방향/비율 일치 추가 검사 필요 |
| 결과 → 3D | 유효 cm만 AvatarSetupCard(autoImportPhoto=true)에 전달, 명시적 비교/취소 | BodyAnalysisResultUiTest: 비교 취소·치수 없으면 진입 차단 | 실제 유효 전신 입력에서의 연결은 미검증 |

누락된 테스트를 곧바로 결함으로 분류하지 않았다. 현재까지 재현된 실행 코드 결함 없음.

CameraX 계약 해석 참고: [공식 ImageProxyTransformFactory 문서](https://developer.android.com/reference/androidx/camera/view/transform/ImageProxyTransformFactory), [AndroidX 구현](https://android.googlesource.com/platform/frameworks/support/+/c10b42ccfc02469ae8a34a33e37eda2da8e34479/camera/camera-view/src/main/java/androidx/camera/view/transform/ImageProxyTransformFactory.java). 실제 테스트는 프로젝트의 고정 CameraX 1.4.2 라이브러리를 사용한다. crop을 무시한 전체 회전 이미지 좌표와 crop 원점 기준 좌표를 구분한다.

## SAFEPOINT 02 — 진행 예정

개인 사진 대신 서로 다른 색의 네 모서리가 있는 합성 JPEG와 수학적으로 지정한 좌표를 사용한다. 예상 방향은 고정된 색 순서 표, 예상 좌표는 손으로 계산한 경계값으로 정한다. 검증할 함수를 다시 호출해 기대값을 만들지 않는다. 기존 letterbox 단위 테스트나 전체 CI를 무조건 반복하지 않는다.

## SAFEPOINT 03 — 이번 범위에서 대기

사용자가 합성 검증만 선택했다. 새로운 전신 입력 승인이나 카메라 촬영은 요청/실행하지 않는다. 개인 사진 VTON의 승인된 CUDA 실행 환경 부재와 실제 16KB 실행 미검증은 P17 상태 그대로이며 후보 조사·다운로드·설정 준비를 반복하지 않는다.
