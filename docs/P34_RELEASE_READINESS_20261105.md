# P34 출시 준비 및 결함 수정

## 현재 판정: NO-GO (진행 중)

P33 HEAD `19e27459b8841eeddf637c6d968b6f9e7cc78db6`에서 시작했다.
통합 브랜치는 `codex/fashionai-p34-release-readiness-20261105`다.
P32 실행 코드 `9623946d532d23e06c5aac7b2578590623e45d79`의 검사 결과와
18604 납품본은 그대로 보존한다. P34 검사 결과로 과거 결과를 덮어쓰지 않는다.

실제 담당자는 총괄 포함 4명이다. 책임 1/4/8/10은 총괄, 2/3/7은 VTON 담당,
5/6은 체형 담당, 9 및 서명 읽기 전용 검수는 QA 담당이 수행했다.
VTON/체형은 별도 worktree, Gradle·Git·통합은 총괄 한 명이 담당한다.

## 새 결함 및 검증 기록

| 경로 | 재현/원인 | 최소 수정 | 최신 결과 |
|---|---|---|---|
| 자체 서버 취소 | 중복 DELETE·HTTP 대기 취소·종료·timeout 중 취소가 작업자 정리를 중단하고 슬롯을 조기 해제 | 작업별 취소 1회, 독립 엔진 task 종료를 shield로 확인한 뒤 슬롯·종료 상태 처리 | 최종 신규 4 + 영향 기존 5 = 9 통과 |
| Android 취소 | 중복 누름 및 이전 요청의 늦은 취소 응답이 새 요청 상태를 바꿈 | ID별 중복 차단·현재 pending 요청만 응답 적용·scope 취소 전파 | RED 4 중 3 실패 → GREEN 6 통과(추가 경계 2 포함) |
| 직접 입력 저장/삭제 | commit false/예외 및 SharedPreferences 메모리 변경을 성공으로 취급 | 마지막 확인값 유지·보상 쓰기·미확인 값 재조회 차단·삭제 성공 안내 조건 수정 | RED 5/5 실패 → 동일 5 통과 |

백엔드 증거: `fashion_app/build/p34-private/backend/vton-cancel-*.xml/.log`.
최종 `vton-cancel-final-02.xml` 9 passed / 24 deselected, exit 0.
timeout probe의 최초 실패와 보강 첫 실행의 8 pass/1 fail도 보존한다.
첫 보강에서는 timeout 상태가 실제 작업자 종료보다 먼저 보이는 문제가 기존 회귀에서 드러났고,
종료 상태 게시를 정리 이후로 옮겨 최종 재검증했다. 이전/최종 반복 검사 수를 합산하지 않는다.
실제 테스트 자식 프로세스 종료 검사를 포함하지만 CUDA 모델 추론은 아니다.
Android RED 증거: `fashion_app/build/p34-private/root/red01.log` 및 최종 XML.
RED01 9개 중 8 실패/1 통과. GREEN01은 테스트 전 `processDebugMainManifest`에서
기존 Gradle transform 캐시 디렉터리 생성 IOException으로 중단됐다.
약 15.9GB 여유·해당 디렉터리 존재·직접 쓰기/읽기 확인 후 캐시 삭제 없이 GREEN02를 실행했다.
원래 오류의 정확한 발생 원인은 미확정이다. GREEN01 wrapper가 복사한 XML은 이전 RED 자료라
`NOT_FRESH.txt`로 표시했으며 새 검사 결과로 계산하지 않는다. GREEN02는 실행 시작 이후 XML만 수집한다.
GREEN02는 exit 0, BUILD SUCCESSFUL(12m40s), 새 최종 XML 7개/53개 통과,
실패·오류·건너뜀 각각 0이다. 로그의 비동기 Exception in thread/OutOfMemoryError도 0.
`root/FOCUSED_VERIFICATION.json`에 XML과 실행 소스 파일 해시를 기록했다.

