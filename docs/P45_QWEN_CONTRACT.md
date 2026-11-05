# P45 QwenCloud 미설정 계약 및 공개 연구 데모 검증

**최신 종료 결과:** 아래 대기 기록 이후 공식 공개 Space에서 4참조 신규 생성 1회·PNG 수신·PC 저장/재열기가 성공했다(06:20:07→06:20:40 KST 응답, 전체88.687초). `P45_FREE_RESEARCH_RESULT_20261106.md`를 우선한다. 자세·배경·제품 세부 보존은 불합격이며 Android/온디바이스/정식 배포 성공이 아니다. QwenCloud 요청은 여전히0회다.

검토일: 2026-11-06 KST. 아래 QwenCloud 항목은 공식 문서의 계약 검토이며 실제 연결 구현이나 실행 증거가 아니다. QwenCloud 계정 로그인·키 열람·서비스 활성화·가입·결제·설정 변경·생성 요청은 수행하지 않았다.

최신 사용자 확인: Singapore의 `qwen-image-2.1-turbo`는 사용자 콘솔에서 무료 지원되지 않는 것으로 확인됐고 사용할 계정·무료 할당량이 없다. 공개 문서의 10장 표기보다 이 상태가 호출 판단에 우선한다. QwenCloud 요청은 0회이며 유료 호출을 기다리지 않는다.

현재 실제 시험 경로는 별도 **공식 Hugging Face Qwen-Image-2.1 공개 연구 데모**다. 총괄이 읽기 전용 GET으로 RUNNING/ZeroGPU, 공개 config·API 계약 및 Space revision을 확인했다. 고정 4개 공개 입력의 SHA256 준비와 prepare가 완료됐고 **06:20:07 KST에 새 generation 요청 1회가 시작됐다**. 이 갱신 시점 결과는 PENDING이며 PNG 수신·품질 합격·Android 성공으로 기록하지 않는다. 이후 종료 결과는 총괄 실행 로그/인수인계가 우선한다.

## 무료 범위와 지역

