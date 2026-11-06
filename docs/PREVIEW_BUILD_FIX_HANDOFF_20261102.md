# Preview APK 빌드 오류 수정 인수인계 — 2026-11-02 KST

## 기준과 작업 범위

- 저장소: `jimnnieo-svg/capstone-integration-backup`
- 작업 브랜치: `codex/fashionai-p0-wardrobe-20261102`
- 원격 조회로 확인한 시작 HEAD: `007d3629ab05ef550b0021edbfb4d12df59b9b52`
- 기준 브랜치 `codex/apk-update-diagnostics-20261026` HEAD: `d7d8612328f807382d682ee23038149f8d74b057`
- 팀 저장소 `JEC0387/capston-second`에는 접근/수정하지 않았다. 운영 DB/서버 설정, 서명키, 기본 브랜치를 변경하지 않는다.
- 새 체형 생성/자체 착장 생성 기능은 추가하지 않는다. 아래 마지막 검증 절의 실행 코드 SHA와 CI를 기준으로 이어받는다.

## 실패 원인과 증거

[실패한 Actions 41384225924](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41384225924),
`build` job `125081383384`, `Unit tests` step. 전체 job 로그와 실패 XML을 확인했다.

- Debug APK 빌드와 Kotlin 컴파일은 성공했다. 최초 실제 실패는
  `SampleWardrobePreviewTest.userPhotosAndUnknownResourceNamesRemainUntouched`, 기존 42행의 AssertionError다.
- 총 **295 tests completed, 1 failed**. Gradle의 test task 실패는 이 AssertionError의 결과다.
- `imageUri=.../not_a_sample`은 그대로였다. 그러나 복사해 만든 테스트 항목에는
  `originalUri=android.resource://com.jeiu.fashionproject/drawable/sample_05`도 남아 있었다.
  구현은 이 알려진 원본 URI를 `com.jeiu.fashionproject.preview`로 정상 보정했다.
- 테스트는 알 수 없는 표시 URI뿐 아니라 항목 전체가 같아야 한다고 잘못 기대했다.
  올바른 기대값은 표시 URI와 사용자 필드는 그대로, 알려진 샘플 원본 URI만 Preview 패키지로 바뀐 객체다.
  필드별 안전 보정 동작을 삭제하거나 테스트를 비활성화하지 않았다.
- 원래 실행의 Lint·기기 테스트 컴파일·Preview 패키징/게시 단계는 선행 실패 때문에 실행되지 않았다.
  이들을 원래 실패 원인이나 성공으로 보고하지 않는다. native strip/Node.js 폐기 안내는 위 AssertionError의 원인이 아니다.

## 수정 및 보존성 검증

- `SampleWardrobePreviewTest`: 기대값을 필드별로 바로잡고, 혼합된 개인/샘플 사진 버전,
  수정한 메타데이터, 유사 이름/다른 패키지/쿼리/경로의 비보정, 반복 적용의 동일성을 검사한다.
- `BackupAndRecommendationLogTest`: 샘플 복원 URI 검사가 고정 패키지 대신 실제 `BuildConfig.APPLICATION_ID`를 사용한다.
  Preview에서도 같은 의미의 검사를 수행하기 위한 변경이다.
- `WardrobeSampleRestoreUiTest`: 모든 샘플 사진 버전의 실제 리소스 스트림/이미지 크기 디코딩,
  첫 load 전 복구 요청의 기존 사용자 옷/메타데이터/사진 보존, 삭제 샘플의 자동 재생성 방지,
  명시적 복구 및 재시작 후 중복 방지, 실제 빈 옷장 버튼 동작을 검사한다.
- `BodyMethodSelectionUiTest`: 기존 선택 카드 콜백에 더해 독립 3D 설정에서 기존 모델 열기/닫기와 사진 분석 진입을 확인한다.
  실제 OpenGL GPU 렌더링을 검사한 것은 아니다.