| 클래스 | 통과 |
|---|---:|
| P34TryOnCancellationTest | 6 |
| P34ManualBodyPersistenceTest | 3 |
| P34ManualBodyPersistenceUiTest | 2 |
| LocalVtonFlowTest | 20 |
| AiTryOnFlowTest | 15 |
| ManualBodyMeasurementsTest | 4 |
| P32ManualBodyDraftUiTest | 3 |

합성 입력·고장 주입·Compose/Robolectric 검사이며 개인 사진/실기기/실제 모델 생성 검증은 아니다.
저장 실패의 보상 쓰기도 실패할 수 있으므로 물리 디스크 고장·프로세스 강제 종료까지
내구성을 보증하지 않는다. 기존 DB/Auth/저장 스키마/분석 기준/Preview 격리는 변경하지 않았다.

실제 변경 파일: `backend/local_vton_server.py`, `backend/tests/test_p34_local_vton_cancellation.py`,
Android `data/ManualBodyMeasurements.kt`, `ui/components/ManualBodyEntryCard.kt`,
`ui/viewmodel/TryOnViewModel.kt`, 위 P34 신규 테스트 3개 및 이 문서.
이 체크포인트 이후 APK/Lint 검증은 별도 기록하며 전체 CI는 실행하지 않는다.

## 실기기 읽기 전용 사전 검사

P34의 새 기록: `fashion_app/build/p34-private/qa/readonly-20261105-163056-4edcde/P34_READONLY_QA_SUMMARY.json`.
기기 식별값·사진·설정 원문은 이 문서에 넣지 않는다.

| 항목 | 상태 | 실제 확인 범위 |
|---|---|---|
| S25+ ADB 연결·잠금 | PASS | device, 잠금 해제; 기기 serial을 명시해 읽기 전용 조회 |
| 설치 Preview | PASS | `com.jeiu.fashionproject.preview`, 18602, 1.1.86.2-preview; 기존 신뢰 인증서·P26 APK 해시 일치 |
| 설치 QA | PASS | `com.jeiu.fashionproject.preview.qa`, 18001, 1.1.80.1-qa; 기존 신뢰 인증서 일치 |
| 새 데이터 기준 | PASS | Preview 24/24, QA 28/28, 두 조회 간 변경/누락/신규 0 |
| P34 APK 설치·버튼 E2E | BLOCKED | 새 버전별 설치 승인 필요. 이번 조회에서는 설치·UI 조작·강제 종료 없음 |
| 실사진 분석·실측 정확도 | BLOCKED | 승인된 유효 전신 입력 및 실측 기준 없음 |
| 개인 사진 자체 VTON | BLOCKED | 승인된 CUDA 실행 환경·검증된 모델/Parser 자산·입력 조건 미충족 |
| 정식 Release 핵심 흐름 | NOT TESTED | 승인된 정식 서명·배포 환경 미확보 |

해시 범위는 접근 가능한 `shared_prefs`, `files`, `no_backup`, `databases`다.
캐시·외부/공유 미디어·Android Keystore·서버·읽기 불가 영역은 제외하며 비원자적 조회다.
전체 데이터 백업/복원 증명이나 향후 설치 직전 기준으로 확대하지 않는다.
업데이트 승인이 오면 설치 직전 조건과 새 기준을 다시 확인해야 한다.
조회 시 PC 약 16.92GB, 기기 data 약 286.08GB 여유. 기존 파일/앱 변경 없음.

## 정식 서명 확인과 승인 후 절차

- 정식 코드 패키지: `com.jeiu.fashionproject`. Preview 및 QA 패키지와 격리 유지.
- `app/build.gradle.kts`의 release 블록에 signingConfig가 없다. 기존 환경변수 키 연결은 debug 전용이다.
- 프로젝트/백업 작업 루트의 별도 `.jks`/`.keystore`는 발견하지 못했다.
  이 검색은 모든 PC·팀원·Play 계정에 키가 없다는 증거가 아니다.
