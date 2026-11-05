# P32 — 실제 생성과 자동 검증을 구분하는 VTON·체형 저장 개선

## 현재 범위

- 기준 P31: `5196e2f83ee68332b51f188523e3176869b34908` (시작 시 로컬·원격 일치, clean).
- 독립 브랜치: `codex/fashionai-p32-vton-body-e2e-20261105`.
- 총괄과 병렬 담당 3명, 실제 총 4명. Android/VTON·Backend/모델·체형/측정·QA/보안/릴리스 책임을 분담했다.
- 실제 GPU·모델·승인 입력이 없는 기존 조건을 재사용했다. GPU 후보 재조사, 다운로드, 라이선스 수락, 유료 호출, 개인 사진 전송, 기기 작업, DB/운영 배포, Actions 실행 없음.
- 이번 수정의 `DEVICE_VERIFIED`, `END_TO_END_VERIFIED`는 **아직 아님**. 과거 P15 공식 예제 실기기 성공은 개인 사진용 자체 모델 성공이 아니다.

## 실제 변경

### VTON 결과·상태·저장

- 작업 ID와 함께 제출 의류의 작은 문자열 메타데이터를 SavedStateHandle에 보존한다. 사진 바이트·토큰은 저장하지 않는다. 기존/손상된 복원 상태에 의류 연결 정보가 없으면 빈 선택과 일치한다고 인정하지 않는다.
- GET 재조회와 화면 재진입 결과를 이전 결과로 표시한다. 전달 의류 목록이 비었거나 여러 벌이어도 원래 제출 의류를 복원하고, 다른 의류를 선택하면 이전 결과·저장 버튼을 숨긴다.
- 자체/외부 AI 화면에서 저장 위치 선택기를 열기 **직전의 결과 바이트**를 고정한다. 대기 중 다른 결과가 도착해도 선택한 이미지를 저장한다. 선택 취소는 스냅샷을 해제하며, 프로세스 손실로 연결이 없으면 다른 결과로 대체하지 않는다.
- 자체 화면에 사용자가 직접 선택한 이전 PNG/JPG 파일을 다시 여는 기능을 추가했다. 실제 디코딩·MIME·8MiB/20MP 상한 및 로컬 문서 범위를 검사한다. 현재 생성 상태에 주입하지 않으며 원본·의류·AI 출처 미확인, 새 생성 아님을 표시한다. 자동 파일 보관/영구 URI 권한은 추가하지 않는다.
- Parser 무결성/CPU 승인/모델 로딩/추론/OOM 안내를 구분한다. 서버 준비 실패를 사진 교체로 해결할 수 있는 문제처럼 안내하지 않는다.

### 서버의 잘못된 성공 처리 방지

- 완료 저널을 디스크에 기록하기 전에 PNG를 공개하지 않는다. 기록 실패는 `journal_unavailable`로 실패 처리하며 새 작업을 차단한다.
- RAM에만 있던 PNG가 서버 재시작으로 사라지면 `expired`로 표시한다. 기존 ID로 다시 조회해도 재생성하지 않는다.
- 취소 기록 실패를 성공 응답 대신 503으로 반환한다. 모델 로딩·추론 실패는 고정 오류 코드로 구분하고 OOM 우선순위/원문 오류 비노출을 유지한다.
- 기존 인증·동의·제한·입력 검증·worker 오프라인 로딩·시간 초과·취소 보호는 유지한다. 이 코드를 운영 서버에 배포하지 않았다.

### 체형 입력 보호

- 저장된 허리 80cm를 `.` 같은 미완성 숫자 초안으로 편집·적용하면 빈 값으로 저장하던 결함을 수정했다.
- 비어 있지 않지만 숫자로 파싱되지 않는 입력은 적용 전에 거절한다. 기존 저장값, 사진 분석 세션, 초안, 편집 화면을 유지한다. 사용자가 의도적으로 비운 optional 항목은 기존대로 허용한다.
- 사진 분석 알고리즘·치수 범위·저장 형식·둘레/정면 너비 구분을 바꾸지 않았다. 실제 촬영 조건/실측 기준이 없어 정확도·MAE·편향을 산출하지 않았다.

## 실패 재현과 검사 기록

모든 신규 이미지/기록은 테스트 샌드박스의 합성 데이터다. Mock 결과를 실제 착장 생성 증거로 사용하지 않는다.

