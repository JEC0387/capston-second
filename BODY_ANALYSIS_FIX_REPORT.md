# 체형분석 결함 수정·안정화 최종 보고서

검증일: 2026-10-18 (Asia/Seoul). 대상은 개인 저장소 `jimnnieo-svg/capstone-integration-backup`의 기존 `fix/body-analysis-20261018` 브랜치이며 시작 커밋은 `0ddf1970db0dd1fcd9e96820c349878100880a52`다. 팀 저장소/다른 브랜치/main은 수정하거나 merge하지 않았다.

## 결과와 검증 범위

코드 수정과 아래 자동 검증을 마쳤다. **실제 인물 사진은 제공되지 않았으며 실제 분류 정확도·정상 사진 실패율·실기기 동작은 검증하지 않았다.** 네 체형의 합성 입력/순수 분류 테스트와 실제 사람의 체형 정확도는 다르다. APK 생성·서명 검증은 기기 실행 검증이 아니다.

| 검증 | 이번 작업의 실제 결과 |
|---|---|
| Android 단위 테스트 | **158개 성공, 실패 0, 오류 0, 건너뜀 0** |
| 신규 테스트 | BodyTypeClassifierTest 10 + SilhouetteReliabilityTest 12 + BodyAnalysisIntegrationSafetyTest 25 = 47개 |
| 기존 테스트 | 111개 유지. 히스토리 설명 문구 기대값 1개를 의도된 UI 문구 변경에 맞춰 갱신했고 테스트를 삭제/비활성화하지 않음 |
| 네 체형 | 역삼각형·삼각형·모래시계형·직사각형의 정상 입력 단위 assertion 모두 통과 |
| assembleDebug | 성공 |
| lintDebug | Error/Fatal 0, Warning 100, Hint 2 |
| Backend | unittest 11개 성공, 실패/오류 0. Backend 소스 변경 없음, 운영 DB 접근/초기화 없음 |
| 갤러리 동의 UI 테스트 | 2개 작성, compileDebugAndroidTestKotlin 성공. 기기 실행은 **미실행** |
| Debug APK | `fashion_app/app/build/outputs/apk/debug/app-debug.apk` / 169432230 bytes |
| APK 서명 | apksigner verify 성공, v2 서명 확인 (최종 APK도 별도 재확인) |

최종 명령은 `bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --console=plain --max-workers=2`다. 마지막 UI 출처 표시 변경 후 **BUILD SUCCESSFUL (60 tasks: 14 executed, 46 up-to-date)**를 확인했다. 이번 작업에서 실행한 동일 소스의 결과를 Gradle이 재사용한 항목은 up-to-date이며, 과거 통합본의 111개 결과를 재사용한 주장은 아니다. 현재 XML의 158개 결과와 새 테스트 실행을 확인했다.

Backend는 `backend/.venv/bin/python -m unittest discover -s tests -v`를 실행했다. 독립 SQLite/테스트 앱을 사용한다. 기존 Supabase/운영 DB 통신, 네이버/FASHN/날씨 키 유효성과 외부 API 응답은 검증하지 않았다.

## 수정 전후의 원인과 동작

수정 전은 시작 커밋의 소스 조사로 확인했다. 원래 분류기가 private Android ViewModel 안에 있어 같은 실제 사진/실행 기기로 수정 전후를 비교하지 못했다. 아래는 코드 경로와 합성 회귀 검증의 비교다. 실제 사진 성공률을 비교한 표가 아니다.

