# P49 — 실제 엔진 실행 환경 판정

| 필수 결과 | 판정 | 증거/범위 |
|---|---|---|
| 사용 가능한 실제 AI 엔진 확보 | NO | 적합한 승인 GPU·접속 환경 없음 |
| 실제 3종 생성형 PNG | FAIL(미생성) | 실행 전 환경 차단; 모델 추론 실패와 구분 |
| P48 서버·Android 통합 | BLOCKED | P48 코드 보존, 실제 엔진 연결 미검증 |
| S25+ 마법봉 실제 E2E | NOT TESTED | 기기 접근·새 설치·서버 운영 승인 없음 |
| 정식 출시 | NO-GO | 3종 실제 생성·품질·앱 E2E 미충족 |

기준/현재 실행 코드 `1e2abd1525679dedd17a8bc9d95580bcb2778737`. 시작 시 P48 브랜치 HEAD 일치, 작업 트리 clean을 확인하고 독립 로컬 브랜치 `codex/fashionai-p49-engine-critical-path-20261106`를 만들었다. 기존 P38~P48 코드·모델·결과·납품본, 설치 Preview18605/QA18607은 건드리지 않았다. Push/Fetch/Merge·CI·빌드·ADB·설치·외부 사진 전송·계정 생성·결제는 하지 않았다.

## 15분 내 판정

판정 시작 2026-11-06 15:20:06 KST. 15:21:53 KST까지 실제 로컬 하드웨어와 기존 승인 자원 기록을 대조했고, **15:26:03 KST에 실제 provider 차단 상태까지 확인했다(5분 57초)**. CPU i5-1155G7, Intel Iris Xe, 총 RAM **7.73GiB**, 당시 여유 RAM **0.87GiB**, C: 여유 **6.18GiB**다. WMI AdapterRAM 1,073,741,824바이트는 내장 GPU의 실제 사용 가능 전용 VRAM 측정치가 아니다. `nvidia-smi`는 없으며 같은 장비의 기존 P40 CUDA 미가용 결과와 P44 전체 모델 미실행 근거를 재사용했다. Torch를 다시 로드하거나 DirectML 부분 연산을 반복하지 않았다.

P40/P44/P47/P48 기록에 **사용 허가와 접속 수단이 확인된 팀·학교 GPU는 없다**. 장비가 존재하지 않는다는 일반 주장이나 전체 네트워크 탐색 결과가 아니다. 승인되지 않은 장비·SSH 계정·비밀 설정을 찾아 접속하지 않았다. 따라서 이 PC에서 CPU 전체 추론 또는 대형 모델 다운로드를 시작하지 않는 판정을 내렸다.

## FLUX.2 klein 4B 확인 결과