| 기록 | 결과와 해석 |
|---|---|
| Backend 저널 RED | 완료/재시작 2건, 취소 저장 실패 1건 재현 |
| Backend 초기 관련 묶음 | 85 passed / 1 skipped. 이후 worker 단계 구분이 추가되었으므로 이 수치를 최종 코드 전체 결과로 사용하지 않음 |
| Backend 단계 구분 RED | 6 failed / 1 passed; 로딩·추론·OOM 고정 코드 검사 |
| Backend 최종 영향 범위 | 44 passed / 0 skipped, exit 0. worker 단계·실행 보호·서버·저널 4개 파일. 실제 GPU 아님 |
| Android RED01 | 복원 의류가 빈 목록이 되는 실패 1건, exit 1 |
| Android RED02 | 7개 중 5 failed / 2 passed. 의류 연결 없는 복원, 재진입 표시, 숫자 초안 삭제, 이전 파일 열기 부재 확인. 저장 경합 검사는 비동기 완료 메시지를 너무 일찍 확인한 테스트 문제로 먼저 실패했으므로 픽셀 경합 재현 성공으로 계산하지 않음 |
| Android RED03 | 완료 메시지 대기를 수정하고 이전 컴파일 산출물 재사용을 시도했으나 AGP가 컴파일 제외 시 bundle provider 접근을 거부. **테스트 실행 없음**, 복사된 XML은 과거 기록이며 증거로 쓰지 않음 |
| Android GREEN01 | `--tests`가 Lint task 뒤에 배치된 호출 오류. **테스트 실행 없음**. 기대값 변경 없이 GREEN02에서 순서 수정 |
| Android GREEN02 | 74개 중 38 failed. BodyAnalysisIntegrationSafety 25개와 실제 loopback 인증/미준비 1개 통과. Compose 초기화의 `ContentCaptureManager$Companion` 클래스 로딩 오류와 Legacy PNG MIME 오류를 제품 기대값 실패와 구분. Lint는 Test task 실패로 실행되지 않음 |
| Android GREEN03 | 6개 클래스 53 passed / 0 failed / 0 skipped. Native graphics와 클래스별 JVM 적용 후 아래 표의 모든 검사 통과. exit 0, Lint Error·Fatal 0 / Warning 164 / Hint 2. 비동기 로그 검수는 아래 별도 기록 |
| GREEN03 비동기 로그 추가 검수 | exit 0 / Lint Error·Fatal 0, Warning 164 / Hint 2. 단, LocalVtonFlowTest의 Native Coil이 미등록 `content://test-original/…` 입력을 읽어 `ShadowContentResolver.UnregisteredInputStream` 예외 21건을 남긴 것을 별도 확인. 테스트 통과와 무관하게 fixture 결함으로 기록 |
| Android GREEN04 | 앱 코드 변경 없이 합성 PNG 입력 스트림을 매번 새로 열도록 등록. 그러나 `forkEvery=1`과 필터가 비대상 후보 클래스도 개별 JVM으로 탐색해 실행기 64번 이상 생성. 현재 소속·시작 시각을 확인한 이 실행의 daemon/자식만 중단(exit 1). 최종 테스트 결과로 사용하지 않음; 원본 로그 및 NOT-FINAL 표식 보존 |
| Android GREEN05 | 대상 `LocalVtonFlowTest.class` 파일만 include한 하나의 테스트 JVM으로 20 passed / 0 failed / 0 skipped, 비동기 스트림 오류 0건 확인. 기대값·앱 코드 동일; 비대상 JVM 반복을 제거한 실행 환경 수정. exit 0 / BUILD SUCCESSFUL / 영향 Lint 통과 |

- Backend 명령: 기존 venv Python으로 `pytest tests/test_local_vton_worker_stages.py tests/test_local_vton_execution_guards.py tests/test_local_vton_server.py tests/test_local_vton_durable_results.py` (backend 디렉터리).
- Android 명령: `:app:testDebugUnitTest`의 `--tests`에 LocalVtonFlowTest, LocalVtonRepositoryTest, AiTryOnFlowTest, P32ManualBodyDraftUiTest, ManualBodyMeasurementsTest, BodyAnalysisIntegrationSafetyTest, P30LocalVtonLiveContractTest를 지정한 뒤 `:app:lintDebug -PfashionPreview=true`.
- `P30LocalVtonLiveContractTest`는 준비되지 않은 실제 loopback FastAPI의 인증·생성 차단을 검사한다. 새 `P32LocalVtonLoopbackTest`는 실제 HTTP 요청·상태·PNG 수신·파일 저장/재읽기를 **합성 엔진**으로 검사한다. 같은 ID의 중복 요청은 합성 엔진 1회, 다른 의류·ID는 별도 결과로 확인했다. 둘 다 실제 모델 추론 검사가 아니다.
- 로그/XML은 Git 제외 `fashion_app/build/p32-private/`에 분리 보존한다. 실패 기록은 덮어쓰지 않는다. 여러 실행의 반복된 검사 수를 합산하지 않는다.
- 환경 진단: Compose 원본 AAR와 변환 runtime/API JAR에 해당 Companion이 있고 실제 바이트/CRC 읽기도 정상이다(두 변환본 클래스 SHA256 `bf28e2cc837880a5ab3bc16e0c5855809cfceb31da9baf21fb92e29db98ee516`). 최초 ClassNotFound 원인은 미확정이므로 앱 코드를 우회 수정하지 않는다. 후속 검사는 클래스별 새 JVM과 기존 프로젝트 test-runtime 설정을 사용한다.
- Robolectric 4.16.1의 Legacy `ShadowBitmapFactory.decodeByteArray`는 `outMimeType`을 채우지 않는 것을 캐시 바이트코드에서 확인했다. LocalVton 이미지 검사/화면 테스트에 Native graphics를 명시해 실제 PNG MIME/픽셀 검사를 수행하며 앱의 MIME·크기·디코딩 기준은 낮추지 않는다.
- 전체 묶음의 SDK35 제한으로 선택됐던 ManualBodyMeasurementsTest가 실제 결과에 없었다. 후속 실행에서는 해당 전역 필터를 제거하고 이 클래스도 명시해 확인한다. 누락된 검사를 통과 수에 넣지 않는다.