- `WardrobeStore`: 추가 합성 테스트로 손상 JSON을 빈 옷장으로 취급하는 기존 문제를 재현했다.
  이제 읽기 실패 시 loaded 상태를 확정하지 않고 조기 반환해 자동 샘플 저장·파일 정리를 막는다.
  샘플 복구 함수도 정상 load가 완료되지 않았으면 false를 반환한다. 원본 JSON/시드 표시/사진은 유지한다.
  손상된 내용을 자동 복구하거나 데이터를 초기화하지 않는다.
- CI: 기존 전체 주 패키지 회귀 테스트는 유지하고 결과를 먼저 보존한다. 배포하는 Preview에서도
  기존 격리/빌드 정보 검사 외에 샘플·옷장 보존·복원 파싱·체형 선택/사진·3D 메시 테스트를 추가 실행한다.
  서명 Secrets, 신뢰 인증서 검사, 단조 증가 버전 검사, Lint와 테스트 게이트는 유지한다.

## 검증 중 발견한 실패 이력

- 시작 코드의 원래 샘플 테스트 실패를 로컬에서도 재현한 뒤 기대값 수정 후 통과했다.
- 새 옷장 UI 테스트 첫 실행은 LazyColumn의 화면 밖 항목이 아직 생성되지 않아 실패했다.
  스크롤 소유자의 performScrollToNode로 실제 항목을 노출한 뒤 버튼을 클릭하도록 수정했다.
- 손상 JSON 보존 테스트는 기존 구현에서 실패함을 확인하고 위 조기 반환을 추가했다.
- 최종 전체 재검증 결과는 아래에 확정한다. 중간 실패를 건너뛰거나 성공으로 간주하지 않는다.

## 설치본과 서명 기준

사용자가 보고한 설치본: FashionAI Preview / `com.jeiu.fashionproject.preview` /
`1.1.12.1-preview`, code **11201**, Git `041d4f811005de77b645d0bd0ac32dc30ec8325f`.

[기존 성공 실행 40219084167](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/40219084167)의
APK 검증 로그에서도 위 패키지/버전/SHA 및 아래 지문을 확인했다.

- 인증서 SHA-256: `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`
- 기존 Preview APK SHA-256: `f473eddcd42f1196aba87bb96a50c274e48b42d0e8755bbbcdc20c2ce334d228`

새 APK는 같은 패키지·인증서와 11201보다 높은 code를 모두 만족해야 한다. 기존 Preview 서명키를
읽어 출력하거나 교체하지 않으며 CI의 관리된 기존 키를 사용한다. 로컬 기본 Debug 키의 APK는 배포하지 않는다.

## 다음 세션과 실기기 주의사항

1. 마지막 CI 확정 절의 SHA·Artifacts·BUILD_INFO를 대조한다. 원래 실패가 수정됐는지부터 반복 개발하지 않는다.
2. 사용자 기기에서 삭제/초기화 없이 업데이트하고 옷장·사진·즐겨찾기·착용/코디 기록을 확인한다.
3. 빈 옷장에서는 샘플 버튼으로 12벌만 추가되는지, 기존 옷장이 있으면 덮어쓰지 않는지 확인한다.
4. 체형 선택→사진/3D→뒤로, 기존 사진/갤러리 동의, 아바타 조작, 수동 피팅/코디 추천을 확인한다.
5. 실기기 GPU·카메라/갤러리·실제 이미지 품질·설치 후 데이터 유지 결과는 아직 미검증이다.
6. Preview의 개인 API 차단 정책은 유지한다. 운영 데이터 이전·동기화/배포·유료 AI 검증은 별도 승인 없이는 실행하지 않는다.
7. 손상된 로컬 JSON을 발견하면 원본과 사진을 보존한 채 별도 복구 절차를 마련한다. 이번 수정은 샘플 복구의 덮어쓰기를 막는 것으로, 모든 저장 경로의 손상 복구까지 구현한 것은 아니다.

Preview BUILD_INFO/체크섬은 기존 validation-info 아티팩트에도 별도 포함한다. 검증 전용 주 패키지 정보는
`fashion_app/build/validation-apk/`, 배포 Preview 정보는 `apk-artifact/` 경로이므로 서로 혼동하지 않는다.
APK ZIP 자체와 내부 APK의 SHA-256은 별개이며 내부 APK는 `apk-artifact/SHA256SUMS.txt`와 대조한다.

