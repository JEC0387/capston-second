# P40 실제 가중치 확보 및 CPU 추론 실험

2026-11-06 KST. P39 `68ab31778127415d95ea5e8dffa74dcb21152000`에서 독립 로컬 브랜치 `codex/fashionai-p40-model-experiment-20261106`으로 진행했다. P38/P39, 기존 납품본, Preview 18605/QA 18607을 변경하지 않았다. 이번에는 실제 가중치를 다운로드하고 실제 모델을 로딩했다. **로딩 성공은 생성 성공이 아니다. 최종 생성형 제품 출시는 NO-GO다.**

## 실제 자산 및 출처

실험 루트: `C:\Users\USER\Documents\ChatGPT\capston\experiments\P40-20261106`. 가중치·공식 예제·런타임·실행 로그는 이 별도 폴더에만 보관한다. Git/APK에는 포함하지 않는다.

| 실제 파일 | 바이트 | 다운로드 후 검증한 SHA256 |
|---|---:|---|
| `weights/model.safetensors` | 1,943,668,048 | `d6cd38286885bc29fa487ea9383f80ffeb95862e7747c630d42c5d3c05bdd35a` |
| `weights/dwpose/yolox_l.onnx` | 216,746,733 | `7860ae79de6c89a3c1eb72ae9a2756c0ccfbe04b7791bb5880afabd97855a411` |
| `weights/dwpose/dw-ll_ucoco_384.onnx` | 134,399,116 | `724f4ff2439ed61afb86fb8a1951ec39c6220682803b4a8bd4f598cd913b1843` |
| `code/official-source.zip` | 223,162 | `4476a782b4196ab0d94368de0e2ad7478330bd1a7a81538b22f2bdd49e55d23b` |

가중치 합계 **2,294,813,897 bytes**. 실제 다운로드 기록은 `weights-download.log`, `downloads-20261106-015003.jsonl`; 모델 실행 때도 전체 SHA/크기를 검증한다.

