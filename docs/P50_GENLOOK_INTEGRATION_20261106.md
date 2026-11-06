# P50 — Genlook 순차 생성 어댑터 인수인계

## 결과와 승인 경계

- **구현 및 부분 로컬 합성 검증 단계. 정식 출시 NO-GO.** 실제 Genlook 생성, 착장 품질, S25+ 호출·저장·재열기는 검증하지 않았다. Android UI 검사 1개도 테스트 JVM 메모리 부족으로 수정 후 합격 여부가 미확인이다.
- 기준 P49 `4e37028871b92f28e3ffd7ae0368ff77c8530f06`에서 독립 로컬 브랜치 `codex/fashionai-p50-commercial-adapter-20261106`로 작업했다. P48 서버·Android 요청 계약을 확장했고 새 API 체계를 만들지 않았다.
- 실제 Genlook API 호출·사진 전송·가입·구매·결제·배포·기기 조작은 모두 **0회**. 이 작업의 실제 서비스 비용은 0이며, 계정·키·잔여 크레딧은 확인되지 않았다. 신규 생성 PNG도 없다.
- 공개 문서 열람은 수행했다. 테스트의 합성 HTTP/PNG는 실제 생성 증거가 아니다.
- 서버 및 Android 승인 게이트는 기본 비활성이다. 현재 Preview/QA의 서버 업로드 차단을 유지한다. 기존 온디바이스 및 수동 2D 피팅은 교체하지 않았다.
- API 호출 예산·계정·개인정보 처리에 대한 별도 승인을 요청했으나 이 기록 시점에는 확보하지 못했다. 응답을 기다리는 동안 가능한 코드와 모의 검증을 수행했다. 승인만으로 계정·선불 잔액·시험 자료 사용권이 존재한다고 간주하지 않는다.

## 실제 API 계약과 구현

