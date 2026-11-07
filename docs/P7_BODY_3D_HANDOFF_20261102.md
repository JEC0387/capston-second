# P7 신체 측정·3D 연결 — 2026-11-02

기준 P6는 `18980db7191a6b3799de0d359d85998f97d75ef7`다. P6 문서 정리를 먼저 Commit·Push했고 기존 작업 트리와 APK를 유지했다. 새 작업 브랜치는 `codex/fashionai-p7-body-3d-measurements-20261102`, 별도 작업 폴더는 `p7-body-20261102`다. 팀 저장소/운영 Railway/DB/유료 자원은 변경하지 않는다.

## 구현 원칙

- BMI 표시 형상, 사진의 정면 너비·길이, 직접 입력한 신체 둘레를 별도 출처로 유지한다. 사진 정면 너비로 둘레를 만들거나 3D 표시 비율을 의류 사이즈 측정값으로 사용하지 않는다.
- 치수 가져오기는 이전/후보 수치와 3D 비교 → 사용자 승인 → 미리보기 반영 → 별도 저장 순서다. 취소하면 기존 프로필을 유지한다. 사진/치수가 없어도 기본 모델은 이용할 수 있다.
- 직접 입력한 둘레는 표시용 타원 가정에만 사용하며 원본은 둘레로 보관한다. 사진만으로 앞뒤 깊이·체지방·가슴 둘레를 복원하지 않는다. 없는 부위는 기본 비율 추정으로 표시한다.
- 저장은 기존 비공개 `avatar_visual_profile_v1` 파일의 schema2다. schema1 읽기는 디스크에 쓰지 않고 기존 조절값을 복원한다. 사용자가 새 저장을 승인할 때 기존 schema1 JSON을 `profile_v1_preserved`에 남긴다. 손상/미지원 schema는 원본을 유지하고 덮어쓰기를 차단한다. 클라우드/기기 이전 백업 제외 정책은 동일 파일에 계속 적용된다.
- 사용자가 기존 초기화 대화창에서 별도로 확인한 경우에만 현재 3D 프로필과 구버전 복사본을 함께 제거한다. 초기화 범위는 대화창에 표시하며 원본 직접 입력/사진 기록은 건드리지 않는다. 이번 작업에서 실제 기기의 초기화나 데이터 변경을 실행하지 않았다.
- 저장한 3D 치수 스냅샷은 별도 선택 저장값이다. 원본 분석/직접 입력을 수정해도 자동 갱신하지 않으며 다시 가져오기/승인이 필요하다. 3D 설정 삭제는 기존 사진·옷장·수동 치수를 지우지 않는다.
- 기존 GLES 메시를 재사용하고 가슴/상체 길이 조절, 연속 보간 링·폐쇄된 손/발 끝·경사에 맞춘 법선·어깨 연결부/관절 비율을 개선했다. 관절 위치는 표시 범위로 제한하므로 실제 3D 스캔이나 옷 시뮬레이션이 아니다. 외부 에셋을 추가하지 않았다.

## 검증 범위

1차 출처·프로필·메시·승인 UI·기존 아바타 회귀26개 통과. 2차 마스크/치수/사진 입력/좌표 표시 회귀49개 통과. 기존 임계값을 완화하지 않았다. 0.65/0.80 confidence에서 윤곽 너비가 크게 달라지면 별도 재촬영 사유로 거절한다. 다리+상체 길이의 키 환산 일관성을 검사한다. 측정선은 EXIF 보정된 분석 비트맵 기준 정규 좌표이며 사진을 Fit으로 표시해 여백을 포함한 위치를 맞춘다. 선 위치는 대표 행, cm는 주변 행들의 중앙값이다. 측정선은 세션 메타데이터로만 사용하고 서버·3D 프로필에 보내지 않는다. 가슴 정면 너비/팔 길이는 검증된 경계가 없어 새 수치를 만들지 않는다.