| 문제 | 시작 코드의 원인 | 수정 후 |
|---|---|---|
| 역삼각형 편향 가능성 | 실루엣 실패 시 어깨 관절폭/고관절폭으로 강제 분류 | 대체 경로 제거. 관절폭을 체형 너비로 사용하지 않음 |
| 허리 생성 | min(shoulder, hip)×0.78 | 제거. 허리 누락은 판정 보류 |
| 불안정 수치 | fullBodyReliable=false라도 분류 가능 | 유효 외곽·품질·수치 확인 없이는 Success를 만들지 않음 |
| 분류 경계 | 작은 오차도 네 유형 중 하나로 확정 | 원래 0.92/1.08/0.80 유지, ±0.01 경계 보류. 빈도를 맞추기 위한 임계값 조정 없음 |
| 높은 신뢰도 | 관절/대칭 점수를 40~99로 강제 | 강제 범위/segmentation bonus 제거, 관절 검출 참고값으로 표시. 판정 정확도 아님 |
| 실루엣 | 고정 중심 X, 고관절 한 높이 중심 | 몸통 중심선 추적, 다중 행 중앙값·이상값 제거, 힙 구간 대표값 |
| 팔·소매 | 연결된 팔이 어깨 행에 포함될 수 있음 | 관절 위치 대비 과도한 어깨 행을 제외/거부. 너비 자체를 관절폭이나 배수로 보정하지 않음 |
| 자세 조건 차이 | 카메라와 갤러리의 AND/OR·임계값 불일치 | PoseQualityRules 공유, 어깨/힙 붕괴를 독립적으로 거부. 좁은 관절 투영 자체의 과도한 배제를 완화 |
| 다리 비율 | 코~발목 높이, 무릎×2 대체, clamp | mask crown~sole 높이와 hip~ankle 길이로 cm/UI 일치. 누락 비율을 만들어내지 않음 |
| 실패 화면 | Success 내부 fallback과 뭉뚱그린 오류 | 성공/일부 측정·판정 보류/분석 불가 구분, 재촬영·사진 선택·취소·45초 대기 종료 |
| 비동기 | 취소된 native 실패가 늦게 상태를 바꿀 수 있음 | 요청 세대와 1회 terminal publish, decoder/native 직렬화, native 소유가 끝난 뒤 bitmap 해제 |
| 저장/추천 | null 측정으로도 저장 callback이 실행될 수 있음 | 세션·queue·추천 경계의 수치/환산/분류 일관성 검사. 저장 실패는 유효 세션을 지우지 않음 |
| 동의 | 큰 갤러리 영역은 동의 함수를 우회 | 모든 선택 표면/콜백/ViewModel 진입에서 확인, 저장 기본 OFF 유지 |
| 외부 사진 | 피팅 시작에 별도 외부 전송 확인 없음 | FASHN 대상·목적·중간 결과·크레딧 안내 후 사진별 batch 1회 승인 |
| 이전 결과 오인 | 스타일 화면의 고정 모래시계형, 마이페이지 기록 출처 미구분 | 실제 Success 분류만 연결, 일반 스타일 예시 구분, 저장 기록과 이번 사진 출처 표시 |
| 히스토리 | 0.5cm/1%가 실제 신체 변화인 것처럼 해석될 수 있음 | 기존 표시 기준은 유지하고 사진 추정값 차이·오차 한계 안내, 명백히 비정상인 그래프 값 제외 |

처음 SP03에서 전체 측정 가드를 어깨 사이즈 추천에 적용해 기존 `neverUsesWaistOrHipAsCircumference`가 실패했다. 이를 숨기거나 테스트를 없애지 않고 어깨·신장·환산의 사용 정보만 검사하도록 고쳤다. 이후 전체 158개가 통과했다.

## 합성/경계 테스트 상세

