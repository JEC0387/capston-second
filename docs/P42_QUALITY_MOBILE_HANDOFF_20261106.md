# P42 — 실제 2/20 단계 완료, 재개 동등성 차단 / 부분 ONNX 검증

P41 `9cd549fae58d6f11165c3cc3cd7f33520baecc1c`에서 독립 로컬 브랜치 `codex/fashionai-p42-quality-mobile-20261106`으로 진행했다. **정식 출시 NO-GO**. 기존 P41 sampler/모델/PNG/로그와 Android 코드는 변경하지 않았다. 원격 Push·기기·운영 서버·유료 자원·전체 CI·APK 빌드/설치는 수행하지 않았다.

실제 증거 루트: `C:\Users\USER\Documents\ChatGPT\capston\experiments\P42-20261106`. 전체 수치는 `P42_RESULTS.json`, 실제 명령·source snapshot·자식 메모리는 각 `*-supervisor`에 있다. 이미지/가중치/체크포인트/원문 로그는 Git에 넣지 않는다.

| 항목 | 실제 검증 결과 |
|---|---|
| 체크포인트 저장·재개 | 저장·재읽기 bitexact PASS. 실제 재개 추론은 BLOCKED |
| 연속2 vs 재개2 | 연속2 PASS, 별도 split1 RAM 보호 종료로 비교 미완료 |
| 실제20단계 | 2/20만 완료; 2단계짜리 별도 스케줄이 아님 |
| 자연스러운 상의 PNG | 미확보. 중간 x_t 시각화는 노이즈가 강하며 완성 결과가 아님 |
| 자연스러운 하의 PNG | 새 정규 실행 없음; P41 진단 PNG는 품질 합격 근거가 아님 |
| 생성형 신발 | 실행 자산·모바일 경로 미확보 |
| Android 모델 변환 | 전체 meta graph 캡처 + 실제 가중치 부분 ONNX/PC ORT 대조 PASS. 전체 모바일 모델은 미완료 |
| S25+ 실제 추론 | NOT TESTED |
| 최종 마법봉 AI 기능 | 기존 안전한 Unavailable 계약 유지, 3종 생성 미완료 |
| 정식 출시 | NO-GO |

## A: 실제 모델 실행과 실패의 구분

공식 P40 가중치 SHA `d6cd38286885bc29fa487ea9383f80ffeb95862e7747c630d42c5d3c05bdd35a`, 원본 source revision `7c0f10af3f91ad4048fe9729c470a13ef905d25a`, P41 prepared-tops01, seed42, FP32, threads2, query128, 원본20단계 RF 스케줄/CFG1.5를 고정했다. 실행 알고리즘은 기존 P41 코드 `d67b34f2599a83d3e84e369650b89d437cb798a5` 그대로다.

| 실행 | 실제 결과 | 전체 시간 | peak RSS / process Commit | 최소 system free RAM / Commit |
|---|---|---:|---|---|
| continuous2 | exit0, 2/20, checkpoint001·002 | 1364.687초 | 862,240,768 / 1,712,607,232B | 165,117,952 / 5,336,002,560B |
| split1 | exit1, `system_memory_reserve_reached`, 0완료 | 310.344초 | 851,910,656 / 1,546,420,224B | 7,630,848 / 5,634,015,232B |
| resume2 | 실행하지 않음 | — | 유효한 split1 checkpoint 없음 | — |

실제 두 단계 계산 시간은 각각669.668초,674.600초다. 단순 선형 외삽으로20단계는 약3.7시간 규모지만 **실제20단계 완료 시간 측정이 아니다**(마지막CFG/자원 변화 차이 포함). 같은 설정의 장시간 재추론은 반복하지 않았다. CPU 수치를 S25+ 속도로 환산하지 않는다.

연속 실행의 저장 상태에는 현재 노이즈 텐서 FP32 `[1,3,864,576]`(5,971,968B), 단계1/2, RNG5,056B, 시드,20단계/shift/CFG, 모델/입력/source/adapter SHA가 포함된다. 상태 해시:

- step1: `e5f8e3c89629adce77844969edfc82cb1705688bb8e6324a10c0d70ea874c963`
- step2: `EXPECTED_CONTRACT.json`과 `continuous2/checkpoint-002/sampling-state.json` 및 `P42_RESULTS.json`에 실제값 보존.

P41 checkpoint schema에는 CPU threads가 빠져 있으므로 새 비교기는 **세 실제 invocation의 스레드/설정/source snapshot/보호 기준/프로세스 정상 종료**까지 대조한다. 비교 출력은 텐서·RNG bitexact만 PASS로 인정하고 maxabs/RMSE도 기록한다. 합성 회귀 PASS를 실제 연속·재개 비교 PASS로 쓰지 않는다. continuous2의 checkpoint001을 독립 split1 성공인 것처럼 대체하지 않았다.

연속 프로세스 종료 후 실제 handle의 RSS36,864B / Commit81,920B, split1 종료 후32,768B /77,824B를 확인했다. 프로세스 자원 반환과 시스템 전체 free RAM 변화는 별개다. split1은 Commit 고갈이 아니라 물리 RAM 보호 조건으로 종료됐고 다른 프로그램의 종료/설정을 조작하지 않았다.

실패 후 P42 실행 도구의 신규 시작 기준을 RAM2GiB / Commit3GiB로 강화했다. 이는 보수적인 시작 여유 정책이며 모델의 측정된 최소 요구량 또는 안정성 보증이 아니다. 기존 P41의 runtime96MiB/768MiB·3회 연속 보호/RSS1536MiB/1800초 제한은 완화하지 않았다. 이 정책 이후 대형 재추론을 실행하지 않았다.

## 중간 PNG와 품질

