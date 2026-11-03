# P11 안정화 후속 — 2026-11-03

저장소 `jimnnieo-svg/capstone-integration-backup`. 원격 P11 `4f2b6b0067f2ddf06e74925b6e4f82ae9ce68c32`를 확인해 후속 브랜치 `codex/fashionai-p11-stability-final-20261103`를 생성했다. 기존 P11/P10/P8 브랜치, 사용자 데이터 및 APK를 보존했다. 주요 수정 SHA `643e419fa47e020acdb9bf98158bdcfd038f9e47`, 자동핏 무변화 안내 포함 코드 SHA `1e05e4af1bdb9b3f16cb2b770978a38b828b6fc8`.

## 실패 재현과 수정

원래 CI 41579873300은 Debug 빌드 후 Android 403개 중 5개 실패로 중단됐다. 해당 세 클래스의 로컬 실행으로 동일 5개 실패를 재현했다.

| 실패 | 원인과 수정 |
| --- | --- |
| BodyAnalysisResultUiTest.photoMeasurementOpensApprovalBeforeChangingSavedAvatar | P11에서 가져오기 버튼을 고급 설정으로 옮겼으므로 취소 후 고급 설정을 펼쳐 원래 가져오기 경로가 살아 있는지 검사. 승인 전 저장 방지 검사는 유지 |
| P9EditorUiTest.switchingBodyPartsPreservesEveryUnsavedMeasurementUntilExplicitSave | 중첩 스크롤에 있던 부위 선택이 줄자/버튼 조작 후 화면 밖으로 이동. 부위 선택 줄을 고정 영역에 배치. 기존 값 검사를 그대로 유지하고 저장 후 store 재로드 검사 추가 |
| P9EditorUiTest.rulerCanStillDragAfterMultipleBodyPartChanges | HIP 선택과 터치 수신은 정상인데 값이 99.5에서 그대로인 것을 진단 출력으로 확인. 로컬 함수 참조를 전달하던 콜백을 현재 body/selected를 명시적으로 캡처하는 람다로 변경. 이전 부위 상태가 남는 경로를 제거. 진단 출력은 삭제 |
| PresentationFittingUiTest.missingServiceDoesNotPromiseGenerationAndCanReturnToClothes | 서비스 미연결 안내가 개선됐지만 테스트는 과거 문구를 요구. 현재의 생성 불가 안내, 생성 비활성, 옷 선택 복귀를 함께 검사 |
| PresentationFittingUiTest.wandWithoutClothesShowsNoticeAndAlignmentRetainsManualScaleControls | 읽기 불가능한 테스트 사진에서 자동핏 실패 시 수동 배율 1.1 유지가 맞는 동작. 기존 1.0 초기화 기대를 수정하고 실패 안내도 검사 |

## 실제 코드 보완

- 숫자 줄자·0.5cm 스냅·직접 입력·±버튼·7부위·P10 무광 메시를 유지했다. 부위 선택을 고정하고 현재 초안/부위 콜백을 사용한다. 손상된 복원 초안을 초기 미리보기로 표시하더라도 저장/피팅 적용은 차단한다.
- 사진 검출의 늦은 완료가 의류를 자동 재배치하던 코드를 제거했다. 포즈 검출은 좌표 자료만 갱신하며, 사용자가 명시적으로 자동핏을 누를 때 배치한다. 모드 전환 중 다른 옷 배치를 건드리지 않는다.
- 자동핏 결과가 기존 위치·크기와 같으면 이미 맞춰진 상태라고 안내한다. 실제 이동이 없는데 이동했다고 표시하지 않는다.
- 자동핏은 의류별 필요한 유효 관절을 검사한다. 원피스에는 어깨·골반, 상의/겉옷에는 어깨, 하의에는 골반, 신발에는 발목을 요구한다. 지원되지 않는 액세서리를 기본 중앙으로 옮긴 뒤 성공으로 표시하지 않는다. 관절이 없으면 기존 배치를 유지한다.
- 홈/Theme/Type은 P8~P11 사이에 변경되지 않았다. 전체 확대의 기기 원인은 미확정이다. 코드에서 확인한 날씨 텍스트의 무제약 가로폭은 weight로 제한하고 버튼 폭도 제한했다. 시스템 글자 배율을 강제로 줄이지 않았다.
- 메시 계산은 기존 백그라운드/최신 요청 구조를 유지한다. GL 큐 실행 시에도 최신 요청인지 재검사해 큐 대기 중 오래된 메시가 적용되는 것을 막는다. 실 GPU 성능 수치는 측정하지 않았다.
- P11의 키·몸무게 → cm 편집 2단계, 사진·직접 입력 고급 설정, P8 분석 화면 정보 구조, AI 서비스 미준비/동의 조건을 유지한다. 자체 GPU 서버 없이 생성 가능하다고 표시하거나 유료 API로 자동 전환하지 않는다.

