# P19 선택 상품 상세·체형별 제안

## 최신 완료 상태 — 두 경로 구현·전체 CI·Preview APK 검증 완료

- 기준: P18 `801c61493b5ae0d2f0d161217e82a26268a9ff69`, 기존 `codex/fashionai-p17-camera-personal-vton-20261104` 유지.
- 최종 실행/CI/APK SHA: `fe68edbbc6e25f95e1389a289044df2a2637c1a2`. 아래 마지막 문서 갱신은 실행 코드를 바꾸지 않는 `[skip ci]` 커밋이다.
- [최종 CI 41817634762](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41817634762): 7개 Job 전부 성공. P16 결과를 P19 결과로 재사용하지 않았다.
- Preview `1.1.80.1-preview` / `18001` 검증 완료. 기존 17901 APK 보존. 기기 설치·실기기 검증은 수행하지 않았다.
- 시작 C: 여유 공간 23,277,850,624 bytes. 기존 작업·APK·비공개 증거를 삭제하지 않았다.

## 실제 변경

- 쇼핑/관심 상품/스타일 카탈로그 → 선택한 ID의 상세. Android Navigation에 ID를 한 번 인코딩하고 받은 인자를 다시 디코딩하지 않는다.
- 상품 이름·브랜드·이미지·가격·좋아요·공유·판매처를 한 카탈로그 선택으로 통일. 없는 ID는 재선택 안내. 상품 전환 시 화면 상태 분리.
- 고정 평점·리뷰·확인되지 않은 색상/사이즈 옵션을 제거하고 정적 가격 및 판매처 확인 안내로 대체. 홈/날씨의 기존 외부 상품 링크 유지(미사용 상세 콜백만 제거).
- 체형 제안은 현재 Success와 기존 `BodyAnalysisPersistence.mayStore` 검증을 모두 만족하는 네 분류에 적용. None/Partial/분석 중/오류/미지원/불일치는 이유와 일반 예시 표시.
- 성별 미선택은 중립 안내. 기존 옷장 이름·분류에서 확인한 후보만 설명하고 샘플/미보유 항목은 제외. 카탈로그 열람은 개인화 적합성과 구분.
- 저장 스키마·인증·동기화·복원 로직·카탈로그 데이터·OutfitEngine은 변경하지 않았다. 새 서버 기록 조회나 로그인 의존성 없음.

## 팀 참고와 실제 역할

- 팀 `JEC0387/capston-second`의 `411d008ebf542ec04c4966936303a686b4f784e5`에서 상품 필드 연결/정적 가격 안내와 네 체형의 실루엣 제안 개념만 참고. 전체 merge/cherry-pick/화면 복사 없음.
- 제외: 잘못된 ID의 고정 상품 fallback, 호환되지 않는 OutfitEngine 인자, 미선택 성별 기본값, 서버 BodyRecords 조회, 팀 AuthStore/db_api/DbSync.
- 실제 하위 담당자 3명: 상품 구현·테스트(역할 1/2/5), 체형 구현·테스트(3/4/6), 읽기 전용 보안·팀 비교·릴리스 검수(7/8 및 9/10 지원). 총괄이 공통 내비게이션·Gradle·Git·최종 배포검증·인수인계(9/10)를 담당. 10명을 실행했다고 주장하지 않는다.

## 검증 계획 및 경계

- 합성 상품·체형 집중 검사와 기존 진입/추천 회귀를 단일 Gradle 실행자가 실행했다. 결과는 아래에 기록했다.
- 최종 전체 CI에서 Android 전체·Preview(P19 포함)·Backend/서버 시작·Lint·패키지/서명/버전·ELF/ZIP 검증을 수행했다.
- 카메라/갤러리/ADB/기기 설치/개인 사진 전송/외부 AI 생성/운영 DB·Railway 변경을 수행하지 않는다. 실제 공유 게시나 구매도 실행하지 않는다.
- 기기 데이터 해시를 새로 비교하지 않았으므로 실기기 데이터 보존을 새로 검증했다고 주장하지 않는다.
- 유효 전신 분석·측정 정확도·개인 사진용 GPU VTON·실제 16KB 실행·팀 소셜 인증 통합은 이번 완료 범위가 아니다.

## 검증 결과

집중 검사 기준은 WIP `1e0584b000328812487c32f4fd56002edc33e9d8`의 실행 코드다. run02는 같은 실행 코드에서 `P19ProductDetailUiTest`의 스크롤 절차만 수정했다.