`partial-2-of-20.NOT-FINAL.png`: 실제 checkpoint002를 공식 tensor_to_pil 및 원본 padding 제거로 **시각화한 중간 상태**다. 모델을 새로 실행한 완성 PNG가 아니다. 576×823 RGB,1,224,015B, SHA256 `66880f84335aac64525529258f4b737770a38f4a4695a2428c14d0ae4f25d668`. 완전 디코딩/형식/해시 확인.

육안으로 강한 색상 노이즈와 희미한 인물 윤곽만 보인다. 기존 옷 제거·얼굴/손/배경 보존·질감·무늬·자연스러움은 판단/합격하지 않는다. 정규 스케줄 초반 중간 상태이므로 완성 모델 품질 실패율이나 20단계 결과로 확대하지 않는다. P41 상·하의 PNG SHA는 기존값과 일치한다.

## B/C: 변환 결함 수정과 모바일·신발 경계

실제 ONNX 오류 두 개를 원인별로 보존했다: mixed-type Einsum → 명시적 FP64 입력 수정 후 FP64 Cos(7) CPU kernel 미지원 → 공식 RoPE 계산은 그대로 CPU 전처리에 두고 FP32 결과를 attention 입력으로 전달. 실제 checkpoint의6개 weight를 사용한 부분 ONNX는 ORT CPU에서 서로 다른 두 입력을 통과했다. adapter 원본오차0, ORT maxabs `7.152557373046875e-7`. 허용 오차를 완화하지 않았다.

전체모델 meta graph는3,064nodes/971,813,808parameters/366state tensors를 캡처했지만 실제 전체 가중치 파일 export가 아니다. 전체 모델의 FP32 parameter 크기만3,887,255,232B이며 활성값/변환 복사본은 별도다. NPU/GPU/S25+ 실측 없음. [모바일 상세](P42_MOBILE_REVIEW.md)에 실제 파일·해시·공식 런타임/라이선스 근거를 기록했다.

신발 전용 실행 자산은 확보되지 않았다. Mobile-VTON의 NC 조건, 공개 Android recipe/종속 모델 권리, FLUX.2-klein의 현재 자원 부족을 [신발·Android 검수](P42_SHOES_ANDROID_REVIEW.md)에 구분했다. 새 가중치·계정·약관 동의·외부 추론을 수행하지 않았다. 무료 ONNX 라이브러리만 별도 실험 폴더에 설치했고 기존 P40 venv는 유지했다.

## 코드·검증·보존

새 코드4개: `p42_run_checkpoint_phase.py`, `p42_compare_states.py`, `p42_mobile_probe.py`, `p42_full_graph_probe.py`. 새 집중 테스트3개와 P42 문서3개를 추가했다. Android/인증/동기화/마법봉 API/기존 데이터 포맷은 변경하지 않았다. 실행 담당1명, 병렬 읽기·도구 작성 담당3명이 소유 파일을 분리했다.

`focused01/results.xml`: **40 PASS / FAIL0 / ERROR0 / SKIP0**, pytest25.44초, 실제감시프로세스exit0. 합성 checkpoint 검수·설정/실패증거 거절·새 메모리 시작 기준·변환입력/실제 ONNX Einsum 회귀를 포함한다. tracer의 고정 상수 경고1개와 종료 시 pyreadline 콘솔 소멸자 경고가 있으나 XML/exit0을 확인했다. 테스트 동안 source hash 변경 없음. P41/Android 전체 검사를 재실행하거나 합산하지 않았다.

기존 Preview18605/QA18607에는 접근하지 않았으며 새 사용자 데이터 보존 검사를 했다고 주장하지 않는다. 기존 APK와 P38/P39/P40/P41 참조는 보존한다. 원격 접근/Push/CI/설치 없음. 최종 로컬 Commit은 실험 폴더의 `LOCAL_CHECKPOINT.json`과 실제 Git HEAD를 우선한다.

종료 시 로컬 APK 해시도 재확인했다: Preview18605 `820f62c3c98602a0ff8dfbd1b3da3e61092444eb6d991622f72a726612eb51ce`, QA18607 `651e901d061eaeb01428fb95b61413892572fc02b44dd465a18d01a9c402f07c`. 기존값과 일치한다. 이 검사는 기기의 설치 상태·사용자 데이터 검사와 구분한다.

## 정확한 재개 조건

1. 기존 결과를 유지하고 별도 새 증거 폴더를 만든다. 안정적인 시작 RAM2GiB/Commit3GiB 확보 후에만 아래 split1 실행. 다른 앱/페이지파일을 자동 변경하지 않는다.
2. `python backend/p42_run_checkpoint_phase.py --reference <P42>/continuous2-supervisor/invocation.json --root <새 폴더> --phase split1`
3. 실제 exit0와checkpoint001 확인 후 같은root에서 `--phase resume2 --resume <새 폴더>/split1/checkpoint-001`. 동일 source/model/input 계약 유지. 자동 재시도 없음.
4. `python backend/p42_compare_states.py --continuous-invocation <P42>/continuous2-supervisor/invocation.json --split-invocation <새 폴더>/split1-supervisor/invocation.json --resumed-invocation <새 폴더>/resume2-supervisor/invocation.json --expected-contract <P42>/EXPECTED_CONTRACT.json --output <새 비교.json>`
5. 이 비교가 실제로 통과하기 전20단계 성공을 선언하지 않는다. 정규 품질 검증에는 안정적인 실행 자원·완주 시간 확보가 필요하다. 모바일은 전체 weight graph/고정PE/정밀도/메모리/SDK 및 승인된 S25+ 실제 실행이 별도로 필요하다. 신발 조건 생성 모델·배포 권리와3종 일관성 검증도 남아 있다.
