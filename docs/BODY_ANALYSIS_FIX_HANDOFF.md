# 체형분석 안정화 인수인계

## SAFEPOINT 04 이후 APK 다운로드 지원

- `fix/body-analysis-20261018`에만 GitHub Actions APK 워크플로를 추가했다. 앱 소스는 SAFEPOINT 04 그대로 유지한다.
- PC 다운로드·휴대폰 설치·재실행 절차는 `APK_DOWNLOAD.md`를 따른다. 워크플로 추가 push가 자동 실행 계기이며 아티팩트 이름은 `body-analysis-stabilized-debug`다.
- 워크플로 커밋/push 여부와 원격 빌드/아티팩트 생성 여부는 별개다. 실제 실행 상태는 GitHub Actions에서 확인한다.
- GitHub Actions debug 서명은 이전 빌드와 다를 수 있다. 기존 앱/데이터를 임의로 삭제하지 않는다. 외부 API 키는 포함하지 않는다.

- 기준: `0ddf1970db0dd1fcd9e96820c349878100880a52` (손유리 병합본).
- 작업/복구 브랜치: `fix/body-analysis-20261018`, 개인 저장소 `jimnnieo-svg/capstone-integration-backup`.
- 문서 갱신 시 HEAD/직전 안전한 커밋 SHA: `b64fd6e8d190dd0a662e3ec32d1b6bbf14250305`.
- 마지막 완료 단계: **SAFEPOINT_04**. 이 문서를 포함하는 `SAFEPOINT_04: validate body analysis stabilization` 커밋이 해당 단계의 안전한 소스다.
- 문서에 자기 커밋 SHA를 넣을 수 없으므로 현재 전체 HEAD는 `git rev-parse HEAD`, 단계별 실제 SHA는 `git log --format="%H %s" --grep=SAFEPOINT_`로 확인한다. 원격 동일 브랜치 SHA와 비교한다.
- 새 개발 브랜치를 만들지 않았다. 기존 원격 브랜치의 로컬 추적 체크아웃만 구성했다. 다른 브랜치/팀 저장소/main 변경, reset/merge/force push 없음.

## 단계별 실제 결과
- SAFEPOINT_01: `119ebc8d784e4985ba522732f8b441c0f6144c32` / push: confirmed / Android: {'tests': 121, 'failures': 0, 'errors': 0, 'skipped': 0} / 관절 너비 fallback/가짜 허리/강제 신뢰도 제거, 순수 4유형 분류기·경계 보류, Partial UI, 무효 측정 저장 차단.
- SAFEPOINT_02: `96de0ca456067e047d405e380357781bd6eaa0f7` / push: confirmed / Android: {'tests': 133, 'failures': 0, 'errors': 0, 'skipped': 0} / 중심선 추적·다중 행 중앙값·팔 혼입 거부·힙 구간 측정·마스크 실패 사유, 카메라/갤러리 공통 자세 검사.
- SAFEPOINT_03: `b64fd6e8d190dd0a662e3ec32d1b6bbf14250305` / push: confirmed / Android: {'tests': 158, 'failures': 0, 'errors': 0, 'skipped': 0} / 요청 세대/취소·자원 수명, 입력/동의/실패 UI, 세션·기록·사이즈·코디 가드, FASHN 1회 전송 확인, 히스토리 오차 안내.
- SAFEPOINT_04: 최종 Android/Python 검증, 보고서·시연 체크리스트·APK·전체 소스 백업.
- 실제 Android 결과: {'tests': 158, 'failures': 0, 'errors': 0, 'skipped': 0}. 컴파일을 포함한 testDebugUnitTest 종료 코드 0. 과거 111개 성공 기록을 이번 결과로 대체하지 않았다.
- 현재 단계 커밋 후 같은 원격 브랜치로 일반 push를 시도한다. 직전 단계의 확인된 push 결과는 위 표에 기록했다. 실패하면 로컬 커밋과 소스 ZIP을 복구 수단으로 유지한다.
- 전체 assembleDebug/lintDebug/Python 결과는 SAFEPOINT 04의 최종 보고서를 따른다. 이전 단계에서 미실행한 검증을 성공이라고 해석하지 않는다.

## 현재 변경 파일
```
M capston-second-main/HANDOFF.md
 M capston-second-main/MERGE_SON_YURI.md
 M capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/MyPageScreen.kt
?? capston-second-main/BODY_ANALYSIS_FIX_REPORT.md
?? capston-second-main/BODY_ANALYSIS_TEST_CHECKLIST.md
```

## 남은 작업 / 재개 순서
- 필수 단계 작업을 마쳤다. 최종 보고서/체크리스트의 미검증 항목을 기기에서 확인한다.