| 검사 | 최종 확인 | 근거 |
|---|---:|---|
| P19ProductDetailSelectionTest | 5 통과 | run01 XML |
| P19ProductNavigationTest | 3 통과 | run01, AndroidX 실제 route matcher/디코딩 |
| P19BodyStyleSuggestionsTest | 6 통과 | run01, 4체형/미지원/Partial/품질/성별/옷장 |
| P19StyleSuggestionScreenTest | 1 통과 | run01, Success→Partial 및 실제 상품 ID 콜백 |
| P19ProductDetailUiTest | 1 통과 | run02, A→B→없는 ID, 좋아요/공유/판매처 |
| BodyMethodSelectionUiTest | 7 통과 | run01, 기존 진입·수치 기능 |
| RecommendationIntegrationTest | 9 통과 | run01, 기존 추천 통합 |

- run01: 32개 중 31 통과/1 실패, 종료 1, 7m12s. 상품 UI 브랜드가 작은 시험 화면의 스크롤 아래에 있어 표시 단언 실패. 앱의 잘못된 상품 표시가 아니었다.
- run02: 해당 UI 1개만 재실행, 실패/오류/건너뜀 0, 종료 0, 4m1s. 브랜드·이름·이미지를 실제 스크롤한 뒤 동일 표시 단언을 적용했다. 테스트 삭제나 기대값 완화 없음.
- 고유 검사 범위 32개(신규 P19 16 + 기존 16). 반복 실행 수를 합산하지 않는다.
- 모든 신규 UI 테스트는 Coil 이미지 IO를 차단하고, 공유/구매 Intent는 Robolectric에서 가로챘다. 외부 게시·구매·개인 데이터 접근 없음.
- 로컬 상세 근거는 Git 제외 `fashion_app/build/p19-private/focused-01.log`, `run01-xml/`, `focused-02.log`, `run02-xml/`에 보존.
- 읽기 전용 검수와 `git diff --check` 완료. 보호 파일/경로·서명/배포 규칙 유지. 이번 변경에서 실측 정확도나 실기기 동작을 검증한 것은 아니다.

### 최종 전체 CI — 대상 SHA `fe68edbbc6e25f95e1389a289044df2a2637c1a2`

| 구성 | 실제 결과 |
|---|---|
| 기본 Android 전체 단위 테스트 | 465 통과, 실패/오류/건너뜀 0 |
| Preview 회귀 | 220 통과, 실패/오류/건너뜀 0 |
| 신규 P19 검사 | 각 구성에서 16 통과(서로 합산하지 않음) |
| 기존 P18 합성 좌표 | 기본 전체 검사에 BodyPhoto 8 + CameraPreview 1 포함, 모두 통과 |
| Backend | 151 통과, 하위 검사 8 통과, 경고 7 |
| APK 릴리스 안전성 단위 검사 | 10 통과 |
| 서버 시작 | Windows Python 3.11/3.12/3.13/3.14, Ubuntu Python 3.12 모두 성공 |
| Lint | 오류 0, 경고 165, 힌트 2 |
| 계측 테스트 | 기본/Preview/별도 QA 컴파일 성공, 기기 실행하지 않음 |
| 기존 합성 P9/P10 렌더링 | CI 성공; 모델이나 렌더러 수정 없음 |

XML/Lint/정렬 보고서 원본: [CI 보고서 Artifact](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41817634762/artifacts/13316677454).
로컬 보고서 `fashion_app/build/p19-private/ci-reports/`, 집계 `ci-summary.json`은 Git 제외. CI 성공 후 같은 전체 검사를 다시 실행하지 않았다.

### 배포 산출물 및 안전한 업데이트 사전 검사

