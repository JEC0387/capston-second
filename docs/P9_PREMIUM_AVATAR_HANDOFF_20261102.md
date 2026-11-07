# P9 cm 아바타 — 2026-11-02

기준 P8 원격 HEAD `ea8a65ff4633bb0b1e8039b9cd76635f1397cd7d`를 확인하고 별도 `p9-avatar-20261102` 작업 트리와 `codex/fashionai-p9-premium-avatar-20261102` 브랜치를 만들었다. 기존 작업 트리/미추적 파일/브랜치/APK는 보존한다. 팀 저장소·운영 DB·Railway·유료 API는 변경하지 않는다.

## 기술 선택과 단위

기존 튜브 메시를 무작정 세분화하는 대신 원래 GLES2 렌더러에서 그릴 수 있는 **독자적인 implicit surface + smooth union + marching tetrahedra** 메시를 추가했다. 머리/턱/목·어깨·골반·팔꿈치/손목·무릎/종아리/발을 하나의 연속 표면으로 추출하고 격자 gradient로 법선을 만든다. 외부 3D 파일/브랜드 이미지/SDK/네트워크 기능을 추가하지 않았다. 구버전 절차 메시와 데이터는 호환 경로로 유지한다.

| 선택지 | 판단 |
|---|---|
| 기존 튜브 단순 세분화 | 빠르지만 관절 간극·교차 표면을 해결하기 어렵다 |
| 자체 implicit/파라메트릭 메시 | 외부 라이선스 없이 형상·단면을 직접 검증할 수 있어 선택. CPU 재생성 비용과 실제 GPU 외관은 검증 필요 |
| 스켈레톤/스키닝 | 포즈/의복 확장에는 유용하지만 별도 리깅·측정 보정·에셋 검증이 필요해 이번에 도입하지 않음 |
| 외부 경량 인체 에셋 | 실제 파일/사용 권한/모프 변형이 확인되지 않은 에셋을 가져오지 않음 |

키/몸무게 초기값은 기존 BMI fullness 비율을 바탕으로 하는 **임시 초기 형상**이다. 검증된 인구 통계/실제 평균/의학 모델이 아니다. 민감한 성별/인종 등을 추정하지 않는다. 키는 사용자 입력값을 유지한다. 어깨는 너비, 가슴·허리·힙·허벅지는 둘레, 다리는 고관절~발목, 상체는 어깨~고관절이다.

둘레 단면은 가슴 기본 깊이/너비 .72, 허리 .70, 힙 .76, 허벅지 .92인 표시용 타원 가정이다. v2 가슴 너비는 어깨의1.1배 이내로 두고 필요한 깊이를 이분법으로 풀어 같은 둘레를 유지한다(깊이/너비 최대1.6 표시 한계). v1 가정 ID도 읽어 보존한다. Ramanujan 둘레 근사로 반지름을 정하고 독립적인 수치 적분 및 **실제 추출 메시 절단선**과 대조한다. 둘레만으로 실제 앞뒤 두께는 알 수 없다. 기준 단면에서 연결부 blend가 둘레를 부풀리지 않게 제한한다. 단순히 가로만 늘려 둘레가 맞는다고 주장하지 않는다.

`AvatarCentimeters`는 초기 추정값/현재값/각 출처/원본 참고 치수/사진 정면 너비/형상 가정/저장 시각을 구분한다. 사진 허리·힙 너비는 둘레로 바꾸지 않는다. 원본과 0.5cm 표시 단위 차이나 표시 범위 밖 값은 참고값으로 보존하고 자동 삭제/실측으로 둔갑시키지 않는다. 자동 추정/사용자 표시 조절은 기존 의류 사이즈 추천의 실측 입력값을 대체하지 않는다.

schema3를 기존 `avatar_visual_profile_v1` 파일에 선택 저장한다. schema1/2 읽기는 쓰지 않는다. 사용자가 cm 모델을 저장할 때 기존 JSON을 별도 키에 보존한다. 손상/미지원 형식은 원본 유지·저장 차단. 기존 명시적 초기화 확인을 거친 경우에만 3D 복사본까지 지우며 옷장/사진/원본 실측은 건드리지 않는다. 이번 작업에서 실제 사용자 데이터 초기화를 실행하지 않았다.

## 자동/시각 검증 상태