## 검증 수준과 남은 인수 조건

| 기능 | IMPLEMENTED | TESTED | DEVICE_VERIFIED | END_TO_END_VERIFIED |
|---|---|---|---|---|
| 서버 저널·단계 오류 보호 | 예 | 최종 44개 영향 검사 | 아니오 | 실제 모델 경로 아니오 |
| VTON 복원·선택·비교·저장 | 예 | Compose/Repository 및 합성 엔진 localhost 통과 | 아니오 | 실제 자체 모델 입력→저장 아니오 |
| 이전 저장 파일 직접 열기 | 예, 생성 상태와 분리 | PNG/JPG 디코딩·저장/재읽기·화면 검사 통과 | 아니오 | 실제 SAF 공급자/앱 재실행 아니오 |
| 미완성 숫자 적용 거절·기존 치수 보존 | 예 | Compose 3개 + 기존 수동 치수 4개 통과 | 아니오 | 실제 사용자 기록으로 아니오 |
| 실사진 분석/실측 정확도 | 기존 구현 유지 | 기존 P18/P30/P31 합성 결과 재사용 | 유효 전신 입력 미검증 | 아니오 |

- SavedState 복원은 새 Handle을 만든 합성 검사이며 Android 강제 종료·OEM 복원·URI 접근 기한을 실증한 것은 아니다. 저장 파일 재조회는 사용자가 직접 파일을 고르는 방식이다. 자동으로 과거 작업의 원본/의류 출처를 복구하지 않는다.
- GPU: 승인된 CUDA 서버, 고정 모델/Parser 번들, 라이선스 적용 범위 검토, 승인된 비개인 입력이 필요하다. P31 `LOCAL_VTON_SERVER.md`/`LOCAL_VTON_PARSER_BUNDLE.md`의 독립 검증 절차를 재사용한다.
- 실제 AI 인수는 모델 로드→추론→새 PNG→Android 수신→비교→저장→재조회→다른 의류 생성까지 별도로 확인해야 한다. 이번 자동화 결과로 완료 처리하지 않는다.
- 체형 인수는 승인된 전신 사진·키·촬영 방향/거리/회전/해상도·랜드마크/실루엣 품질·동일 종류의 실측 기준을 준비해야 한다. 정면 너비와 둘레를 비교해 오차를 만들지 않는다.
- 주요 버튼·배경 전환·재시작·권한/네트워크 오류의 수동 항목: `P32_USER_FLOW_QA_MATRIX_20261105.md`. 미검사 버튼은 정상이라고 표시하지 않는다.

## APK·Git·데이터 보호

- Android 실행 코드가 바뀌었으므로 기존 18603에는 이번 수정이 없다. 새 후보의 필요성을 인정하며 검증된 코드만 별도 후보로 준비한다.
- 기존 Preview 18602/후보 18603/납품 폴더는 보존한다. 새 버전의 S25+ 설치는 별도 승인 대상이다.
- 기기 데이터의 새 해시 비교·설치·삭제·초기화를 수행하지 않았다. 접근하지 않은 사용자 저장소 전체의 보존을 새로 검증했다고 주장하지 않는다.
- 최종 코드 SHA·Push·APK 상태는 검증 후 아래에 갱신한다. `[skip ci]`, Actions 자동 실행/수동 전체 CI 없음.

## 실제 변경 파일

