# P19 선택 상품 상세·체형별 제안

## 현재 상태 — 집중 검사 완료 / 최종 CI 대기

- 기준: P18 `801c61493b5ae0d2f0d161217e82a26268a9ff69`, 기존 `codex/fashionai-p17-camera-personal-vton-20261104` 유지.
- 실행 코드와 집중 검사를 완료했다. 최종 전체 CI·새 APK 검증은 아직 완료하지 않았다. P16 결과를 P19 결과로 사용하지 않는다.
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

- 합성 상품·체형 집중 검사와 기존 진입/추천 회귀를 단일 Gradle 실행자가 실행한다. 결과는 아래에 후속 기록한다.
- 최종 전체 CI는 Android 전체·Preview(P19 포함)·Backend/서버 시작·Lint·패키지/서명/버전·ELF/ZIP 검증을 수행한다.
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

대기: 최종 CI, APK 산출/서명/소스 SHA/해시/업데이트 호환성.

## 변경 파일

- `.github/workflows/android-debug-apk.yml`: 기존 Preview 검사에 P19 필터 추가.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/navigation/{Screen,AppNavigation}.kt`: 상품 ID 및 전체 분석 상태 연결; 기존 보호 경로 유지.
- 같은 main 패키지의 `data/ProductDetailSelection.kt`, `recommend/BodyStyleSuggestions.kt`: 조회/스타일 규칙.
- 같은 main 패키지의 `ui/screens/{ProductDetailScreen,ShopScreen,StyleSuggestionScreen,WardrobeScreen,HomeScreen,WeatherOutfitScreen}.kt`: 화면 및 실제 호출 연결; 홈/날씨는 미사용 콜백 제거만.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P19{ProductDetailSelection,ProductNavigation,ProductDetailUi,BodyStyleSuggestions,StyleSuggestionScreen}Test.kt`: 합성 집중 검사.
- 이 인수인계 문서. 실제 목록은 기준 커밋 대비 `git diff --name-only 801c614..HEAD`로 확인 가능.
