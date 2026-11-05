# P47 — 실제 3종 생성형 피팅 납품 경로 판정

2026-11-06 KST. P46의 새 생성 성공은 재사용했으며 이번에는 생성·업로드·가입·결제·모델 다운로드·ADB·설치를 하지 않았다.

| 필수 제출 항목 | 판정 |
|---|---|
| 1. P46 모델/라이선스 | 공식 **Qwen/Qwen-Image-2.1** 공개 Space / **Qwen Research License**, 별도 상업 허가 필요. QwenCloud Turbo가 아니다. |
| 2. 무료 상업 운영 | 현재 사용 권한과 실제 연산 자원을 모두 확보한 조합 **없음**. 공개 모델의 상업 허용과 무료 데모 서비스 이용권은 별개다. |
| 3. S25+ 마법봉 실제 생성 | **BLOCKED**. 기본 엔진은 Unavailable이며 세 품목 전체의 실행 가능한 Android 모델/어댑터가 없다. |
| 4. 세 품목 품질 | P46 공개 예제 한 장에서 세 품목 생성·시각 개선 확인. 정확한 얼굴·손·상품·자세 보존과 일반화 품질은 미충족/미검증. |
| 5. Android/저장/재열기 | 인터페이스·입력 고정·오류·저장·재열기 구현과 합성 자동 검사 근거 있음. **실제 세 품목 모델→Android 결과→저장 E2E 미검증**. |
| 6. Commit | 기준 `a792e62b6e2efa08ab35c8cd92e07e894368c3e4`; P47 독립 로컬 브랜치 `codex/fashionai-p47-delivery-gates-20261106`. 최종 SHA는 인수 응답과 별도 로컬 검증 기록에 기록한다. Push 없음. |
| 7. 해결 책임 | 아래 권한·연산·엔진·개인정보·QA·서명 담당별 차단표 참조. 개발자가 부재한 계약·계정·연산 자원을 만들어 완료 처리하지 않는다. |
| 8. 정식 출시 | **NO-GO**. 현재 확보한 무료 연구 데모를 개인 사진용 상업 엔진으로 승격하지 않는다. |

## P46의 정확한 실행 출처와 서비스 경계

- P46은 PC 클라이언트가 `https://qwen-qwen-image-2-1.hf.space`의 공개 Gradio API에 요청한 **외부 서비스 실행**이다. Qwen 가중치를 PC 또는 S25+에서 로드한 실행이 아니다.
- 검토한 Space 앱 SHA는 `5694809f4c56a4e8c40787b5392cecac36493c5d`. 소스의 기본 모델 ID와 pipeline은 `Qwen/Qwen-Image-2.1`, `QwenImage21Pipeline`이다. 소스는 `QWEN_IMAGE_MODEL` 환경 override를 허용하고 `from_pretrained`에 가중치 revision을 고정하지 않는다. 따라서 **실제 서버가 로드한 가중치 commit/hash는 공개 API로 확인되지 않았다**. Space 코드 SHA를 가중치 SHA로 대체하지 않는다.
- 당시 공개 익명 호출이 성공했다. 클라이언트는 `token=False`, Authorization 헤더 없음으로 호출했다. 앱의 DataAuth/Bearer 사용자별 권한이 이 Space와 연결된 것은 아니다.
- `/prepare_request`→같은 세션의 `gr.State`→`/generate_request` 계약은 P45/P46에서 확인했다. 공식 Gradio API라는 사실과 고정 버전 운영 API/SLA는 다르다. 계약 변경 시 기존 CLI는 차단한다. 운영용 다중 사용자 연결에서는 작업별 세션 격리가 필요하며, 하나의 Client를 공유해 A/B 입력을 섞으면 안 된다.
- 보존된 공식 `public-app.py`의 `GenerationLogger`는 **입력·결과 이미지와 원문/최종 프롬프트**를 PNG/JSON으로 서버에 기록한다. 임시 파일도 `delete=False`로 만든다. 검토 범위에서 명시 TTL·삭제 API가 없다. 이것은 공개 노출을 입증한 것이 아니지만, 비보관·즉시 삭제·24시간 만료를 약속할 근거도 아니다.
- 모델 라이선스는 연구/평가용 사용과 상업용 사용을 구분한다. 별도 상업 허가 없이 정식 엔진으로 채택하지 않는다. HF 서비스 약관과 개인정보정책은 별개이며, 모델 권한이나 Space별 개인 사진 처리 계약·삭제기한을 대신하지 않는다.
- 공식 API 문서의 현재 일반 ZeroGPU 할당은 익명 2분/일(공유 pool), 무료 계정 5분, PRO 40분이다. 이는 P47에서 확인한 **실계정 잔여량이 아니며 요청 성공·지속성 보장도 아니다**. 서비스 약관은 중단/변경 가능성과 가용성 비보장을 명시한다. 공개 연구 API 호출 가능성을 무료 상업 운영 인프라 확보로 해석하지 않는다.

