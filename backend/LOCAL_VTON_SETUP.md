# P5: 독립 실행형 자체 AI 착장 이미지 생성 (FASHN VTON v1.5)

**P30 현재 기준:** 아래는 P5 당시 기록이며 자체 서버·Android 연결은 이미 구현되어 있으므로, 새 모델 다운로드·라이선스 수락 없이 승인된 준비 환경에서만 최신 [LOCAL_VTON_SERVER.md](LOCAL_VTON_SERVER.md)의 manifest 필수 검증·오프라인 실행·실제 GPU 시험 절차를 따르세요.

**상태: 실행 모듈 + 합성 테스트 준비 완료. 실제 GPU 모델 추론 및 Galaxy S25+ 연결은 미검증.**

이 문서는 유료 `api.fashn.ai` **호출이 아니라** 별도 컴퓨터/GPU에서 공개 모델을 직접 실행하는
경로를 설명한다. 앱의 기존 외부 API 요청, 인증/동의, 사용자 사진 저장 흐름은 변경하지 않았다.
이 모듈은 현재 **CLI로만 실행**한다. Android 앱이 이 CLI를 원격 호출하는 API는 아직 없다.
가짜 시연 결과나 2D 이미지 합성을 AI 이미지 생성으로 표시하지 않는다.

## 공식 모델

- GitHub: https://github.com/fashn-AI/fashn-vton-1.5
- 모델 가중치: https://huggingface.co/fashn-ai/fashn-vton-1.5
- 공개 모델 라이선스: Apache-2.0. **함께 사용되는 구성요소의 별도 라이선스도 확인 필요**.
- 공식 추론 인터페이스: `TryOnPipeline(weights_dir=..., device=...)` 실행 후
  `pipeline(person_image=PIL_image, garment_image=PIL_image, category="tops" ...).images[0]`.
- 지원: tops(상의), bottoms(하의), one-pieces(원피스). 신발·가방·액세서리는 지원하지 않음.
- 모델 가중치 약 2GB + DWPose + 최초 실행에 추가 다운로드되는 인물 파서 가중치.
- CUDA NVIDIA GPU 권장. CPU는 매우 느릴 수 있고, 스마트폰 내 실행은 지원하지 않음.
- 초회 다운로드와 연산을 위한 인터넷/컴퓨팅 환경은 **무료를 보장하지 않는다**. API 호출료와 GPU 임대료는 구분할 것.

## 격리된 GPU 실험 준비

개인정보가 없는 합성·허가된 샘플 이미지를 사용한다. 원본 사용자 사진, 토큰, 가중치,
결과 파일은 GitHub에 올리지 않는다.

```bash
# Python 3.10~3.12 환경에서 실행하는 예시
git clone https://github.com/fashn-AI/fashn-vton-1.5.git
cd fashn-vton-1.5
python -m venv .venv
source .venv/bin/activate
pip install -e .
python scripts/download_weights.py --weights-dir ./weights
```

공식 패키지의 `onnxruntime-gpu`는 CUDA 환경에 맞게 준비해야 한다.
CPU만 있을 때는 공식 README 안내대로 `onnxruntime`로 교체할 수 있지만, 속도는 실용적이지
않을 수 있다. 의존성 설치/가중치 다운로드를 본 앱의 `backend/requirements.txt`에
넣지 않았다. 따라서 Railway 및 기존 서버 빌드에 영향을 주지 않는다.

```bash
# 자신이 준비한 별도 GPU 환경에서 프로젝트의 backend/ 폴더로 이동
cd /path/to/capstone-integration-backup/backend

# 생성 없이 사전점검 (실제 결과 아님)
python local_vton.py --person /private/person.jpg \
  --garment /private/shirt.png --category tops \
  --weights-dir /path/to/fashn-vton-1.5/weights \
  --output /private/generated.png --dry-run

# 실제 신경망 추론 (CUDA GPU 필요)
python local_vton.py --person /private/person.jpg \
  --garment /private/shirt.png --category tops \
  --garment-photo-type flat-lay \
  --weights-dir /path/to/fashn-vton-1.5/weights \
  --output /private/generated.png
```

이미 결과 파일이 있으면 덮어쓰지 않는다. 의도적으로 바꾸는 경우에만
`--overwrite`를 사용한다. `--device cpu --allow-cpu`는 오랜 시간이 걸릴 수 있는 CPU 추론을
명시적으로 허용하는 옵션이다. 도중에 오류가 나면 생성 성공으로 표시하거나 가짜 사진을
저장하지 않는다. 임의 URL 입력은 지원하지 않는다.

`--dry-run` JSON 출력에 `"generated": false`가 나온다.
모델 실행이 정상 완료되어 **PNG를 실제 저장했을 때에만** `"generated": true`다.
테스트의 가짜 파이프라인은 단위 테스트 안에서만 생성되며 앱/CLI 기본 경로에서는 실행되지 않는다.

## 개인정보 및 다음 연결 단계

- **현재 API 엔드포인트 없음**: 어떤 운영 서버에서도 이 모듈이 자동 실행되지 않는다.
- 개인 사진을 서버로 업로드하는 기능을 붙이기 전에는 사용자 동의, HTTPS/토큰 인증,
  입력·결과 TTL, 재시도·취소 정책, GPU 동시 실행 제한, 사진/로그 비보존 여부를 설계해야 한다.
- 현재 `tryon_proxy.py`의 FASHN 유료 서버 흐름은 변경되지 않았다. P5 CLI로 갈아탔다고
  표시하거나 유료 API를 삭제하지 않는다.
- 외부 GPU 임대를 결정할 때 CPU/GPU 성능, GPU 메모리, 시간당 비용, 출력 품질을
  **실제 샘플 이미지에서 기록**해야 한다.
- 11월 3일 실기기 테스트는 앞서 성공한 Preview `1.1.27.1`(versionCode 12701)로
  진행할 수 있다. 본 변경은 Android UI나 배포 APK에 AI 생성 버튼을 추가한 것이 아니다.

## 자동 테스트 (실제 모델·인터넷·결제 불필요)

```bash
cd backend
python -m pytest -q tests/test_local_vton.py
```

합성 이미지 기반 입력 검증, EXIF/알파/리사이즈, 지원 카테고리, GPU/가중치 없음 시 차단,
기존 출력 보호, 모의 파이프라인 출력 규격, 비생성 사전점검을 확인한다.
성공해도 **실제 모델 가중치를 로드하고 생성했다는 뜻이 아니다**.

## P6 후속 구현

이 문서는 P5 CLI 기준이다. P6에서 독립 FastAPI 서버와 Android 자체 AI 화면을 추가했다. 최신 실행·인증·삭제 정책은 [LOCAL_VTON_SERVER.md](LOCAL_VTON_SERVER.md), 검증 범위는 [P6 인수인계](../docs/P6_VTON_HANDOFF_20261102.md)를 따른다. 아래 실제 GPU 검증은 아직 남아 있다.

1. GPU 환경에서 실제 인물/옷 이미지로 첫 생성·사진 품질 측정
2. 생성 성공·실패, 평균 소요 시간, 이미지 결과(허가된 테스트 샘플만) 확보
3. 기존 인증·동의·서버 프록시의 안전 요구사항을 유지하며 별도 로컬 모델 서버 구성
4. 유료 API 모드와 자체 모델 모드를 앱에 **정확하게 구분**하여 연결
5. 원본/결과 비교 및 저장, 사진 삭제/보관 정책 테스트

**모델이 실행되는 GPU 서버가 준비되기 전에는 실제 AI 생성이 앱에서 작동한다고 주장하지 않는다.**