### 이번 변경 파일

```text
.github/workflows/android-debug-apk.yml
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/WardrobeStore.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/SampleWardrobePreviewTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/BackupAndRecommendationLogTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyMethodSelectionUiTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/WardrobeSampleRestoreUiTest.kt
docs/PREVIEW_BUILD_FIX_HANDOFF_20261102.md
docs/README.md
docs/APK_DOWNLOAD.md
```

## 로컬 검증 체크포인트

- JDK 21 / Android SDK android-37.0 / build-tools 36.0.0 / 기존 Linux 테스트 init script 사용.
- `testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin lintDebug`: **BUILD SUCCESSFUL**.
- 전체 **303개 / 43개 클래스**, 실패·오류·건너뜀 0(기존 295개와 신규 8개).
- Lint Fatal/Error 0, Warning **152**, Hint 2. 경고를 없앤 것으로 보고하지 않는다.
- 배포 안전성 Python 도구 단위 테스트 **3개 성공**.
- Compose UI는 Robolectric JVM에서 실행했다. 기기 테스트는 Kotlin 컴파일만 했고 연결 기기에서 실행하지 않았다.
- Preview 별도 검사와 최종 원격 결과는 아래 마지막 절에서 확정한다. 로컬 기본 서명 APK는 배포용이 아니다.

### 재실행 명령

`fashion_app/`에서 JDK/SDK 환경을 준비하고 다음을 실행한다. 전체 결과를 보존한 뒤 Preview를 실행해야 덮어쓰이지 않는다.

```bash
bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:lintDebug --init-script gradle/test-runtime.init.gradle.kts --no-daemon --console=plain --max-workers=2
bash gradlew :app:assembleDebug :app:testDebugUnitTest -PfashionPreview=true --tests '*PreviewIsolationTest' --tests '*AppBuildInfoTest' --tests '*SampleWardrobePreviewTest' --tests '*WardrobeSampleRestoreUiTest' --tests '*BackupAndRecommendationLogTest' --tests '*BodyMethodSelectionUiTest' --tests '*BodyPhotoEntryUiTest' --tests '*BodyAvatarTest' --init-script gradle/test-runtime.init.gradle.kts --no-daemon --console=plain --max-workers=2
```

Preview는 개인 API를 차단하는 배포 변형이므로 원래의 주 패키지 전체 회귀는 첫 실행에서 검증하고,
두 번째 실행에서는 Preview 격리와 패키지별 사진/화면/보존 동작을 검증한다. 기존 검사를 삭제하거나 무시하지 않는다.

로컬 Preview 별도 검사도 **43개 / 8개 클래스, 실패·오류·건너뜀 0** 및 Preview APK 빌드 성공.

## 사용자 업데이트 절차 — 앱 삭제/초기화 없음

1. 마지막 CI 확정 절에 링크한 **성공 실행의 Preview APK** ZIP을 받는다. 로컬 code 4 검증 APK나 주 패키지 APK를 사용하지 않는다.
2. 압축 안 APK·BUILD_INFO.json/txt·SHA256SUMS.txt를 함께 보관한다. Windows에서는
   `Get-FileHash .\실제파일명.apk -Algorithm SHA256`을 동봉된 APK 체크섬과 비교한다.
3. package=`com.jeiu.fashionproject.preview`, code>11201, 서명 지문=`f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`,
   sourceDirty=false, Git SHA가 최종 실행 코드와 일치하는지 확인한다.
4. 기기에서 APK를 열어 **업데이트**하거나 확인한 APK에만 `adb install -r 실제파일명.apk`를 사용한다.
   이번 세션에서는 이 설치 명령을 실행하지 않았다. `-r`은 정상 데이터 유지 업데이트 옵션이다.