- [Preview APK 다운로드](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41817634762/artifacts/13316214085) — 비공개 저장소 로그인 필요.
- 파일: `C:\Users\USER\Documents\ChatGPT\capston\p16-device-20261104\fashion_app\build\p19-release\fashionai-1.1.80.1-preview-vc18001-fe68edbb.apk`.
- 패키지: `com.jeiu.fashionproject.preview`, versionCode `18001`, versionName `1.1.80.1-preview`.
- 인증서 SHA-256: `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`.
- APK SHA-256: `24ade8650d71973575b392ad53ce01544bc125940bd74a7281aa724a54c89b8d`.
- 로컬 `check_apk_update.py`로 보존된 17901 APK와 실제 manifest/인증서 비교: 동일 패키지·동일 서명·버전 증가 확인. 이는 파일 간 호환성 사전 검사이며 현재 휴대폰 설치 상태나 데이터 유지의 실기기 증거가 아니다.
- APK 내부 `assets/build_identity.json`과 `BUILD_INFO.json`, `SHA256SUMS.txt`, 최종 SHA·브랜치·clean source가 일치한다. ZIP CRC 통과, P19 상품 선택/스타일 규칙 클래스·경로·안내 문구 DEX 포함 확인.
- CI의 9개 64비트 ELF 정렬 보고서가 다운로드 APK 해시와 일치한다. 로컬 `zipalign -c -P 16 4`도 통과. 실제 16KB 기기 실행은 미검증.
- `.env`/keystore/jks/p12 APK 항목 없음. CI의 기존 서명 비밀값·키 파일 노출 차단 검사 통과. 신규 코드/테스트에 사용자 사진·토큰·기기 식별값을 넣지 않았다.
- 기존 17901 APK 해시 `95060779ed1af0804762b1c039da2e28d51ea9ba01b53a44b90148b0563e0119`를 다시 확인하여 보존 확인. 사용자 앱 데이터 해시를 새로 검증한 것은 아니다.
- 로컬 근거: `fashion_app/build/p19-release/LOCAL_VERIFICATION.json`, `fashion_app/build/p19-private/update-preflight.txt`. APK·보고서·전체 로그를 Git에 넣지 않았다.
- QA APK는 CI에서 별도 패키지로 산출되었지만 Preview 업데이트 파일로 제공하거나 설치하지 않았다. 실제 업데이트 시 기존 Preview를 삭제/초기화하지 말고 위 Preview APK의 패키지·서명·증가 버전을 확인한다. 설치 오류가 나면 삭제로 우회하지 않는다.

### 남은 항목

- S25+에서 두 화면의 실제 터치·외부 공유 선택기/판매처 실행·업데이트 후 데이터 보존은 미검증. 자동 검사는 합성 데이터와 가로챈 Intent를 사용했다.
- 카탈로그는 정적이며 실시간 가격·재고·원격 이미지 가용성을 검증하지 않았다. 스타일 제안은 규칙 기반 참고이며 실측 핏을 보장하지 않는다.
- 유효 전신 실촬영 분석·실측 오차 검증, 개인 사진용 자체 GPU VTON 실행/Android 결과 수신, 실제 16KB 실행은 기존 외부 조건이 남아 있다.
- 팀 소셜 로그인/인증 전체 통합·운영 DB/Railway 변경은 수행하지 않았다. 이번 결과는 상품 상세·체형 스타일 두 경로의 완료이며 팀 전체 통합이나 앱 전체 개발 완료가 아니다.

### Git 기록

- `1e0584b000328812487c32f4fd56002edc33e9d8`: 검증 전 WIP 체크포인트, `[skip ci]`, Push 완료.
- `fe68edbbc6e25f95e1389a289044df2a2637c1a2`: 집중 검증 후 최종 코드/테스트, Push 및 전체 CI 성공.
- 마지막 문서 갱신도 같은 기존 작업 브랜치에 `[skip ci]`로 Commit·Push한다. 실행 코드와 APK SHA는 위 `fe68edb`이며, 문서 커밋 때문에 재빌드하지 않는다. 최종 HEAD와 원격 일치는 Push 후 `git rev-parse HEAD`/`git ls-remote`로 확인한다.

## 변경 파일

- `.github/workflows/android-debug-apk.yml`: 기존 Preview 검사에 P19 필터 추가.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/navigation/{Screen,AppNavigation}.kt`: 상품 ID 및 전체 분석 상태 연결; 기존 보호 경로 유지.
- 같은 main 패키지의 `data/ProductDetailSelection.kt`, `recommend/BodyStyleSuggestions.kt`: 조회/스타일 규칙.
- 같은 main 패키지의 `ui/screens/{ProductDetailScreen,ShopScreen,StyleSuggestionScreen,WardrobeScreen,HomeScreen,WeatherOutfitScreen}.kt`: 화면 및 실제 호출 연결; 홈/날씨는 미사용 콜백 제거만.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P19{ProductDetailSelection,ProductNavigation,ProductDetailUi,BodyStyleSuggestions,StyleSuggestionScreen}Test.kt`: 합성 집중 검사.
- 이 인수인계 문서. 실제 목록은 기준 커밋 대비 `git diff --name-only 801c614..HEAD`로 확인 가능.
