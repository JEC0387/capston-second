# P8 체형 분석 UI/UX — 2026-11-02

저장소: `jimnnieo-svg/capstone-integration-backup`. 기준 P7 브랜치 `codex/fashionai-p7-body-3d-measurements-20261102`의 원격 HEAD `6e0c3c51bb1565890eb2dc1c6f03fff0fc929821`에서 `codex/fashionai-p8-body-ui-20261102`를 만들었다. P7 브랜치·기존 APK·팀 저장소를 변경하지 않았다. P8 구현 커밋은 `b3ca6e9` → `3caabda` → `5682200` → `b6d3ac765a5e0826956c9220283a0d619fcefe8c`이다. 이 문서를 저장하는 별도 커밋은 APK의 코드 SHA와 다르다.

## 화면 구조와 디자인

| 화면 | P7 기본 구조 | P8 기본 구조 |
| --- | --- | --- |
| 체형 분석 시작 | 사진·3D 선택 중심, 사이즈·기록 진입이 분산됨 | 사진·3D를 주요 카드로, 사이즈 비교·지난 기록을 하단 메뉴로 배치. 네 경로 모두 실제 화면에 연결 |
| 사진 분석 | 큰 업로드 영역과 카메라·갤러리 버튼 중복, 촬영 안내·직접 입력 노출 | 키 확인 → 촬영/갤러리 선택 → 동의 → 분석. 촬영 팁과 직접 치수 입력은 펼침 영역, 진행·취소 유지 |
| 분석 결과 | 비율 그래프·측정 cm·설명·폼·3D 설정이 한 번에 표시 | 사진·측정선 → 판정 → 주요 cm → 유효 치수의 3D 적용 → 상세 비율/특징 → 사이즈·스타일 → 기록/재분석. 상세·사이즈·입력·스타일은 필요할 때 확장 |
| 3D 설정 | 출처·입력·슬라이더가 한 카드에 연속 표시, 수동 치수 카드 상시 표시 | 모델 보기 → A 키·몸무게 추정/B 사진 측정/C 직접 입력 선택 → 세부 조절 확장 → 저장. 직접 치수 카드 별도 확장 |

Home/Wardrobe/MyPage에서 사용 중인 Cream/Camel/Brown/White, `MaterialTheme` 글꼴, 20dp 화면 여백, 둥근 카드와 기존 하단 탭·뒤로 가기를 재사용했다. 새로운 폰트·그라데이션·내비게이션 체계는 추가하지 않았다. 작은 화면과 큰 글자에서 3D 주요 조작을 스크롤해 접근하는 Compose 테스트를 추가했다.

## 유지 기능과 변경 범위

사진 카메라·갤러리·개인정보 동의·저장 선택, 측정선과 cm 계산, P7 사진/직접 입력/키·몸무게 출처 비교·승인·취소, GLES 메시·7부위 변형, 저장·복원, 옷 실측 사이즈 비교, 기록, 스타일 추천, 가상피팅, 자체 AI/기존 유료 AI 진입을 제거하거나 알고리즘을 변경하지 않았다. 사진의 정면 너비와 실제 둘레는 계속 구별한다. 유효한 cm 측정값이 없으면 해당 사진의 3D 생성 버튼을 표시하지 않는다. 사용자가 출처 승인 후 다시 저장하기 전에는 기존 프로필을 덮어쓰지 않는다. 기존 사용자 데이터·DB 스키마·서버 설정은 변경하지 않았다.

P7 이후 변경 파일:

```text
.github/workflows/android-debug-apk.yml
fashion_app/app/src/main/java/com/jeiu/fashionproject/navigation/AppNavigation.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/navigation/Screen.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/AvatarSetupCard.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/BodyAnalysisScreen.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/BodyMethodSelectionScreen.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/BodySizeComparisonScreen.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyAnalysisResultUiTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyMethodSelectionUiTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyPhotoEntryUiTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/P7AvatarUiTest.kt
```

## 검증 증거

로컬 전체 Android 단위·Compose Robolectric **365개 통과**(실패/오류/건너뜀 0), Kotlin/단위 테스트 Kotlin/계측 테스트 Kotlin 컴파일, Debug APK 빌드, `lintDebug` 성공. Lint는 Error/Fatal 0, Warning 153, Hint 2. Preview 패키지 선택 회귀 **129개 통과**(실패/오류/건너뜀 0), Preview APK 빌드 성공. 단위 테스트 수는 Preview에서 중복 실행하므로 합산하지 않는다. 계측 테스트 코드는 컴파일만 했고 기기/에뮬레이터에서 실행하지 않았다.

로컬 Preview APK는 `tools/apk_release.py verify --preview`로 manifest/내장 build identity, 필수 기능 표식, APK 서명, 비밀 설정 파일 포함 여부를 검사해 통과했다. 그러나 로컬 빌드는 개발용 인증서·`versionCode=4`·`1.1-local-preview`이므로 기존 S25+ 설치본의 업데이트 파일이 **아니다**. 배포용 APK는 GitHub Actions의 기존 관리 서명키와 증가한 versionCode로 별도 검증/게시해야 한다.