5. 서명/버전 불일치가 나오면 중단한다. 앱 삭제·데이터 초기화·강제 다운그레이드로 우회하지 않는다.
6. 업데이트 후 마이페이지의 패키지/버전/code/Git SHA를 대조하고, 기존 옷·사진·즐겨찾기·착용/코디 기록을 확인한다.
   기존 개인 옷장이 있으면 샘플 버튼 테스트를 위해 비우지 않는다. 빈 옷장 조건의 검사는 별도 시험 환경에서 진행한다.
7. 체형 선택 화면의 사진 분석과 3D 설정, 뒤로 이동, 기존 촬영/갤러리 동의, 모델 조작, 수동 피팅과 추천을 확인한다.
8. Preview의 개인 API 차단 표시를 유지하고 기존 운영 계정 복구 코드를 입력하지 않는다. 유료 AI 생성은 별도 승인 전 실행하지 않는다.

패키지·공개 인증서·버전의 일치는 정상 업데이트의 사전 조건이다. 실제 S25+의 설치·데이터 유지 결과를
대신 증명하지 않는다. 실기기 결과를 다음 세션에 기록하되 사용자 사진·토큰·원본 JSON은 공개 로그에 올리지 않는다.
아티팩트가 만료되면 최신 브랜치에서 새 workflow 실행으로 버전을 발급한다. 오래된 실행 재시도는
다른 브랜치/최신 배포보다 낮은 code가 되어 배포 안전 검사에서 거절될 수 있다.

## 최종 원격 검증 확정 — 2026-11-02 13:55 KST