3차 사이즈/피팅 회귀46개 통과 후 손상된 수동 치수 형식 복원 테스트를 추가했고 해당 묶음7개 통과. 상품별 실측표 선택·빠진 상품 치수/신체 치수 구분·원피스의4개 치수 근거·사진 너비만으로 둘레 확정 차단을 검증했다. 데이터 원본/브랜드 실측표를 새로 만들지 않았다. 기존 핏 규칙을 슬림/정핏/루즈 참고로 설명하고 실제 착용감 보장은 하지 않는다.

최종 관련23개 테스트도 통과했다. 표시 메시의 발목 기준을 고관절~발목 입력 정의와 맞췄고, 삼각형 정점의 좌우 대칭·어깨 연결·키 전체 높이·모든 부위 변형을 확인했다. 프로필 초기화는 사용자 확인 후에만 구버전 복사본까지 제거하며 자동 복구/로드는 삭제하지 않는다. 손상된 수동 치수의 필드 타입도 원본 유지/안내 대상으로 처리한다.

피팅은 동일한 `AvatarParameters.joints()`를 사용하고 가슴·허리 표시 너비도 의류 배치 계산에 반영한다. 프로필 변경만으로 수동 위치/배율/회전을 덮어쓰지 않는다. 명시적 자동핏은 정면/기본 줌으로 되돌리고 재계산한다. 회전 시 의류가 몸을 감싸지 않는 2D 오버레이라는 기존 안내를 유지한다. 사진 모드 ML Kit·자체 AI·유료 FASHN 경로는 교체하지 않았다.

실제 GPU GLES 화면과 실사진 측정 오차는 미검증이다. 합성 마스크·좌표·Robolectric UI와 정점 계산 안정성은 실제 측정 정확도 향상률을 증명하지 않는다. 최종 자동 테스트/CI/APK 증거와 변경 파일 목록은 검증 완료 후 이 문서에 기록한다.

## 11월3일 S25+ 점검 순서

1. 기존 Preview/레거시 앱을 지우지 않는다. 옷장 수·사진·체형 입력값·현재 3D 설정과 기존 백업을 먼저 보존한다. 서명 비교를 통과한 새 Preview만 업데이트하고 마이페이지 versionCode/Git SHA를 확인한다. 실제 설치는 이번 세션에서 실행하지 않았다.
2. 기존 schema1 프로필이 있으면 같은 키/몸무게/조절값으로 열리는지 확인한다. 저장 전 원본 JSON을 덮어쓰지 않아야 한다. 손상된 자료는 안내하고 자동 초기화하지 않아야 한다.
3. 체형 → 3D 설정에서 A/B/C 출처를 선택한다. 사진 결과가 없을 때 B, 직접 입력이 없을 때 C가 비활성인지 확인한다. 기본 모델은 계속 이용할 수 있어야 한다. 사진 방식은 현재 세션의 유효 분석 결과/결과 화면을 사용한다. 과거 서버 기록을 원본 환산 메타데이터 없이 자동 가져오지는 않는다.
4. 정면 사진을 키와 동의 후 분석한다. 어두움/흔들림/측면/팔 겹침/머리·발끝 누락/불확실한 윤곽이 거절되거나 이유를 안내하는지 확인한다. 가슴 둘레나 팔 길이가 측정값처럼 생기면 안 된다. 측정선의 청록/노랑/보라가 실제 사진의 대표 위치와 일치하는지 확인한다.
5. B 선택 → 이전/후보 수치·3D 비교 → 취소 시 기존 저장값 유지 → 승인 시 미리보기만 변경 → 별도 저장 순서를 확인한다. C에서 허리 둘레를 입력해도 사진의 정면 너비 항목이 덮어써지지 않아야 한다.
6. 가슴·어깨·허리·힙·허벅지·다리·상체 비율을 바꾸고 정면/측면/후면에서 실제 부위 형상을 확인한다. 전체 보기/회전·핀치/초기화, 앱 일시정지·재개, GLES 컨텍스트 복구와 오류 안내를 확인한다. 실제 GPU 렌더링은 이 단계에서 검증한다.
7. 저장 후 앱 프로세스를 종료·다시 열어 출처·스냅샷·조절값을 복원한다. 원본 수동 입력을 바꿔도 기존 아바타를 자동 덮어쓰지 않아야 한다. 삭제/초기화는 테스트 데이터에만 별도 승인 후 실행한다.
8. 실제 상품표를 선택하고 상의/하의/원피스, 슬림·정핏·루즈 참고 차이, 몸/상품/여유량/직접 입력 출처를 확인한다. 둘레가 없으면 추가 입력 안내만 나와야 하며 임의 브랜드표를 만들면 안 된다.
9. 3D 피팅에서 의류를 수동 이동·배율 조정 후 체형을 변경한다. 기존 배치가 유지되고, 마법봉을 누른 경우에만 정면/기본 줌 기준으로 새 형상에 맞추는지 확인한다. 옷은 2D 오버레이이며 회전해 몸을 감싸는 실제 의복이 아니다.
10. 사진 선택·카메라·옷 등록·옷장 스크롤·코디 추천·기존 수동/자체 AI/외부 AI 진입을 회귀 확인한다. 유료 생성 버튼은 승인 없이 실행하지 않는다. P6 실제 모델 생성은 여전히 별도 CUDA 서버 준비가 필요하다.