- `C:/Users/USER/.android/debug.keystore` 존재. 인증서 SHA256
  `a84bf80dff502b0c0ee303ebaff86ab3404e211b7da787336c26cd19a372b8fb`.
  기본 개발 키이며 정식 키로 인정하지 않는다.
- 알려진 `fashionai-preview-signing-20261026.zip`은 존재만 확인했다. 복호화하지 않았다.
  Preview 신뢰 인증서 `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`를
  정식 키로 전용하지 않는다. 키·비밀번호는 Git/APK/로그/Artifact에 넣지 않는다.
- Google Play App Signing 등록 여부·인증서·보관 담당자는 미확인이다.
- 사용자 지시에 따라 새 키 생성·담당자 지정은 별도 명시 승인 전 수행하지 않는다.

승인 전 준비 순서:

1. 관리자가 정식 패키지·직접 APK/Play 배포 방식·기존 서명/업로드 인증서를 확인한다.
2. 기존 키가 없다면 새 키의 소유자, 접근 담당자, 유실/교체 정책과 생성 자체를 승인한다.
3. 승인 후 저장소 밖 제한된 디렉터리에 생성하고 비밀번호는 대화/명령 로그 대신 승인된 비밀 관리 수단으로 입력한다.
4. Windows 계정에만 의존하지 않는 암호화 오프라인 백업 2개와 별도 암호 보관을 준비하고 격리 복구 검사를 한다.
5. 인증서 지문을 고정한 Release 서명·패키지·버전 게이트를 연결한 뒤 비디버그/CRC/SHA/ZIP·ELF/비밀정보 검사를 수행한다.
6. 버전별 설치 승인 후 별도 정식 패키지의 설치·인증·저장·재조회 검증을 수행한다.
   Preview 데이터가 새 패키지로 자동 이전된다고 가정하지 않는다.

기존 primary distribution 차단, Preview 서명 검사, Actions 비용 보호는 유지한다.
정식 앱은 서명 준비만으로 출시 완료가 아니다. 기본 설정의 cleartext 허용 및
서버 URL 검증 정책도 Release 보안 검토 대상으로 남아 있으며 Preview 개발 설정을 그대로 승인하지 않는다.

## 팀·운영 서버·외부 조건

- 읽기 전용 팀 main 조회: `411d008ebf542ec04c4966936303a686b4f784e5`, 기존 비교 기준과 동일.
  P19 상품/스타일, P23 게스트 안전성, P30/P31 SQL 충돌·이력 검증 결과를 재사용한다.
  삭제 위험 AuthStore, X-Device-Id 인증, 팀 SQL 005 일괄 이식은 제외 상태 유지.
- Railway 기본 주소의 GET `/health`, `/api/auth/status`, `/api/tryon/config`가 이번 확인에서 404.
  배포 커밋·환경변수 값·정확한 원인은 이 응답만으로 확정하지 않는다. 운영 변경/사진 전송 없음.
- GPU 부재 재조사·모델 다운로드·라이선스 수락·유료 생성·운영 DB 적용은 하지 않았다.
- 과거 공개 예제 AI 성공은 개인 사진 자체 서버 성공과 다르며, P34 새 생성 증거가 아니다.

## 다음 인수 게이트

새 설치 승인 전에는 기존 Preview 18602/QA 18001 및 18603/18604 납품본을 유지한다.
통합 코드 검사 완료 후 새 후보가 있으면 패키지·버전·신뢰 서명·실행 SHA·해시·네이티브 정렬을 먼저 검증한다.
승인된 QA에서 치수 저장/삭제 실패·취소·재조회, AI 중복 취소·다른 의류·이전 결과 숨김·저장/재열기를 확인한다.
새로운 사진/모델 생성은 해당 입력과 처리 환경 승인이 있을 때만 수행한다.

**GO 조건 미충족:** 실제 자체 모델 신규 PNG, 실측 기준 정확도, 최신 APK 실기기 E2E,
정식 서명·운영 API 호환성. 자동 검사·읽기 전용 ADB PASS로 이 항목들을 대체하지 않는다.
