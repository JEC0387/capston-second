# Fashion AI 최종 평가 인수 — 2026-11-07

**평가용 전체 인수 FAIL / 정식 서비스 출시 NO-GO.** QA 18610 설치·데이터 보존·기존 저장 결과 재열기는 통과했다. 이번 상세 참조 적용 신규 AI 요청은 입력 검증에서 실패했으며, 성공 이미지로 대체하지 않았다. P54의 기존 S25+ Genlook E2E 성공은 별도 근거로 보존한다.

## 실제 납품본과 소스

- 브랜치: `codex/fashionai-final-delivery-20261107`; 로컬 커밋만 수행, Push/Merge/Actions 실행 없음.
- Android APK 소스: `1ce86abda2a189f780a51478879cb518fb72327a`.
- 최종 Backend 수정: `61480f104952e04d852cabf2237a7391ffaf5324`.
- APK: `deliveries/FINAL-EVALUATION-QA-18610-20261107/fashionai-1.1.86.10-qa-vc18610-1ce86abd.apk` (worktree의 상위 capston 기준).
- 패키지 `com.jeiu.fashionproject.preview.qa`, 버전 `1.1.86.10-qa` / **18610**, 149043437 bytes.
- SHA256 `d3ad317fe72f8c02524a45e0d664ccf4bdf5000170037842423b4d239712853a`.
- 신뢰 서명 SHA256 `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`.
- 사용자의 버전별 승인 후 QA 18609 → 18610 삭제 없는 업데이트 완료. 기존 QA 18608/18609 APK 및 백업도 유지. Preview 18605 미변경.
- 이후 변경은 Backend뿐이므로 동일 Android APK를 다시 빌드하지 않았다. 정식 Release APK가 아니다.

## 결함·수정·실행 결과

1. 기존 임시 서버의 완료 작업 저널 때문에 신규 연결에서 생성이 계속 막힘: QA 전용 명시적 종료·과금 검토 후 기존 저널을 정확한 바이트로 보관하도록 수정. 원격 취소/환불/삭제를 뜻하지 않으며 인증/비용 검사는 유지. QA 18610에서 실제 보관 처리 확인.
2. 이번 상세 참조는 원본 해시에 묶였으나 API 정규화는 ICC 메타데이터를 제거했다. 독립 회귀에서 바지 원본의 동일 픽셀인데 직렬화 해시가 다른 실패 재현. Provider도 metadata-free RGB PNG로 맞춤. 해상도·방향·RGBA·sRGB 픽셀 불변, 원본/정규화 해시 별도 기록.
3. 위 ICC 수정만으로는 실제 요청 실패가 해결되지 않았다. **초기 설명은 불완전했다.** 앱은 `DeviceModelSpec.maxSide=1024`로 이미지를 준비하지만 시험 서버의 인물/상품 검증은 원본 해시를 요구했다. 두 실제 요청 모두 `invalid_provider_input`으로 종료. 실제 요청 본문은 보존하지 않아 분기별 원문 추적은 없으며, 후속 독립 S25+ 디코더 검사와 소스 계약으로 이 불일치를 재현했다.
4. S25+ 앱과 동일한 디코더·sample 계산의 실제 출력: 인물 512×676, 상의 465×600, 하의 425×600, 신발 600×352. 이는 기존 앱의 크기 제한 결과이며 원본과 같은 해상도/픽셀이라고 주장하지 않는다. 2048 이하 원본을 크기 변경 없이 디코딩한 별도 검사는 원본 픽셀과 같았다.
5. 서버 `ProductReferences`를 수정해 **측정된 모바일 입력 해시와 검토된 원본 상세 이미지 해시를 각각 고정**할 수 있게 했다. 모바일 입력을 임의 원본으로 교체하지 않는다. 상세 crop은 원본의 동일 픽셀로 보존한다. 근사 해시·오류 무시·검증 삭제 없음. 디코더 출력과 상세 4장 연결은 외부 호출 없는 MockTransport에서 검증했다.
6. 사용자가 동일 오류 재발 시 중단을 지시했으므로 추가 실제 생성은 수행하지 않았다. 마지막 수정의 실제 Genlook 생성 효과와 상품 재현 품질은 **미검증**이다. 이번 최종 신규 PNG 없음.

## 비용·안전