[QwenCloud 가격표](https://docs.qwencloud.com/developer-guides/getting-started/model-prices)는 Singapore/International의 `qwen-image-2.1-turbo`를 출력당 USD 0.016, 신규 무료 할당량 10장으로 기재한다. 계정에 실제 잔여량이 있다는 뜻은 아니다. 무료량은 일회성 시험 범위이며 영구 무료 운영 계획이 아니다. 4개 참조를 받지 않는 더 저렴한 텍스트 생성 전용 모델은 이번 요구의 대안으로 계산하지 않았다.

[QwenCloud 무료량 문서](https://docs.qwencloud.com/resources/free-quota)는 Singapore 키·모델·endpoint의 일치를 요구한다. 통상 90일이며 신규 계정은 결제 정보 확인을 요구할 수 있다. `Free quota only`는 기본 OFF다. 사용자가 결제/가입을 승인하지 않은 이번 작업에서는 해당 절차를 자동 수행하지 않는다. 키 존재만으로 무료 실행 가능 상태라고 판정하지 않는다.

[Model Studio 무료량 안내](https://www.alibabacloud.com/help/en/model-studio/new-free-quota)는 활성화 후 quota 반영 및 스위치 전파에 지연이 있음을 설명한다. 무료 전용 활성화 후 고갈 시 `AllocationQuota.FreeTierOnly`로 차단되지만, 변경 직후 호출은 과금 위험이 남는다. 잔여량·만료·모델·리전·활성화된 보호 상태를 콘솔에서 확인해야 한다. 이 검토에서 이를 조회/보장하는 공식 공개 API나 요청별 무료 전용 플래그는 확인하지 못했다.

[2026-08-03 공지](https://www.alibabacloud.com/notice/detail?_p_lc=1&id=2037)는 8월20일 전체 리전 확장을 예고하지만, 이후 10월29일 안내와 현재 QwenCloud 문서는 Singapore 제한을 기재한다. 문서 간 충돌을 숨기거나 다른 리전의 무료 혜택을 확정하지 않는다. 현재는 어느 리전에도 QwenCloud 생성 요청을 보내지 않는다.

## QwenCloud 4개 참조 계약 — 향후 검토용, 미설정·미실행

QwenCloud와 Alibaba Model Studio의 키·endpoint를 구분한다. [QwenCloud 리전 문서](https://docs.qwencloud.com/developer-guides/getting-started/regions)는 workspace ID가 없는 `ap-southeast-1.maas.qwencloudapi.com`을 사용한다. Singapore는 데이터 저장 리전이며 추론은 중국 본토 밖 International 노드다. Alibaba Model Studio의 `{WorkspaceId}.ap-southeast-1.maas.aliyuncs.com`과 서로 교환하지 않는다.

근거: [QwenCloud 이미지 편집 API](https://docs.qwencloud.com/api-reference/image-generation/qwen-image-editing). 다른 모델의 3개 입력 예제를 2.1의 최대 개수로 오인하지 않는다.

- 모델: `qwen-image-2.1-turbo`; 참조 1–10개, 배열 순서대로 의미 부여. 4개 슬롯은 person/top/bottom/shoes로 고정한다.
- 확인된 동기 POST: `https://ap-southeast-1.maas.qwencloudapi.com/api/v1/services/aigc/multimodal-generation/generation`; `Authorization: Bearer`와 JSON. 이 API에 async 헤더를 추측하여 붙이지 않는다.
- 본문: `input.messages`의 단일 user 객체, content에 image 4개와 text 1개. Base64 data URL 지원. 각 파일≤10MB, 변1–8000px, 비율≤10:1.
- 단일 출력 `n=1`, 명시 크기(예: `1024*1536`), 고정 seed. `negative_prompt`는 2.1에서 사용하지 않는다.
- 성공 응답은 `request_id`와 `output.choices[].message.content[].image`. PNG URL은 24시간 유효하다. request_id는 상태 조회용 task_id가 아니다.

[QwenCloud 이미지 편집 안내](https://docs.qwencloud.com/developer-guides/image-generation/image-editing)의 OpenAI 호환 경로는 `/compatible-mode/v1/images/generations`이며 image 문자열 배열을 받는다. `/images/edits` 또는 multipart로 임의 치환하지 않는다. 안내의 FAQ는 OSS 저장 호스트가 변할 수 있어 고정 허용 목록을 제공하지 않는다고 명시한다. 예시의 `dashscope-result-sz.oss-cn-shenzhen.aliyuncs.com`과 `dashscope-result-hz.oss-cn-hangzhou.aliyuncs.com`을 전체 보증 목록으로 확대하지 않는다.

[QwenCloud 일반 async 안내](https://docs.qwencloud.com/developer-guides/run-and-scale/async-task-management)는 일부 모델의 task 생성·조회 및 PENDING일 때만 가능한 `/api/v1/tasks/{id}/cancel`을 제공한다. 그러나 검토한 Qwen 편집 API에는 task_id나 해당 모델의 비동기 지원 계약이 없다. Wan 예제를 Qwen 2.1에 적용하지 않는다. [Alibaba 통합 API](https://www.alibabacloud.com/help/en/model-studio/qwen-image-generation-and-editing-api-reference)의 비동기 설명 역시 QwenCloud 직접 계약으로 대체하지 않는다. 위 동기 경로는 향후 계정·무료 보호·사용 승인이 확보될 경우의 참고이며 이번 실제 구현 경로가 아니다. 공급자 idempotency 키는 확인하지 못했다.

## 공개 연구 시험의 안전 조건과 향후 연결 요구

아래는 공식 서비스의 보장사항이 아니라 이번 프로젝트의 설계 요구다. 현재 구현은 공개 연구 시험의 입력 경계와 1회 예약 저널이며 유료 cloud adapter·상용 앱 통합·온디바이스 엔진이 아니다.

1. 공개·승인 입력 manifest의 ID/해시/권한만 받아 4개 역할을 고정한다. 임의 경로·URL·개인 옷장 입력을 허용하지 않는다. Base64와 키는 로그/Android/Git에 넣지 않는다.
2. 서버가 무료 전용 상태를 확인하지 못하면 생성은 disabled다. 로컬 boolean은 제공자 상태를 검증하는 API가 아니다. 오프라인 모의 테스트 결과와 실제 계정 검증을 분리한다.
3. 최초 네트워크 전 durable 예약을 기록한다. 사용자별 고정 요청 ID·4개 입력 해시·모델·prompt 버전을 결합하고 동시/중복 POST를 막는다. POST 응답 유실은 성공/실패 확정 없이 `UNKNOWN`으로 남기고 자동 재생성하지 않는다.
4. 시험 한도는 최대 새 생성 1회로 시작한다. 실패/취소/통신 유실 시 예약을 자동 반환하거나 다른 모델/리전/계정으로 우회하지 않는다.
5. 대기 종료와 제공자 생성 취소를 구분한다. 우리 작업 ID와 제공자 request_id를 보존하되 존재하지 않는 원격 조회 기능을 주장하지 않는다. 취소됐다는 이유로 무료량이 보존됐다고 주장하지 않는다.
6. 결과 다운로드는 검토된 정확한 HTTPS 호스트·공개 IP·크기 제한·리다이렉트 차단을 적용한다. 임의 `*.aliyuncs.com` 허용으로 SSRF 경계를 넓히지 않는다. URL 전체는 로그에서 제외한다. 실제 PNG 디코딩/해시/저장 후 재열기를 확인하고 결과를 요청에 결합한다.
7. API 요청 수락, 이미지 파일 수신, 시각 품질, Android E2E를 별도 상태로 보고한다. 일반 이미지 편집 모델의 4참조 지원이 신발·얼굴·의류 무늬 보존 품질을 보증하지 않는다.

## 사용 조건과 아직 확인하지 못한 사항

[QwenCloud Customer Agreement](https://www.qwencloud.com/legal/agreement)는 입력·출력 사용에 필요한 권리와 제3자 동의를 고객 책임으로 둔다. 출력 사용은 약관·법률 준수 조건이며, 고객 콘텐츠의 모델 개선 사용은 별도 동의를 전제로 한다. 공개 예제의 열람 가능성이 외부 재업로드 권한과 같지는 않다. 문서의 24시간 결과 URL 만료를 모든 서비스 로그/입력의 즉시 삭제 보장으로 확대하지 않는다. 이 문서를 읽은 행위로 새 계약을 수락하지 않았다.

QwenCloud 계정·무료 보호·서버 키는 미확보다. USD0.016은 승인 전 사용할 수 없는 참고 단가다. 공개 연구 시험의 4개 입력 해시 준비는 총괄이 확인했으며, 생성 품질·Android 저장/재열기·S25+ E2E는 별도 실행 증거가 확보될 때까지 미검증이다.

## 무료 공개 연구 데모 — 후보 2개만 확인

1. [공식 Qwen-Image-2.1 Space](https://huggingface.co/spaces/Qwen/Qwen-Image-2.1): 조회 시 `Running on Zero`. [공식 실행 소스](https://huggingface.co/spaces/Qwen/Qwen-Image-2.1/blob/main/app.py)는 최대 10개 참조와 실제 QwenImage21Pipeline 40단계를 사용한다. 초기 web 읽기 도구의 config/API 오류 이후 총괄 GET에서 공개 계약과 revision이 확인됐고 새 생성 1회가 시작됐다. 익명 실행의 영구 보장, 의류 품질, 앱 운영 권한은 별개다.
2. [공식 Qwen-Image-Edit Space](https://huggingface.co/spaces/Qwen/Qwen-Image-Edit): 조회 시 `Running on Zero`지만 [공식 소스](https://huggingface.co/spaces/Qwen/Qwen-Image-Edit/blob/main/app.py)의 입력은 단일 image다. 요구된 4개 개별 참조를 충족하지 않아 실행 후보에서 제외한다. 이미지를 합쳐 입력 개수를 감추거나 지원 범위를 확대하지 않는다.

[ZeroGPU 공식 안내](https://huggingface.co/docs/hub/spaces-zerogpu)는 기존 Spaces 무료 사용을 설명하지만 대기열·할당량·앱별 권한은 별개다. 로그인/할당량 장벽이 나오면 계정 생성·유료 전환·우회 호출을 하지 않는다. API 접근이 가능하다는 이유만으로 운영 앱의 지속 서비스 권한을 얻은 것으로 보지 않는다.

2.1 Space에는 입력·출력 PNG와 프롬프트를 서버에 저장하는 코드가 있고, prompt enhancement 활성화 시 별도 `hugging-apps/qwen-image-2-1-prompt-enhancer` Space로 참조를 전달한다. 개인 자료는 금지하며 승인된 공개 연구 입력만 대상이다. 보관·삭제 정책을 FashionAI 자체 서버 정책과 혼동하지 않는다. [모델 라이선스](https://huggingface.co/Qwen/Qwen-Image-2.1/blob/main/LICENSE)는 연구/평가의 비상업 사용으로 제한하고 상용 사용에 별도 허가를 요구한다. 데모 체험 결과가 확보되어도 정식 앱 배포 권한 및 최종 GO를 충족하지 않는다. 이 담당자는 업로드·생성 POST·모델 다운로드·약관 수락을 하지 않았다.
