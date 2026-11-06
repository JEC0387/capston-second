# P44 — 제한 시간 내 모델 대체 경로 판정

2026-11-06 KST. 읽기 전용 소스 검토 담당 결과다. 모델 다운로드, 네트워크 추론, 런타임 설치, 기기 조작, Git 작업은 이 검토에서 수행하지 않았다. 실제 P44 가속기 측정과 생성 결과는 별도 실험 로그를 우선한다.

## 결론

**상의·하의·신발 전체를 생성하고 S25+에서 즉시 실행할 수 있는 새 배포용 모델 자산은 이번 제한 조사에서 확인되지 않았다.** 기존 공식 FASHN 가중치에 대한 가속기 실행 검사가 가장 짧은 실제 검증 경로다. 부분 그래프 성공만으로 전체 추론이나 Android 완료를 판정하지 않는다. 생성형 신발과 승인된 실제 S25+ 검증이 없으므로 현재 정식 출시는 **NO-GO**다.

## 현재 공식 자산 확인

[FASHN 공식 모델 카드](https://huggingface.co/fashn-ai/fashn-vton-1.5)는 사람·의류 이미지 조건, 972M MMDiT, 576×864 출력, Apache-2.0 및 tops/bottoms/one-pieces를 명시한다. 신발 카테고리는 없다. 공식 기본 파이프라인은 첫 사용 시 Human Parser를 자동 다운로드할 수 있으므로 기존 P40/P41의 parser-free flat-lay 전처리 경계를 유지한다. 이번 조회에서 공식 distilled/mobile 또는 신발용 체크포인트를 추가로 확인하지 못했다. 이는 모든 공개 모델이 없다는 주장이 아니다.

P40/P42의 기존 자산·라이선스 기록을 재사용한다. 현재 FASHN 본체 SHA-256은 `d6cd38286885bc29fa487ea9383f80ffeb95862e7747c630d42c5d3c05bdd35a`다. 이미 확보한 파일을 재다운로드할 이유가 없다. Human Parser/SegFormer의 별도 권한 문제가 본체의 Apache 표기로 해소되지는 않는다.

## 새 단서: 제3자 네이티브 FASHN 포트

[저자 저장소](https://github.com/gargpratyush/fashn-vton-stable-diffusion-port)는 FASHN을 C++/GGML로 실행하는 구현과 소스 파일 경로를 공개한다. 이는 FASHN 개발사 공식 대체 모델이 아니다. 저장소는 MIT로 표시되지만 이번 조회에서 LICENSE 원문 접근이 실패했다. 따라서 전체 의존성·재배포 권한 검토가 끝났다고 기록하지 않는다.

[실행 안내](https://github.com/gargpratyush/fashn-vton-stable-diffusion-port/blob/main/docs/fashn_quickstart.md)에 따르면 기존과 같은 SHA의 공식 가중치를 사용하며, 준비된 입력은 RGB PNG 두 장과 pose PNG 두 장이다. 기존 NPZ를 그대로 CLI에 전달할 수 없다. Windows x64 AVX2/FMA·VS2022의 floating CPU 경로를 검증 대상으로 삼는다. raw preparation을 끄면 추가 parser/pose 다운로드가 필수가 아니지만, 소스의 pinned submodules와 빌드 도구가 필요하다. 양자화·GPU는 정상 배포 경로로 검증되지 않았고 공개 양자화 생성도 차단돼 있다. 이 프로젝트의 자체 벤치마크를 현재 PC의 성능으로 사용하지 않았다.

[Android 검토 및 실행 기록](https://github.com/gargpratyush/fashn-vton-stable-diffusion-port/blob/main/docs/fashn_android_plan.md)은 Samsung S23의 Q4_K 20단계 CPU 완료를 보고하지만 소요 시간은 **6시간 41분 39초**이고 동일 정책 수치 대조가 실패했다. APK/JNI·GPU·NPU는 별도 미완료다. 이는 S25+ 성능도, 우리 실기기 실행도 아니다. 따라서 이 경로를 P44 두 시간 내 전체 제품 완성 경로로 채택하지 않는다. 후속 네이티브 이식 참고 후보로만 남긴다.

## 재조사하지 않은 기존 후보

| 후보 | 재사용한 P42 판단 | P44에서 추가 수행 |
|---|---|---|
| Mobile-VTON | 공개 연구 checkpoint가 있어도 CC BY-NC-SA와 Android 자산/신발 검증 문제가 남음 | 새 다운로드·동의·실행 없음 |
| Oxygen-TryOn / ShoeFit | 실행 자산 또는 허용된 배포 경로 미확보 | 후보 조사 반복 없음 |
| FLUX.2-klein-4B | 공식 일반 편집 자산은 있으나 기존 PC의 확보·실행 자원 조건과 신발/Android 실증이 없음 | 대용량 다운로드·추측 성능 계산 없음 |

상세 근거는 `docs/P42_MOBILE_REVIEW.md`, `docs/P42_SHOES_ANDROID_REVIEW.md`에 있다. 예전 확인 상태를 새 실행 성공으로 바꾸지 않았다.

## 실행으로 이어질 수 있는 다음 경계

1. 이미 소유한 FASHN 실제 가중치 부분 그래프의 Intel GPU 실행·원본 수치 대조·메모리·시간을 제한된 실험으로 확인한다. 해당 결과는 총괄 실험 기록으로 판정한다.
2. 통과하더라도 전체 고정 해상도 graph, 공식 전처리와 sampler 계약, 전체 메모리 보호를 별도로 충족해야 전체 추론을 시도할 근거가 생긴다.
3. 신규 상의·하의 PNG가 나와야 원본과 품질 비교할 수 있다. 신발은 별도의 합법적 실행 모델과 실제 결과가 필요하다.
4. S25+ native 엔진·성능·취소·입력/결과 결합을 검증하기 전 P39 `Unavailable` 엔진을 준비 완료로 바꾸지 않는다. 기기 설치는 별도 승인 범위다.

**이 문서는 모델 실행 결과가 아니다.** 문서 검토만으로 자연스러운 생성, 세 종류 착장, 마법봉 실기기 성공 또는 배포 권한 완료를 주장하지 않는다.

## P41 streaming sampler의 DirectML 연결 경계 검수

추가 읽기 전용 검수 대상은 `backend/p41_streaming.py`, `p41_attention.py`, `p41_infer.py`, `p41_sampling_state.py`와 고정된 공식 `tryon_mmdit.py`다. 이 절의 작성자는 sampler를 수정하거나 추론을 실행하지 않았다. 작은 실제 가중치 블록의 GPU 성공 여부는 총괄 P44 실행 증거로 따로 판정한다.

가장 작은 연구용 경계는 공식 모듈의 `_attn_processor(q,k,v)`다. 공식 RoPE·QKNorm 이후의 FP32 Q/K/V만 GPU attention에 전달하고 동일 shape의 CPU 결과를 반환하면, 가중치 읽기·선형층·CFG 두 번 실행·Euler 스케줄은 기존 코드로 유지할 수 있다. 쿼리만 128씩 분할하고 K/V는 모두 유지해야 한다. 비인과·dropout 0·마지막 축 softmax·기존 head dimension의 scale을 바꾸지 않는다. 세션 생성은 chunk/블록마다 반복하지 않고, 3456 및 6912 token 경로와 마지막 chunk를 명시적으로 다룬다.

이는 정확성 검사의 범위를 줄이는 제안이지, 전체 추론 속도 향상을 보증하는 구현은 아니다. 실제 전체 shape의 서로 다른 두 입력과 공식 CPU attention 대조, 실제 DML provider 실행·CPU fallback 없음, finite/dtype/shape, 취소와 오류 전파를 통과한 뒤에만 통합 실험을 검토한다. 기존 P42 허용 오차를 넓히지 않는다. 이후에도 실제 모델 forward와 샘플링의 누적 오차·시간·메모리가 별도 게이트다.

### 없어지지 않는 메모리와 전송 비용

- `DiskLinear`는 BF16 파일의 한 tensor만 읽어 FP32로 승격한다. 모델 전체를 GPU로 옮기는 경로가 아니며 현재 입력 계약은 CPU FP32다. 가속기 연결을 위해 이 검사를 제거하거나 state_dict 전체를 복제하면 기존 streaming 보장을 잃는다.
- 실제 P42 `continuous2/events.jsonl`의 최대 Linear 출력은 **247,726,080 bytes**다. SingleStreamBlock은 `linear1`에서 QKV와 MLP를 함께 생성한다. attention만 가속해도 이 CPU 활성 tensor와 MLP·잔차·디스크 읽기는 남는다.
- B=1/H=10/L=6912/D=128/FP32 산술상 Q·K·V·출력은 각각 **35,389,440 bytes**다. query128 score tensor도 **35,389,440 bytes**이며 softmax 사본·ORT/드라이버 workspace는 별도다. 이것은 OS peak 측정치가 아닌 tensor 크기 계산이다.
- naive CPU feed로 54개 chunk마다 K/V 전체를 다시 보낸다면, Q/K/V 입력과 결과의 논리적 전송량은 한 full-length attention당 약 **3,892,838,400 bytes**다. 실제 unified-memory 전송 구현은 측정해야 한다. K/V를 한 번 GPU에 유지할 수 있는 provider binding이 검증되지 않았다면 반복 전송 비용을 무시하지 않는다.
- Iris Xe 공유 메모리는 별도 무료 VRAM이 아니다. 작업자 RSS만 보지 않고 기존 system available RAM/commit 보호를 유지한다. 기존 P42는 peak RSS 862,240,768B / commit 1,712,607,232B였으며, 이 값에 GPU 메모리가 모두 포함된다고 가정하지 않는다.

### 체크포인트와 시간 판단

기존 checkpoint 계약은 adapter 전체 해시, `p41-streamed-fp32-v1`, Torch 버전, 입력/모델 해시, CFG·스케줄·쿼리 크기를 고정한다. DML backend를 추가한 코드가 예전 `checkpoint-002`를 같은 계약으로 읽도록 검사를 생략하면 안 된다. 별도의 알고리즘/backend identity와 명시적 전환 검증 없이 CPU 2단계를 DML 진행률로 이어붙이지 않는다. 현재 CPU checkpoint는 원형 그대로 보존한다.

P42 두 단계 실측은 1364.687초이고 4회 model forward를 포함한다. 해당 block-start 로그만으로 attention과 MLP의 독립 소요 시간을 분리할 수 없으므로 attention이 전체 병목이라고 단정하지 않는다. 두 시간 판단에 사용할 값은 작은 16-token 속도가 아니라 full-length kernel의 CPU↔GPU 전달 포함 시간, 프로세스 메모리, 실제 한 forward 시간이다. 전체 20단계는 공식 설정상 39 forward이므로 제한 시간 내 품질 검사까지 가능한 측정 근거가 없으면 정규 실행을 무작정 시작하지 않는다.
