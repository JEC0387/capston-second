# 1차 작업 보고서 (P0~P2)

작성일: 2026-10-25. 이번 작업의 기준은 업로드된 `capston-second-main (4).zip`이다. 원본 ZIP은 수정하지 않았고, 수정은 별도 `phase1-working/capston-second-main`에 적용했다. ZIP의 SHA-256은 `627fc6b569f131cefd4396afe7dba1eb942ddba906faa200c994c1a3fda5983d`이다.

별도 결과물은 작업 디렉터리의 `capston-second-phase1-modified-20261025.zip`(481개 파일)이다. 빌드 캐시, 테스트 가상환경, APK, `.env`, `local.properties`, 업로드 파일은 포함하지 않았다.

## 비교 기준과 통합 판단

- 요청한 비공개 GitHub 브랜치 `fix/ai-fitting-20261019`에 실제 접근해 커밋 `d9ec44506a56fe6edeb708c66a059b4cf1d63617`을 별도 참조 디렉터리에 복제했다.
- 업로드 ZIP은 471개 파일, 보안 브랜치는 283개 파일이다. ZIP에는 이후 팀 기능인 서버 날씨 예보·네이버 이미지 검색, Railway 기본 서버 주소와 추가 테스트·자료가 있다. 브랜치를 통째로 덮어쓰지 않았다.
- AI 가상피팅의 직접 FASHN API 호출과 APK 내 `FASHN_API_KEY`를 제거했다. 보안 브랜치의 HTTPS 인증 프록시, 개인 토큰, 중복 유료 요청 방지 저널, 요청 한도, 결과 보호, 화면 상태 복구와 공통 AI 화면을 작업본에 적용했다. 서버 `main.py`에는 프록시 등록과 종료 처리만 추가해 ZIP의 팀 엔드포인트를 보존했다.
- 수동 피팅의 의류 선택·위치/크기/투명도 조절, 무신사 상품·구매 화면은 유지하고 AI 생성 부분만 공통 화면으로 연결했다. 날씨·쇼핑·옷장 기능은 이 단계에서 재구현하지 않았다.
- AI 유료 생성은 기본 비활성화다. 서버 키·개인 토큰·HTTPS·Linux 단일 worker·비공개 영속 저널 설정은 `backend/AI_TRYON_SETUP.md`를 따른다. 실제 FASHN 호출이나 운영 배포는 하지 않았다.

## P1: 체형 결과와 직접 입력

- 체형 분석 화면에서 키, 어깨 너비, 가슴·허리·엉덩이 **둘레**, 다리 길이(고관절~발목), 상체 길이(어깨~고관절)를 cm 단위로 직접 입력한다. 키와 치수 하나 이상을 요구하고 범위·비율·NaN을 검증한다.
- 직접 입력값과 사진 추정값의 출처를 분리해 보여 준다. 어깨 너비만 같은 단위로 비교한다. 사진의 허리·엉덩이 **정면 너비**를 직접 입력한 **둘레**로 변환하거나 사진 체형 분류에 섞지 않는다.
- 현재의 사진 분석 실패·판정 보류·사진 기반 사이즈 카드 동작은 유지했다. 새로운 브랜드별 사이즈 규칙은 추가하지 않았다.

## P2: 저장·복원과 기존 기능 연결

- `이 기기에 입력값 저장`은 기본 꺼짐이다. 저장을 선택하면 앱의 비공개 SharedPreferences에 숫자만 저장하고 재실행 후 복원한다. 선택하지 않으면 현재 앱 프로세스에서만 사용하며, 이전에 저장했던 숫자도 지운다. 수동 삭제 기능을 제공한다.
- 직접 입력값은 서버·백업 코드로 전송하지 않는다. Android 자동 백업과 기기 이전 규칙에서도 해당 파일을 제외했다.
- 저장되거나 이번 실행에서 입력한 키는 사진 분석 입력에 사용할 수 있고 마이페이지에서 확인·수정 화면으로 이동할 수 있다. 다리·상체 길이를 모두 입력한 경우에는 기존 코디 비율 계산의 같은 정의를 재사용한다. 현재 사진 분석의 유효한 길이가 있으면 이를 우선한다.

## 변경 파일

| 범위 | 파일 |
|---|---|
| AI 서버·설정 | `backend/main.py`, `backend/tryon_proxy.py`, `backend/tryon_tokens.py`, `backend/AI_TRYON_SETUP.md`, `backend/.env.example` |
| AI 앱·수동 피팅 | `fashion_app/app/build.gradle.kts`, `data/TryOnRepository.kt`, `ui/viewmodel/TryOnViewModel.kt`, `ui/screens/AiTryOnScreen.kt`, `ui/screens/VirtualFittingScreen.kt`, `ui/screens/MusinsaClosetScreen.kt` |
| 체형 입력·연동 | `data/ManualBodyMeasurements.kt`, `ui/components/ManualBodyEntryCard.kt`, `ui/screens/BodyAnalysisScreen.kt`, `ui/screens/MyPageScreen.kt`, `ui/screens/OutfitRecommendScreen.kt`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` |
| 테스트·문서·CI | `backend/tests/test_tryon_proxy.py`, `app/src/test/java/com/jeiu/fashionproject/AiTryOnFlowTest.kt`, `PresentationFittingUiTest.kt`, `ManualBodyMeasurementsTest.kt`, `.github/workflows/android-debug-apk.yml`, `README.md`, `docs/AI_TRYON_STATUS.md`, `docs/README.md`, 이 보고서 |

## 검증 및 남은 범위

- Android `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin`: 성공. 기존 Compose API 사용에 대한 deprecation 경고는 남아 있다.
- 정적 확인: Android 본체와 Gradle 설정의 `FASHN_API_KEY`·직접 FASHN `/v1` 호출 참조 0개. ZIP의 `/api/weather/forecast`, `/api/naver/image-search` 라우트 유지. 보안 브랜치 핵심 AI 코드 8개 파일은 바이트 단위로 동일하다.
- 서버 Python 문법 검사: `main.py`, `tryon_proxy.py`, `tryon_tokens.py` 통과.
- 서버 프록시 Mock: Windows에서 실행 가능한 4개 통과. 전체 39개 중 35개는 보안 브랜치의 Linux 전용 `fcntl` 파일 잠금이 Windows에 없어 `journal_unavailable`로 실패했다. 운영 안전장치를 우회하지 않았다. Linux CI에서 전체 결과를 확인해야 한다.
- Android 전체 단위 테스트 `:app:testDebugUnitTest`: 26개 테스트 클래스, 240개 테스트 통과. 실패 0·오류 0·건너뜀 0. AI 피팅, 수동 화면, 직접 입력 검증·선택적 저장 테스트를 포함한다.
- 실기기 촬영·사진 선택·저장, 실제 FASHN 유료 생성, HTTPS 운영 서버 배포는 검증하지 않았다.
- P3 3D 마네킹과 P4 사이즈 추천 확장은 시작하지 않았다. Git commit·push·merge도 하지 않았다.