실사진 평가는 동의한 시험 참가자의 반복 촬영과 같은 측정점 정의의 직접 선형 치수(어깨/고관절~발목/어깨~고관절)로 짝을 맞춰 차이를 기록한다. 정면 허리/힙 너비를 줄자로 잰 둘레와 비교해 정확도 지표를 만들지 않는다. 실제 평가 데이터가 쌓이기 전 오차·성공률·정확도 개선 수치를 제시하지 않는다.

## 남은 기술적 한계

앞뒤 깊이·실제 3D 표면·체지방·사진 기반 둘레 복원, 검증된 가슴 정면 너비/팔 길이, 옷의 헐렁함 자동 판정과 원근 왜곡 보정은 구현하지 않았다. 기존 포즈/윤곽 검사는 보수적인 촬영 휴리스틱이다. 절차적 메시의 연결부는 작은 중첩으로 간극을 줄인 저폴리곤 표시이며 단일 watertight 인체 토폴로지가 아니다. 실기기 프레임률·상황별 GL 복구/색/관절 외관·실사진 오차는 미검증이다. 새 외부 3D 에셋을 쓰지 않았고 원래 프로젝트의 Kotlin 생성 메시를 개선했다. 이 수치를 의료·스캔 정확도나 실제 착용 핏으로 사용하지 않는다.

## P6 기준 대비 변경 파일

```text
.github/workflows/android-debug-apk.yml
README.md
docs/P6_VTON_HANDOFF_20261102.md
docs/P7_BODY_3D_HANDOFF_20261102.md
docs/README.md
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/AvatarProfile.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/BodyAvatar.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/ManualBodyMeasurements.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/recommend/MeasuredSizeRecommendation.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/AvatarApplicationDialog.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/AvatarSetupCard.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/BodyAvatarDialog.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/BodyMeasurementOverlay.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/MeasuredSizeCard.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/BodyAnalysisScreen.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/VirtualFittingScreen.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/BodyMeasurementCalculator.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/SilhouetteMeasurer.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/MeasuredSizeUiTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/P7AvatarTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/P7AvatarUiTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/P7FitSizeTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/P7MeasurementTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/PresentationFittingUiTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/SilhouetteMeasurerTest.kt
tools/apk_release.py
```

P6 문서의 DEX 표식 개수 표기만 10→9로 정정했다. P6 브랜치/기능/성공한 APK는 변경하지 않았다.