- `backend/local_vton_server.py`: 완료/취소 저널 실패 차단, 재시작 결과 만료, 단계 오류 코드.
- `backend/local_vton_worker.py`: 로딩/추론 단계 분리 및 민감한 오류 원문 비노출.
- `backend/tests/test_local_vton_durable_results.py`, `backend/tests/test_local_vton_worker_stages.py`: 저널 및 실제 worker 함수 경계의 합성 회귀.
- `backend/tests/p32_android_loopback_server.py`: 명시적 테스트 opt-in과 임시 디렉터리를 요구하는 localhost 전용 합성 엔진. 제품 서버 실행/실제 AI 모델이 아니다.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/data/LocalVtonRepository.kt`: 안전한 이전 파일 열기, 실제 이미지 검사, 단계별 안내.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/TryOnViewModel.kt`: 제출 의류 복원, 결과 연결, 이전 요청 표시.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/LocalVtonScreen.kt`: 복원 선택 유지, 저장 스냅샷, 별도 이전 파일 보기.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/AiTryOnScreen.kt`: 이전 결과 표시와 저장 스냅샷 보호. 유료 요청 실행 없음.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/ManualBodyEntryCard.kt`: 미완성 숫자를 빈 치수로 저장하지 않음.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/LocalVtonFlowTest.kt`, `LocalVtonRepositoryTest.kt`, `AiTryOnFlowTest.kt`: 복원/선택/중복/저장/오류 집중 회귀.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P32ManualBodyDraftUiTest.kt`: 실제 Compose 적용·취소와 합성 저장소 보존 검사.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P32LocalVtonLoopbackTest.kt`: 실제 Repository HTTP→loopback FastAPI→합성 PNG→저장/재읽기, 별도 요청/의류 결과 구분. 실기기·실제 모델 시험 아님.
- `docs/P32_USER_FLOW_QA_MATRIX_20261105.md`, 이 인수인계: 네 단계 검증 수준과 버튼별 수동 인수 조건.

실제 모델을 로드하지 않은 상황의 치명적인 제품 성공 오표시를 허용하지 않는다. 실제 데이터에 대한 검증 범위는 늘리지 않았으며, 새로운 실기기 검사·실측 오차·GPU 성능 수치는 없다.

## 최종 집중 검사 집계

| 클래스 | 통과 | 실패/오류/건너뜀 | 실행 |
|---|---:|---|---|
| LocalVtonFlowTest | 20 | 0/0/0 | GREEN05; 합성 입력 스트림 명시, 비동기 오류 0 |
| LocalVtonRepositoryTest | 10 | 0/0/0 | GREEN03 |
| AiTryOnFlowTest | 15 | 0/0/0 | GREEN03 |
| P32ManualBodyDraftUiTest | 3 | 0/0/0 | GREEN03 |
| ManualBodyMeasurementsTest | 4 | 0/0/0 | GREEN03 |
| P32LocalVtonLoopbackTest | 1 | 0/0/0 | GREEN03; 실제 HTTP + 합성 엔진 |
| BodyAnalysisIntegrationSafetyTest | 25 | 0/0/0 | GREEN02 통과 재사용; 이후 앱 코드 변경 없음 |
| P30LocalVtonLiveContractTest | 1 | 0/0/0 | GREEN02 통과 재사용; 실제 HTTP 미준비 서버 |

서로 다른 Android 집중 검사 **79개**이며 전체 Android/전체 CI를 수행한 것은 아니다. GREEN02의 실패 38건을 성공 수에 더하지 않았다. GREEN03에서 이미 통과한 두 클래스를 반복하지 않았으며, GREEN05의 동일 20개 재검사도 중복 합산하지 않았다.
Compose 클래스 로딩 오류는 클래스별 JVM + 기존 test-runtime 설정을 사용한 GREEN03에서 재발하지 않았으나 최초 환경 오류의 단일 원인은 확정하지 않았다. PNG MIME 검사는 Native graphics에서 기준 완화 없이 통과했다. 제품 코드에 테스트용 그림자나 클래스 로딩 우회는 넣지 않았다.

최종 로컬 Lint: **Error/Fatal 0, Warning 164, Hint 2**. 기존 경고를 제거하거나 억제하지 않았다. GREEN05는 앱/AndroidTest 분석을 재사용하고 바뀐 단위 테스트 분석만 수행했다. 변경된 Kotlin 파일에 보고된 Lint 경고는 없었다. Gradle 10 비호환 deprecated 기능 경고는 기존 9.3.1 실행에 남아 있으며 이번 작업에서 도구 버전을 변경하지 않았다.
기계 판독 집계: Git 제외 `fashion_app/build/p32-private/FINAL_TEST_VERIFICATION_05.json`. 최초 `_VERIFICATION.json`은 스트림 fixture 정리 전 기록으로 보존하며 최종 납품은 `_05`를 사용한다.
