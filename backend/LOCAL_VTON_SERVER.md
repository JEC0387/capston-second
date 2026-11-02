# P6 독립 자체 AI 개발 서버

`local_vton_server:app`은 기존 `main:app` 및 유료 `tryon_proxy.py`와 분리돼 있다. 운영 Railway에 배포하지 않는다. 기본 상태는 비활성이다. 가중치·CUDA GPU가 없는 PC에서 모델 생성 성공을 만들지 않는다.

## GPU 운영자가 준비할 순서

1. CUDA NVIDIA GPU와 전용 Python 3.11/3.12 환경을 준비한다. 공식 모델 카드는 약 8GB VRAM을 안내하지만 실제 GPU별 사용량은 별도 측정한다.
2. [공식 코드](https://github.com/fashn-AI/fashn-vton-1.5)의 검토 커밋 `7c0f10af3f91ad4048fe9729c470a13ef905d25a`를 체크아웃하고 그 환경에 설치한다. PyTorch와 ONNX Runtime CUDA/cuDNN 호환성을 먼저 확인한다. 이 의존성을 운영 requirements에 추가하지 않았다.
3. [Human Parser](https://github.com/fashn-AI/fashn-human-parser/blob/main/LICENSE) 및 [SegFormer 연구·평가용 비상업 제한](https://github.com/NVlabs/SegFormer/blob/master/LICENSE)을 검토한다. 본체 Apache-2.0만으로 전체 구성요소의 상용 사용 권한을 주장하지 않는다.
4. 저장소 밖의 모델 디렉터리에 명시적으로 준비한다. 이 명령만 공식 가중치를 다운로드하며 사진을 업로드하거나 추론하지 않는다.

```bash
python prepare_local_vton_weights.py --weights-dir /private/fashionai/weights --accept-parser-research-license
python create_local_vton_token.py /private/fashionai/access
```

준비 도구는 공식 `fashn-ai/fashn-vton-1.5`와 `fashn-ai/DWPose`의 고정 revision/LFS SHA-256과 다운로드를 대조하고 `vton.manifest.json`을 만든다. 공식 Human Parser 패키지가 최초 캐시를 준비한다. 원래 토큰은 지정한 비공개 파일에만 저장되고 출력되지 않는다. Android에 입력할 토큰은 해당 파일에서 안전하게 전달하며 외부 FASHN API 키/백업 복구 코드를 재사용하지 않는다.

5. `local_vton.env.example`을 저장소 밖 비공개 파일로 복사해 다음을 설정한다. 실제 토큰을 파일/로그/Git에 더 복제하지 않는다. `TOKEN_HASHES`는 위 도구가 출력한 해시다.

```text
LOCAL_VTON_ENABLED=true
LOCAL_VTON_TOKEN_HASHES=<SHA-256 hashes, comma separated>
LOCAL_VTON_WEIGHTS=/private/fashionai/weights
LOCAL_VTON_JOURNAL=/private/fashionai/state/jobs.json
LOCAL_VTON_RESEARCH_LICENSE_ACCEPTED=true
```

6. 같은 머신 개발은 루프백으로만 구동한다. Android 에뮬레이터는 `10.0.2.2`, 실제 폰은 사용자가 설정한 USB reverse로 `127.0.0.1`을 사용할 수 있다. 앱에서 개발용 루프백 옵션을 켜고 서버도 `LOCAL_VTON_LOOPBACK_HTTP=true`를 명시해야 한다. 외부/LAN HTTP는 차단한다. 이번 작업은 USB reverse나 APK 설치를 실행하지 않는다.

```bash
python -m uvicorn local_vton_server:app --env-file /private/fashionai/local.env --host 127.0.0.1 --port 8010 --workers 1 --no-proxy-headers --no-access-log
```

외부 접속은 운영자가 유효한 TLS 인증서로 HTTPS를 구성해야 한다. Uvicorn 직접 TLS 또는 신뢰하는 프록시만 사용하며 임의의 전달 헤더를 신뢰하지 않는다. 이 작업에서 인증서/클라우드/유료 GPU를 생성하지 않았다.

## API 계약

- `GET /health`: 공개 준비 여부만 표시.
- `GET /api/local-vton/config`: Bearer 인증. 모델 엔진·준비 이유·단일 의류·결과 TTL·연구 제한을 표시. 파일 경로와 키는 반환하지 않는다. `model_loaded=false`는 요청마다 별도 프로세스에서 모델을 로드한다는 뜻이며 실제 첫 생성 성공 증명이 아니다.
- `POST /api/local-vton/jobs`: 인증과 HTTPS/명시적 루프백 검사를 **본문 읽기 전**에 수행. JSON에는 UUID `request_id`, inline base64 `person`, `garments=[{category,image}]` 한 벌, 명시적 `consent=true`가 필요하다. `garment_photo_type`은 flat-lay 또는 model이다. sampling 20~50, seed 0~2^31-1. URL·파일 경로·다중 의류·임의 옵션은 거절한다.
- `GET /api/local-vton/jobs/{id}`: 요청자 본인만 상태·실제 생성 후 측정값을 조회한다. 모델 로딩/추론 시간, 출력 크기, PyTorch peak VRAM은 실제 worker가 성공했을 때만 기록한다.
- `GET /api/local-vton/jobs/{id}/result`: 본인만 PNG, `Cache-Control: no-store`. 결과가 없거나 만료되면 410.
- `DELETE /api/local-vton/jobs/{id}`: 본인 작업 취소 및 결과 제거. 실행 프로세스 종료와 wait가 끝난 후 GPU 슬롯을 반환한다.

미설정/가중치/의존성/GPU/라이선스/저널 문제는 준비 불가 및 503이다. 인증 실패는 401, 외부 HTTP는 403, 잘못된 입력은 400/413, 동시 실행·요청 한도는 429, 같은 ID의 다른 입력은 409다. 다른 사용자의 ID는 404다. 원본 요청·토큰·개인 사진을 로그/오류 응답에 넣지 않는다.

## 보관·중복·자원 범위

- 입력은 디코딩 시 EXIF 방향을 바로잡고 메타데이터를 제거한다. 비율을 유지해 긴 변 1296px로 제한하며 임의 신체 크롭·얼굴 덮어쓰기·색상 보정을 하지 않는다. 투명 의류는 흰 배경으로 처리한다. 인물 256px/의류128px 최소 해상도·최대20MP·애니메이션·과도한 인물 구도를 검사한다. 팔/손·얼굴·무늬 보존은 실제 GPU 평가 전 보장하지 않는다.
- 서버 전체 추론 1개, 업로드 파싱 2개, 본문15MiB·이미지각5MiB, 소유자 시간당30개, 메타데이터1000건으로 제한한다. 모델 결과는 최대8MiB/20MP다.
- 기본 작업 제한240초. 별도 subprocess를 kill/wait하므로 Python 추론 thread가 계속 GPU를 잡은 채 슬롯을 반환하지 않는다. PyTorch allocator는 VRAM의80%로 제한한다. ONNX Runtime의 별도 CUDA 예약을 포함한 실제 총 VRAM 상한은 GPU 실측 전 미검증이며 엄격한 GPU 격리는 전용 GPU/MIG 등 별도 운영 구성이 필요하다.
- 요청마다 모델을 다시 로드한다. 메모리 격리/확실한 취소를 우선한 개발 구현이며 warm worker 지연시간 최적화는 아직 하지 않았다.
- 원본 PNG 임시 파일·설정·생성 파일은 사용자 입력 경로가 아닌 비공개 임시 디렉터리에 두고 작업 종료/취소/오류 때 제거한다. 응답 결과만 RAM에 최대300초 두며 만료·삭제한다. bytes의 즉각적 물리 메모리 덮어쓰기/포렌식 삭제를 보장하지 않는다.
- 임시 작업은 저널 옆의 서비스 전용 `local-vton-work/job-*`에만 둔다. 강제 종료로 남은 작업 폴더는 다음 서버 시작 때 저널 잠금을 확보한 뒤 소유 표식·실제 경로를 확인해 정리한다. 그 외 디렉터리는 지우지 않는다. 서버가 재시작되지 않으면 운영자가 해당 비공개 폴더의 잔여 사진을 안전하게 제거해야 한다. Windows는 POSIX mode만으로 계정별 ACL을 보장하지 않으므로 전용 사용자 폴더/암호화 디스크를 사용한다.
- 개인 사진 없는 작업 ID·소유자 해시·입력 해시·상태·성능만 비공개 로컬 저널에 기록한다. Unix/Windows 프로세스 잠금으로 worker 중복 기동을 차단한다. 재시작 시 진행 중 요청은 interrupted로 표시하고 같은 ID를 자동 재생성하지 않는다. 메타데이터는24시간/1000건 한도이며 디렉터리를 웹으로 제공하지 않는다.
- 앱은 전송 응답이 불확실하면 같은 작업 ID를 GET으로 조회한다. 화면 이동/재진입/프로세스 복원 시 POST를 자동 반복하지 않으며 토큰은 저장하지 않는다. 실제 재생성은 다시 동의해야 한다.

## 실측과 11월3일 테스트

실제 모델이 준비된 GPU 서버에서 승인된 테스트 사진으로 상의·하의·원피스를 각각 한 벌씩 실행한다. 응답 metrics의 로딩/생성 시간·VRAM·해상도와 성공/실패 횟수를 기록하고 얼굴·손·목선·원래 옷 경계·색/무늬·배경·구도를 원본과 비교한다. 동일 seed/steps/사진을 유지하고 최소3회 반복해 OOM·시간 초과·성공률을 기록한다. 현재 PC는 CUDA가 없어 수치와 실생성 PNG는 미확보다. 합성 테스트를 실측 품질로 보고하지 않는다.

## 무료 GPU 검토

[Colab FAQ](https://research.google.com/colaboratory/faq.html): 무료 GPU와 사용 시간은 보장되지 않으며 가용량/한도는 변한다. 무료 런타임을 상시 API 호스팅으로 우회 사용하지 않는다. 승인된 사진으로 노트북 내부 CLI 연구 검증에 사용할 수 있는지 먼저 조건을 확인한다.
[ZeroGPU](https://huggingface.co/docs/hub/spaces-zerogpu): 일일 할당량과 대기열이 있으며 Gradio 기반이므로 이 FastAPI 서버의 즉시 대체 환경이 아니다. 무료 사용과 직접 호스팅 권한을 구분해야 한다. 이번 작업에서 외부 데모에 사진을 업로드하거나 GPU 자원을 생성하지 않았다. 우선 학교/팀의 사용 권한이 있는 CUDA 머신에서 검증한다.