GitHub Actions의 백엔드 회귀 **151개 + 8 subtests**와 도구 테스트 3개 및 서버 기동 매트릭스 5개 환경은 통과했다. [최종 Android debug APK 실행 #42](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41439193092)의 7개 작업 모두 성공했다. CI Android 작업에서 Debug/Preview 빌드, 전체 단위 테스트, 계측 테스트 Kotlin 컴파일, Lint, 기본 APK 검증, Preview 선택 회귀, 배포용 서명·버전 검증과 아티팩트 업로드가 통과했다. 중간 실행은 후속 코드 커밋으로 취소됐으므로 성공으로 집계하지 않는다.

[배포용 Preview APK 아티팩트](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41439193092/artifacts/13140291029) · [BUILD_INFO/검증 정보](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41439193092/artifacts/13140176102) · [테스트/Lint 보고서](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41439193092/artifacts/13141085771). APK 파일명 `fashionai-1.1.42.1-preview-vc14201-b6d3ac76.apk`, 패키지 `com.jeiu.fashionproject.preview`, `versionCode=14201`로 P7의 13801보다 높다. CI가 확인한 서명 인증서 SHA-256 `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`는 P7 배포 APK와 같다. APK SHA-256 `796f42d914484d3435e28b3b4ba36db9abe450fa02c94532d90ac0ddf42ac257`. CI의 `BUILD_INFO.json`/manifest/내장 빌드 정보는 버전·패키지·브랜치·코드 SHA `b6d3ac765a5e0826956c9220283a0d619fcefe8c`로 일치했다. 배포 검사는 서명 비밀번호/키 파일 바이트·비공개 설정 파일 포함 여부도 검사해 통과했다. GitHub 로그인과 아티팩트 보존 기간 내 다운로드가 필요하다.

동일 패키지·인증서와 증가한 versionCode로 기존 Preview에 대한 **업데이트 사전 조건**은 충족했다. 실제 S25+ 설치와 기존 앱 데이터 유지 결과는 기기에서 아직 검증하지 않았다. 기존 앱을 삭제하거나 데이터를 지우지 말고 새 APK로 업데이트해야 한다. 기기/GPU 테스트는 자동 검증과 구별한다.

## S25+ 실기기 점검

1. 기존 Preview를 삭제하거나 앱 데이터를 초기화하지 않는다. P7 Preview `1.1.38.1-preview`/13801의 옷장·사진·분석 기록·수동 치수·3D 프로필을 먼저 확인한다. 새 APK의 패키지·서명·증가한 versionCode를 비교한 뒤 업데이트하고 BUILD_INFO의 Git SHA를 확인한다.
2. 홈/옷장/마이페이지와 체형 분석 화면의 색·글꼴·카드·버튼·하단 탭 및 뒤로 가기가 어울리는지 확인한다. 일반 글자와 큰 글자, 세로 짧은 화면, 키보드 표시 상태에서 주요 조작에 도달하는지 확인한다.
3. 시작 화면의 사진/3D 카드와 사이즈/기록 메뉴 네 경로, 빈 기록 안내를 확인한다. 옷장과 기존 데이터가 그대로인지 다시 확인한다.
4. 사진 화면의 키 입력/오류, 촬영/갤러리 차이, 촬영 팁·직접 입력 펼침, 동의 거절/승인, 분석 진행·취소, 실패 후 재시도, 사진 선택에서 돌아오기를 확인한다. 실제 참가자 사진이나 토큰을 GitHub에 올리지 않는다.
5. 결과 화면에서 사진 측정선·cm, 상세 비율·특징·스타일·사이즈 영역의 펼침, 너비/둘레 안내, 기록/재분석을 확인한다. 유효 측정값이 있을 때만 3D 출처 비교가 열리고 취소 시 저장 프로필이 그대로인지 확인한다.
6. 3D 화면의 A/B/C 선택, 미보유 자료에서 B/C 비활성, 모델 회전·확대/축소, 어깨·가슴·허리·골반·허벅지·다리·상체 슬라이더의 실제 GLES 형상 변화, 저장 후 프로세스 재시작 복원을 확인한다. S25+ GPU 품질·터치·실제 카메라/갤러리 동작은 자동 테스트로 확인할 수 없다.
7. 사이즈 비교·가상피팅·코디 및 기존 AI 경로가 계속 열리는지 확인한다. 유료 AI 실행과 운영 DB/서버 변경은 이 UI 검증에 포함하지 않는다.

## 남은 확인 사항

실기기 설치/기존 데이터 유지, GPU 렌더링·실사진 측정 정확도·카메라/갤러리 결과, 운영 DB/Railway 연결은 수행하지 않았다. 실제 업데이트 안전성은 패키지/인증서/versionCode의 사전 검증과 구별한다. 사진 기반 정면 너비는 둘레·실제 3D 스캔이 아니며, 3D 아바타에 올린 의류 이미지는 실시간 의복 시뮬레이션이 아니다.