- 정확한 후보: `black-forest-labs/FLUX.2-klein-4B`, distilled 4-step. 확인된 공식 revision `e7b7dc27f91deacad38e78976d1f2b499d76a294`. 공식 Hub metadata는 public/ungated 상태다. **실제 가중치를 다운로드하거나 로드한 것은 아니다.** [공식 metadata](https://huggingface.co/api/models/black-forest-labs/FLUX.2-klein-4B)
- 4B 모델과 공식 추론 코드는 Apache-2.0이다. 9B/dev 또는 연구 Space와 구분한다. 이 사실이 무료 GPU나 공개 서비스의 운영권을 제공하지 않는다. 전체 배포 번들·런타임·입력 권한 검토도 별도다. [모델 카드](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B), [공식 코드](https://github.com/black-forest-labs/flux2), [공식 상업 이용 설명](https://help.bfl.ai/articles/7108141705-can-i-run-or-fine-tune-flux-2-klein-locally)
- 카드/공식 도움말은 약 **13GB VRAM**, 공식 GitHub README는 약 **8GB VRAM**로 다르다. 어느 값도 4참조 입력의 실제 피크 메모리 보장으로 취급하지 않는다. CPU offload 예제도 CUDA GPU를 사용한다. 현재 Iris Xe/7.73GiB RAM 조합에 대한 검증된 실행 경로는 확인하지 못했다.
- 공식 비교표는 klein 최대 4개 참조를 설명하고 Diffusers `Flux2KleinPipeline`은 이미지 목록을 처리한다. `[person, tops, bottoms, shoes]`는 검토 가능한 입력 순서지만 실제 의류 무늬·양발·얼굴 보존 증거가 아니다. [공식 비교표](https://help.bfl.ai/articles/6122710168-which-flux-2-model-should-i-choose), [pipeline 코드](https://raw.githubusercontent.com/huggingface/diffusers/main/src/diffusers/pipelines/flux2/pipeline_flux2_klein.py)
- 공식 파일 목록의 전체 repository 약23.7GB에는 transformer 중복 표현이 있다. root transformer 약7.75GB 또는 diffusers transformer 중 하나와 text encoder 약8.05GB, VAE·tokenizer 등이 필요하다. Transformer 파일 하나를 완전한 엔진으로 보지 않는다. 현재 여유 디스크에 전체 표준 번들을 무리하게 받지 않았다. [파일 목록](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B/tree/main), [text encoder](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B/tree/main/text_encoder)
- 공식 FP8/NVFP4 후보도 있으나 현재 장비에서 호환 런타임과 전체 추론 메모리가 실증되지 않았다. 양자화 파일 존재를 실행 가능으로 바꾸지 않았다. [FP8](https://huggingface.co/black-forest-labs/FLUX.2-klein-4b-fp8), [NVFP4](https://huggingface.co/black-forest-labs/FLUX.2-klein-4b-nvfp4)
- 공식 전체 4참조 Android/S25+ 실행 근거는 확인하지 못했다. 스마트폰 처리 시간·NPU 지원·자연스러움을 추정하지 않는다.

## 기존 엔진/통합 보존

FASHN VTON v1.5 가중치와 P41/P42/P44 결과를 그대로 유지했다. 공식 상의·하의 범위와 별도로 신발 생성 경로가 필요하며, 기존 1단계 PNG·2/20단계·부분 GPU 연산을 3종 완성품으로 계산하지 않는다. P46 연구 PNG도 이번 앱이 새로 생성한 결과로 재사용하지 않았다.

P48 provider/worker는 Qwen-Image-Edit-2511 전용이다. FLUX는 pipeline·encoder·scheduler 계약이 달라 가중치 경로만 교체할 수 없다. 실제 FLUX 4참조 추론이 성공한 뒤 기존 `VirtualTryOnProvider`의 별도 구현으로 연결해야 한다. 이번에는 실제 엔진 성공 근거가 없으므로 기존 API·Android·Qwen provider를 재작성하거나 모델 식별/라이선스 검사를 완화하지 않았다. 새로운 코드 결함은 재현되지 않았다.

## 실제 수행 증거와 검증 범위

증거 폴더: `C:\Users\USER\Documents\ChatGPT\capston\experiments\P49-20261106`.

- `environment-observation.json`: 새 읽기 전용 하드웨어 관측값. 디스크/RAM 값은 해당 관측 시점에 한정한다.
- `check_existing_provider.py`, `provider-check01.json`: 기존 실험 Python에서 설치 패키지 metadata와 **현재 실제 환경 설정의 provider 상태**만 읽는다. 자격 증명 값·경로 값은 기록하지 않고 설정 여부만 남긴다. enabled provider는 초기화하지 않으며 모델 로딩·네트워크·생성 요청·기기 작업을 수행하지 않는다. 이 결과를 모델 실행 시험으로 계산하지 않는다.
- `final-receipt.json`: 최종 로컬 SHA·Git 상태·증거 SHA-256·환경 판정 시간 기록. 사진/토큰/대형 모델/비공개 실행 증거는 Git에 넣지 않는다.

새 확인 결과는 `DisabledProvider`, `ready=false`, `provider_disabled`다. GPU 활성/배포 승인/모델 번들/manifest/작업 경로/VRAM 설정 모두 false이며 raw 설정값을 출력하지 않았다. P40 실험 venv의 metadata는 torch `2.6.0+cpu`, safetensors `0.5.3`, Pillow `13.0.0`, diffusers/transformers/accelerate 미설치다. 이는 해당 venv만의 결과이고 PC 전체 설치 상태를 일반화하지 않는다. 모델 런타임은 import하지 않았다. 증거 JSON SHA-256: `df7c6232c0e90ebf6aeb9324dbaa781a4f44c4e1fed60bf528d43e6c06774ab9`.

첫 sandbox 호출은 결과 파일/관측된 Python 프로세스 없이 대기해 소유한 wrapper만 중단했다(exit1). 허용된 로컬 실행 권한으로 동일 읽기 전용 검사 1회를 완료했다(exit0). 이 호출 환경 문제를 모델 로딩 실패로 분류하지 않는다.

실행 코드 변경이 없으므로 P48 Android32/Backend60 집중 검사 근거를 재사용하고 다시 실행하지 않는다. 새로운 PNG/PNG SHA는 없으며 기존 PNG의 해시를 새 성공 증거로 제시하지 않는다. 새 APK·새 서명·기기 데이터 해시도 생성하지 않았다. 사용자 데이터는 작업으로 변경하지 않았으나 이번에 기기 저장소 보존을 새로 측정한 것은 아니다.

## 다음 진행의 정확한 조건

1. **클라이언트/팀 인프라 담당:** 실제 사용이 승인된 적합 GPU의 장비/접속 수단, RAM·디스크·시간 제한 제공. 새 계정·결제·무허가 접속으로 대체하지 않는다. 8/13GB 문구만으로 메모리 적합성을 보장하지 말고 4참조 실제 피크를 검증해야 한다.
2. **모델 담당:** 공식 고정 revision의 완전한 FLUX bundle 및 종속 구성요소를 검토·해시 고정하고, 승인된 공개 입력 4장으로 전체 추론을 실행한다. 신규 PNG/hash·GPU명·최대 VRAM·시간·얼굴/의류/양발/손/배경 품질 증거를 확보한다. 실패하면 그 단계만 정확하게 기록한다.
3. **Backend 담당:** 실제 추론 성공 후에만 기존 P48 인터페이스에 FLUX provider를 연결한다. 인증·동의·동일 작업 ID·취소/종료 확인·복구·결과 무결성은 유지한다. 새로운 API 구조를 만들지 않는다.
4. **클라이언트/개인정보 운영 담당:** 서버 실행 위치·사진 처리·보관/삭제·TLS·계정 권한을 명시 승인한 뒤 별도 서버 모드를 활성화한다. 기존 Preview/QA 격리 정책을 우회하지 않는다.
5. **Release/QA 담당:** 새 APK 검증과 버전별 설치 승인 후 실제 S25+ 요청→결과 비교→저장→재열기를 검증한다. 서버 추론을 온디바이스 추론이라고 표시하지 않는다.

현재 납품 범위는 보존된 P48 실행 코드와 이번 자원 판정/실행 준비 근거다. 계약상 3종 자연스러운 생성형 착장, 실제 마법봉 E2E, 정식 출시는 미완료다.
