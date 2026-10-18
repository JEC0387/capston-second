# 다음 세션 재개 프롬프트

기존 저장소 jimnnieo-svg/capstone-integration-backup, 브랜치 fix/body-analysis-20261018에서 발표 전 최종 품질 점검을 이어주세요. Android는 capston-second-main/fashion_app, Backend는 capston-second-main/backend입니다. 이번 기준 SHA 5de350b741177ef1c85c08581280d09c54d7c3f1, 문서 작성 직전 안전한 SHA fae4c79acbbf0cf613bed6283924208c9008aa34, 현재 단계 FINAL입니다. 현재 문서가 포함된 실제 SHA는 git rev-parse HEAD로 확인하세요.

처음 git status, branch, HEAD, log -10, remote, 원격 같은 브랜치 최신 HEAD를 확인하고 REAL_DEVICE_FIX_HANDOFF.md / PRESENTATION_QUALITY_REPORT.md / APK_DOWNLOAD.md를 읽으세요. 완료한 SAFEPOINT01~04, SAVEPOINT01~04 및 이번 완료 작업을 다시 구현하지 마세요. 새 브랜치/main/팀 저장소/다른 브랜치 수정·merge·force push·reset --hard·사용자 DB/앱 데이터 삭제는 금지합니다. 예상 밖 Git 상태는 변경하지 말고 보고하세요.

진행:
P0/P1 검증 후 발표 버전3(1.0-presentation)을 준비했다. 최종197개와 Backend11개, 빌드/Lint/UI 컴파일, 로컬 APK 서명·해시 검증을 완료했다. 기존 알고리즘·분류 임계값·저장/추천·옷장·Navigation 등 핵심19개와 Backend를 보존했다. 공식 FASHN 문서 열람과 실제 GitHub API 조회가 가능해져 이전 Actions 실패 원인을 수정했다. 발표 체크리스트·최종 보고서·다운로드 안내를 갱신했다. 최종 커밋 push 후 같은 SHA의 원격 Actions·아티팩트·다운로드·원격 APK 서명을 확인한다. 실제 기기·정상 사진/native ML·EXIF·유료 생성/저장은 미검증이다.

기록:
- INITIAL: `18cf0a2847102a24b44560a5a48caeb632132bdb` / push confirmed
- P0: `bcc37fc3d985564728e6baeeee7e7fc34200648b` / push confirmed
- P1: `fae4c79acbbf0cf613bed6283924208c9008aa34` / push confirmed

검증:
- android: 최종197개성공·실패/오류/건너뜀0; testDebugUnitTest/assembleDebug/lintDebug/compileDebugAndroidTestKotlin 성공
- device: Compose는 Robolectric API35 JVM 실행. 실제 삼성 IME·카메라·정상 사진 EXIF/native ML·정확도는 미검증
- remote: 기존Actions의잘못된runner context를수정후actionlint0오류·실제job/SDK설치성공. 최종이문서포함커밋의원격빌드/아티팩트는push후확인해야함.
- paid_api: 공식 v1.6 문서 열람 성공. URL/data URI·명시 category·1결과1크레딧을 확인했으나 실제 사진/API·계정 요금·인증/크레딧 유효성은 미검증, 유료 호출0.
- ui: Robolectric Compose17개 실제 성공. 미설정 AI는 성공 약속/실행 금지·수동복귀, 미지원신발 안내,360dp높이 수동조작 스크롤을 추가 검증. 실제 기기 IME/native ML/API/저장은 미검증.
- lint_review: 기존100Warning+2Hint를 검토:16KB 미정렬은 동일MediaPipe arm64 라이브러리3건(벤더 교체 필요), StaticFieldLeak2는 applicationContext만 보관, SelectedPhotoAccess1은 기존 권한/URI호환성 검토 대상. 대규모 의존성 업그레이드/무관 리팩터링/경고 숨김 안 함.
- backend: unittest11성공, 격리DB, Backend코드/운영데이터 변경없음
- lint: Error/Fatal0, Warning100, Hint2; 심각도/중복/관련성검토완료. 16KB native 제한은 남음
- apk: com.jeiu.fashionproject / versionCode3 / 1.0-presentation / apksigner성공 / 로컬SHA256 228ce314e5011fc496dde8a2bc3d7c9aebcef6a56c67e0538aef0f40e9dc6400, 이전로컬인증서와동일·폰서명미확인

다음 우선순위:
- 같은 최종 SHA의 Actions 성공·아티팩트 생성·다운로드/서명을 실제 확인하고 전달 manifest에 기록한다. 실패하면 원인을 해결한다.
- 발표 전 삼성폰 설치/기존 서명/IME/카메라/정상 사진·EXIF/전체 사용자 흐름을 체크리스트로 리허설한다.
- 유료 승인 후 FASHN 입력·인증·크레딧·품질·저장을 검증한다. 서버 proxy와 MediaPipe16KB 호환성은 남은 제한이며 무관한 대형 기능을 추가하지 않는다.

대형 기능/무관한 UI 재작성/근거 없는 분류 임계값 변경을 하지 마세요. P0 키 입력·동의·촬영/선택/재시도·저장/추천 안전성을 먼저 검증/저장하고 P1은 확인된 UX 오류만 수정하세요. 수동 배치/AI 생성·미설정은 구분하고 유료 API는 사전 승인 없이 호출하지 마세요. 외부 API가 실패해도 로컬 옷장·체형분석·일반 추천이 유지되는지 점검하세요. 옷장 헤더/필터는 기존 구현을 보존하세요.

Android 검증: fashion_app에서 bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --init-script gradle/test-runtime.init.gradle.kts --console=plain --max-workers=2. JDK21/SDK37.0 사용. 현재 머신의 /workspace/body-analysis-work/tools/run_android.py 도우미를 재사용할 수 있으나 새 환경에서 존재한다고 가정하지 마세요. Backend 격리 unittest는 11개 기준입니다. 기존 Android191개+추가 테스트 수를 구분하고 삭제/skip/검증 약화로 통과시키지 마세요. Compose JVM UI와 실제 휴대폰/native ML/실제 사진 정확도/유료 API를 구분하세요.

P0 후/ P1 후/최종 검증 후 같은 브랜치 commit/일반 push 및 두 문서 갱신을 필수로 하세요. 검증되지 않은 코드는 WIP로 저장하세요. APK applicationId/증가한 versionCode/서명·최종 SHA와 Actions 아티팩트를 확인하세요. GitHub API가 Forbidden이면 기존 허용 초안의 적용 여부를 확인하되 비밀키를 요청/출력하거나 접근 정책을 우회하지 마세요. 원격 성공을 확인하지 못하면 보류로 기록하고 사용자의 Actions 확인 방법을 안내하세요.