## 범위와 미검증

단위 테스트의 합성 관절 계산은 실제 사진의 ML Kit 검출 정확도나 실기기 배치 성공을 증명하지 않는다. 사진 모드 배치는 기존 ViewModel 세션 보존이며 프로세스 종료 후 사진/배치 자동 복원은 구현하지 않았다. 3D 프로필·아바타 의류 배치는 기존 별도 선택 저장/복원 경로를 유지한다. URI 권한 만료의 모든 외부 제공자, 실제 카메라/갤러리, S25+ 글자 확대·GPU·회전·장시간 메모리는 미검증이다. 운영 서버/DB·유료 API·휴대폰 삭제/초기화는 실행하지 않았다.

## 검증 및 배포

주요 수정의 로컬 전체 Android Unit/Compose **405개 통과, 실패/오류/skip 0**. Kotlin·계측 테스트 소스 컴파일, Debug 빌드, Lint 성공(Error/Fatal 0, Warning153, Hint2). 마지막 자동핏 무변화 안내 수정 후 관련 피팅 회귀 **16개 통과**. 최종 코드의 로컬 Preview 빌드와 선택 회귀 **169개 통과, 실패/오류/skip 0**. Preview 실행은 전체 테스트의 일부를 별도 패키지로 반복한 것이므로 고유 테스트 수에 더하지 않는다. 최종 Actions #59의 백엔드 **151개 + 8 subtests**와 APK 도구 테스트 **3개**가 통과했다. 최종 [Actions #59](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41587576091)는 전체 7개 Job 성공. Android 전체/Preview 테스트, Kotlin/계측 소스 컴파일, Debug/Preview 빌드, Lint, CPU 메시 비교 렌더, APK 검증 및 업로드가 성공했다. 이전 [실행 41586745088](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41586745088)은 최종 안내 커밋으로 자동 취소됐으며 성공이나 새 실패로 집계하지 않는다. 계측 테스트는 기기/에뮬레이터에서 실행하지 않았다.

P10 대비 AndroidManifest, SDK/Gradle 의존성 변경은 없고 새 권한도 추가하지 않았다. 변경 파일에서 APK·키스토어·.env·빌드 산출물이 커밋되지 않았음을 확인했다.

## S25+ 확인 순서

1. 기존 Preview 14801을 삭제하지 않는다. 옷장·즐겨찾기·기록·저장 치수를 확인하고, 동일 패키지/인증서의 증가한 버전만 업데이트한다. 보안 경고가 나면 보호 기능을 끄지 말고 중단해 APK 해시와 경고 내용을 확인한다.
2. 홈을 일반/큰 글자·화면 확대 설정에서 비교한다. 날씨 문구/추천 버튼과 하단 탭의 잘림 여부를 기록한다.
3. 키·몸무게 → 3D 생성 → 허리/어깨/가슴/허리 순서로 줄자·직접 입력·±버튼을 섞어 조절한다. 각 값을 기록하고 원래 부위로 돌아왔을 때 그대로인지 확인한다.
4. 비교·승인 전에는 기존 저장값이 유지돼야 한다. 취소/뒤로 가기 경고, 승인 저장, 재진입과 앱 종료 후 저장값 복원을 확인한다. 고급 설정의 사진/실측 가져오기에도 도달해야 한다.
5. 사진과 옷 선택 후 수동 이동/배율 변경 → 검출 실패 사진의 자동핏에서 배치 유지와 안내 확인 → 정상 전신 사진의 자동핏에서 실제 위치·크기 변경 확인. 부분 검출·원피스·하의·신발을 따로 확인한다.
6. 사진/3D 모드 전환, 창 크기·화면 회전, 아바타 배치 저장/재로드를 확인한다. 실제 GL의 빠른 연속 줄자 조작과 백그라운드 복귀를 확인한다.
7. 분석 네 진입, 사진 동의·취소·측정선·3D 승인·기록, AI 서비스 미준비 상태와 동의 없는 생성 차단을 확인한다. 외부 유료 생성은 별도 승인 없이 실행하지 않는다.