공식 [create 문서](https://genlook.app/docs/tryon-api/endpoints/create-try-on)는 1–4개 상품을 허용하지만 [multi-product 문서](https://genlook.app/docs/tryon-api/multi-product)는 다중 상품을 의류로 한정한다. 신발을 다른 상품과 함께 요청하면 `MULTI_PRODUCT_NOT_SUPPORTED`다. 따라서 **상의 → 하의 → 신발, 단일 상품 요청 3회**로 구현했다. 앞 단계에서 다운로드·디코딩한 실제 결과만 다음 단계의 인물 입력으로 사용한다. 기존 얼굴·포즈·의류가 유지되는지는 실제 결과 검수가 필요하다.

- 서버 전용 환경변수 키, 고정 HTTPS API, `x-api-key`, 공식 multipart 스키마. `crop=false`로 인물 이미지를 먼저 업로드하여 직접 입력의 4:5 강제 크롭을 피한다.
- 기존 P48 Bearer 인증, 사용자 격리, 동의 정책, 요청 ID·입력 SHA, 결과 TTL·저장 계약을 유지한다. Genlook 처리·비용·보관 고지를 추가하고 고지가 바뀌면 Android 동의를 초기화한다.
- 선불·자동 충전 꺼짐·약관·개인정보·비용 검토 확인과 예산이 없으면 BLOCKED. 시작 및 단계별 잔액 확인, 요청당 3크레딧 영속 예약, 누적 크레딧·금액 상한을 적용한다.
- 단일 소유자 잠금과 원자적 journal을 사용한다. 유료 POST 이전 의도를 기록하며 응답 유실·재시작 후 같은 작업을 자동 재전송하지 않는다. 미확정 작업은 운영자 확인 전 차단한다.
- 공식 API에 개별 생성 취소 보장이 없어 **앱 대기 종료·후속 단계 중단과 원격 취소를 구분**한다. 외부 실행·과금·삭제가 불확실하면 성공으로 표시하지 않는다.
- 결과는 허용된 정확한 HTTPS 호스트에서 별도 무인증 클라이언트로 다운로드한다. 리다이렉트, 과대 파일, MIME/실제 형식 불일치, 잘못된 크기·프레임·디코딩을 거절한다. WebP/JPEG는 실제 디코딩 후 PNG로 변환한다. API 키·응답 오류 원문·서명 URL은 HTTP 라이브러리 로그에서도 가린다.
- 모든 단계 종료 후 해당 작업의 고객·상품만 삭제 요청한다. 삭제 확인 실패 시 성공을 차단한다. 진행 중 작업의 DELETE를 취소 보장처럼 사용하지 않는다.
- 결과 출처는 `genlook`, `tryon/v1`, 3개 생성 ID, 입력·결과 식별값과 크레딧 예약/잔액으로 남긴다. 공급자 실제 모델·가중치 버전은 `not_disclosed`, 품질은 `NOT_EVALUATED`다. P48 모델 revision/hash 필드는 이 어댑터의 검토된 설정 계약을 식별하며 실제 가중치 해시가 아니다.

가격 공지는 PAYG 125크레딧/USD5, 표준 단일 요청 1크레딧을 명시한다. 3단계는 명목 USD0.12 상당이지만 **계정 가격·세금·실청구 확인이나 결제 승인이 아니다**. 이미지 보관 설정 1일 및 완료 후 삭제 요청과 별개로 공급자 기록·동의·백업 정책, EU 저장/글로벌 처리 및 계정 대시보드 접근을 검토해야 한다. 자세한 공식 출처, 설정, 취소·삭제 한계는 [운영 계약](../backend/GENLOOK_OUTFIT_PROVIDER.md)에 정리했다.

## 로컬 검증

검사 대상은 아래 P50 파일의 해시를 실행 시작/종료에 비교한 작업 트리다. 로컬 커밋 후 해당 파일 해시와 대조한다. 기존 P48 결과를 이번 코드의 성공으로 대체하지 않았다.

| 검사 | 실제 결과 | 범위 |
|---|---|---|
| Backend `backend01` | **99 PASS**, 실패/오류/건너뜀 0; pytest 24.92초 | 신규 어댑터 27, 신규 API 통합 12, 기존 API 31, 기존 provider 29. MockTransport/TestClient·합성 이미지, 실제 외부 호출 없음 |
| Android `focused01` | 실행 명령 오류 | PowerShell의 따옴표 없는 `-P` 인자가 분리됨. 컴파일 이전 실패이며 앱 오류가 아니다. 실행 스크립트만 수정 |
| Android `focused02` | **23 PASS / 1 FAIL**, 컴파일 통과 | P48 회귀 17 및 P50 계약 6 통과. P50 UI 1개는 `assertTextContains`의 기본 전체 텍스트 매칭에 짧은 문구를 전달한 검사 오류 |
| Android `focused03` | **BLOCKED**, 컴파일 통과 후 테스트 JVM `OutOfMemoryError: Java heap space`; 종료 1 | UI 기대값을 실제 전체 문구의 `assertTextEquals`로 교정. 앱 코드는 변경하지 않음. 384MB 테스트 힙에서 중단; XML 1개 skipped이며 합격이 아니다. 총 실행 8분48초 |
| Android `focused04` 사전 조건 | **시작하지 않음** | Gradle 힙 768→384MB, 테스트 힙 384→768MB로 합계 상한을 유지하는 별도 실행안 준비. 가용 RAM 696MiB, 최종 790MiB로 사전 기준 1100MiB 미달. 마지막 가용 Commit 8.10GiB / 디스크 6.07GiB. 보호 기준을 낮추거나 사용자 프로그램을 종료하지 않음 |
| 실제 Genlook / S25+ | **NOT TESTED / BLOCKED** | 계정·비용·처리 승인 및 실제 서버·승인된 설치 조건 없음 |

Backend 검사는 중복 요청·잔액 부족·예산·POST 응답 유실·취소 불확실성·재시작·삭제 실패·URL/이미지/로그 보호·결과 소유권/출처 및 기존 journal 호환성을 포함한다. 통과한 Android 검사는 공급자 계약, 출처 누락 차단, 기존 ticket 복원 및 P48 요청 상태 회귀를 포함한다. 정책 변경 시 UI 동의 해제와 원격 취소 불확실성 화면의 전체 흐름은 작성됐으나 이번 최종 UI 검사 합격으로 보고하지 않는다. **Android 중복 없는 최종 집계: 23 PASS, 1 BLOCKED.** 서버와 Android 각각의 모의 검사이며 실제 네트워크 E2E는 아니다.

비공개 로컬 증거:

- `C:\Users\USER\Documents\ChatGPT\capston\experiments\P50-20261106\backend01\`: pytest 로그, XML, 시작 파일 해시, 종료 코드.
- `C:\Users\USER\Documents\ChatGPT\capston\p16-device-20261104\fashion_app\build\p50-private\focused01`, `focused02`, `focused03`: 독립 로그·XML·종료 기록. 실패 증거를 덮어쓰지 않았다. `focused04-preflight-final.json`은 실행 전 자원 차단 기록이다.
- 충분한 메모리가 확보되면 `fashion_app/build/p50-private/run-focused04.ps1`로 이미 컴파일된 `P50ServerProviderDisclosureUiTest` 1개만 재검증한다. 실행 전 RAM 1100MiB 이상, Commit 여유 3GiB 이상, 디스크 2GiB 이상 및 다른 Gradle 작업 부재를 확인한다. 현재 `focused04` 결과 폴더는 생성되지 않았다. 같은 경로 결과가 생겼다면 새 실행 번호를 사용한다.

전체 CI, Lint, APK 빌드·서명 검사 및 설치는 이번 단계에서 수행하지 않았다. 기존 APK를 이번 P50 코드가 포함된 APK라고 표시하지 않는다.

## 실제 변경 파일

- `backend/genlook_outfit_provider.py` — 승인·비용·순차 API·영속 원격 작업·취소/삭제·이미지/로그 보호 어댑터.
- `backend/outfit_vton_provider.py` — 선택적 provider와 공개 고지/출처 계약.
- `backend/outfit_vton_api.py` — 고지 및 출처 검증·저장, 원격 종료 불확실성 보존, 종료 정리.
- `backend/genlook.env.example`, `backend/requirements-genlook.txt` — 비밀값 없는 기본 비활성 설정 및 선택 의존성.
- `backend/tests/test_genlook_outfit_provider.py`, `backend/tests/test_outfit_genlook_integration.py` — 집중/회귀 검사.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/data/ServerOutfitVtonRepository.kt` — 고지/출처 파싱 및 ticket 호환성.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/ServerOutfitVtonViewModel.kt` — 공급자별 작업 식별, 취소 오류 안내.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/ServerOutfitVtonScreen.kt` — 제3자 처리·비용·보관·동의·취소·결과 출처 표시.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P50ServerProviderDisclosureTest.kt`, `P50ServerProviderDisclosureUiTest.kt` — Android 계약·Compose UI 검사.
- `backend/GENLOOK_OUTFIT_PROVIDER.md`, 이 문서 — 운영 조건 및 인수인계.

## 보존과 다음 실행 조건

- P38–P49 자료, 기존 APK, 사용자 데이터, DataAuth/DbSync/백업복구/Preview 격리/서명·워크플로 설정은 이번 수정 대상이 아니다. 기기에 접근하지 않아 데이터 보존을 새로 해시 검증했다고 주장하지 않는다.
- 커밋 대상 14개 파일을 한정했다. APK·사진·가중치·키 파일 및 고위험 비밀키 패턴 검사는 탐지 0건이다. 이는 제한된 소스 검사이며 비밀정보 부재 전체를 수학적으로 보증하는 검사가 아니다. 로그·XML·합성 PNG·로컬 ledger는 Git에 추가하지 않는다.
- 작업자는 root 포함 실제 4명: provider 구현, Android 구현, 공식 계약·보안 검수, 총괄 API 통합·단일 테스트 실행·로컬 커밋. 동일 파일 편집자를 분리했다.
- **클라이언트/계정 책임자**: 합법적 기존 계정, API 약관·DPA·처리 지역/보관, 시험 입력 사용권, 선불 잔액·자동 충전 해제·최대 3크레딧 승인.
- **서버 운영 책임자**: 승인된 비공개 키 주입, 검토 기록 ID, 고정 누적 예산, 사설 ledger와 인증 HTTPS 배포. 미확정 작업은 기록을 지워 해결하지 말고 공급자와 대조한다.
- **개발/QA**: 승인 이후 딱 한 번의 순차 생성, 실제 잔액·시간·3개 ID·PNG/SHA·삭제 응답 확보. 공급자의 제품 DELETE 상태 코드와 결과 호스트도 실제 확인한다. 각 단계의 얼굴/자세/의류/신발 품질 불합격이면 재생성하지 말고 원인 보고.
- **Android/출시 책임자**: 승인된 서버 활성화 빌드·신뢰 서명과 버전별 설치 승인 후 S25+ 마법봉→비교→저장→재열기, 기존 데이터 보존 및 정식 운영/Release 조건 검증.

이 조건과 실제 품질·단말 E2E가 충족될 때까지 **NO-GO**. 로컬 커밋만 보존하며 원격 Push·Merge는 하지 않는다.
