# Current Development Handoff

2026-10-13 (Asia/Seoul) 클라우드 개발 검증 완료. 저장소는 `/workspace/capstone-integration-backup`, Android 프로젝트는 `capston-second-main/fashion_app`이다.

## Git 기준

- 현재 작업 브랜치: `feature/wardrobe-interest-outfit-scope`.
- 현재 완료 HEAD: 이 문서를 포함하는 `feat: add wardrobe filters and outfit source selection` 커밋. 자기 SHA를 커밋 내용에 포함할 수 없으므로 실제 전체 SHA는 `git rev-parse HEAD`로 확인한다.
- 문서 갱신/완료 커밋 생성 직전 실제 HEAD: `e4073d7d8dbc2194b85ca919aca5eac2102e043c`.
- 기준 완료 브랜치: `feature/body-data-privacy-controls`, 기준 커밋: `e4073d7d8dbc2194b85ca919aca5eac2102e043c`.
- `origin`, `backup` 모두 `https://github.com/jimnnieo-svg/capstone-integration-backup.git`. 이번 세션에서 같은 저장소의 `backup` 별칭을 추가했다.
- 원래 클라우드 체크아웃은 clean인 `work` / `016dbacc14cd65ef75a79518536de2bd5024d34d`였다. 최신 원격 feature SHA를 확인한 후 기준 커밋에서 새 작업 브랜치를 만들었다. 기존 체크아웃을 reset/restore/delete하지 않았다.
- `main`, `integration/team-merge`, 기존 완료 feature 브랜치를 직접 수정하거나 force push하지 않는다. `local.properties`, `build/`, `.gradle/`, 캐시, 도구 설치 파일을 커밋하지 않는다.
- 완료 후 push 대상은 `backup`의 `feature/wardrobe-interest-outfit-scope` 하나다. 원격 실제 저장 상태는 `git ls-remote backup refs/heads/feature/wardrobe-interest-outfit-scope`와 로컬 HEAD를 비교한다.

## 완료된 기능

- 옷장의 `전체 / 내 옷 / 관심 옷` 필터. 기본값 전체, 기존 카테고리와 독립적으로 적용하며 화면 상태를 유지한다.
- 내 옷은 기존 `WardrobeStore.items`를 그대로 사용한다. 기존 시연 샘플 등록·의류 추가/수정/삭제·사진 관리·즐겨찾기·DB 동기화를 유지한다.
- 기존 쇼핑 화면의 `likedSet`과 상품 상세의 독립 `isFavorited`를 단일 `ProductInterestStore.ids` StateFlow로 옮겼다. SharedPreferences에 안정적인 상품 식별자 목록을 저장한다. 이전 화면 상태는 영속 저장되지 않았으므로 이전 영속 관심 목록의 마이그레이션 대상은 없었다.
- 쇼핑·상품 상세·옷장 하트는 같은 관심 상태를 관찰한다. 관심 해제 시 목록/추천 후보가 갱신된다. 관심 옷은 채워진 하트와 출처 문구로 표시하고, 보유 옷의 기존 즐겨찾기는 별 아이콘으로 구분한다.
- 옷장 `코디 추천받기`의 잘못된 상품 상세 연결을 고쳤다. 범위 선택 Dialog에서 `내 옷으로만 / 내 옷 + 관심 옷 / 관심 옷으로만`을 선택해 추천 화면으로 이동한다. 설명 및 추천 화면 범위 변경 칩을 제공한다.
- `OutfitSources`가 선택 범위별 후보를 기존 `OutfitEngine`에 전달한다. 오늘·5일 예보·상황별 추천 모두 적용한다. 관심 후보는 WardrobeStore/DbSync에 추가하지 않는다.
- 추천 카드에 관심 아이템 이름과 `내 옷 N개 + 관심 옷 N개` 출처를 표시한다. 빈 목록과 코디 부족 상태에는 실제 등록/쇼핑 화면으로 이동하는 안내를 제공한다.
- 홈 자동 추천, MissingItemFinder는 기존 보유 옷 기준을 유지한다. 날씨/계절/TPO/색상/취향/체형 점수 규칙을 다시 작성하지 않았다.
- WardrobeItem에 기본 true인 임시 후보 속성 `isOwned`를 추가했다. 관심 후보만 false이며, OutfitEngine의 착용 기록 점수 대상에서 제외한다. 관심 옷을 오래 보유하거나 착용했다고 간주하지 않으며 보유 옷의 기존 점수는 유지한다. DB/기존 JSON 스키마는 변경하지 않았다.