- 네 정상 유형, 어깨÷힙 0.92/1.08 및 허리 경계 근처, 경계 안 작은 섭동, 0/음수/NaN/Infinity/누락값을 검사한다.
- segmentation null/unreliable, 원본 비율과 다른 마스크 X/Y 스케일, crown/sole 잘림, 관절-마스크 불일치, 두 번째 큰 전경, 반전된 배경 마스크, 굽힌 무릎/측면/붙은 팔의 거부를 검사한다.
- 몸통 기울임/위치 이동, 힙의 한 줄 이상값, 작은 중심 구멍, 소매 연결, 불안정 허리, 허용 가능한 검출 likelihood, 반복 5회 및 좌우 반전 합성 마스크의 일관성을 검사한다.
- 비트맵/ML Kit/MediaPipe는 기존의 같은 EXIF-normalized bitmap을 사용한다. API 28+ ImageDecoder와 API 24~27 EXIF 1~8 matrix 처리 코드는 시작 버전에서 변경하지 않았음을 비교 확인했다. Native EXIF 실행은 미검증이다.
- 유효 세션 재사용/미동의 저장 차단/저장 실패 보존/잘못된 분류·비율의 저장 차단/일반 코디 fallback/연속 요청·취소·늦은 완료/외부 전송 승인 소모를 테스트한다.
- UI 테스트 2개는 실제 큰 선택 영역/버튼의 동의 Dialog와 기본 OFF를 검사하도록 작성했지만 실행 기기가 없어 **컴파일만 검증**했다.

## 팀 기능 보존과 변경 범위

CameraPreviewScreen, WardrobeScreen, HomeScreen, ShopScreen, UserPreferencesViewModel, WeatherViewModel, ShopCatalog, ProductInterestStore, OutfitEngine, MissingItemFinder, TastePreferenceStore, WearLogStore, WardrobeStore, AddClothingViewModel, NaverImageSearchRepository, ShoppingInsightRepository의 16개 파일은 시작 커밋과 바이트 단위로 같음을 확인했다.

따라서 촬영 UI·자동 캡처, 성별, 네이버 검색, 옷장 3필터, 추천 3범위, 날씨/TPO/취향/착용기록 및 옷 등록 흐름을 삭제하거나 다시 작성하지 않았다. 무신사/FASHN 화면은 승인 Dialog와 안내만 보강했고 API/상품/성별 선택을 유지한다. MyPage는 체형 출처 문구만 추가했으며 성별 로직을 바꾸지 않았다. 코드 보존 확인은 외부 서비스의 실동작을 검증했다는 의미가 아니다.

### 주요 변경 파일

```text
capston-second-main/BODY_ANALYSIS_FIX_HANDOFF.md
capston-second-main/BODY_DATA_PRIVACY.md
capston-second-main/fashion_app/app/src/androidTest/java/com/jeiu/fashionproject/BodyGalleryConsentTest.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/data/BodyAnalysisCompletion.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/data/BodyAnalysisPersistence.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/data/BodyAnalysisSessionStore.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/data/DbSync.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/navigation/AppNavigation.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/recommend/BodyProfileAnalyzer.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/recommend/BodySizeRecommendation.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/camera/CaptureGuide.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/camera/PoseQualityRules.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/components/PrivacyConsentDialog.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/BodyAnalysisScreen.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/BodyHistoryScreen.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/MusinsaClosetScreen.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/MyPageScreen.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/StyleSuggestionScreen.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/AnalysisSafety.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/BodyAnalysisViewModel.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/BodyHistoryAnalyzer.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/BodyMeasurementCalculator.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/BodySegmenter.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/BodyTypeClassifier.kt
capston-second-main/fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/SilhouetteMeasurer.kt
capston-second-main/fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyAnalysisIntegrationSafetyTest.kt
capston-second-main/fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyHistoryAnalyzerTest.kt
capston-second-main/fashion_app/app/src/test/java/com/jeiu/fashionproject/BodyTypeClassifierTest.kt
capston-second-main/fashion_app/app/src/test/java/com/jeiu/fashionproject/SilhouetteReliabilityTest.kt
capston-second-main/BODY_ANALYSIS_FIX_REPORT.md
capston-second-main/BODY_ANALYSIS_TEST_CHECKLIST.md
```

## 개인정보·설정·제한