## P11 기준 변경 파일

```text
.github/workflows/android-debug-apk.yml
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/BodyAvatarDialog.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/PremiumAvatarEditor.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/HomeScreen.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/VirtualFittingScreen.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/AvatarFittingTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyAnalysisResultUiTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/HomeWeatherUiTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/P9EditorUiTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/PresentationFittingUiTest.kt
docs/P11_STABILITY_FINAL_20261103.md
docs/README.md
```

## 최종 Preview 배포 증거

- [Preview APK](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41587576091/artifacts/13209585919)
- [BUILD_INFO/검증 정보](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41587576091/artifacts/13209595941)
- [테스트·Lint·CPU 비교 렌더 보고서](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41587576091/artifacts/13209571015)
- 파일 `fashionai-1.1.59.1-preview-vc15901-1e05e4af.apk`, 패키지 `com.jeiu.fashionproject.preview`, 버전 `1.1.59.1-preview`, versionCode `15901 > 14801`.
- APK 코드 SHA `1e05e4af1bdb9b3f16cb2b770978a38b828b6fc8`, 브랜치 `codex/fashionai-p11-stability-final-20261103`, sourceDirty=false. manifest·내장 build identity·BUILD_INFO와 CI 체크아웃이 일치했다. 최종 문서 커밋의 HEAD는 APK 코드 SHA와 다르다.
- APK SHA-256 `155bbb4102e7c4ea43b20daabfe685c50fce7a37046a3f76da5bd3faf8a3d8e1`.
- 인증서 SHA-256 `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`. P10 배포 기록과 같으며 CI의 신뢰 인증서 비교를 통과했다. 새 키 생성/교체는 하지 않았다.
- CI의 `tools/apk_release.py verify --distribution --preview`가 apksigner·버전 증가·빌드 정보·기능 DEX 표식·ZIP 전체 엔트리 읽기(CRC 검사)·비밀 설정/서명 비밀번호/키 파일 바이트 미포함 검사를 통과했고 `distributionVerified=true`를 반환했다. 로컬 개발용 APK를 배포 파일로 대체하지 않았다.
- P10 원격 HEAD `495a56f18836a88c5f89d64661a2a93ddba10c45`와 기존 [14801 APK 아티팩트](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41568182287/artifacts/13200492861)를 보존했다. 아티팩트가 만료되지 않았음을 확인했다.
- 같은 패키지·인증서·상향 버전으로 업데이트 사전 조건을 충족한다. 실제 S25+ 설치와 기존 데이터 유지 결과는 미검증이며, 앱 삭제/초기화나 Samsung 보호 기능 우회를 안내하지 않는다. GitHub 로그인과 보존 기간 내 다운로드가 필요하다.

## 다음 세션 시작점

코드를 다시 구현하지 말고 위 S25+ 체크리스트의 실제 결과부터 확인한다. 자동 테스트 통과와 실제 기기 검증 완료를 혼동하지 않는다. 사진 모드 프로세스 종료 후 복원, 모든 URI 제공자, 실제 GPU 성능/메모리, 실사진 ML Kit 검출 품질은 별도 후속 범위다. 저장된 사용자 자료를 자동 초기화하지 않는다.
