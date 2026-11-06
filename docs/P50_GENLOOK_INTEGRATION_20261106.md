# P50 — Genlook 순차 생성 어댑터 인수인계

## 최신 보완 — 품질 개선 코드·3종 시험 준비 / 2026-11-06 KST

이번 보완에서는 **Genlook 인증 조회·업로드·생성 모두 0회**, 구매·자동 충전·APK 설치·Push도 없다. 잔액 9는 직전 실제 시험의 마지막 관측값이며 새로 조회한 값이 아니다. 기존 실생성 PNG·ledger·오류 기록·커밋은 보존했다.

### 새 시험 인물

- [PixelPanda 공식 이미지](https://pixelpanda.ai/gallery/man-in-minimal-neutral-outfit-earth-tones-perfectly-coordinated-clean-aesthetic--87de04bf)는 **기존 공개 AI 합성 인물** 및 CC BY 4.0을 명시한다. 실제 개인 사진이나 이번에 새로 생성한 사진이 아니다. 상의·긴 바지·머리·양쪽 신발이 전부 보이고, 거의 정면·단순 벽 배경이다. 한 손이 주머니에 있어 손 보존 검증에는 제한이 있다.
- [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)의 복제·수정 권한과 제공자의 AI Reference 안내에 근거해 중립적인 의류 수정 시험용 후보로 확보했다. 출처/라이선스/변경 표시는 유지한다. 특정 처리 서비스 승인이나 실존 인물 초상권 확인으로 확대하지 않는다. 원문 페이지·이용약관·CC 법률문·해시·시각 검수 자료도 로컬 보존했다.
- 원본 WebP 1792×2368 / 374488바이트 / SHA256 `4123b405ff9f0d92677313aaf3fe9efe27be49ed4502b7b97e81dcfec4cf6c4b`.
- 비율 유지 축소 준비본 PNG 1024×1353 / 1535458바이트 / SHA256 `665ec554f94e04b92a13d9a1563ffe6823a1f6bb360181bbcd2c1a96a355d15f`. 잘라내기 없이 전신과 양발을 유지했다. Genlook 전송 안 함.
- 자료 위치: `experiments/P50-20261106/quality-prep-01/asset-candidates/`, `next-trial-review/`. 이미지·원문 HTML은 Git/APK에 넣지 않는다. 실사진 자연스러움이나 실제 신체 정확도 근거로 사용하지 않는다.

### 규격·원인·코드

공식 [다중 상품](https://genlook.app/docs/tryon-api/multi-product)은 의류 2–4개를 한 요청에 허용하며 표준 2크레딧이다. 상의+하의 가능, 신발 혼합은 `MULTI_PRODUCT_NOT_SUPPORTED`; 신발은 별도 단일 1크레딧이다. 2K는 요청당 1크레딧 추가다. [업로드](https://genlook.app/docs/tryon-api/endpoints/upload-image)의 `crop=false` 후 `imageId` 참조를 유지한다. 직접 URL/fileKey 인물은 4:5 크롭되므로 사용하지 않는다. 크롭 해제는 생성 결과 보존 보장이 아니며 공식 mask/pose-lock 필드는 확인되지 않았다.

관측된 결함은 상의 편집 중 비대상 치마 무늬 제거다. 긴 원피스형 입력·범용 상품 설명·폐쇄형 모델의 영역 판단이 원인 후보지만 공급자 내부 동작은 확인하지 못했다. 실제 원본 818×1024와 결과 928×1152는 크기도 다르다. 단순 좌표 대응이나 crop=false만으로 픽셀 보존을 보증할 수 없다. 문서 기본 출력 긴 변1200px와 실제1152px 차이도 기록하며 크롭 원인으로 단정하지 않는다.

- `genlook_request_contract.py`: 네트워크 없는 단일/상의+하의/신발 요청 규격·크레딧 계산. 혼합 신발·중복 부위·ID 혼용을 실행 전 거절한다. 실제 provider의 단일 요청을 이 함수에 연결해 비대상 의류·무늬·얼굴·손·배경 보존 설명을 구체화했다. 의미상 개선 시도이며 추가 실생성 품질은 미검증이다. 다중 요청 설명은 함께 교체할 다른 선택 의류를 보존하라고 모순되게 지시하지 않는다.
- provider revision을 `genlook-sequential-v2-preservation-20261106`으로 올려 과거 계약 ledger를 새 계약으로 자동 재사용하지 않는다. 기존 v1 지출 원장으로 시작하면 `genlook_journal_unavailable`로 차단된다. 원장을 삭제하거나 새 승인 예산처럼 초기화하지 않는다. 기존 3단계·3ID·동의·예산·불확실 요청 차단은 유지한다. 2회 계획은 검토용이며 Backend/Android의 기존 3회 계약으로 완료 처리하지 않는다.
- `outfit_region_preservation.py`: 해시로 묶인 원본·AI 결과·수동 마스크·정렬 검토를 요구하는 로컬 후처리. 크기 불일치·정렬 미확인·오래된 마스크를 차단하고 보호 영역 RGBA를 원본 그대로 유지하며 경계는 편집 영역 안쪽으로만 섞는다. 자동 분할·정렬 기능은 없다. 새 생성이나 순수 모델 출력으로 표시하지 않는다.
- 기존 실제 결과에 수동 검토용 리샘플링/마스크를 적용한 진단에서 보호 대상 **809656픽셀, 변경0**. 원본 치마 무늬를 보존하지만 어깨·소매의 기존 의류 잔여와 허리 경계가 남아 **자연스러움 FAIL**. 마스크 밖 픽셀 일치는 마스크의 의미적 정확도나 실사진 품질 보장이 아니다. 원본 AI PNG는 그대로이며 로컬 후처리 비교판에 별도 표식을 넣었다.

### 검증·다음 승인 범위

| 집중 검사 | 결과 | 실행 범위 |
|---|---|---|
| `quality-contract01` | **28 PASS**, 7.03초, Starlette/anyio deprecation 경고1 | 새 규격11 + 영향받는 provider5 + 기존 API통합12. 전부 모의 응답, 외부 호출0 |
| `quality-region01` | **12 PASS**, 1.49초 | 픽셀/alpha 보호·안쪽 블렌딩·해시/정렬/마스크 오류·EXIF·PNG·메타데이터 |
| `quality-ledger01` | **1 PASS**, 2.63초 | 계약 v1→v2 시 기존 지출 원장 바이트 보존·네트워크 추가 호출/재생성 차단 |
| 기존 AI 결과 로컬 재검토 | 파일 생성·재열기 및 보호 픽셀 검사 통과 / 자연스러움 FAIL | 새 추론·외부 업로드0, raw 크기 불일치는 거절 |
| Android/S25+ | 이번 재실행/설치 없음 | 화면은 이미 품질 검수 필요·3회 순차·이전 결과·원격 취소 한계를 구분. 기존 UI 검사1개 RAM 차단은 계속 미해결 |

실행 기록은 기존 증거와 별도 경로이며 이전 검사 수와 합산하지 않는다. Android 코드는 변경하지 않았고 동일 APK/전체 CI를 반복하지 않았다. FastAPI/Android 계약은 연결돼 있으나 **실제 서버 3종 생성→Android 수신→S25+ 저장·재열기는 미검증**이다. 후처리는 현재 API에 자동 연결하지 않았다. 연결하려면 정렬/마스크 검수·후처리 출처·AI 표식을 서버와 화면에 함께 추가해야 한다.

다음 제안은 **상의+하의1회(2크레딧)→품질 검수→신발1회(1크레딧), 총 최대3**이다. 새 인물·정확한 의류 파일·규격·상한에 추가 승인을 받은 뒤에만 실행한다. 첫 결과가 실패하면 신발 단계도 중단하며 자동 재생성하지 않는다. `next-trial-review/PLAN.json`과 `REQUEST_SCHEMA_REVIEW_ONLY.json`은 실행 기능이 없는 검토 자료다. 새 2회 모드의 실제 운영 연결은 아직 없으므로 기존 3회 결과 계약으로 위장하지 않는다.

추후 S25+는 승인된 서버/정책·서명된 새 APK의 버전별 설치 승인 후 입력 고정→동의→실제 생성→비교→저장→재열기·취소·재진입·데이터 보존을 확인한다. 기존 Preview18605/QA18607에는 접근하지 않았다. **NO-GO 유지: 전체 착장 품질·3종 실생성·실제 서버/기기 E2E·운영/정식 출시 조건 미충족.**

## 최신 실제 시험 — 공개 입력 상의 1회 / 2026-11-06 19:15 KST

사용자가 키를 다시 저장한 뒤 요청한 인증 재검증 **1회는 HTTP 200 / 오류 코드 없음 / 잔액 10**이었다. 사용자가 지정한 공개 도메인 역사 인물 사진과 Met 공개 도메인 재킷만 사용했다. 마지막 “먼저 1회만 시험 생성” 지시에 맞춰 **상의 생성 POST 1회**에서 멈췄으며 하의·신발·유료 재시도는 수행하지 않았다.

| 실제 항목 | 결과 |
|---|---|
| 인물 업로드 / 생성 접수 | HTTP 201 / HTTP 201 |
| 상태 / 결과 수신 | `COMPLETED` / HTTP 200 |
| 저장·재열기 | 개발 PC에서 실제 디코딩한 PNG, 928×1152, 692251바이트 |
| 잔액 | 10→9, 관측 차감 1크레딧. 구매·충전 수행 없음 |
| 경과 시간 | 입력 검증·잔액·생성·다운로드·삭제 요청 포함 12.375초; 순수 모델 추론 시간은 비공개 |
| PNG SHA-256 | `78010181017ed80c423b1da9284831c38aad89c22565922e190770dd4bb1f142` |
| 품질 | 상의 적용은 보이나 **기존 체크 치마가 무지로 바뀌어 비선택 의류 보존 FAIL**. 얼굴·자세·배경은 대략 유사할 뿐 정확 보존 합격 아님. 발이 가려져 신발 평가 불가 |
| 실행 경계 | 로컬 시험 실행기→기존 Genlook provider `_stage`→외부 서비스. FastAPI 실요청·Android·S25+ E2E는 미실행 |

사진 출처는 [사용자가 지정한 Commons 파일](https://commons.wikimedia.org/wiki/File:Full-length_portrait_of_a_woman,_standing,_facing_front_LCCN2005692484.jpg)의 PD-old-100-expired/Public Domain Mark이며, Wikimedia 다운로드 403 뒤 해당 페이지가 명시한 **동일 LOC 항목 2005692484 원출처**에서 818×1024 파일을 받았다. 의류는 [Met 91377](https://www.metmuseum.org/art/collection/search/91377) 공개 도메인 재킷이다. 개인 사진·기존 Qwen/FASHN 예제는 전송하지 않았다.

보관은 1일·`crop=false`·AI 표식을 요청했다. 종료 후 시험 고객(사진/결과) DELETE와 생성한 상의 상품 DELETE는 각각 **204**였다. 기존 정리 함수가 생성하지 않은 하의 상품까지 지우려다 **404 PRODUCT_NOT_FOUND**를 받아 원래 ledger/receipt에 `cleanup_failed`가 남았다. 이 원본 증거는 고치지 않았다. 미제출 상품은 삭제 요청에서 제외하도록 최소 수정했고, 실제 생성된 상품의 삭제 실패·미확정 생성 차단은 유지했다. 공급자 별도 로그/백업의 즉시 삭제까지 보증하지 않는다.

- 실패 재현 `cleanup-red01`: 신규 모의 회귀 1 FAIL(`cleanup_failed` 대 `failed`). 수정 후 `cleanup-green01`: **14 PASS**, 3.88초. 부분 중단·오류·취소·잘못된 결과·미확정 POST·정상 3단계·실제 삭제 실패 보호만 확인. 외부 재생성 없음.
- 시험 실행기 모의 검사: 기존 1회 제한/응답 유실 3건 및 최종 기록 저장 오류 1건 통과. 실제 생성 증거와 별도 폴더로 보존했다.
- 비공개 실제 증거/PNG: `C:\Users\USER\Documents\ChatGPT\capston\experiments\P50-20261106\actual-single-top-01\`의 `actual_top_result.png`, `RECEIPT.json`, `REVIEW.json`, `http-events.jsonl`, `owned-ledger/ledger.json`. 키·서명 URL·원문 응답은 기록하지 않았다. 보수적 ledger 예약 3은 실제 차감 1과 다르며 재실행 방지 기록을 유지한다.
- 실제 호출 당시 코드 HEAD `dd055fcc83deb7bba4f6c980c9b39bce2c4541d3`; 뒤의 정리 수정은 로컬 모의 검사만 수행했다. 기존 99개/Android 전체 검사를 반복하지 않았다.
- `backend/.env`는 Git 무시·비추적이며 실제 ASGI 진입점에서 읽는다. 운영 승인 플래그/Android 서버 활성화는 계속 false. APK·기기·사용자 데이터에 접근하거나 설치·Push하지 않았다.

**상의 외부 생성 1회 성공과 전체 착장 제품 완성은 별개다.** 하의·신발 순차 생성, 비선택 의류/얼굴 품질, 실제 FastAPI 인증 경로·Android·S25+ 저장/재열기, 운영/정식 서명 조건은 남아 있으므로 **NO-GO**다. 아래 기록은 당시의 과거 상태로 보존한다.

## 후속 상태 — 사용자 API 키 입력 준비

사용자가 기존 계정·API 키 발급을 완료했다고 알렸고, 키 입력 및 잔액 확인 후 **비개인 시험 입력 최대 1세트·3크레딧** 실험을 승인했다. 아래 초기 기록의 승인 대기는 이 후속 요청으로 변경됐다. 신규 가입·구매·자동 충전·개인 사진·기기 설치·Push는 여전히 수행하지 않는다.

- 현재 작업 트리의 `backend/.env`는 없었으며, Git 추적 파일이 아니고 `.gitignore`의 `**/.env` 규칙에 해당함을 먼저 확인했다. 기존 환경 파일을 덮어쓰지 않았다.
- 정확한 입력 파일: `C:\Users\USER\Documents\ChatGPT\capston\p16-device-20261104\backend\.env`. **키는 사용자가 편집기에 직접 입력**한다. 채팅으로 요청하거나 출력하지 않는다.
- 새 파일의 NTFS 상속 권한을 끄고 현재 Windows 사용자·SYSTEM·Administrators만 허용했다. 키 값은 작성하지 않았다. 승인 플래그는 false, 크레딧 상한만 3으로 준비했다. 기존 운영 환경·DB는 변경하지 않았다.
- `outfit_vton_app.py`에서 다른 프로젝트 import 이전에 정확한 sibling `.env`를 명시적으로 로딩한다. `override=False`, `interpolate=False`; 기존 프로세스 설정 우선. 기존 `database.py`의 간접 로딩에 의존하지 않는다. 키만 있어도 서비스가 활성화되는 것은 아니다.
- 신규 `test_outfit_vton_environment.py`: **5 PASS, 0.36초**, 실제 키 파일 대신 임시 합성 파일과 가짜 앱/provider 경계를 사용. 경로 고정·호출 순서·기존 환경 보존·치환 금지·키만 있을 때 비활성·파일 무수정·무출력을 확인했다. 실제 API/DB/네트워크는 사용하지 않았다.
- `experiments/P50-20261106/env01`은 PowerShell XML 인자 전달 오류로 검사 시작 전 종료4; `env02`는 수정된 명령으로 검사 5개 통과·종료0. 두 기록 모두 보존했다. 기존 99개 및 Android 검사는 불필요하게 반복하지 않았다.
- 사용자에게 키 저장 완료와 자동 충전 비활성 상태 확인을 요청했다. 입력 완료 통지 전에는 실제 호출하지 않는다. 시험 자료 사용권, 계정 잔액과 비용 정책, 사설 1회 ledger를 확인한 뒤 실제 생성한다. 키 준비가 Android 서버 활성화·운영 배포·S25+ 설치 승인은 아니다.
- 기존 P45/P46 공개 입력 manifest도 읽기 전용 검토했다. `experiments/P45-20261106/public-inputs-20261106-060727`의 인물/상의/신발은 Qwen 연구 데모 예제이고 Genlook 재전송 사용권은 현재 기록에서 확인되지 않는다. 하의만 Met Open Access/CC0 기록이 있다. 전체 4장을 새 제3자 서비스에 전송할 수 있다고 자동 간주하지 않는다. 기존 PNG/입력은 수정·전송하지 않았다.

후속 변경 파일은 `backend/outfit_vton_app.py`, `backend/tests/test_outfit_vton_environment.py`, `backend/GENLOOK_OUTFIT_PROVIDER.md`, 이 문서다. 실제 `.env`는 Git 제외 상태를 유지한다. 초기 `FINAL_VERIFICATION.json`은 초기 커밋 기록으로 보존하며 덮어쓰지 않는다.

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