## 데이터 범위와 한계

- 기존 8개 쇼핑 상품을 ShopCatalog로 옮겼으며 쇼핑몰 API나 새로운 상품 데이터를 추가하지 않았다. 실제 상품명에 명시된 의류 종류만 분류 메타데이터로 기록했다.
- 카드 배경색은 의류 색상이 아니다. 상품명에 명시된 네이비 셔츠(6405693), 블랙 샌들(3142059), 블랙 팬츠(3304653)만 현재 추천 후보로 사용할 수 있다. RGB는 명시된 색상명을 표현하는 대표값이며 사진에서 측정한 실제 원단 RGB가 아니다.
- 색상이 없거나 여러 색상 중 선택 variant를 알 수 없는 상품은 관심 옷 목록에는 표시하되 추천에서 제외하고 정보 부족을 안내한다. 계절/스타일/핏/기장을 임의로 채우지 않으며 null에 대한 기존 엔진 fallback만 사용한다.
- ID/이름/이미지/명시된 색상 정보가 없거나, 분류가 불명확하거나, 엔진 미지원 분류인 관심상품은 추천에서 제외한다. 엔진이 지원하는 신발은 포함하고 가방/액세서리를 상의·하의로 바꾸지 않는다.
- 쇼핑 상품은 고정 구매 URL을 안정적인 ID로 사용한다. 기존 상품 상세는 별도 시연 상품이므로 `demo:detail-knit-j19`로 구분한다. 기존 상세 화면과 가디건의 공유 구매 URL만으로 같은 상품이라고 합치지 않았다. 상세의 선택 색상/사이즈는 관심상품 variant에 연결하지 않았고 해당 상품은 추천에서 제외된다.
- 보유 옷에는 연결된 productId가 없다. 따라서 동일 관심상품 ID 중복은 제거하지만, 관심 옷과 내 옷을 이름/사진으로 임의 병합하지 않는다.
- 관심 상태는 이 기기의 앱 로컬 저장소이며 계정/서버 동기화는 추가하지 않았다. 옷장/추천 범위는 화면 세션 상태이며 계정 DB에 저장하지 않는다.

## 개인정보·기존 기능 보존

- 유효 체형 측정값은 기존 BodyAnalysisSessionStore 메모리 세션을 우선 사용한다. 저장 동의 시에만 숫자 JSON 저장을 시도하며, 세션 → 동의한 서버 기록 → 기존 추천 순서를 유지한다.
- 체형 보조 점수 최대 +0.25, 어깨 단면 기반 사이즈 추천, ML Kit/MediaPipe 분석, 동의 철회·세션 삭제·서버 기록 전체 삭제 기능을 변경하지 않았다.
- 사진/URI/키의 기존 메모리 전달과 임시 사진 정리, 서버 전송 대기열의 기존 개인정보 범위는 [BODY_DATA_PRIVACY.md](BODY_DATA_PRIVACY.md)를 따른다. 이 문서는 대기열 암호화나 비정상 종료 직후 캐시 삭제를 새로 보장하지 않는다.

## 주요 파일

Android 파일은 `capston-second-main/fashion_app/app/src` 아래다.

- 추가: `main/java/com/jeiu/fashionproject/data/ShopCatalog.kt`, `data/ProductInterestStore.kt`, `recommend/OutfitSources.kt`, `test/java/com/jeiu/fashionproject/OutfitSourcesTest.kt`.
- 수정: `data/WardrobeStore.kt`의 모델 속성, `recommend/OutfitEngine.kt`의 착용 기록 대상 필터, `navigation/Screen.kt`, `navigation/AppNavigation.kt`, `ui/components/OutfitCard.kt`, `ui/screens/WardrobeScreen.kt`, `ShopScreen.kt`, `ProductDetailScreen.kt`, `OutfitRecommendScreen.kt`.
- 루트 `HANDOFF.md` 갱신. Backend, BODY_DATA_PRIVACY.md, HomeScreen, MissingItemFinder, 기존 테스트 파일은 수정하지 않았다.

## 실제 최종 검증

