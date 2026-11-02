# P6 자체 AI 생성 통합 — 2026-11-02

기준: P5 `34d13a196f15a265c5548d403fd49e905721240b`. 작업 브랜치 `codex/fashionai-p6-vton-integration-20261102`.
팀 저장소, 운영 Railway/DB, 기존 APK·서명·사용자 데이터는 변경하지 않는다.

## P6-1 검증

- 공식 코드 검토 기준: `fashn-AI/fashn-vton-1.5` 커밋 `7c0f10af3f91ad4048fe9729c470a13ef905d25a`.
  P5의 `TryOnPipeline(weights_dir, device)`와 PIL 입력·category·flat-lay/model·num_samples=1·timesteps·seed·segmentation_free 호출은 공식 API와 일치한다.
- 필수 파일은 `model.safetensors`, `dwpose/yolox_l.onnx`, `dwpose/dw-ll_ucoco_384.onnx`다. 별도 Human Parser 캐시도 필요하다.
- 빈 파일·Git LFS 포인터·HTML 다운로드 오류 검사를 추가했다. 서버용 SHA-256 manifest는 공식 Hugging Face 저장소의 파일 메타데이터/고정 revision을 이용해 준비한다. 경로나 토큰을 요청으로 받지 않는다.
- 준비 도구 `backend/prepare_local_vton_weights.py`는 명시적 실행에서만 공식 가중치를 받고 체크섬을 확인한다. 준비되지 않은 모델을 요청 중 자동 다운로드하지 않도록 작업 프로세스는 offline 모드를 쓴다.
- 현재 PC: Intel Iris Xe, CUDA NVIDIA GPU 확인되지 않음. 실제 모델/가중치는 준비하지 않았고 실제 생성 PNG·속도·VRAM 측정은 **미완료**다. CPU 실행은 명시적 허용이 필요하며 무료 GPU·유료 GPU·외부 API를 임의로 호출하지 않았다.
- P5 및 새 manifest 합성 테스트 **18개 통과**. 합성 파이프라인 결과를 실제 생성으로 보고하지 않는다.

## 라이선스와 환경

- [VTON 코드](https://github.com/fashn-AI/fashn-vton-1.5), [모델](https://huggingface.co/fashn-ai/fashn-vton-1.5): Apache-2.0. DWPose·YOLOX: Apache-2.0.
- [Human Parser 라이선스](https://github.com/fashn-AI/fashn-human-parser/blob/main/LICENSE)는 [NVIDIA SegFormer 라이선스](https://github.com/NVlabs/SegFormer/blob/master/LICENSE)를 따른다. 연구/평가 목적의 비상업적 사용 제한이 있어 전체 구성요소를 무제한 상용 Apache 라이선스로 설명하면 안 된다. 모델이나 라이선스 제한을 앱에 숨기지 않는다.
- 공식 모델 카드의 출력은 576×864이며 약 8GB VRAM 안내가 있다. 이는 이번 PC에서 측정한 성능이 아니다. PyTorch CUDA 빌드와 ONNX Runtime의 CUDA/cuDNN 호환 버전을 GPU 기계에서 확인해야 한다. 본체 bf16은 지원 GPU에서만 사용하고 공식 파이프라인의 dtype 결정을 유지한다.
- [PyTorch 설치](https://pytorch.org/get-started/locally/), [ONNX Runtime CUDA 호환성](https://onnxruntime.ai/docs/execution-providers/CUDA-ExecutionProvider.html). GPU 모델 의존성을 기존 운영 `backend/requirements.txt`에 추가하지 않는다.

## 이어지는 작업

P6-2 독립 서버, P6-3 Android 모드 분리 및 P6-4 입력/품질 검증 도구를 구현하고 전체 CI 결과로 이 문서를 확정한다. 실제 GPU 품질·성능은 GPU 환경 확보 후 수행한다.

## P6-2 독립 서버

`local_vton_server:app` 및 [실행 문서](../backend/LOCAL_VTON_SERVER.md)를 추가했다. 운영 앱에 라우터를 섞지 않는다. 인증·동의·외부 HTTPS·명시적 개발 루프백·단일 의류·소유자 격리·재시작 중복 방지·작업 수 제한·취소/시간 초과·최대5분 결과 보관을 구현했다. 실제 자식 프로세스 kill/wait 및 임시 파일 제거 테스트도 포함한다. 가중치·모델 없이 모의 엔진으로 검증한 서버 테스트는25개다. 새 키나 모델 사진을 운영에 올리지 않는다. 부모 서버 종료 감시와 OOM 오류 구분을 추가했으나 실제 GPU 종료·VRAM 한도는 미검증이다.
