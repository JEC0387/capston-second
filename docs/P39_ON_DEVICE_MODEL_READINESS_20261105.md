# P39 — 온디바이스 생성형 VTON 준비 상태

2026-11-05 조사. **BLOCKED / 실제 엔진 미구현·모델 추론 미검증.** P38의 기기 내 비생성형 착장 합성과 구분한다. 이 문서는 P38 작업 브랜치의 독립 조사 결과이며, 별도 브랜치·worktree·가짜 추론 엔진을 만들지 않았다.

## 확인한 로컬 범위

- 작업 저장소의 backend와 Android assets, 빌드·가상환경을 제외한 모델 파일 이름을 읽기 전용 검색했다. 실제 FASHN `model.safetensors`, DWPose 두 ONNX, `vton.manifest.json`, `parser.manifest.json`은 발견되지 않았다. PC 전체 또는 미제공 외부 경로의 부재를 보증하지 않는다.
- Android assets의 `selfie_segmenter.tflite`는 249,537 bytes인 분할 자산이다. 착장 생성 모델이 아니다. 별도 생성용 LiteRT/ONNX Runtime Android 의존성도 확인되지 않았다.
- P30/P31 서버의 오프라인 로딩·SHA256·Parser 번들·고정 구현 revision 검사와 기존 인증 경로는 유지한다. 기존 자동 검사의 모의 모델 통과는 실제 가중치 로딩 또는 생성 성공이 아니다.

## 공식 모델과 적용 한계