- [공식 코드](https://github.com/fashn-AI/fashn-vton-1.5/tree/7c0f10af3f91ad4048fe9729c470a13ef905d25a): `7c0f10af3f91ad4048fe9729c470a13ef905d25a`, Apache-2.0. 압축 CRC/경로와 추출 소스 20개 SHA를 확인했다. `source-manifest.json`으로 고정한다.
- [FASHN 가중치](https://huggingface.co/fashn-ai/fashn-vton-1.5/tree/7720683168567eb5a2a4c67f15116c6e29c83ded): `7720683168567eb5a2a4c67f15116c6e29c83ded`, 모델 카드 Apache-2.0.
- [DWPose 가중치](https://huggingface.co/fashn-ai/DWPose/tree/548b5df25b84d9f4aac0611dfa1c2a7a12f15571): `548b5df25b84d9f4aac0611dfa1c2a7a12f15571`, 모델 카드 Apache-2.0. FASHN 재호스팅이며 IDEA-Research/YOLOX 등의 원출처 표시를 유지한다.
- 라이선스/모델 카드는 `licenses/`에 보존했다. Human Parser·SegFormer는 다운로드/설치/라이선스 수락하지 않았다. NVIDIA 계열 Parser 배포 권리가 확보됐다는 뜻이 아니다.
- Python의 codeload TLS 검증은 로컬 issuer 문제로 실패했다. 실패 기록을 보존하고, Windows Schannel 인증서 저장소를 사용하는 `curl.exe`의 정상 TLS 검증으로 동일 공식 ZIP을 받았다. 인증서 검증 해제·자격 증명 추출은 하지 않았다.

공식 공개 예제 `person3.png`와 평면 상의 `garment3.jpeg`만 기존 P14 허가 자료에서 복사했다. 원출처는 공식 Space revision `1c49e7328d65aab0b784637275dbe17d4c0fa1b3`; `inputs/INPUTS.json`에 출처·크기·SHA가 있다. 기존 생성 결과를 재사용하지 않았다. 개인 사진·갤러리·옷장·기기는 접근하지 않았다. 예제의 앱/Git 재배포 권한을 주장하지 않는다.

## PC 및 격리 런타임

`environment.json`: Windows 11 Home 26200, i5-1155G7 4코어/8스레드, Iris Xe, RAM 8,300,711,936 bytes. 최초 가용 RAM 약 0.92 GiB, C 여유 14,756,421,632 bytes. WMI의 AdapterRAM 값은 전용 VRAM 보증이 아니다. 실제 Torch `cuda_available=false`다. WSL의 Ubuntu/docker-desktop 존재만 확인하고 실행·변경하지 않았다.

별도 `.venv`에 Python 3.11.9, 공식 CPU wheel Torch 2.6.0+cpu/torchvision 0.21.0+cpu, ORT 1.21.0, safetensors 0.5.3, numpy 2.2.6, OpenCV headless 4.11.0.86 등을 설치했다. 정확한 버전은 `runtime-packages.txt`, 직접 의존성 라이선스 메타데이터는 `runtime-license-metadata.json`, 설치 로그는 `install-*.log`다. 전역 Python/기존 backend 환경을 변경하지 않았다.

## 실제 실행 경로와 제한

`backend/p40_flatlay_probe.py`는 PC 연구 전용이다. 수정하지 않은 공식 모델/DWPose/전처리/샘플러 코드를 고정 SHA로 검증하고 실행한다. 허용 범위는 **flat-lay 의류 + segmentation-free**뿐이다. 이 조건에서 공식 `agnostic.py`의 두 영상 처리 함수는 입력 픽셀을 그대로 반환하므로 별도 Parser를 사용하지 않는 경로를 분리했다. 가짜 Parser를 주입하거나 기존 서버의 Parser·GPU·라이선스 검사를 무력화하지 않았다.

인물은 실제 DWPose로 검출한다. 평면 의류에 사람이 없다는 공식 garment-pose 규약만 사용한다. 의류를 입은 모델 사진, 신발, 세 의류 동시 생성은 이 실험에서 지원하지 않는다. 해상도 576×864, 최소 20단계, 기존 조건·CFG·스케줄을 유지한다. PNG는 전체 단계가 끝난 뒤 실제 디코딩·형식·크기·SHA 검증을 거쳐서만 게시한다.

`p40_supervise.py`는 자신이 생성한 단일 자식만 시간/RAM/commit 한도로 종료한다. 다른 앱을 종료하지 않는다. 새 증거 폴더와 실행 소스 스냅샷을 사용한다. `HF_HUB_OFFLINE` 등과 Python socket guard는 실험의 다운로드 방지 장치이며 OS 전체 방화벽이라는 뜻은 아니다. 로컬 입력·실제 자산만 사용한다.

## 실행 증거 및 발견한 오류

| 실행 | 실제 결과 | 증거 |
|---|---|---|
| `tops01` | 실제 DWPose 18개 관절, 971,813,808 모델 parameter 로딩. 첫 forward에서 텐서 4차원/3차원 불일치로 실패. 0/20단계, PNG 없음 | `runs/tops01/events.jsonl`, `final.json`, `runs/tops01-supervisor/` |
| `tops02` | 차원 수정 후 실제 forward 진입. 337.172초에 가용 RAM 보호 기준으로 중단. 0/20단계 완료, PNG 없음 | `runs/tops02/events.jsonl`, `runs/tops02-supervisor/supervisor.json` |
| `tops03` | 실제 체크포인트를 INT8로 변환하던 중 44.078초에 같은 RAM 보호 기준으로 중단. 44개 Linear 변환 후 index 44 변환 시작 기록까지 존재. 전체 INT8 변환·forward·PNG 생성은 미완료 | `runs/tops03/events.jsonl`, `runs/tops03-supervisor/supervisor.json`, `invocation.json`, `probe-source.py` |

`tops01` 오류는 **새 실험 어댑터의 회색조 포즈 배치 차원 누락**이었다. 공식 single-batch repeat 처리를 복구했다. 실제 Torch 텐서로 수정 전 실패와 수정 후 픽셀·채널 보존을 검사했다. 앱 결함이나 모델 가중치 결함으로 분류하지 않는다. `tops02`는 모델 로딩 약 33.94초, 첫 연산 시작 약 34.27초. 종료 원인은 `system_memory_reserve_reached`; 종료 순간 free RAM 59,228,160 bytes, free commit 3,681,951,744 bytes였다. 강제 자식 종료 때문에 probe의 `final.json`이 없으며 supervisor 종료 기록을 근거로 사용한다. 이전 성공 로그로 채우지 않는다.

BF16은 공식 CUDA 권장 정밀도이며 이 실험의 CPU BF16은 별도 호환성 실험이다. 공식 CPU FP32 경로는 메모리 부담이 더 크다. 같은 설정을 반복하지 않고 Linear를 하나씩 FP32→INT8로 교체하는 대안을 실제 시도했다. 전체 모델 복제/일괄 FP32 변환을 피했지만 변환 중 메모리도 현재 PC에서 감당하지 못했다. `tops03` 종료 순간 free RAM 14,262,272 bytes, free commit 2,963,947,520 bytes였다. 새 INT8 모델 파일도 생성하지 않았다. 세 번째 시도 이후 무한 재시도하지 않았다. 양자화 테스트 통과는 전체 모델 변환·Android 변환·품질 동등성 증명이 아니다.

실험 종료 후 free RAM 551,960,576 bytes, free commit 7,129,075,712 bytes, C 여유 11,761,750,016 bytes를 확인했다. 시간·메모리 종료는 실험 자식만 대상으로 했으며 다른 앱은 종료하지 않았다. 종합 목록과 실제 기록은 `P40_FINAL_EVIDENCE.json`에 보존했다. `tops02`/`tops03` 강제 종료 당시 이벤트는 중간 기록이며 전체 프로세스 peak RAM/VRAM을 확정하는 근거로 사용하지 않는다.

## 자동 검사와 보존

- `probe-tests02.xml`: **9 PASS / 0 FAIL / 0 ERROR / 0 SKIP**, 실제 Torch 차원 회귀 포함. 모의/계약 검사를 모델 생성 성공으로 계산하지 않는다.
- `safety-tests01.xml`: **8 PASS / 0 FAIL / 0 ERROR / 0 SKIP**. 기존 자산 덮어쓰기 거절, 디스크 부족, 악성 리다이렉트, 전송 시간 제한, 실행 실패 기록, 소스 스냅샷, 소유한 자식만 종료를 검사했다.
- 최종 `probe-tests03.xml`: **10 PASS / 0 FAIL / 0 ERROR / 0 SKIP**, 실제 동적 INT8 커널의 독립 계산 기대값·오차·bias·공유 모듈 검사를 추가했다. 이전 9개 재실행을 합산하지 않는다. 최종 고유 집중 검사는 **10 + 8 = 18개**다. `pip check`도 통과했다.
- pytest 종료 후 `pyreadline3` 콘솔 객체 소멸자에 Windows invalid-handle 경고가 출력됐다. pytest exit 0 및 XML 실패 0과 구분한다. 전역 콘솔 설정을 바꾸지 않았다.
- Android/생산 서버/의존성/마법봉 계약을 수정하지 않아 기존 전체 Android/CI/APK를 다시 실행하지 않았다. P39의 과거 검사 결과는 P40 모델 추론 증거가 아니다.
- 기기·사용자 데이터는 접근/변경하지 않았으며 이번에 새 데이터 해시 대조를 수행한 것으로 주장하지 않는다. 원격 Push/Fetch/Pull/Merge/Reset이나 Actions 실행은 하지 않았다.
- 기존 로컬 APK를 읽기 전용 재해시했다. Preview 18605 `820f62c3c98602a0ff8dfbd1b3da3e61092444eb6d991622f72a726612eb51ce`, QA 18607 `651e901d061eaeb01428fb95b61413892572fc02b44dd465a18d01a9c402f07c`가 기존 기준과 일치한다. 이는 기기 데이터 재검증이 아니다.

## S25+ 및 신발: 독립 판정

FASHN은 PC PyTorch 원본 자산이며 Android용 변환 자산을 확보한 것은 아니다. 실제 전체 모델 ONNX/LiteRT/QNN export, 동일 입력 수치 비교, S25+ 로딩/메모리/추론/저장·재열기는 **미실행**이다. CPU에서 메모리 제한으로 중단된 사실을 S25+ 실행 불가능의 증명으로 확대하지 않는다.

[Mobile-VTON 공식 코드](https://github.com/tmllab/2026_CVPR_Mobile-VTON)와 [공식 저자 checkpoint](https://huggingface.co/FlashStight/Mobile-VTON/tree/602e3bc3609d4fdd253cd388fe3db6a6a17e8906/checkpoint)는 확인했으나 다운로드하지 않았다. 표시 약 3.76 GB, CC BY-NC-SA 4.0이며 정식 앱 포함 권한이 아니다. 공개 코드는 인물·의류 조건 VTON이지만 즉시 사용 가능한 Android 변환 패키지는 확인하지 못했다. [논문](https://arxiv.org/html/2603.00947v4)의 Snapdragon 8 Gen 5 결과는 저자의 기기 결과이지 S25+ 측정값이 아니다. 상의 기본 CLI와 하의 코드 경로 존재를 실제 하의/신발 실행 성공으로 혼동하지 않는다.

[LiteRT Torch](https://github.com/google-ai-edge/litert-torch), [ORT QNN](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html)은 변환·런타임 경로다. 이 모델의 연산자/정적 shape/양자화 graph가 실제 export되지 않아 구체적인 지원 성공·실패 연산자를 단정하지 않는다. [Qualcomm SD1.5](https://aihub.qualcomm.com/models/stable_diffusion_v1_5)는 그 자체로 의류 조건 VTON이 아니다. 새 계정/클라우드 컴파일/유료 사용은 하지 않았다.

신발 후보는 [ShoeFit](https://colorful-liyu.github.io/ShoeFit/), [Oxygen-TryOn](https://github.com/OxygenVision/Oxygen-TryOn), [ShoeModel](https://arxiv.org/html/2404.04833v2)을 제한적으로 확인했다. 이번에 공식 가중치·실행 코드·사용 권한이 함께 확인되어 즉시 실행할 수 있는 신발 파이프라인은 확보하지 못했다. 공개 계획이나 제3자 mirror를 실제 자산으로 취급하지 않는다. 상의 성공 여부와 무관하게 신발/3종 전체 생성은 BLOCKED다.

## P39 연결과 다음 실행 조건

P39 `DeviceVtonEngine`의 불변 입력, 한 작업 잠금, 취소, 결과 receipt/PNG 검증, 비교·저장 경로를 보존했다. 기본 `UnavailableDeviceVtonEngine`을 Ready로 바꾸지 않았다. PC 연구 어댑터를 Android 엔진 또는 인증된 자체 서버처럼 연결하지 않았다.

완료에 필요한 조건은 (1) 승인된 로컬 자원에서 전체 추론 완료 및 품질 검수, (2) 신발·세 카테고리 지원 자산과 배포 권리, (3) 실제 Android 그래프/런타임의 수치·메모리·취소 검증, (4) 별도 버전 설치 승인 후 S25+ 새 생성→비교→저장→재열기 E2E다. GPU/결제/외부 업로드를 자동으로 요청하거나 실행하지 않는다. 이 P40 실험은 유료 비용 0이며 실제 GPU/VRAM 사용도 없다.

| 판정 항목 | 최종 상태 |
|---|---|
| 실제 생성형 모델 파일 확보 | PASS — 원본 FASHN 본체와 DWPose 2개, 실제 SHA 검증 |
| 실제 PC 모델 로딩 | PASS — 원본 BF16 parameter를 strict assign, meta tensor 잔존 검사 |
| 실제 PC 추론 / 새 착장 PNG | FAIL / 없음 — 차원 결함 수정 후 자원 보호 중단 |
| 상의 | 공식 지원·실제 요청 시도, 전체 생성 미완료 |
| 하의 | 공식 지원, 실제 실행 미검증; 상의 메모리 실패 후 반복하지 않음 |
| 신발 / 3종 전체 착장 | BLOCKED — FASHN 비지원, 승인된 즉시 실행 모델 미확보 |
| 실사진 자연스러움 | NOT TESTED — 실제 생성 출력 없음 |
| S25+ 모델 로드·추론·비교·저장·재열기 | NOT TESTED — Android 모델/런타임 미준비, 기기 접근/설치 없음 |
| P39 마법봉 | 기존 미준비 차단·불변 입력·취소·저장 계약 유지, 실제 엔진 미등록 |
| 출시 | **NO-GO** |

이번 변경 파일은 `backend/p40_download.py`, `backend/p40_flatlay_probe.py`, `backend/p40_supervise.py`, `backend/tests/test_p40_flatlay_probe.py`, `backend/tests/test_p40_experiment_safety.py`, 이 문서다. 새 APK는 만들지 않았다. 로컬 커밋 SHA는 이 문서가 포함된 커밋을 조회하며, 원격 Push는 현재 제한에 따라 수행하지 않는다.