## 실행 환경과 명령
- Android 프로젝트: `capston-second-main/fashion_app`. 작업: `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
- 이번 머신의 실행 도우미: `/workspace/body-analysis-work/tools/run_android.py sp01 :app:testDebugUnitTest` (이후 sp02/sp03/final). 로그: `/workspace/body-analysis-work/logs/`. 이 도우미는 Git 외부다.
- JDK/SDK는 `/workspace/body-analysis-work/tools/`에 공식 체크섬/TLS 검증을 유지해 준비했다. Gradle cache는 `/workspace/.cache/gradle`, SDK user 경로는 tools/android-user. 새 머신에 이 경로가 있다고 가정하지 않는다.
- 시스템 Java 신뢰 저장소와 환경 proxy를 지원 옵션으로 사용한다. 비밀 값은 출력·문서·커밋에 기록하지 않는다. .env/local.properties는 커밋하지 않는다.
- Backend 검증: backend에서 `.venv/bin/python -m unittest discover -s tests -v` (실제 결과는 최종 보고서). 운영 DB 접근/마이그레이션/삭제는 수행하지 않는다.

## 확인된 제한 / 미검증
- 분류 임계값 0.92/1.08/0.80 및 경계 ±0.01은 휴리스틱이며 정확도/의학적 체형 표준이 아니다.
- 실제 사람 사진/실기기/에뮬레이터가 제공되지 않았다. 합성 마스크/관절 테스트와 실제 촬영 정확도·실패율을 구분한다. native ML, EXIF 이미지 실행, 외부 API/운영 DB는 별도 검증 대상이다.
- 2D 관절·단일 인물 분할 모델은 겹친 여러 사람이나 모든 자세 회전을 완전히 검출할 수 없다. 헐렁한 옷·소매·가림·약한 경계의 실제 신체 외곽은 추정 한계다.
- 기존 네이버/성별/무신사/쇼핑·관심 옷/날씨·TPO/취향/착용기록 기능을 삭제하지 않는다. 비교 기준은 MERGE_SON_YURI.md와 시작 커밋이다.
- FASHN 클라이언트 BuildConfig 키 구조에는 추출 위험이 있다. 자격증명/서버 인프라 없이 안전한 서버 proxy 배포 완료라고 주장하지 않는다. 상세 결과는 최종 보안/설정 문서를 따른다.


## SAFEPOINT 03 회귀 수정 및 UI 테스트
- 최초 SP03 검사에서 BodySizeRecommendationTest.neverUsesWaistOrHipAsCircumference 1개가 실패했다(133개 중 1개). 전체 측정값 가드를 어깨 비교에 과도하게 적용한 것이 원인이었다. 기존 테스트를 유지하고 어깨·신장·환산에 필요한 정보만 검증하도록 수정했다.
- 이후 단위 테스트 158개 모두 통과. 갤러리 동의 UI 테스트 2개는 compileDebugAndroidTestKotlin 성공, 연결 기기 실행 환경이 없어 실행하지 않았다. 이 머신의 adb 열거는 읽기 전용 사용자 홈 때문에 실패했다.
- 기존 BodyHistoryAnalyzerTest는 분류 변경 문구가 실제 신체 변화가 아니라 사진 추정 분류임을 반영하도록 문자열 기대값 1개만 갱신했다. 테스트를 삭제/비활성화하거나 수치 비교 검증을 줄이지 않았다.
- StyleSuggestionScreen의 고정 모래시계형 배지도 현재 Success 결과에 연결했다. 유효한 결과가 없으면 분석 결과 없음으로 표시하고 기존 스타일 카드는 일반 예시로 구분한다.

## SAFEPOINT 04 최종 실제 결과
- 마지막 MyPage 저장 기록/이번 사진 출처 표시 변경 후 testDebugUnitTest / assembleDebug / lintDebug / compileDebugAndroidTestKotlin 모두 종료 코드 0.
- 단위 158 성공·실패/오류/건너뜀 0. UI 테스트 2개 컴파일 성공·미실행. Lint Error/Fatal 0·Warning 100·Hint 2.
- Backend 11개 성공, Backend 코드 변경/운영 DB 초기화 없음. APK 서명 apksigner verify v2 성공.
- APK: fashion_app/app/build/outputs/apk/debug/app-debug.apk (169432230 bytes). 별도 복사본: /workspace/body-analysis-work/artifacts/body-analysis-stabilized-debug.apk.
- 최종 전체 소스 ZIP은 커밋 후 /workspace/body-analysis-work/backups/ 에 생성한다. 실제 SHA/push 결과 목록은 최종 응답·Git log·/workspace/body-analysis-work/checkpoints.json에 기록한다.
- BODY_ANALYSIS_FIX_REPORT.md / BODY_ANALYSIS_TEST_CHECKLIST.md / BODY_DATA_PRIVACY.md가 최종 제한·보안·설정·시연 절차의 기준 문서다.
- native ML/EXIF 실기기 실행, 실제 사람 분류 정확도/실패율, 겹친 다인·모든 흐림 검출, 외부 API/운영 DB, 클라이언트 API 키의 서버 proxy 배포는 미검증 또는 미구현이다. 완료했다고 주장하지 않는다.