## 최종 자동 검증·배포 증거

- 구현 커밋: `7955f6097bce4eaf169206821d064a6f2aa20ee9` → `a6e56880f48f542989a721308c44a935938f9900` → **`b42f66702dcd2c62083b8b0faa5a9f840e63a12c`**. 별도 문서 마감 커밋은 이 APK의 코드 SHA가 아니다. 최종 문서 포함 HEAD는 `git rev-parse HEAD`로 확인한다.
- [최종 GitHub Actions #38 성공](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41432753525). 중간 #36/#37은 후속 커밋으로 기존 concurrency 설정에 따라 자동 취소됐으며 성공으로 집계하지 않는다. 최종 #38의 전체 작업이 성공했다.
- Android 전체 단위/Compose Robolectric **356개 통과**, Preview 패키지 **120개 통과**, 실패/오류/skip 모두0. Preview는 일부 테스트의 다른 패키지 실행이므로 고유 테스트 수에 합산하지 않는다. P6 전체332개에서 새 회귀24개를 추가했고 기존 테스트를 제거하지 않았다.
- Kotlin/단위 테스트 Kotlin/계측 테스트 Kotlin 컴파일, Debug/Preview 빌드, Lint 성공. Lint **Error/Fatal 0, Warning166, Hint2**. P6와 이슈 종류·메시지·파일 목록을 비교해 추가/제거0임을 확인했다. 계측 테스트는 컴파일만 했고 기기/에뮬레이터에서 실행하지 않았다.
- Linux 백엔드 pytest 전체 **151개 통과, 8 subtests 통과, skip0**, APK 버전/서명 도구 테스트3개 통과. Windows Python3.11/3.12/3.13/3.14와 Linux3.12의 서버 기동5개 환경 성공. 운영 DB/유료 API/실제 모델 GPU는 사용하지 않았다.
- [Preview APK 다운로드](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41432753525/artifacts/13136187118) — 비공개 저장소 로그인 필요, 30일 보관. 버전 **`1.1.38.1-preview`**, versionCode **`13801`**, 패키지 `com.jeiu.fashionproject.preview`, 앱 이름 FashionAI Preview.
- 정확한 로컬 APK: `C:\Users\USER\Documents\ChatGPT\capston\p7-body-20261102\fashion_app\build\p7-evidence\preview-vc13801\fashionai-1.1.38.1-preview-vc13801-b42f6670.apk`
- APK manifest·`assets/build_identity.json`·`BUILD_INFO.json`·브랜치/코드 SHA 일치. ZIP CRC 정상. SHA-256 **`74bb497117b68a5cbb88c28c766928161deac5a50e15778a3e057e29af146914`**. 기존/P6/P7 기능 필수 DEX 표식13개 모두 확인.
- 서명 인증서 SHA-256 **`f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`**. 보존한 P6 APK13501과 실제 파일끼리 대조했다. 패키지/인증서 동일 +13801>13501로 업데이트 사전 검사 통과. 새 키 생성/교체·실제 APK 설치·사용자 데이터 이전은 하지 않았다. 레거시 앱은 계속 별도 보존한다.
- 변경26개 파일에서 검사한 비밀 파일/키 패턴 미검출. APK의 .env/서명키/모델 가중치 파일 및 검사한 개인키/GitHub/cloud-key 패턴 미검출. CI의 실제 서명 비밀번호/키 파일 바이트 혼입 검사도 통과했다. 모든 비밀정보 탐지를 보증하는 표현은 하지 않는다. 실제 토큰/사진/가중치를 Git에 올리지 않았다.
- `fashion_app/build/p7-evidence/P7_APK_VERIFICATION.json`, `ci-reports/`, APK 옆 BUILD_INFO/SHA256SUMS는 ignored 빌드 증거로 보존한다. Git에 빌드 결과물을 커밋하지 않는다.