[FASHN v1.5 공식 모델 카드](https://huggingface.co/fashn-ai/fashn-vton-1.5)는 972M MMDiT, 픽셀 공간 생성, 576×864 출력을 설명한다. [기존에 고정한 pipeline](https://github.com/fashn-AI/fashn-vton-1.5/blob/7c0f10af3f91ad4048fe9729c470a13ef905d25a/src/fashn_vton/pipeline.py)은 PyTorch 모델과 DWPose·Human Parser, 기본 30단계 Euler/CFG 샘플링을 사용한다. 지원 카테고리는 `tops`, `bottoms`, `one-pieces`이며 **신발 생성은 지원 범위 밖**이다. 상·하의·신발 동시 생성도 검증되지 않았다.

본체와 공식 DWPose 모델 페이지는 Apache-2.0으로 표시된다. 하지만 [Human Parser 라이선스](https://github.com/fashn-AI/fashn-human-parser/blob/f2771f2fb8655349e87e2869bbde7ace0bd06f2c/LICENSE)는 SegFormer 라이선스를 상속하며, [고정 NVIDIA 라이선스 3.3절](https://github.com/NVlabs/SegFormer/blob/65fa8cfa9b52b6ee7e8897a98705abf8570f9e32/LICENSE)은 비상업 연구·평가용으로 제한한다. 본체 라이선스로 전체 파이프라인의 상용 허가를 대신할 수 없다. 의존성·재배포 권리까지 최종 검토되지 않아 **현재 조합의 정식 제품용 다운로드·배포를 권장하지 않는다.** 라이선스를 수락하지 않았다.

## 자산 후보와 승인 범위 — 다운로드하지 않음

아래 URL은 공식 저장소에서 확인한 commit을 넣은 **향후 승인 검토용 다운로드 후보**다. 저장 후보는 저장소 밖 `C:/Users/USER/AppData/Local/FashionAI/model-assets/p39-review`이며, 이번에 생성하지 않았다. 승인에는 대상 파일·최대 용량·저장 위치·연구용 목적·별도 Parser 이용조건 검토를 포함해야 한다. 상용 허가나 기기 설치 승인으로 확대할 수 없다.

| 파일 | 고정 revision의 다운로드 후보 URL | 크기(bytes) |
|---|---|---:|
| FASHN 본체 | https://huggingface.co/fashn-ai/fashn-vton-1.5/resolve/7720683168567eb5a2a4c67f15116c6e29c83ded/model.safetensors | 1,943,668,048 |
| DWPose pose | https://huggingface.co/fashn-ai/DWPose/resolve/548b5df25b84d9f4aac0611dfa1c2a7a12f15571/dw-ll_ucoco_384.onnx | 134,399,116 |
| DWPose detector | https://huggingface.co/fashn-ai/DWPose/resolve/548b5df25b84d9f4aac0611dfa1c2a7a12f15571/yolox_l.onnx | 216,746,733 |
| Human Parser 본체 | https://huggingface.co/fashn-ai/fashn-human-parser/resolve/1f80c34dbab321c5730dda5c3fea279fd3e97498/model.safetensors | 256,146,352 |
| Human Parser config | https://huggingface.co/fashn-ai/fashn-human-parser/resolve/1f80c34dbab321c5730dda5c3fea279fd3e97498/config.json | 공식 목록 약 1.65 kB; 정확한 byte 수 미확인 |

네 바이너리 합계는 **2,550,960,249 bytes**다. config·라이선스 파일·런타임·변환 산출물·작업 공간 및 메모리는 별도다. 이 용량만으로 휴대폰 실행 가능 여부를 판단할 수 없다.

| 파일 | 공식 LFS 포인터의 SHA256 |
|---|---|
| FASHN 본체 | `d6cd38286885bc29fa487ea9383f80ffeb95862e7747c630d42c5d3c05bdd35a` |
| DWPose pose | `724f4ff2439ed61afb86fb8a1951ec39c6220682803b4a8bd4f598cd913b1843` |
| DWPose detector | `7860ae79de6c89a3c1eb72ae9a2756c0ccfbe04b7791bb5880afabd97855a411` |
| Human Parser | `e43c8c8a9b04f28798f0a4630cf18caa2cdb27a0d454fae43a5716e6f7078244` |

**증거 한계:** revision은 공식 commit 페이지에서, 크기·SHA256은 공식 `main`의 작은 LFS 포인터에서 확인했다. 고정 revision raw 포인터 조회는 웹 도구에서 실패했다. 따라서 위 고정 URL의 바이트를 내려받아 검증했거나 revision과 해시를 최종 대조했다고 주장하지 않는다. 향후 승인 후 고정 revision metadata와 실제 파일 해시를 다시 일치시켜야 한다. config의 해시도 아직 없다.

- 공식 commit: [FASHN](https://huggingface.co/fashn-ai/fashn-vton-1.5/commit/7720683168567eb5a2a4c67f15116c6e29c83ded), [DWPose](https://huggingface.co/fashn-ai/DWPose/commit/548b5df25b84d9f4aac0611dfa1c2a7a12f15571), [Parser](https://huggingface.co/fashn-ai/fashn-human-parser/commit/1f80c34dbab321c5730dda5c3fea279fd3e97498).
- 확인한 metadata 포인터: [FASHN](https://huggingface.co/fashn-ai/fashn-vton-1.5/raw/main/model.safetensors), [pose](https://huggingface.co/fashn-ai/DWPose/raw/main/dw-ll_ucoco_384.onnx), [detector](https://huggingface.co/fashn-ai/DWPose/raw/main/yolox_l.onnx), [Parser](https://huggingface.co/fashn-ai/fashn-human-parser/raw/main/model.safetensors). 실제 가중치 파일은 받지 않았다.

## Android 실행 경계와 이번 하드웨어 관측

[LiteRT Torch](https://github.com/google-ai-edge/litert-torch)는 PyTorch→TFLite 변환을 제공하지만 이 FASHN 모델의 export·연산자·정밀도·메모리 통과 증거는 없다. [ORT Mobile](https://onnxruntime.ai/docs/tutorials/mobile/)은 실제 ONNX 모델이 필요하며, safetensors 파일이나 DWPose만으로 착장 본체를 실행할 수 없다. [모바일 검사 도구](https://onnxruntime.ai/docs/tutorials/mobile/helpers/model-usability-checker.html)도 실제 그래프를 분석하고 기기 성능 확인을 권고한다. [QNN](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html)의 shape·연산자·backend 제약은 선택 버전과 실제 export를 대상으로 검증해야 한다. runtime 설치만으로 지원 판정을 하지 않는다.

이번 총괄의 읽기 전용 기기 관측: **SM-S936N / SM8750 / arm64-v8a / API 37 / Adreno 830 / OpenGL ES 3.2**. `MemTotal=11379860 kB`, `MemAvailable=2077720 kB`는 한 시점의 스냅샷이다. NPU 서비스의 존재는 확인됐지만 모델·delegate 실행은 확인되지 않았다. 일련번호나 사용자 데이터는 기록하지 않는다. CPU/GPU/NPU 추론, peak 메모리, 지연시간, 전력·발열 및 품질 수치는 **미측정**이다.

## 남은 실행 조건과 판정

1. 사용 목적에 맞는 전체 라이선스와 자산 제공/다운로드 승인을 확정한다. 위 Parser의 연구용 허가를 상용 허가로 바꾸어 해석하지 않는다.
2. 승인된 고정 자산을 기존 manifest로 검증한 뒤, 본체 한 단계 export와 원본 출력 수치 비교부터 수행한다. 현재 자산이 없어 이 단계도 실행하지 않았다.
3. 반복 sampler와 DWPose/Parser 전처리 동등성, 지원 카테고리, ONNX/LiteRT 연산자 및 정밀도를 검증한다. 양자화에는 허가된 대표 입력과 품질 비교가 필요하다.
4. 실제 Android CPU 경로부터 메모리·취소·시간 제한을 측정하고 GPU/NPU는 별도 검증한다. 이후 새 이미지→비교→저장→재조회 E2E와 실사진 품질을 검사한다. 신발은 별도 지원 모델 근거가 필요하다.

현재 가능한 코드 작업은 P38의 실제 합성 결함 수정이다. 지원 변환 모델이 없는 상태에서 사용되지 않는 readiness framework나 추론 성공을 가장한 엔진을 추가하지 않는다. **P39 모델 로드·실제 생성·Android 결과 E2E는 BLOCKED, 정식 생성형 제품 출시는 NO-GO**다. 새 계정·유료 자원·다운로드·라이선스 수락·사진 전송·기기 설치를 수행하지 않았다.
