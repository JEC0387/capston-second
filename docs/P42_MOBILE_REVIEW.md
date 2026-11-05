# P42 모바일 변환 검토 — 전체 Android 엔진과 부분 그래프를 구분

## 최신 실제 결과 (아래의 준비·중간 실패 기록보다 우선)

- `mobile/full-meta01`: 원본 전체 모델의 strict meta graph 캡처 PASS. 971,813,808 parameters, 366 state tensors, 3,064 nodes. 실제 가중치/ONNX/LiteRT/Android 실행은 아님. 48.078초, peak RSS313,753,600B / Commit557,395,968B.
- `mobile/subgraph02`: 실제 가중치의 Torch export PASS, ONNX checker PASS, ORT 로딩 FAIL: `Einsum`의 float32/float64 혼합. 단순 패키지 미설치로 처리하지 않음.
- `mobile/subgraph03`: 명시적 FP64 위치 승격은 원본 eager와 정확히 일치. ORT는 FP64 `Cos(7)` 구현 부재로 FAIL. 모든 Android backend가 이 연산을 지원하지 않는다는 뜻이 아님.
- `mobile/subgraph04`: **PASS_CPU_SUBGRAPH_ONLY**. 원본 `EmbedND` 계산은 CPU 경계 밖에서 그대로 수행하고 공식 반환값 FP32 PE를 전달. 실제 모델 가중치 attention(x,pe) export와 서로 다른 두 입력 ORT CPU 대조 성공. adapter 원본 오차0, ORT 최대 절대오차 `7.152557373046875e-7`. 29.094초, peak RSS412,868,608B / Commit646,000,640B.
- ONNX 파일26,246,974B, SHA256 `59593a1748881b4f040b476b64f79da42ac4379f4dbb951c6af8fb385f6f632a`. **합성16 tokens의 단일 블록**이며 전체6912 tokens/모델의 export·성능·S25+ 실행 근거가 아님.
- 공식 PyPI ONNX1.17.0 휠14.5MB를 `mobile/onnx-deps02`에 격리 설치했다. 기존 P40 환경·전역 Python은 변경하지 않았다. 최초 sandbox 임시파일 설치 권한 실패와 `subgraph01`의 의존성 경로 실패는 보존했다. 새 로그 경로에서만 수정 검증했다.
- 앞으로 전체 고정 해상도 PE 테이블을 원본과 대조하고, 실제 전체 weight graph/메모리/Android QNN 또는 GPU 실행을 검증해야 한다. 현재 앱 엔진을 Ready로 변경하지 않는다.

2026-11-06 KST. P41 원본 모델·실험·APK와 Android 소스를 변경하지 않았다. 외부 모델/가중치 다운로드, 계정 생성, 기기 설치, 클라우드 컴파일은 수행하지 않았다. 실제 실행 결과는 총괄이 직렬 실행한 `experiments/P42-20261106/mobile/`의 `result.json` 및 supervisor 기록을 우선한다. 이 문서의 준비된 절차를 실행 성공으로 해석하지 않는다.

## 준비한 실제 부분 그래프 검사

`backend/p42_mobile_probe.py`는 고정된 공식 FASHN 소스 SHA를 검증하고, P40 가중치 SHA `d6cd38286885bc29fa487ea9383f80ffeb95862e7747c630d42c5d3c05bdd35a`의 **`double_blocks.0.img_attn.*` 실제 가중치**만 읽는다. 전체 모델이나 임의 초기화 모델이 아니다. hidden=1280, heads=10을 유지하며 공식 SelfAttention/EmbedND의 Linear·RMSNorm·RoPE·SDPA를 그대로 실행한다. 테스트 sequence는 합성 16 tokens로 제한한다. 실제 6912 tokens 모델 메모리·품질·지연시간의 증거가 아니다.

검사 순서: source/hash/schema → eager CPU 결과 → strict `torch.export` → 서로 다른 합성 입력 2개로 재실행 비교 → ONNX opset18 export 및 checker → 가능한 경우 ORT CPU 수치 비교. 임의 입력을 고정한 graph가 통과하지 않도록 두 번째 입력의 특성과 좌표도 바꾼다. 실제 tensor namespace/shape/dtype가 맞지 않으면 무작위 weight로 대체하지 않고 거절한다. 결과에 `whole_model_exported=false`, `android_executed=false`, `png_generated=false`를 명시한다.

P40 환경 파일의 초기 확인 결과 Torch 2.6.0+cpu와 ORT 1.21.0은 있으나 ONNX/ONNXScript/LiteRT는 없다. 추가 패키지를 별도 실험 경로로 준비한 경우에만 `--onnx-deps`로 명시한다. 기본 환경·P40 venv는 변경하지 않는다. ONNX 미설치는 의존성 차단이며 미지원 연산자 오류가 아니다. 실제 export가 실패하면 단계/예외를 별도 기록하며, 부분 그래프 성공으로 전체 TryOnModel 변환 성공을 주장하지 않는다.

