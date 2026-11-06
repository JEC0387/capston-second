# P22 — 사진 수동 피팅의 ContentScale.Fit 좌표 보정

## 범위와 보존

- 기준 `8298c366b91766a678ddd31cdbad747ba00dc1dc`, 기존 `codex/fashionai-p17-camera-personal-vton-20261104`에서 작업. 시작 Git clean.
- P21 사진 선택·실제 키·실기기 아바타 검사는 보류. P22에서 ADB/휴대폰/갤러리 접근, 사용자 화면 준비 요청, 실제 사진·외부 AI 요청 없음.
- 설치된 Preview/QA 18001과 사용자 데이터는 조작하지 않았다. 새 기기 해시 비교에 의한 데이터 보존 검증을 주장하지 않는다.
- 새 브랜치/worktree, 사진 영구 저장, URI 영구 권한, 데이터 마이그레이션 없음. 아바타 저장 스키마·인증·동기화 변경 없음.

## 실패 재현과 수정

기존 `onSizeChanged`는 사진과 아바타에 캔버스 X/Y 비율을 각각 적용했다. 동작을 바꾸지 않고 해당 반복문을 실제 화면에서 사용하는 `ManualFittingViewModel.resizeCanvas`로 추출한 뒤, 독립 계산 기대값을 가진 회귀 테스트를 먼저 실행했다.

- 사진 1000×2000, 캔버스 400×800 → 400×600, 의류 중심 (100,200), 기본 너비 80.
- 기대: 옛 사진 배율 .4 → 새 배율 .3, 새 왼쪽 여백 50이므로 중심 (125,150), 너비 60.
- `red-01`: **1개 실패**, XML의 실제 실패 `expected 125.0 but was 100.0`, Gradle 종료 1 / BUILD FAILED. 산술 설명에만 머물지 않고 앱의 추출된 리사이즈 경로로 재현했다. 기기 재현은 아니다.

수정:

- `PhotoFittingCoordinates.kt`: 이전 사진 여백 제거 → 동일 사진 좌표의 등비 배율 변경 → 새 여백 적용. 정규화된 캔버스 좌표와 구분한다. Double 중간 계산으로 왕복 오차를 줄이고 최종 Float만 저장한다.
- `VirtualFittingScreen.kt`: 사진 배치만 위 함수를 사용한다. 사용자 scale/rotation, 선택 의류·슬롯·투명도는 보존. 아바타 및 되돌리기 배치는 기존 캔버스 정규화 방식 유지.
- 사진 크기는 포즈 검출이 아닌 **Coil의 실제 표시 drawable 크기(방향 처리 후)**에서 얻는다. 포즈 실패/대기 중에도 사용 가능하다.
- 크기 미확인 때는 기존 배치의 숫자를 유지한다. 늦게 크기가 확인돼도 즉시 배치를 재해석하거나 옮기지 않으며, 확인 이후 리사이즈부터 적용한다. 따라서 크기를 모르던 구간의 사진 상대 정합성까지 보증하지 않는다.
- 0/음수/비유한 크기는 배치에 적용하지 않는다. 0크기 레이아웃은 마지막 유효 기준을 보존한다.
- 사진 세대와 URI를 함께 확인해 이전 이미지의 늦은 응답을 거부한다. 같은 URI 재선택도 새 painter 세대로 크기 콜백을 받는다. 이전 사진에서 시작된 늦은 의류 로딩은 새 사진의 배치를 덮어쓰지 않는다.

## 실제 변경 파일

1. `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/VirtualFittingScreen.kt`
2. `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/PhotoFittingCoordinates.kt`
3. `fashion_app/app/src/test/java/com/jeiu/fashionproject/P22PhotoResizeTest.kt`
4. `fashion_app/app/src/test/java/com/jeiu/fashionproject/P22PhotoResizeUiTest.kt`
5. `.github/workflows/android-debug-apk.yml` — 기존 Preview 회귀에 P22 검사 추가, 기존 검사 유지.
6. 이 문서.

## 집중 검증 완료 — 최종 CI 확인 예정

- `red-01`: 수정 전 1개 실패. 증거 `fashion_app/build/p22-private/red-01.{log,xml,exit}` 및 추출 당시 patch/test 보존.
- `focused-02`: 신규 P22 7개와 기존 피팅 회귀 22개, 총 29개 중 27 통과/2 실패. 하나는 왕복 후 Float 중간 계산 오차(기대 Offset의 비트 단위 동일성 실패), 하나는 Compose 호스트에서 높이 변경 후 실제 레이아웃 이벤트를 기다리지 못한 테스트 환경 문제였다.
- 계산 정밀도를 높여 좌표 기대값을 유지했다. UI 호스트는 requiredHeight로 실제 측정 크기를 바꾸도록 수정했으며, 앱의 onSizeChanged나 단언을 우회하지 않는다. 동일 URI 재선택 검사도 추가했다.
- `focused-03`: 7개 중 5 통과/2 실패. 합성 의류 생성 시각의 비결정성 때문에 동일 객체 비교가 실패했고, UI 레이아웃 완료 동기화가 더 필요했다. 합성 등록 시각을 고정하고 선택 의류의 조작 영역까지 레이아웃이 안정된 뒤 기준을 확보했다.
- `focused-04`: 좌표 단위 6개 통과. UI는 실제 캔버스 560×460 → 560×260에서 기대 위치/너비/사용자 변형까지 통과했으나 왕복의 레이아웃 대기에서 실패. 왕복·동일 URI 재선택에도 Compose idle 동기화를 적용했다.
- `focused-05`: UI 1개 통과, 실패/오류/건너뜀 0, Gradle 종료 0 / BUILD SUCCESSFUL (1m45s). 실제 크기 변경·왕복·동일 URI 재선택까지 확인했다. 단언/기대 좌표/시간 제한은 완화하지 않았다. 신규 고유 검사 범위는 7개이며 재실행을 합산하지 않는다. 기존 회귀 22개는 focused-02에서 모두 통과했다.
- 테스트는 합성 좌표/픽셀만 사용하며 Coil IO를 차단한다. UI 검사는 실제 VirtualFittingScreen, 이미지 성공 콜백, 실제 레이아웃 크기 변경, 선택·배율·회전·왕복 복귀를 검사한다. 기대값을 production 변환 함수로 계산하지 않는다.

## 남은 검증 경계

최종 실행 코드의 CI·APK 검증 완료 전 배포 완료로 보고하지 않는다. 새 APK가 생성되면 기존 패키지/신뢰 인증서, 18001 초과 버전, 내장 소스 SHA·무결성·정렬을 확인하며 기기에 설치하지 않는다.

실제 개인 사진의 방향/좌표, 기기 회전·분할 화면, P21 전신 분석·스타일 연결은 미검증이다. 이번 결과는 수동 사진 피팅의 합성 좌표 정합성 수정이며 실측 정확도·개인 사진 VTON 완료와 무관하다.