- Linux에서 같은 Gradle wrapper와 다음 작업을 실행: `bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain --max-workers=2`.
- 실행 완료: `BUILD SUCCESSFUL`, 52개 작업 실행, 종료 코드 0.
- `assembleDebug`: 성공. APK: `app/build/outputs/apk/debug/app-debug.apk` (169104250 bytes). APK/build 산출물은 Git에 넣지 않는다.
- Android 단위 테스트: **111개 성공, 실패 0, 오류 0, 건너뜀 0**. 기존 83개 + 새 OutfitSourcesTest 28개. 현재 실행의 `app/build/test-results/testDebugUnitTest/TEST-*.xml` 합계를 확인했다.
- BodyAwareOutfitRecommendationTest 10개, BodyDataPrivacyTest 10개, RecommendationIntegrationTest 9개를 포함한 기존 모든 테스트가 통과했다. 기존 테스트 파일은 기준 커밋에서 변경하지 않았다.
- 새 테스트는 범위/카테고리 조합, 출처/키 구분, 관심 해제 반영, 각 후보군과 빈 상태, 혼합 코디 완성, 정보 누락/불명확 분류/미지원 상품 제외, 실제 카탈로그, 비·면접 제한, 취향·체형 점수, 보유/관심 착용 기록 처리를 검증한다.
- `lintDebug`: 성공. XML 기준 **Error/Fatal 0, Warning 93, Hint 1**. 이전 Windows 인계 기록의 103개 경고와 환경/도구가 다르므로 감소를 코드 개선 효과로 해석하지 않는다.
- Backend 변경 없음. 이번 기능 작업에서 Backend 테스트는 실행하지 않았다.
- `git diff --check` 통과. 관련 없는 변경 파일이 발견되지 않았다.

## 검증 도구와 후속 실행

- 환경 설정 초안/네트워크 정책을 변경하지 않았다. 기본 Java에 javac가 없어 최초 검증은 실패했으며, JDK 자동 다운로드는 api.foojay.io에서 403이었다. 공식 Temurin 21.0.8 배포본의 SHA-256을 확인한 후 저장소 밖 `/workspace/dev-tools/jdk-21.0.8+9`에서 사용해 해결했다.
- 검증용 SDK는 공식 저장소 메타데이터의 체크섬을 확인한 도구로 저장소 밖 `/workspace/dev-tools/android-sdk`에 준비했다. Platform 37.0, build-tools 36.0.0, platform-tools를 사용했다. 사용자 설정 경로는 `/workspace/dev-tools/android-user`, Gradle 캐시는 `/workspace/.cache/gradle`이었다.
- 시스템 Java 신뢰 저장소 `/etc/ssl/certs/java/cacerts`와 환경이 제공한 HTTPS proxy를 Java/Gradle의 지원 옵션으로 사용했다. TLS/체크섬 검증을 끄지 않았다. 비밀 값은 문서나 코드에 기록하지 않았다.
- 이번 머신의 실행 도우미는 `/workspace/dev-tools/run_android.py`, 검증 로그는 `/workspace/onboarding/android-current.log`다. 도구/도우미/로그는 Git에 포함하지 않는다. 새 머신에 이 경로가 남아 있다고 가정하지 말고 설치된 JDK/SDK와 proxy 설정을 확인한다.

## 남은 작업·다음 세션

필수 기능 구현과 build/unit-test/lint 검증은 완료했다. 실기기/에뮬레이터 검증과 실제 서버/운영 DB 접근은 수행하지 않았다.

1. 먼저 `git status`, `git branch --show-current`, `git rev-parse HEAD`, `git log -10 --oneline`, `git remote -v`로 실제 상태와 원격 feature HEAD를 확인한다. 완료 작업을 다시 구현하지 않는다. 새로운 개발은 이 완료 브랜치를 기준으로 새 브랜치에서 진행한다.
2. 후속 검증 우선순위: 실기기에서 쇼핑 관심 표시 → 옷장 범위/카테고리 → 추천 3개 범위 → 관심 해제 → 앱 재시작의 상태 반영, 빈 상태 버튼/뒤로 이동/화면 회전, 저장 거부·세션 체형 삭제 상태를 확인한다.
3. 이후 별도 기능 후보: 실제 상품 variant/색상 메타데이터 확보, 구매·옷장 등록 시 안정적인 productId 연결과 중복 정책, 관심 상태의 계정 동기화 설계. 이번 작업에는 구현하지 않았다.