- 사용자 승인: 최대 3크레딧 한 세트, 상하의 2 → 중간 육안 검수 → 사용 가능할 때 신발 1. 자동 재호출/결제 없음.
- 최초 입력 차단 후 사용자에게 원인을 알리고 **명시적으로 승인된 재시작 1회**만 실행. 총 Android POST 2개, 모두 로컬 입력 실패. 외부 Provider 원장 작업은 두 원장 모두 0개.
- 잔액 `creditBalance`: **250 → 250, 소비 0**. 이전 P56은 같은 필드로 3 → 0이었고 이후 사용자가 충전을 알렸다. 단위 변경 근거는 없다. 충전 금액/구매 내역 원문을 조회하거나 추정하지 않았다. 현재 250을 무료 잔액이라고 부르지 않는다.
- 공식 기본 해상도 규격은 여러 상품 2크레딧, 단일 1크레딧, 2K는 추가1이다. 이번 설정은 2K 사용 안 함. https://genlook.app/docs/tryon-api/endpoints/create-try-on
- 자동 충전 OFF는 사용자 확인. 키는 서버 `.env`에 유지; 키/QA 토큰/터널 주소/기기 식별값을 이 문서·APK·Git에 넣지 않았다.
- 인증 없는 요청401, 잘못된 토큰401, 유효QA200, 문서경로404, TLS 검증 통과.
- 임시 Quick Tunnel 종료 후 외부 요청 HTTP530 확인. 이 작업의 로컬 FastAPI·게이트웨이도 종료. PostgreSQL은 localhost 전용이며 운영 DB를 사용하지 않았다.
- 새 업로드가 없어 이번 시험의 원격 이미지 삭제 대상 없음. 기존 보관/삭제 정책과 고객 개인정보 처리 동의를 정식 운영 승인으로 간주하지 않는다.

## 실제 검사와 범위

| 항목 | 결과 | 근거·한계 |
|---|---|---|
| QA APK 컴파일·빌드 | PASS | 1ce86ab, 로컬 assembleDebug/lintDebug |
| Android 집중 검사 | PASS | 38개 고유 검사. 실행 반복 수 합산 안 함 |
| Lint | PASS(치명 오류 기준) | 오류0, Warning166/Hint3. 기존 경고가 없는 것은 아님 |
| 서명·패키지·버전·CRC·정렬 | PASS | LOCAL_VERIFICATION/BUILD_INFO/native-alignment/zipalign. 16KB 실제 런타임은 미검증 |
| Backend 최종 회귀 | PASS | 61480f1, 126 passed, 2 warnings. 외부 추론 성공으로 계산 안 함 |
| QA 18610 설치 | PASS | 삭제 없는 업데이트, 41개 파일 설치 전후 전부 일치 |
| 시작·게스트·옷장·피팅 이동 | PASS | 실제 18610에서 게스트 진입·재실행 후 경로 확인 |
| 외부 계정 로그인 | BLOCKED/NOT TESTED | 공급자/운영 인증의 실제 성공 근거 없음 |
| 전신 촬영·유효 체형 분석·치수 정확도 | NOT TESTED | 이번에 촬영/개인 사진 사용 안 함. 승인 입력·실측 없음; P17 사람 없는 촬영은 정확도 증거 아님 |
| 3D 아바타 편집·취소 | 기존 근거 유지 | 이전 단계/이번18609 확인 범위. 18610에서 새 전수 검사로 계산 안 함 |
| 옷장 등록·수정·삭제 | NOT TESTED(이번 최종본) | 기존 기록을 시험용으로 변경하지 않음. 표시·보존만 확인 |
| 코디 추천 | 표시 확인 | 추천 품질 또는 실측 핏 보장 아님 |
| 상의·하의·신발 드래그 | PASS(조작) | 18610 실제 카드 길게 누르기/드롭. 생성 품질과 별개 |
| 신규 마법봉·Genlook 생성 | FAIL | Android 요청 접수 후 invalid_provider_input, 외부 생성 없음 |
| 신규 결과 비교·저장 | NOT TESTED | 신규 결과 없음. P54 성공으로 대체하지 않음 |
| 기존 P54 PNG 재열기 | PASS | 18610 실제 표시, 앱 force-stop/재실행 후 다시 열기 |
| 기존 기록 보존 | 범위 내 PASS/별도 변경 검토 | 원래 옷장11개+시험3개 유지. 설치직후41일치. 사용후 비교는 아래 참조 |

## 데이터·기존 이미지