- 기기 내 체형분석은 외부 AI로 사진을 보내지 않는다. 저장 동의 시에만 새로 검증된 숫자 JSON을 기존 queue에 넣는다. 무효 분석은 새로운 기록이 되지 않으며 기존 유효 기록을 삭제/DB 초기화하지 않는다.
- 기존 기록의 실루엣 품질/촬영 조건을 소급 입증할 메타데이터는 없다. 기존 서버 기록/대기열을 임의로 삭제하거나 정확하다고 재인증하지 않았다.
- SharedPreferences 숫자 대기열의 별도 암호화는 기존에 없으며 새로 구현했다고 주장하지 않는다.
- FASHN/NAVER 등 BuildConfig 클라이언트 키는 APK에서 추출될 수 있다. 키를 삭제/새로 노출하지 않았으며, 자격증명·운영 인프라 없이 안전한 서버 proxy 배포를 완료하지 않았다. 이미 노출된 키는 교체하고 운영 전 인증/속도 제한 proxy로 이전해야 한다.
- 실제 키·비밀 값은 보고서·응답·커밋 메시지에 포함하지 않는다. local.properties/.env/빌드·캐시·도구는 커밋하지 않는다.
- 단일 인물 모델과 2D 좌표만으로 겹친 사람/모든 회전/흐림을 완벽히 판별할 수 없다. 큰 분리 전경과 심한 명암/해상도 결함 등은 검사하지만 느슨한 옷·소매·배경·가림과 실루엣 자체의 바이어스는 남는다.
- 45초 watchdog은 UI 대기를 종료하지만 hung native 작업을 강제로 종료/해제하지 않는다. 지속되면 앱 재실행과 기기 진단이 필요하다.
- 이 머신의 adb는 사용자 홈 `~/.android`가 읽기 전용이라 기기 열거가 실패했다. 연결 기기 실행/사진 정확도/발표 리허설은 미검증이다.
- 설정·설치·키 없는 동작·API 주소·시연 절차는 BODY_ANALYSIS_TEST_CHECKLIST.md, 사진 전송 범위는 BODY_DATA_PRIVACY.md를 따른다.

## SAFEPOINT와 복구

| 단계 | 실제 커밋 | 해당 단계 단위 테스트 | 원격 push |
|---|---|---|---|
| SAFEPOINT_01 | `119ebc8d784e4985ba522732f8b441c0f6144c32` | 121개 통과 | confirmed |
| SAFEPOINT_02 | `96de0ca456067e047d405e380357781bd6eaa0f7` | 133개 통과 | confirmed |
| SAFEPOINT_03 | `b64fd6e8d190dd0a662e3ec32d1b6bbf14250305` | 158개 통과 | confirmed |
| SAFEPOINT_04 | 이 문서를 포함하는 `SAFEPOINT_04: validate body analysis stabilization` 커밋 | 158개, 전체 build/lint/Python 완료 | 커밋 후 같은 브랜치로 push하고 로컬/원격 SHA를 비교 |

문서가 자기 커밋 SHA를 포함할 수 없으므로 최종 실제 SHA는 `git rev-parse HEAD` / `git log --format="%H %s" --grep=SAFEPOINT_`로 확인한다. 최종 응답과 별도 `checkpoints.json`/`SAFEPOINT_COMMITS.json` 산출물에도 4개 실제 SHA와 확인한 push 결과를 남긴다. 새 브랜치/다른 브랜치 수정/merge/force push는 하지 않았다.

단계별 전체 소스 ZIP은 `/workspace/body-analysis-work/backups/SAFEPOINT_XX-<해당 SHA 7자리>-source.zip`에 저장했다. GitHub push 실패 여부와 관계없이 로컬 복구 수단이며, ZIP/도구/생성 APK는 Git에 커밋하지 않는다. 시작 커밋은 보존되며 01→02→03→04를 순서대로 따라갈 수 있다.

최종 APK 복사본: `/workspace/body-analysis-work/artifacts/body-analysis-stabilized-debug.apk`.
SHA-256: `bc20bce6dde710a57b242715387dc4f860a82beecefdd2add90cec6e96ebdc58`.
실기기 검증 및 실제 사진 정확도 미검증을 숨기지 않고 BODY_ANALYSIS_TEST_CHECKLIST.md 절차로 후속 확인한다.