초기 신규/기존 관련27개 통과. 외형 조정 중 팔과 몸통이 연결되어 가슴 절단선이 부풀어지는 회귀를 발견했고, 연결부/기준 단면 처리를 수정해 같은 오차 기준의 신규9개가 다시 통과했다. 실패 테스트의 기준을 완화하지 않았다. 실제 메시 절단선 둘레 허용차는 합성 기본 프로필에서1.3cm이며 실제 사람 측정 정확도가 아니다. 전체 회귀·다양한 체형·UI·APK 결과는 최종 검증 후 추가한다.

`tools/render_avatar_review.py`는 Kotlin이 내보낸 실제 위치/법선 버퍼를 CPU z-buffer로 그린다. 출력은 ignored `fashion_app/app/build/p9-visual`에 두며 S25+ GLES 캡처가 아니다. 첫 비교에서 어깨·골반의 각진 연결을 확인해 곡선을 수정했다. 실제 단말 외관/프레임률은 아직 미검증이다.

참고 이미지/동영상은 현재 도구에서 제공받은 파일이 확인되지 않았다. 지정 Downloads 폴더/ZIP도 없었다. 자료를 봤다고 주장하지 않고 텍스트 요건과 Fashion AI 색/글꼴을 사용한다. 참고 파일을 찾더라도 분석에만 쓰고 Git/APK에 복사하지 않는다.

## 줄자·화면·저장 연결

3D 설정의 키/몸무게와 기존 A/B/C 선택은 유지하고 `3D 아바타 생성 · cm 줄자로 조절`로 전용 화면을 연다. 모델은 고정된 상단 공간에서 회전/핀치·정면/측면/후면을 제공하고, 하단 부위 탭과 줄자는 좁은 화면에서 스크롤한다. 키보드 입력은 별도 확인창이며 모델과 줄자 상태는 유지한다. 초안은 SavedState로 복원하되 저장 파일에는 명시적 비교/승인 전 쓰지 않는다.

Material Slider가 아닌 Canvas 눈금/고정 중앙선/실제 숫자/0.5cm snap을 구현했다. 드래그·±버튼·직접 입력·접근성 SetProgress는 같은 데이터값을 사용한다. 직접 입력에서 사용자가 선택한 경우만 줄자 실측으로 표시하고, 일반 드래그는 사용자 조절로 구별한다. 초기값과 불러온 원본을 비교/복원할 수 있다. 저장 확인창은 실제 저장인지 미리보기 승인인지 문구를 구분한다.

새 메시 생성은 단일 background consumer와 conflated queue로 최신 요청만 반영한다. GL thread에서 연속 CPU 메시 계산을 하지 않는다. 화면 분리 시 작업을 취소하고 재연결 시 복원한다. GLES2 정점/fragment 법선 조명·웜 아이보리 재질을 사용하며 외부 SDK나 권한은 추가하지 않았다. 실제 스마트폰 성능/GLES 실행은 미검증이다.

선택 회귀38개 중 UI/기존 경로는 통과했고, 순수 계산1개에서 half-step 경계 float 오차를 발견했다. UI/데이터가 같은 양자화 범위를 쓰도록 수정한 후 관련26개(새 기하/줄자/UI/기존 프로필)가 통과했다. 0.0001cm 수치 epsilon만 사용했으며 실제 표시 허용 범위를 넓힌 것이 아니다. 기존 cm 저장 프로필을 일반 저장 버튼으로 다시 저장해도 cm 값이 사라지지 않는 UI 회귀도 포함한다. 실제 기기에 설치하거나 데이터를 이전하지 않았다.

## 추가 검증과 CI 보호

표시 범위 양 끝·77.5cm 입력·100/230cm 프로필을 확장 검증했다. 큰 힙 설정에서 팔이 접촉하는 문제를 고쳐 팔 위치가 몸통/힙 외곽을 피하게 했고, 허벅지와 종아리 연결/좌우 간격도 해당 단면 크기를 반영한다. 기준170cm 프로필의14개 끝점 조합×4개 단면56건은 동일한1.3cm 기준을 통과했다. 관측 최대 수치 오차는1.2829285cm(요청 가슴182→추출 메시180.71707)다. 이는 격자 근사 오차이며 실제 사람 측정 정확도가 아니다. 여러 부위의 모든 극단값을 동시에 조합한 외형·실기기 성능까지 보증하지 않는다.

