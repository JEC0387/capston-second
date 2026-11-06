# FashionAI 3D 아바타·가상피팅 인수인계 및 11월 3일 실기기 점검

- 저장소: `jimnnieo-svg/capstone-integration-backup`
- 작업 브랜치: `codex/fashionai-p1-avatar-fitting-20261102`
- 검증 기준 코드 커밋: `559bc27fa6e86bb64396ec18ea89d9b8f9790582`
- 기준 브랜치: `codex/fashionai-p0-wardrobe-20261102` (`434baaa66ca6e9b0e0f2c6d8b44471a984e5bd25`). 기준 브랜치와 기존 APK는 수정하지 않았다.
- Android Actions 실행: https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41396196709
- 배포 Preview APK: https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41396196709/artifacts/13120005826
- 검증 `BUILD_INFO` 아티팩트: https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41396196709/artifacts/13119558467
- 단계별 체크포인트: P1 `e60b765`, P2 `74a016b`, P3 `ab7514e`, P4 `559bc27` (모두 위 작업 브랜치에 Push).

## 구현 범위와 실제 한계

1. **P1 완료 — 키·몸무게 기반 3D 형상.** 체형 분석의 3D 설정 화면에서 키·몸무게를 검증하고, BMI를 제한된 시각적 형상 추정에 사용한다. 어깨·허리·골반·허벅지·다리 비율 조절은 실제 GLES 메시의 정점 또는 관절 높이를 바꾼다. 기존 모델 뷰어의 회전·확대를 유지한다. 실제 신체 치수·체지방·의류 핏 측정이 아니다.
2. **P2 완료 — 별도 로컬 저장.** 아바타 표시 프로필은 `avatar_visual_profile_v1`에 동기식으로 저장하고 화면 재진입 시 복원한다. 기존 사진 분석 및 직접 입력 치수의 저장소는 건드리지 않는다. 손상된 원본은 덮어쓰지 않고 알리며, 초기화는 확인 대화상자를 거친다. Android 클라우드 백업과 기기 간 이전에서 이 저장소를 제외했다. 운영 DB 전송 코드는 추가하지 않았다.
3. **P3 부분 완료 — 가상피팅 연결.** 기존 전신 사진 모드와 별도인 3D 아바타 모드를 추가했다. 저장한 프로필을 GLES 뷰어에 표시하고, 옷장에서 선택한 의류 사진을 정면 이미지 오버레이로 겹친다. 이동·배율·회전, 명시적 저장·복원이 가능하다. 배치는 의류 ID와 정규화 좌표만 `avatar_fitting_overlay_v1`에 저장한다. 옷 사진이나 몸 사진은 이 저장소에 복제하지 않는다. 손상된 배치는 보존하고, 사용자가 요청할 때에만 그 배치 저장값을 초기화한다. 3D 옷감 메시·실시간 의복 시뮬레이션·생성형 AI 이미지는 구현되지 않았다. 아바타를 회전해도 옷 이미지는 정면에 고정된다. GPU·실사용 시각 품질도 아직 검증하지 못했다.
4. **P4 기능 구현, 정렬 품질 미검증 — 마법봉 자동핏.** 사진 모드는 기존 ML Kit 어깨·골반·발목 검출을 사용한다. 상의는 어깨, 하의는 골반, 신발은 발목, 겉옷은 상의보다 넓게 배치한다. 원피스는 골반·발목 위치와 사진 종횡비를 반영해 길이를 추정한다. 아바타 모드는 저장된 신체 표시 비율과 GLES 카메라 투영을 사용한다. 사용자 수동 조절값을 지우는 동작은 마법봉을 누를 때만 발생한다.

기존 AI 피팅은 별도의 서버 프록시·동의 흐름에 남아 있으며, 새 아바타 모드에서 유료 API를 호출하지 않는다. 기존 옷장·사진·체형 기록을 지우거나 강제로 덮어쓰는 마이그레이션은 없다.

## 변경 파일

- `fashion_app/app/src/main/java/com/jeiu/fashionproject/data/AvatarProfile.kt`, `AvatarFittingStore.kt`, `BodyAvatar.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/AvatarSetupCard.kt`, `BodyAvatarDialog.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/BodyMethodSelectionScreen.kt`, `VirtualFittingScreen.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/navigation/AppNavigation.kt`
- `fashion_app/app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/AvatarProfileTest.kt`, `AvatarFittingTest.kt`, `BodyMethodSelectionUiTest.kt`, `PresentationFittingUiTest.kt`
- `.github/workflows/android-debug-apk.yml` (새 테스트를 Preview 변형에도 실행)

## 자동 검증 기록

