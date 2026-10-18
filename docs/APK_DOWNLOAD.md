# 최종 발표용 APK 다운로드

applicationId `com.jeiu.fashionproject`, **versionCode3 / versionName1.0-presentation**. 기존 실기기 수정 버전2와 구분한다.

1. 저장소 접근 권한 GitHub 계정으로 로그인한다.
2. https://github.com/jimnnieo-svg/capstone-integration-backup/actions?query=branch%3Afix%2Fbody-analysis-20261018 를 연다.
3. 최종 응답에 적힌 발표 커밋과 같은 SHA의 **Body analysis debug APK** 실행을 선택한다. 과거5de350b/bcc37fc는 workflow context 오류로 실패했으므로 선택하지 않는다. 녹색 성공까지 기다린다.
4. **Artifacts → body-analysis-stabilized-debug** 클릭→ZIP해제→**body-analysis-stabilized-debug.apk**를 꺼낸다. BUILD_INFO.txt의 커밋/버전, SHA256SUMS.txt의 해시, SIGNATURE.txt의 인증서를 함께 보관한다.
5. 소스/재개 문서는 **real-device-fix-source**, 테스트/Lint 보고서는 **real-device-fix-test-reports**다. 보관30일. 만료/일시 실패는 해당 실행 Re-run jobs로 재실행한다(권한 필요).
6. 폰에 APK를 복사해 파일 앱에서 연다. 요청 시 해당 파일 앱의 알 수 없는 앱 설치 허용을 켠다. Android7.0/API24 이상이 필요하다.

같은 인증서이면 versionCode2→3의 업데이트 설치가 가능하다. USB 설치는 `adb install -r body-analysis-stabilized-debug.apk`. `-r`은 기존 데이터를 유지하는 정상 업데이트다. 이전 폰의 인증서가 다르면 실패할 수 있으며 앱을 임의로 삭제하거나 데이터를 초기화하지 않는다. 기존 키/백업 방법을 확인한다. 로컬 버전2·3의 인증서는 같지만 이전 Actions 폰 서명은 알 수 없다. Actions 키 캐시가 유지되면 이후 빌드 서명이 유지되며 캐시 소실/다른 머신은 예외다.

외부 API키는 Actions APK에 넣지 않는다. AI 미설정 안내는 정상이고 수동 배치/로컬 체형분석/옷장/일반 추천을 사용할 수 있다. FASHN 유료 생성은 사전 승인·키/크레딧·전송 동의 후 별도 검증한다. 실제 기기/인물 사진/EXIF·분류 정확도를 이미 확인했다고 해석하지 않는다. 발표 전 PRESENTATION_TEST_CHECKLIST.md를 실행한다.

최종 원격 성공·다운로드는 같은 SHA의 실제 Actions/Artifacts와 최종 전달 응답을 확인한다. 보고서 파일이 존재하는 것만으로 배포 성공을 가정하지 않는다.