총괄은 `p41_monitor.py`를 통해 별도 실제 작업자에서 실행한다. helpers는 `p40_flatlay_probe.py`, `p41_streaming.py`이며 probe 인자는 `--source-dir`, `--source-manifest`, `--weights-dir`, `--output-dir`이다. 180초 정도의 유한 시간과 기존 RAM/Commit 보호 조건을 유지한다. 자동 다운로드나 전체 모델 재로딩을 하지 않는다.

별도 `p42_full_graph_probe.py`는 원본 TryOnModel 전체의 meta tensor shape graph를 strict `torch.export`로 조사하도록 준비했다. **실행 결과 확인 전 상태는 미실행**이다. 실제 weight를 읽거나 ONNX/LiteRT 파일로 저장하지 않고 연산자·shape 목록만 기록한다. 입력은 정규 864×576이며 category/mask까지 meta로 고정한다. source의 CPU 상수→meta 이동, 좌표 대입 functionalization, dataclass/einops는 실제 capture에서 검증할 사항이다. [PyTorch 2.6 meta 문서](https://docs.pytorch.org/docs/2.6/meta.html)에서처럼 실제 값이 없는 tensor이므로 수치·품질·device kernel 성공 증거가 아니다. [strict export](https://docs.pytorch.org/docs/2.6/export.html)의 실패를 다른 backend 실패로 바꾸지 않는다. 도구 메모리와 실행 시간은 계속 supervisor로 제한한다.

### 실제 부분 export 실패와 최소 수정

`mobile/subgraph02/result.json`: 실제 weight 6개를 사용한 Torch export는 두 입력 모두 최대 절대오차0으로 통과했다. ONNX 26,253,631 bytes 생성 및 checker도 통과했으나 ORT 로딩이 `/position/Einsum_2`에서 FP32/FP64 입력 타입 불일치로 실패했다. 이것은 실제 PC ONNX export 경계의 실패이며 S25+ NPU 미지원 연산자 판정이 아니다.

고정 원본 RoPE는 FP64 omega와 FP32 좌표에 대한 eager 승격을 사용한다. legacy ONNX exporter가 해당 승격을 명시하지 않아 생긴 차이를 해결하도록 **실험 wrapper에서만 ids를 FP64로 명시 변환**했다. 원본 소스·P41 sampler는 그대로이며, 기존 원본 FP32 ids eager 결과를 독립 기준으로 유지했다. wrapper eager는 bitexact, Torch export는 기존 rtol/atol 1e-5, ORT 비교는 기존 rtol1e-4/atol1e-5를 적용했다. 결과는 다음 `subgraph03` 기록과 같으며 이 수정만으로 모바일 준비 완료가 아니다.

후속 실제 `subgraph03`: wrapper eager 및 Torch export 최대오차0으로 통과했지만 ORT CPU의 FP64 `Cos(7)` kernel 부재로 실패했다. 이 결과도 원본 실패 증거로 보존한다. 마지막 제한 실험 `subgraph04`에서는 공식 EmbedND 계산을 변경하지 않고 CPU 전처리 경계로 분리해, **공식 코드가 마지막에 반환하는 FP32 PE를 attention graph의 입력**으로 제공한다. FP64 수식을 FP32 근사로 교체하지 않는다. 서로 다른 두 좌표의 PE와 실제 weight를 사용하며, 원본 eager 대비 adapter는 bitexact를 요구한다. 나머지 Torch/ORT 허용 오차는 그대로다. FP64 승격/Einsum 회귀 검사는 기존 실패 원인을 검증하는 기록으로 유지하지만 최종 attention graph에는 해당 연산을 포함하지 않는다. 전체 해상도의 PE·Android 상수 전달·전체 denoiser 연결은 별도 미검증이다. `subgraph04`의 실제 실행 전에는 성공으로 기록하지 않는다.

## 실제 Android 실행 경계

| 경로 | 현재 근거 | 아직 필요한 검사 |
|---|---|---|
| ONNX + ORT CPU | 이미 설치된 PC ORT로 부분 그래프 대조 가능 | 전체 고정 그래프 export, full-shape 수치·최대 메모리, Android ARM64 런타임, 취소·저장 연결 |
| ORT QNN / Hexagon | 공식적으로 Snapdragon Android 대상 경로가 존재 | 호환 QNN SDK·EP 버전, static shapes, graph partition 및 데이터 타입, 실제 S25+ compile/run |
| LiteRT Torch | PyTorch→LiteRT 변환 경로 존재 | 현재 공식 converter 설치 요건은 Linux. 이 Windows 실험에 converter 미설치; S25+ delegate 결과 없음 |
| GPU / NPU 성능 | 기기 추론하지 않음 | 실제 장치 메모리·지연·열·취소 측정. PC CPU 시간을 S25+로 환산하지 않음 |

[ORT QNN 문서](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html)는 Android 지원과 고정 shape, 지원 연산·데이터 타입 조건을 명시한다. 문서에는 FP16 옵션과 HTP 양자화 지침이 함께 있으므로 모든 SDK/모델에 같은 정밀도 지원이 된다고 단정하지 않는다. ONNX checker 통과는 QNN compilation 통과가 아니다. 단순 연산 이름이 목록에 있더라도 rank/type/shape 조건과 실행 partition을 검증해야 한다.

[LiteRT Torch 공식 저장소](https://github.com/google-ai-edge/litert-torch)는 변환기의 Linux 요구사항을 명시한다. [LiteRT 런타임](https://github.com/google-ai-edge/LiteRT)의 Android CPU/GPU/NPU 플랫폼 지원을 Windows 변환 도구나 FASHN 호환성으로 확대하지 않는다. 현재 새 WSL/SDK/런타임을 설치하거나 사용 약관을 수락하지 않았다.

실제 본체는 971,813,808 parameters이며 FP32 parameter만 계산해도 3,887,255,232 bytes다. 원본 BF16 safetensors는 1,943,668,048 bytes, DWPose 두 파일까지 합하면 2,294,813,897 bytes다. 활성 tensor·graph·allocator·캐시·복사본은 별도다. P41의 disk-streamed CPU peak는 모든 가중치를 상주시킨 Android graph의 메모리 상한이 아니다. 전체 batch1 또는 query-chunk 대응 모바일 graph를 실제 만들기 전에는 12GB 등의 기기 RAM 숫자만으로 실행 가능 판정하지 않는다.

## 라이선스와 더 작은 모델의 실제 가용성

[FASHN 모델 카드](https://huggingface.co/fashn-ai/fashn-vton-1.5)는 Apache-2.0, 상의/하의/원피스와 576×864 픽셀 공간 구조를 명시한다. 신발은 공식 카테고리가 아니다. 코드·weight의 Apache 표기는 관련 고지 보존이 필요하며 모든 종속 모델의 배포 권리를 일괄 보증하지 않는다. DWPose는 P40에 보존한 공식 모델 카드·원출처 확인을 재사용한다.

[Human Parser 라이선스](https://raw.githubusercontent.com/fashn-AI/fashn-human-parser/main/LICENSE)와 [모델 카드](https://huggingface.co/fashn-ai/fashn-human-parser)는 NVIDIA SegFormer 계열 조건을 명시한다. 현재 실험은 공식 flat-lay/segmentation-free에서 parser 결과가 사용되지 않는 제한 경로이며 해당 자산을 포함하지 않는다. 일반 model-worn 의류 파싱·정식 앱 배포 권한까지 해결됐다는 뜻은 아니다.

[Mobile-VTON 공식 코드](https://github.com/tmllab/2026_CVPR_Mobile-VTON)는 CC BY-NC-SA 4.0이고 [저자 checkpoint](https://huggingface.co/FlashStight/Mobile-VTON/tree/main/checkpoint)는 commit `602e3bc`의 약3.76GB 파일 묶음이 실제 존재한다. 연구 후보와 상용 앱 포함은 구분한다. [논문](https://arxiv.org/html/2603.00947v4)의 약80초 결과는 Xiaomi 17 Pro Max / Snapdragon 8 Gen 5 / INT8 NPU의 저자 측정이며 S25+ 결과가 아니다.

[공개 inference.py](https://raw.githubusercontent.com/tmllab/2026_CVPR_Mobile-VTON/main/inference.py)는 Diffusers/Transformers/Accelerate와 두 text encoder, DINOv2, VAE, denoiser들을 함께 사용한다. [실행 스크립트](https://raw.githubusercontent.com/tmllab/2026_CVPR_Mobile-VTON/main/inference.sh)는 연구용 배치 추론이다. 공개 Python 코드와 checkpoint가 곧 Android용 양자화 graph/런타임 패키지는 아니다. 이 PC 메모리 상황에서 3.76GB를 추가로 내려받아 같은 문제를 반복하지 않는다. 우선 합법적인 배포 권한, 저자 모바일 export recipe/자산, 전체 종속 모델 provenance를 확보해야 한다. 신발 지원도 확인되지 않았다.

P39 마법봉을 Ready로 변경하지 않는다. 현재 연구용 부분 변환의 결과를 앱의 인물+상의+하의+신발 실제 생성·비교·저장 성공으로 반환하지 않는다.