- APK 실행 코드 Commit: **`f4f72c7938be77180da0bbdaaea9bf0ca1b09078`**.
- [작업 브랜치](https://github.com/jimnnieo-svg/capstone-integration-backup/tree/codex/fashionai-p0-wardrobe-20261102): `codex/fashionai-p0-wardrobe-20261102`.
- [최종 Actions 41389809309](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41389809309):
  **completed / success, 7개 작업 전부 성공**. 주 패키지 빌드/테스트, 기기 테스트 Kotlin 컴파일,
  Lint, Preview 빌드/테스트, 서명·버전 검증, 게시 및 후처리까지 성공했다.
- 원격 테스트·Lint ZIP을 다운로드해 XML을 직접 집계했다.
  - 전체 주 패키지: **303개 / 43개 클래스**, 실패·오류·건너뜀 0.
  - 별도 Preview: **43개 / 8개 클래스**, 실패·오류·건너뜀 0. 전체 결과를 별도 보존했으며 두 결과를 합쳐 고유 테스트 수로 보고하지 않는다.
  - 원격 Lint: Fatal/Error 0, Warning **165**, Hint 2. 로컬 152개와 차이 13개는
    `AndroidGradlePluginVersion` 2개와 `GradleDependency` 11개 분류의 개수 차이로 확인했다.
    버전 알림을 해결하거나 의존성을 교체하지 않았다. `Aligned16KB` 등 기존 잔여 경고의 실제 기기 영향은 미검증이다.
- 백엔드 로그: **105 passed, 8 subtests passed, 7 warnings**. 배포 안전성 도구 **3개 통과**.
- 서버 기동: Linux Python 3.12, Windows Python 3.11/3.12/3.13/3.14 전부 성공.
  DB 없는 기동과 접속 불가능한 시험 DB 주소를 확인한 것이며 운영 DB에 연결하지 않았다.

### 게시된 Preview APK

- [Preview APK 다운로드](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41389809309/artifacts/13117431112)
  - artifact: `fashionai-preview-apk-vc12301-f4f72c7938be77180da0bbdaaea9bf0ca1b09078`
  - 파일: **`fashionai-1.1.23.1-preview-vc12301-f4f72c79.apk`**
  - artifact ID `13117431112`, zip 170,059,137 bytes, **2026-12-02 13:53 KST 만료 예정**.
- [BUILD_INFO·체크섬 별도 다운로드](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41389809309/artifacts/13117585869)
  - artifact ID `13117585869`, Preview 정보는 `apk-artifact/` 경로.
- [전체·Preview 테스트와 Lint 보고서](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41389809309/artifacts/13117471131)
  - artifact ID `13117471131`, 전체 결과는 `fashion_app/build/primary-test-results/`,
    Preview는 `fashion_app/app/build/test-results/testDebugUnitTest/` 경로.
- 비공개 저장소이므로 권한이 있는 GitHub 계정으로 다운로드한다.

### 실제 APK 검사와 BUILD_INFO 대조

실제 APK의 `aapt` manifest/`apksigner verify`·공개 인증서·내장 identity 검사와 APK SHA 계산은
**CI에서 실행**했다. 이 환경은 APK 직접 다운로드 경로가 제한돼 대용량 APK를 로컬에서 다시
검사하지는 않았다. 별도 metadata/test ZIP은 내려받아 직접 분석했으며 CI APK 검사 출력과
Preview BUILD_INFO.json/txt/SHA256SUMS의 정확한 일치를 확인했다.

| 항목 | 기존 11201 배포 APK 검증 기록 | 새 게시 APK |
|---|---|---|
| Package | `com.jeiu.fashionproject.preview` | 동일 |
| Version | `1.1.12.1-preview` | **`1.1.23.1-preview`** |
| Version Code | 11201 | **12301**, 증가 |
| 인증서 SHA-256 | `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9` | 동일 |
| Git SHA | `041d4f811005de77b645d0bd0ac32dc30ec8325f` | `f4f72c7938be77180da0bbdaaea9bf0ca1b09078` |
| Dirty / Distribution verified | false / true | false / true |

새 **APK 파일** SHA-256:
`33e26b34086d55512853f167e458554b77681ed355900b8357e33f557ac0befe`

위 값은 GitHub artifact zip digest와 다르다. BUILD_INFO는 APK의 실제 manifest와 내장
`assets/build_identity.json`, checkout SHA/브랜치/버전, 신뢰 서명 지문을 검사한 결과다.
기존 `tools/check_apk_update.py`의 compatibility 함수로 위 두 배포 메타데이터를 비교해
**COMPATIBLE: package, signer and version pass**를 확인했다.
현재 S25+ APK를 직접 읽거나 설치하지는 않았으므로 실제 설치·데이터 유지 성공으로 보고하지 않는다.

서명 비밀번호·키 파일이 APK에 들어가지 않았는지 기존 게시 검사도 성공했다. 변경 9개 경로의
민감 파일/빌드 산출물 검사, 추가 내용의 고신뢰 비밀 패턴 검사, `git diff --check`도 통과했다.
기존 workflow의 localhost 시험용 DB 자격정보는 변경하지 않았고 운영 비밀번호/API 키는 추가하지 않았다.
키스토어/사용자 사진/원본 사용자 데이터/APK를 Git에 넣지 않았다.

### 최종 보존 및 다음 시작 지점

- 기준 브랜치 `codex/apk-update-diagnostics-20261026`은 `d7d8612328f807382d682ee23038149f8d74b057`,
  P2/P6 `codex/p2-p3-core-upgrade-20261025`는 `ff58d48165bd25020dd941ddce467a1b677865f0`,
  기존 백업/P0·P1 브랜치도 원격 조회로 그대로임을 확인했다.
- 팀 저장소 수정, 사용자 데이터 삭제/초기화/강제 덮어쓰기, 서명키 교체, 운영 설정/DB 변경,
  유료 API, Merge, Force Push는 수행하지 않았다.
- 남은 단계는 위 사용자 업데이트 절차에 따른 **실제 S25+ 업데이트·데이터 유지·사진/3D/피팅/추천 수동 검증**이다.
  손상 JSON의 일반 복구 도구, 16KB/사진 권한/수명주기 경고 점검은 별도 후속 작업이다.
- 이 절을 확정하는 후속 커밋은 **문서만** 변경한다. 최신 브랜치 HEAD와 검증된 APK 코드 SHA를
  구분하고, 문서 자체의 확정 커밋은 `git log -1 --format=%H -- docs/PREVIEW_BUILD_FIX_HANDOFF_20261102.md`로 확인한다.