근거: [모델 라이선스](https://huggingface.co/Qwen/Qwen-Image-2.1/blob/main/LICENSE), [공식 Space](https://huggingface.co/spaces/Qwen/Qwen-Image-2.1), [Space API](https://huggingface.co/docs/hub/spaces-api-endpoints), [ZeroGPU](https://huggingface.co/docs/hub/spaces-zerogpu), [서비스 약관](https://huggingface.co/terms-of-service), [개인정보정책](https://huggingface.co/privacy).

로컬 출처 증거: `../experiments/P45-20261106/demo-metadata-20261106-055802/public-app.py`, `../experiments/P46-20261106/pose-anchored-trial-01/execution.json`, `PROVENANCE_NOTES.json`. P46 PNG SHA256 `77bdd3642fe2bfa4a7af16681084b44322385e55c1c5547529e8a7d5b21ecdc9`는 기존 증거이며 P47 생성 성공이 아니다.

## 상업 허용 모델과 현재 무료 연산의 교집합

| 후보 | 공식 권리/자산 | 현재 실행 판정 |
|---|---|---|
| 기존 FASHN VTON v1.5 | 기존 본체·DWPose 보존. 본체 Apache-2.0, 종속 Parser 권리는 별도 | 메모리 보호를 유지한 정규 전체 추론 미완료, 신발 공식 미지원. 동일 CPU 재실행 안 함. |
| Qwen-Image-Edit-2511 | Apache-2.0, 공개 가중치, 다중 참조 편집. Transformer만 40.9GB | 현재 PC/S25+ 전체 실행 미검증, 승인된 적합 연산 자원 없음. 4장/3품목 품질도 실행 안 함. |
| FLUX.2-klein-4B | Apache-2.0, 공개 가중치, 다중 참조 편집. 본체 단일 파일 7.75GB | 공식 모델 카드 약13GB VRAM과 GitHub 설명 약8GB는 구성 차이를 확인해야 함. 현재 장비에서 사용 가능한 전체 런타임/메모리 실측 없음. |

공식 자료: [Qwen2511 카드](https://huggingface.co/Qwen/Qwen-Image-Edit-2511), [가중치](https://huggingface.co/Qwen/Qwen-Image-Edit-2511/tree/main/transformer), [FLUX4B 카드](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B), [라이선스](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B/blob/main/LICENSE.md), [파일](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B/tree/main), [공식 구현](https://github.com/black-forest-labs/flux2).

Qwen2511 공식 Space의 Running on Zero 상태는 개인 사진의 정식 처리 권한이나 보장된 무료 연산의 증거가 아니다. 해당 [Space 코드](https://huggingface.co/spaces/Qwen/Qwen-Image-Edit-2511/blob/main/app.py)는 기본 prompt rewrite에서 첫 이미지를 별도 DashScope `qwen-vl-max`로 보내는 경로도 포함한다. 모델 Apache-2.0만 보고 이 서비스의 데이터 흐름을 승인할 수 없다. [BFL 호스팅 API](https://docs.bfl.ai/quick_start/pricing)는 계정·크레딧·키가 필요하다. 새 가입·유료 호출은 하지 않았다.

**현재 실행 가능한 무료 상업 납품 경로는 확정할 수 없다.** 이는 모든 무료 서비스가 존재하지 않는다는 단정이 아니라, 확인한 모델·현재 장비·허가된 자원 안에서 교집합을 확보하지 못했다는 판정이다. 다음 조건부 실험 후보는 상대적으로 작은 Apache-2.0 FLUX.2-klein-4B 자체 실행이지만, 사용 허가된 적합 자원·전체 의존성 검토·품질/런타임 검증 전에는 납품 후보가 아니다. 새 가중치는 다운로드하지 않았다.

## Android 실제 상태 검수

| 경로 | 이번 확인 | 기존 증거와 남은 한계 |
|---|---|---|
| 사진/3종/배치 선택 | `VirtualFittingScreen`에서 원본 source·배치·revision 복사 및 지연 카드 읽기 무효화 유지 | 소스 검수. 이번 기기 조작 없음 |
| 마법봉 입력 고정 | `DeviceVtonSelection`, `DevicePreparedJob`, bytes 방어 복사 유지 | P39/P46 합성 검사 근거, 실제 모델 입력 미검증 |
| 엔진 호출 | `DeviceVtonEngine.generate` 경계 존재, P46 동일 readiness snapshot 사용 | 기본 `UnavailableDeviceVtonEngine`. 실제 생성 어댑터 미등록 |
| 취소/중복 | 작업 종료 전 단일 실행 잠금, 입력 변경 시 취소·이전 결과 숨김 | P46의 Controller6/UI5/Availability4 통과를 재사용, 재실행 안 함 |
| 다운로드/무결성 | 온디바이스 경로는 HTTP 다운로드 없음. 결과 job/input/model/역할 검증 + PNG 구조·CRC·디코딩 | 연구 CLI의 HTTPS 결과 다운로드는 별도이고 Android에 연결하지 않음 |
| 비교/저장 | 원본과 결과 분리, SAF 저장 티켓은 최초 PNG bytes에 고정 | 합성 UI/픽셀 테스트 근거. SAF 쓰기는 비원자적, 저장 후 자동 readback 없음; 부분 실패 안내 유지 |
| 재열기 | 선택 파일 디코딩, `저장 이미지 · 새 생성 아님` 표시 | 앱 재시작 후 수동 파일 선택 가능. 미저장 결과/진행 작업은 프로세스 종료 후 복구하지 않음 |
| 데이터 보호 | 옷장/복원/인증/DB 코드 변경 없음. 개인 사진·기기 데이터 접근 없음 | 이번에 새로운 기기 데이터 해시 보존 검증을 수행한 것은 아님 |

Android에서 새로 입증한 결함은 없어 실행 코드를 수정하지 않았다. P46 테스트를 P47의 새 실기기/E2E 결과로 계산하지 않는다. 기존 Preview18605·QA18607·모든 납품본을 그대로 둔다.

## 이번 최소 수정 및 검증

연구 CLI의 예외 기록이 `str(exc)[:1200]`를 보관해 공급자 오류에 섞인 URL/경로/문자열이 증거 로그에 남을 수 있었다. 기존 연구는 공개 입력 전용이며 실제 비밀 유출이 확인된 것은 아니다. 합성 오류로 재현한 뒤 `safe_error_fields`의 고정 코드형 오류로 바꾸고 실패 단계·기존 1회 journal·UNKNOWN/BLOCKED·재시도 금지를 유지했다. 임의 예외 클래스명과 메시지를 기록하지 않고, 허용목록의 내부 오류 사유만 보존한다. 이는 연구 CLI의 해당 실패 경계 수정이며 정식 개인정보 처리 서버를 구현한 것은 아니다.

- 수정 전 RED: 사전 검사/생성 응답의 원문 노출 **2 FAIL**, 기존 1회 예약과 자동 재요청 차단 **2 PASS**.
- 수정 후 GREEN: 신규 개인정보 오류 경계 **5 PASS** + 기존 결과/프로토콜 **7 PASS** + 프롬프트 격리 **1 PASS** = **13 PASS**, 실패/오류/건너뜀0, 1.52초. RED의 반복 검사를 추가 성과로 합산하지 않는다.
- 네트워크 소켓 차단·가짜 SDK·임시 합성 입력/저널만 사용했다. 기존 P45/P46 입력·결과·실행 저널과 실제 프로세스 환경변수를 변경하지 않았다. P47 실제 외부 생성 요청0, 이미지 전송0.
- 증거: `../experiments/P47-20261106/red01.log`, `red01.xml`, `green01.log`, `green01.xml`.
- 변경 파일: `backend/p45_public_demo_trial.py`, `backend/tests/test_p47_public_error_privacy.py`, 본 인수 문서. Android 실행 코드·데이터 스키마·모델·APK는 변경하지 않았다. 전체 CI와 APK 빌드는 실행하지 않았다.

재현은 기존 격리 Python에서 `PYTHONPATH=backend`로 설정한 뒤 `python -m pytest backend/tests/test_p47_public_error_privacy.py backend/tests/test_p45_public_demo_trial.py backend/tests/test_p46_pose_trial.py -q`를 실행한다. 실제 연구 CLI의 `--execute-public-research-once` 옵션을 실행하지 않는다.

## 차단별 해결 책임과 수락 증거

| 차단 | 해결 책임자(역할, 미지정 담당자를 임의 지정하지 않음) | 닫기 위해 필요한 증거 |
|---|---|---|
| Qwen2.1 상업 권리 | 클라이언트 계약/권리 담당 + 권리자 | 별도 상업 허가 또는 상업 허용 모델의 전체 구성요소 라이선스 확정 |
| 실행 자원 부재 | 클라이언트 인프라/예산 담당 | 이미 사용 허가된 적합 연산 자원, 무료 범위/접근 조건. 신규 계정/비용은 별도 승인 |
| 3종 실제 엔진 | 개발팀 AI/Android 담당 | 고정 모델·4입력 전체 추론·3품목 품질·메모리·취소·S25+ 실행 증거 |
| 외부 개인 사진 처리 | 클라이언트 개인정보/운영 담당 + 서비스 운영자 + 보안 개발 | 실행 방식 변경 승인, 명시 동의, 처리자/국외 처리/보관/삭제/사용자 격리 정책 및 구현 검증 |
| 모델/API 지속성 | 서비스 운영자/인프라 담당 + Backend 담당 | 고정 버전·사용량 상한·안정 접속·작업별 인증·응답 유실/취소/만료 계약 |
| 품질/실기기 인수 | 개발팀 QA + 승인 시험 입력 제공자 | 여러 의류/자세 품질, 실제 생성→비교→저장→앱 종료→재열기, 데이터 비교 |
| 정식 서명/납품 | 클라이언트 Release 키 관리 담당 + Release 개발 | 승인된 정식 키·패키지·운영 환경·설치 승인과 인수 결과 |

권한·연산 자원 확정 후에야 엔진 구현과 실제 기기 인수를 완료할 수 있다. 현재 증거로 완료 일정이나 S25+ 속도를 약속하지 않는다. 공개 데모를 온디바이스 엔진으로 위장하거나 사용자 입력 제한을 해제하지 않는다.