- 기본 Debug: Kotlin 컴파일, `assembleDebug`, Android 계측 테스트 **소스 컴파일**, `lintDebug` 성공. 로컬 Robolectric/Compose 포함 **317개 테스트 통과**. 계측 테스트를 실제 Android 기기에서 실행한 것은 아니다.
- Preview: 로컬 별도 패키지 변형 빌드 및 선택된 Robolectric/Compose 회귀 테스트 **62개 통과**. 로컬 APK는 versionCode 4의 개발 키 서명본이므로 기존 S25+에 설치하지 않는다.
- Actions `41396196709`: **7개 Job 모두 성공**. 백엔드 회귀 **105개 테스트 + 8개 하위 테스트 통과**, Windows/Linux 서버 시작 검증 5개 Job 성공. Android Job에서 기본 APK·전체 단위 테스트·계측 테스트 소스 컴파일·Lint·Preview APK·Preview 선택 테스트가 성공했다.
- 배포 Preview 검증값 (`BUILD_INFO` 생성과 같은 검증 로그): `com.jeiu.fashionproject.preview`, `1.1.27.1-preview`, versionCode `12701`, Git SHA `559bc27fa6e86bb64396ec18ea89d9b8f9790582`, 서명 인증서 SHA-256 `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`, APK SHA-256 `8e9cd713c1ee3b3edaa80242b30174f5e4b2ac0addf9d1f7147c4e50aa5f2275`, `sourceDirty=false`, `distributionVerified=true`. 기존 설치본의 패키지·서명과 같고 versionCode가 12301보다 높으므로 업데이트 조건을 충족한다. 실제 S25+ 업데이트 결과는 미검증이다. CI가 APK 내용에서 서명 암호/키스토어 포함 여부도 검사했다.
- 실기기 GPU 렌더링, 터치 회전·확대, 사진 촬영·갤러리, 실제 옷 사진의 정렬, S25+ 기존 앱의 데이터 유지 업데이트, 네트워크 전환은 **미검증**이다.

## 2026-11-03 S25+ 실기기 체크리스트

1. 설치 전 기존 FashionAI Preview의 버전 `1.1.23.1-preview`(12301), 옷장 개수·샘플·사용자 의류·체형 기록을 확인한다. 기존 앱을 삭제하거나 앱 데이터를 지우지 않는다. 필요한 사용자는 기존 앱의 백업 기능을 사용하되 개인 백업 코드를 공유 채널에 게시하지 않는다.
2. 최종 Actions의 `fashionai-preview-apk-*` 아티팩트에서 APK와 `BUILD_INFO`를 함께 받는다. `BUILD_INFO`의 Git SHA가 위 코드 커밋인지, 패키지가 `com.jeiu.fashionproject.preview`인지, versionCode가 12301보다 높은지, SHA-256 서명 인증서가 기존 신뢰값 `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`와 같은지 확인한다. APK 해시도 `SHA256SUMS`와 대조한다. 이 조건이 맞을 때만 `adb install -r <APK>` 또는 Android의 일반 업데이트로 설치한다. `adb uninstall`, `pm clear`, 다운그레이드 설치를 사용하지 않는다.
3. 업데이트 직후 기존 옷장 수와 사용자 사진, 직접 입력 치수, 사진 분석 기록이 유지되는지 확인한다. Preview의 샘플 의류 이미지와 샘플 복구 버튼도 확인한다.
4. 체형 분석 → **3D 아바타로 체형 설정**에서 키·몸무게의 빈값·범위 초과 입력을 확인한다. 유효한 값을 입력하고 모델을 연다. 어깨·허리·골반·허벅지·다리 조절 시 **모델 형상**이 변하는지, 회전·핀치 확대가 동작하는지 확인한다. 저장 후 앱을 완전히 종료·재실행하여 값이 복원되는지 확인한다. 이 수치를 실제 측정치로 오해할 표현이 없는지 확인한다.
5. 가상피팅 → **내 전신 사진**에서 사진 선택, ML Kit 정렬, 원피스·상의·하의·신발·겉옷의 자동핏, 수동 드래그·크기 변경을 확인한다. 사진 분석 화면 및 기존 AI 피팅 진입도 유지되는지 확인한다. 실제 유료 생성은 실행하지 않는다.
6. 가상피팅 → **내 3D 아바타**에서 저장 프로필을 불러오고, 옷장 의류를 선택해 이미지를 올린다. 수동 이동·확대·회전, 마법봉 자동핏, 배치 저장 후 앱 재실행 복원을 확인한다. 사진 모드와 아바타 모드의 의류 선택이 섞이지 않는지 확인한다. 아바타를 회전할 때 옷 이미지가 정면에 남는 제한을 확인한다.
7. Wi-Fi ↔ 모바일 데이터 ↔ 비행기 모드를 전환해 기존 로컬 옷장·아바타 프로필·배치가 유지되는지 확인한다. 아바타 프로필·배치 저장 코드가 서버 동기화를 호출하지 않는지 개발 코드와 테스트에서 확인하고, 운영 DB 설정은 바꾸지 않는다.
8. 3D 미표시, 이미지 오프셋, 설치 실패, 저장값 손상 메시지가 보이면 **기존 앱 삭제·데이터 초기화 없이** 기기 모델/OS, 화면 캡처, 재현 단계, 설치 전후 버전, 해당 Actions SHA를 기록해 수정한다.

## 다음 개발: 자체 AI 착장 이미지 생성

기존 `TryOnRepository`는 사용자 동의 후 인증된 서버 프록시로 사진을 보내고, 외부 FASHN 결과를 받는다. 자체 생성 모델은 아직 없다. 다음 단계에서는 (1) 권리·라이선스를 확인한 학습/평가 데이터, (2) 인물 자세·신체 분할·의류 마스크와 가림 영역 추정, (3) 얼굴·손·배경 보존 및 옷 경계·그림자 평가 지표, (4) GPU 메모리·처리 시간·요금 상한, (5) 서버 프록시의 인증·동의·사진 보관/삭제·중복 과금 방지, (6) 무료/스테이징 샘플로 품질 기준을 먼저 확정해야 한다. 기존 2D 오버레이 결과를 AI 생성이라고 표시하지 않는다. 운영 DB 변경, Railway 배포, 유료 API 실행은 사전 승인 범위 밖이다.