- 설치 직전 DPAPI 암호화 백업과 파일 해시 확보; 직후 화면 탐색 전 41 same / 0 changed / 0 missing / 0 new.
- 사용 후 설치직후 기준과 비교: 35 same / 6 changed / 0 missing / 1 new.
- 변경: 현재 요청 저널, weather_alert, Android profileInstalled, WorkManager WAL/SHM, DataTransport events DB. 신규: 검토 완료된 이전 QA 요청 보관 저널. DataTransport DB 내용을 전부 감사한 것으로 간주하지 않는다.
- 기존 옷장11개 구조와 값 및 나머지 옷장 설정 동일. 승인된 시험3개 이미지 바이트와 URI 동일. 이전 요청 저널은 정확한 바이트/SHA로 별도 보관.
- 검사 범위: QA shared_prefs/files/no_backup/databases, 지정된 P54 결과 하나. 전체 외부 저장소·SAF·Keystore·다른 앱·서버 데이터까지 보존을 보증하지 않는다.
- 기존 P54 저장 PNG: 896×1184, SHA256 `2e55f86c8b9d08395cea184380209b52578a6d688d329d9d14846f6de6b2d90d`. 새 결과가 아니다.
- P56 상품 세부 불합격(단추·주머니·허리·신발·손)은 이번에 개선됐다고 판정할 근거가 없다.

## 증거·재현

비공개 증거는 `fashion_app/build/final-delivery-private/`에 보존하며 Git에 추가하지 않는다:
- metadata-before.xml/log: 수정 전 실패1.
- metadata-after02.xml/log: 중간 수정94PASS.
- references-mobile01.xml/log: 최종126PASS.
- PIXEL_AND_COLOR_VERIFICATION.json: 원본/정규화 해시와 색상·방향·픽셀 비교.
- android-codec-diagnostic/RESULT.json 및 sample1024/RESULT.json: S25+ 독립 코덱 진단(앱 E2E 대신 사용하지 않음).
- OFFLINE_DEVICE_BINDING_PROOF.json: 모바일 입력/원본 상세 crop 연결 및 Mock 2단계 검사.
- api-storage/jobs.json, 양 Provider 원장, gateway-requests.jsonl: 실제 로컬 실패2개, 외부 생성0개.
- FINAL_NETWORK_AND_CREDITS.json: 잔액250, 터널530.
- install-install-1793985116996811100: 설치 직전 암호화 백업·41개 해시 비교.
- FINAL_POST_USE_COMPARISON.json, FINAL_AUDIT-*: 기록 보존 범위.
- P54_SAVED_REOPENED_ON_18610.json, P54_SAVED_REOPENED_AFTER_RESTART_18610.json: 기존 이미지 재열기 증거.

Backend 재현은 격리 환경에서 `python -m pytest tests/test_genlook_product_references.py tests/test_genlook_p51_pair.py tests/test_genlook_outfit_provider.py tests/test_genlook_p52_progress.py tests/test_genlook_p54_review.py tests/test_genlook_p55_details.py tests/test_genlook_request_contract.py tests/test_outfit_vton_api.py -q` (backend 작업 폴더). 실제 API 호출이 없는 검사이다.

## 최종 차단·책임

- 개발: 수정된 모바일 입력/원본 상세 참조 설정의 실제 재생성 및 결과 품질은 미검증. 현 지시상 동일 오류 재발 뒤 추가 호출은 중단. 기존 실패/지출 원장을 지우거나 자동 재시도하지 않는다.
- 클라이언트+개발: 이후 실제 시험을 재개하려면 목적·설정·비용·시간을 재검토하고 별도 명시적 호출 승인 필요. 이번 문서가 다음 호출을 승인하지 않는다.
- 클라이언트/운영 담당: 평가 시간 가용성이 보장되는 HTTPS 서버, 운영 사용자 인증·사용량 예산·복구·개인정보 동의/보관/삭제 정책. 임시 터널은 운영 SLA 아님.
- 서명 보관 담당: 정식 Release 키 소유·관리/배포 승인 미확정. Preview 키 전용이나 신규 키 생성 안 함.
- 검수 담당: 정상 전신 분석·실측 비교·실제 로그인·최종본 전체 CRUD 등 미검증 기능의 인수. 새 개인 사진 승인을 가정하지 않음.
- 팀 main 통합 제외. 안전한 로컬 커밋 보존만 완료, 원격 일치/팀 전체 통합 완료 주장 안 함.

**납품 가능한 것은 설치 검증된 QA 18610과 로컬 검증된 Backend 수정·증거 묶음이다. 상품 정확도와 이번 신규 E2E, 정식 운영·서명이 미충족이므로 완성품 인수 PASS 또는 정식 출시 GO로 표시하지 않는다.**