중간 CI #44는 Android 작업은 성공했지만 기존 합성 VTON 테스트1개가 실패했다. 공용 CI 실행기 지연으로80ms짜리 결과가 정상 조회 전에 만료됐고 테스트의 상태 대기가 expired를 종결로 보지 못했다. 운영 API/보관시간/추론 timeout은 그대로 두고 테스트 시계만 제어해 TTL 전/후를 결정적으로 검사하도록 고쳤다. 해당26개 로컬 테스트가 통과했다. 최종 APK 작업은 backend와 서버 기동 매트릭스가 모두 성공해야 시작하도록 gate를 추가했다. 실패한 중간 실행/APK를 최종 성공 배포로 안내하지 않는다.

## 실행 코드와 로컬 최종 확인

- 브랜치: `codex/fashionai-p9-premium-avatar-20261102` (원격 `backup`, 개인 백업 저장소).
- 단계 커밋: `5ce064ec60ebca7d0be4f7daa1f3060bf9350fc6` (cm 데이터·메시), `41fd297cc4869e5cd3d6ca132ff7bb8ba3aebb6d` (줄자·편집·연동), `7de3d7598413c79cddcdfd1c45c88eb74ccc51d5` (끝점·피팅 회귀·CI 보호).
- 최종 로컬 집중 검사: **53개, 실패0·오류0**. 신규 P9 26개(기하13·줄자5·편집6·피팅2)와 기존 관련27개를 포함한다. 서로 겹치는 이전 실행 횟수를 합산하지 않는다.
- 최종 로컬 백엔드 관련 검사: **26개 통과**. actionlint와 `git diff --check` 통과. 변경 파일 검사에서 키 파일·APK·ZIP·빌드 산출물과 일반적인 개인키/토큰 패턴이 없었다.
- 최종 전체 CI: [#45](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41464329727), **7개 작업 모두 성공**. 아래는 다운로드한 실제 보고서와 APK의 확정 결과다.

## 최종 CI·APK 결과

| 항목 | 결과 |
|---|---|
| Android 전체 단위/Compose Robolectric | **391개**, 실패0·오류0·skip0 (P8 365개 + P9 26개) |
| Preview 패키지 선택 회귀 | **155개**, 실패0·오류0·skip0. 전체 검사와 중복이므로 합산하지 않음 |
| Kotlin·계측 테스트 Kotlin 컴파일 / Debug·Preview APK 빌드 | 모두 성공. 기기 계측 테스트 실행은 아님 |
| Lint | Error/Fatal0, Warning166, Hint2. P8 실제 CI 보고서와 ID·메시지·파일 목록 추가0/제거0 (P8 로컬 경고 수와 혼동하지 않음) |
| Linux 백엔드 회귀 | **151개 + 8 subtests 통과**, skip0. APK 배포 도구 검사3개 통과 |
| 서버 기동 | Windows Python3.11/3.12/3.13/3.14, Linux3.12의5개 환경 모두 성공 |
| 실제 기기·실제 GPU·실측/생성 품질 | 미검증. 운영 DB/서버 배포·유료 요청·사용자 데이터 이전 없이 검사 |

[Preview APK 다운로드](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41464329727/artifacts/13151346853) · [테스트/Lint 보고서](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41464329727/artifacts/13151092300) · [BUILD_INFO](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41464329727/artifacts/13152200270). 비공개 저장소 로그인이 필요하며 APK 보존 만료는2026-12-02다.

- 파일: `fashionai-1.1.45.1-preview-vc14501-7de3d759.apk`.
- 로컬 보존: `C:\Users\USER\Documents\ChatGPT\capston\p9-avatar-20261102\fashion_app\build\p9-evidence\final-vc14501\fashionai-1.1.45.1-preview-vc14501-7de3d759.apk`.
- 앱 이름 `FashionAI Preview`, 패키지 `com.jeiu.fashionproject.preview`, 버전 **1.1.45.1-preview / 14501**. 안정 P8의14201보다 증가했다.
- APK SHA-256: `6b7cd5ecef8fdb478f24e7773269c624a166798667d81af77fa9ba5226c728e5`.
- 서명 인증서 SHA-256: `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`. 다운로드한 P8 안정 APK와 일치했다. 새 키를 생성하거나 교체하지 않았다.
- APK manifest·내장 identity·BUILD_INFO의 버전/브랜치/코드SHA가 일치하며 `sourceDirty=false`, `distributionVerified=true`다. 문서만 추가한 후속 HEAD와 APK 구현 SHA를 구분한다.
- APK ZIP CRC와 파일 SHA-256 통과. DEX에서 P9 편집기/줄자/메시/치수 클래스 및 기존 AI/측정 경로가 확인됐다. 이것만으로 실제 GPU/UI 동작을 증명하지는 않는다.
- P8 대비 권한 목록 동일, 네이티브 라이브러리15개 이름·바이트 동일, 이미지/영상/모델 리소스 바이트 동일. Gradle 의존성과 manifest도 변경하지 않았다. 참고 자료·신규 SDK·실제 사용자 사진을 추가하지 않았다.
- CI에서 서명 비밀번호/키 파일 바이트·비공개 설정 파일의 APK 포함 검사를 통과했다. 로컬에서도 키 파일/환경파일명·일반적인 개인키/토큰 패턴이 발견되지 않았다. 키·비밀번호·토큰 원문을 출력하지 않았다.
- 로컬 감사 결과: 같은 폴더의 `P9_APK_AUDIT.json`. 테스트 보고서는 `fashion_app/build/p9-evidence/ci45-reports`에 보존했다. 생성물·APK·외부 참고 파일은 Git에 넣지 않았다.

안정 Preview14201과의 패키지/인증서/상향 버전 조건은 통과했다. 해당 Preview 사용자는 APK를 열어 업데이트할 수 있는 정적 조건을 만족한다. 실제 S25+ 설치는 실행하지 않았으며 기존 원본 FashionAI 앱을 이 APK로 덮어쓰거나 삭제하지 않는다. 업데이트가 거절되면 삭제/초기화로 우회하지 않고 설치본 패키지·서명·버전을 다시 확인한다. 설치 후 마이페이지에서 `1.1.45.1-preview / 14501 / 7de3d759`를 확인한다.

전후 비교 이미지는 로컬 `fashion_app/app/build/p9-visual/avatar-comparison.png`에 있다. 실제 메시 버퍼로 그린 P8/새 정면·측면·후면 비교다. CI와 로컬의 `premium.bin` SHA가 같음을 확인했다. 기본 메시37101개 삼각형/약4.0MB 출력 버퍼이며 전체 앱 메모리나 폰 성능 측정치는 아니다. 이미지 재생성은 개발 환경의 numpy/Pillow로 `python tools/render_avatar_review.py fashion_app/app/build/p9-visual`을 실행한다. 외부 모델·텍스처·폰트 에셋을 받지 않았고 독자 코드의 생성 메시이므로 별도 외부 인체 에셋 라이선스는 없다.

## 다음 개발 순서

1. 이 브랜치에서 `git status`와 HEAD를 먼저 확인하고, 위 APK 구현 SHA와 후속 문서 커밋을 구분한다. 기존 P8/다른 worktree를 초기화하지 않는다.
2. 사용자가 실제 참고 파일을 제공하면 저장소 밖에서만 외형·줄자 조작을 비교한다. 현재 지정 폴더/ZIP이 없어 참고 자료 기반 검토는 미완료다.
3. 아래 S25+ 점검표로 실제 외형·드래그 갱신 지연·화면/GL 복원을 확인한다. 필요하면 그 결과로 LOD/메시 캐시/갱신 빈도와 관절 외형을 조정한다.
4. 합성 범위를 넓혀 여러 부위 극단값의 동시 조합을 추가 검증하고 지원 불가능한 조합은 원본을 유지하며 안내한다. 표시 모델의 허용 범위를 인체 정상 범위로 해석하지 않는다.
5. 실제 측정 정확도나 의복 핏 개선은 별도 동의된 측정 자료·평가 설계 후 진행한다. 운영 연결/데이터 이전/유료 AI/배포는 별도 승인 범위를 유지한다.

## 사용 순서와 보존 경계

1. 기존 체형 시작 화면에서 3D 체형 설정으로 들어간다. 키·몸무게를 입력하거나 A/B/C 원본을 선택한다.
2. `3D 아바타 생성 · cm 줄자로 조절`을 누른다. 기존 프로필이 있으면 출처·원본 참고값과 함께 불러온다. 새 A/B/C 선택은 비교·승인 경로를 유지한다.
3. 부위를 선택하고 줄자를 왼쪽으로 밀면 증가, 오른쪽으로 밀면 감소한다. 숫자·±0.5·직접 입력이 같은 상태를 쓴다. 직접 잰 값 선택 시에만 수동 실측 출처가 된다.
4. 정면/측면/후면·전체 보기와 터치 회전/핀치를 사용한다. 모델을 보면서 하단 컨트롤을 스크롤한다.
5. `비교 후 3D 설정 저장` 또는 `저장 후 가상피팅에 적용`에서 비교창을 승인해야 기기에 저장된다. 미저장 초안 닫기는 확인을 요구한다.
6. 가상피팅에서 저장한 cm 메시를 사용한다. 옷의 수동 위치·회전·크기는 유지하며 자동 맞춤은 명시적 버튼을 눌러야 실행된다. 의류는 기존 2D 오버레이이고 3D 의복 물리 시뮬레이션이 아니다.

옷장·사진 분석·결과·직접 신체 치수·개인정보 동의·사진 AI/유료 FASHN·자체 모델 서버 경로는 유지한다. 자동 추정 cm를 실제 의류 사이즈 측정값으로 자동 승격하지 않는다. Preview의 운영 개인 데이터 동기화 차단 정책도 유지한다. 실제 폰의 기존 앱·대기열·백업을 읽거나 쓰거나 설치/초기화/이전하지 않았다.

## S25+ 후속 확인 (아직 실행하지 않음)

- 설치 전 Preview의 기존 패키지/서명/versionCode 확인. 원본 FashionAI 앱은 별도 패키지이며 삭제하지 않는다. 서명/버전 오류가 나면 중단하고 앱 데이터를 유지한다.
- 홈→체형→3D 경로, 키/몸무게만으로 생성, 7부위 선택, 숫자/드래그 동기화, 초기값/불러온 값 복원을 확인한다.
- 회전·핀치·전후면, 연속 드래그 시 메시 갱신 지연·발열·메모리·프레임률을 확인한다. 앱 전환/복귀·화면 회전·잠금 해제 후 GLES 컨텍스트 복원을 확인한다.
- 작은 화면/큰 글꼴/키보드에서 모델·줄자·저장 버튼 접근과 취소/뒤로 가기 동작을 확인한다.
- 저장→앱 재시작→복원→가상피팅 적용 후 기존 옷 배치 보존을 확인한다. 사진 AI 화면도 접근하되 승인 없는 외부 생성 요청은 하지 않는다.
- 삼성 보안 경고가 나타나면 설치를 중단하고 경고 정보와 APK SHA-256을 확인한다. 시스템 보호 기능을 끄도록 안내하지 않는다.

외형은 연속 메시·매끈한 법선·무광 아이보리 조명으로 개선했지만 실제 고급 브랜드 수준의 시각 품질을 검증했다고 주장하지 않는다. 세부 얼굴·손가락·사용자 실제 신체 복제·의류 충돌/주름·포즈 리깅·실측 정확도 검증은 구현 범위 밖이다. 여러 부위의 극단값 조합, S25+ GPU 및 참고 자료 기반 외형 검토는 후속 작업이다.

## P8 대비 변경 파일 전체 (24개)

- `.github/workflows/android-debug-apk.yml`
- `README.md`
- `backend/tests/test_local_vton_server.py`
- `docs/P9_PREMIUM_AVATAR_HANDOFF_20261102.md`
- `docs/README.md`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/data/AvatarCentimeters.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/data/AvatarProfile.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/data/BodyAvatar.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/data/CmRulerMath.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/data/PremiumAvatarMesh.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/navigation/AppNavigation.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/AvatarApplicationDialog.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/AvatarSetupCard.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/BodyAvatarDialog.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/CmRuler.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/PremiumAvatarEditor.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/BodyMethodSelectionScreen.kt`
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/VirtualFittingScreen.kt`
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P9EditorUiTest.kt`
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P9FittingUiTest.kt`
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P9GeometryTest.kt`
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P9RulerTest.kt`
- `tools/apk_release.py`
- `tools/render_avatar_review.py`
