# P18 사진 방향·좌표 검증 — 2026-11-04

## 최신 상태 — 사용자 요청으로 WIP 체크포인트 저장

**최종 테스트 통과/배포 완료 상태가 아니다.** 사용자가 현재까지의 작업 저장을 요청했다. 앱 실행 코드·의존성·설치 APK는 변경하지 않았고, 추가한 합성 테스트 두 파일과 이 인수인계를 기존 P17 브랜치에 저장한다. 마지막 집중 검사 `focused-04`는 저장 시점에 실행 중이다. 새 개발이나 전체 CI/APK 빌드는 시작하지 않는다.

- 이전 완료 체크포인트: `5f3ad4215d99daedd1fbcea3958dfa7a82206407` (SAFEPOINT 01 문서, 원격 일치 확인됨).
- 이번 저장 파일: 이 문서, `BodyPhotoCoordinateTest.kt`, `CameraPreviewCoordinateTest.kt` (둘 다 `fashion_app/app/src/test/java/com/jeiu/fashionproject/`). 실행 코드와 UI 변경 없음.
- 미검증 테스트 체크포인트는 커밋 제목에 **WIP / [skip ci]**를 명시한다. 전체 APK CI를 새로 유발하거나 검증된 배포라고 보고하지 않는다.
- 저장 직전 C: 여유 공간 22,942,744,576 bytes. 기존 P15/P16/P17 증거·APK·백업은 그대로다. P18에서 ADB/카메라/갤러리를 사용하지 않았으며, 새 사용자 데이터 해시 비교를 수행한 것처럼 주장하지 않는다.

### 실제 검사 결과와 미해결 상태

| 실행 | 실제 결과 | 해석 |
|---|---|---|
| focused-01 | 9개 중 4 통과, 5 실패 | CameraX 크롭 좌표 1개 및 API27 분석 디코더 3개 통과. 현대 ImageDecoder 파일 디스크립터 경로는 JVM에서 `Only supported on Android` 오류; Coil API27도 실패. 앱 회전 결함으로 단정하지 않음 |
| focused-02 | 테스트 실행 전 Gradle 캐시 생성 오류 | 공간 부족 아님. 기존 캐시는 삭제하지 않음. 해당 위치에 새 probe 디렉터리/파일 생성·읽기 성공 후 그 probe만 제거 |
| focused-03 | 수정한 테스트 입력 연결에도 실패 남음 | API27의 Windows file URI가 `file://C%3A%5C...` 형태가 되어 Coil fetcher가 거절. API35 custom shadow에서 native 호출 설정 누락으로 decoder null 확인. 원인과 실패 XML 보존 |
| focused-04 | **실행 중 / 최종 결과 미확인** | Coil에는 동일 합성 JPEG 바이트를 제공하고, source-only shadow의 `callNativeMethodsByDefault=true`를 지정. 픽셀/EXIF/치수/좌표 기대값은 유지. 마지막 수정의 통과 여부를 먼저 확인해야 함 |

테스트가 직접 만드는 네 모서리 색 JPEG만 사용한다. API27 기존 분석 디코더는 실제 private loadBitmap을 호출한다. API35에서는 JVM이 지원하지 않는 Android FD I/O만 동일 JPEG ByteBuffer로 연결하며 native 디코딩·production sampling 설정을 사용한다. Coil도 같은 encoded bytes로 비교한다. 이 구성은 **Android URI/FD 읽기·실제 촬영 사진·실제 신체 분석 성공을 검증하지 않는다**. 지원하지 않는 환경 오류를 앱 변경으로 우회하거나 기대 좌표를 완화하지 않았다.

### 실행 중 작업 / 다음 세션의 정확한 재개 순서

1. 기존 P17 브랜치/status를 확인한다. 원격으로 강제 reset하거나 P18 브랜치를 새로 만들지 않는다.
2. `fashion_app/build/p18-private/focused-04.log`와 Java/Gradle 실행 상태를 먼저 확인한다. 같은 대화에서 exec session `56758`이 유효하면 해당 세션의 완료 출력을 조회한다. **실행 중이면 동일 검사를 중복 시작하지 않는다.** 저장 당시 Gradle daemon PID 17620 / wrapper PID 26208이었으나 종료·재사용될 수 있으므로 PID만 보고 프로세스를 종료하지 않는다.
3. 종료됐다면 `fashion_app/build/p18-private/run04-xml/`의 최종 XML과 로그의 BUILD 결과를 읽는다. 아직 `focused-04` 성공으로 기록한 문서는 없다. 실패 시 실제 원인만 수정하고 별도 run05 경로를 사용한다. 성공한 CameraX 검사는 불필요하게 반복하지 않는다.
4. 필요한 경우에만 `fashion_app`에서 JAVA_HOME을 Android Studio JBR로 설정하고 아래와 같은 집중 명령을 사용한다. 새 실행에는 새 init/report/log 이름을 사용해 앞선 실패 증거를 덮어쓰지 않는다.

```powershell
.\gradlew.bat --offline --no-daemon --max-workers=1 -I build/p18-private/test-output04.init.gradle :app:testDebugUnitTest --tests 'com.jeiu.fashionproject.BodyPhotoCoordinateTest'
```

5. 최종 테스트 결과를 이 문서에 갱신하고, 실행 코드 무변경이면 동일 17901 APK를 재빌드/재설치하지 않는다. 전체 CI 기준은 기존 P16 41726982899이며 이번 WIP의 CI 성공을 뜻하지 않는다.
6. 사용자는 이번에 합성 검사만 선택했다. 전신 사진 검증·개인 VTON·16KB 실기기 작업을 임의로 시작하지 않는다.

비공개 로그/보고서: `fashion_app/build/p18-private/{focused-01.log,focused-02.log,focused-03.log,focused-04.log,run01-xml,run03-xml,run04-xml}`. run02는 테스트에 도달하지 않아 XML이 없을 수 있다. 첫 실행은 AGP 기본 test-results에 기록돼 run01-xml로 별도 보존했으며, 이후에는 projectsEvaluated 시점의 init script로 P18 전용 보고서 경로를 지정했다. 기존 P16/P17 비공개 보존 폴더는 수정하지 않았다. 이러한 로그·생성 이미지·기기 식별값·비밀정보는 Git에 추가하지 않는다.

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

## SAFEPOINT 02 — 진행 중 (최신 WIP 상태는 위 참조)

개인 사진 대신 서로 다른 색의 네 모서리가 있는 합성 JPEG와 수학적으로 지정한 좌표를 사용한다. 예상 방향은 고정된 색 순서 표, 예상 좌표는 손으로 계산한 경계값으로 정한다. 검증할 함수를 다시 호출해 기대값을 만들지 않는다. 기존 letterbox 단위 테스트나 전체 CI를 무조건 반복하지 않는다.

## SAFEPOINT 03 — 이번 범위에서 대기

사용자가 합성 검증만 선택했다. 새로운 전신 입력 승인이나 카메라 촬영은 요청/실행하지 않는다. 개인 사진 VTON의 승인된 CUDA 실행 환경 부재와 실제 16KB 실행 미검증은 P17 상태 그대로이며 후보 조사·다운로드·설정 준비를 반복하지 않는다.
