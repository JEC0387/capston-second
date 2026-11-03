# P13 자체 AI 착장 생성 인수인계 — 2026-11-03

저장소 `jimnnieo-svg/capstone-integration-backup`, 개발 브랜치 `codex/fashionai-p13-ai-vton-20261104`. P12 기준 원격 HEAD `7bc74e9d18ceb295520d28ff7c4ff148ff9be4d2`에서 분기했다. 기능 체크포인트는 `bb24facd4f802ecc94844d7d3fca42b1f9eddc33`, APK 기능 표식 수정 `ec840d1288c955e02708877a37ced3f55a0a4a1f`, Preview 샘플 의류 입력 수정 `26391febeccdb5f10d49cd2429cc42f5dc807200`. 팀 원본·운영 Railway·운영 DB·유료 FASHN API는 변경/호출하지 않았다.

## 실제 생성 상태와 차단 원인

**실제 FASHN VTON v1.5 모델 추론과 생성 PNG 획득은 아직 실패/미실행 상태다.** 현재 실행 환경에는 `/dev/nvidia*` 장치와 `nvidia-smi`가 없고, PyTorch·ONNX Runtime·모델 가중치·승인된 GPU 서버 접속 정보도 없다. 허가된 테스트 인물/의류 샘플로 GPU에서 종단 간 추론한 결과가 없으므로 생성 시간, VRAM, 결과 해상도/품질, 비용은 보고할 실측값이 없다. 테스트의 가짜 추론 이미지는 단위 테스트 안에서만 사용한다. 로컬 임시 서명 APK나 서버 준비 확인을 실제 생성 증거로 사용하지 않는다.

기존 구현은 `backend/local_vton.py`의 공식 `TryOnPipeline` 호출, 별도 `local_vton_server.py`의 Bearer 인증/동의/단일 작업/상태 조회/취소/결과 PNG, Android `LocalVtonRepository`·`LocalVtonScreen`·`TryOnViewModel`의 한 번만 POST/GET 복구/비교/저장 구조다. 외부 유료 경로와 분리돼 있다. 상세 설정·비공개 파일 정책은 [서버 가이드](../backend/LOCAL_VTON_SERVER.md)를 따른다. 본체 Apache-2.0과 달리 [Human Parser](https://github.com/fashn-AI/fashn-human-parser/blob/main/LICENSE)는 [SegFormer의 연구·평가용 비상업 제한](https://github.com/NVlabs/SegFormer/blob/master/LICENSE)을 따르므로 GPU 운영자가 사용 조건을 확인해야 한다. 라이선스를 대신 수락하거나 가중치를 자동 다운로드하지 않았다.

## 무료 GPU 경로 실제 확인

2026-11-03에 [Hugging Face의 공식 ZeroGPU 문서](https://github.com/huggingface/hub-docs/blob/main/docs/hub/spaces-zerogpu.md)를 확인했다. 기존 ZeroGPU Space의 무료 이용이 가능하다고 명시하고, 이메일 인증·30일 이상 계정 등 조건을 충족한 개인 무료 계정은 최대 2개 Space를 무료 호스팅할 수 있다고 명시한다. 다만 ZeroGPU는 **Gradio SDK 전용**이므로 기존 FastAPI `/api/local-vton` 서버를 그대로 올릴 수 없다. 계정·일일 할당량·대기열이 있어 현재 사용 가능한 GPU 슬롯을 보장하지 않는다. 공식 [FASHN VTON 저장소](https://github.com/fashn-AI/fashn-vton-1.5/blob/main/README.md)는 공개 Space와 약 2GB 가중치 및 최초 사용 시 Human Parser 캐시 약 244MB를 안내한다. 서비스/가중치 도메인에 대한 이 실행 환경의 직접 HTTPS 접근은 403이었고 연결된 Hugging Face 계정도 없으므로 Space 실행·무료 할당량·실제 추론을 확인하지 못했다.

가장 빠른 **실측** 경로는 조건을 충족하는 무료 계정에서 공식 예제/Space를 허가된 샘플로 한 번 실행하는 것이다. Android의 기존 인증·작업 ID·취소·비공개 사진 정책까지 충족하는 서버 경로로 사용하려면 별도 안전한 Gradio 어댑터와 실제 테스트가 필요하다. Colab 무료 GPU도 가용성이 보장되지 않고 이 환경에서 공식 FAQ 직접 접근이 403이라 현재 사용 가능하다고 주장하지 않는다. 유료 GPU·API는 승인 전 사용하지 않는다.

## 이번 코드 변경

- 가상피팅 화면에서 수동 스티커 배치나 기존 전신 사진을 먼저 만들지 않아도 `자체 AI로 입어보기`에 들어갈 수 있다. 기존 옷장 목록에서 지원 의류 한 벌을 고르고 화면 안에서 전신 사진을 선택한다. 수동 피팅, 아바타, 외부 유료 AI 화면은 유지한다.
- 자체 AI 화면은 기존 Cream/Brown/Camel 색과 명확한 `사진 → 옷 → 서버 → 동의 → 생성·비교·저장` 순서를 사용한다. 의류 선택은 화면 회전 후에도 유지한다.
- 업로드 중 취소는 POST 결과가 확정된 뒤 같은 작업 ID로 DELETE하여 404 경합을 막는다. 응답이 불확실하면 새 POST를 자동 재시도하지 않고 같은 ID 조회·취소를 안내한다.
- 상태 조회 직후 결과가 만료되어 410을 받으면 완료된 생성 이미지로 표시하지 않고 추적을 종료한다. 서버는 결과 TTL 만료 상태를 저널에 영속 기록하므로 재시작 후 `completed`가 잘못 복원되지 않는다.
- Android는 PNG 서명/크기뿐 아니라 축소 디코딩도 성공해야 결과로 수락한다. 손상 PNG는 화면의 성공 결과나 저장 파일로 사용하지 않는다.
- Preview 옷장의 `android.resource` 샘플 의류를 기존 이미지 전처리로 읽을 수 있게 했다. 앱 자체 패키지의 리소스 URI만 허용하고 다른 앱의 리소스 URI는 거절한다. 로컬/Preview 패키지별 URI를 실제 Preview 테스트에서 확인했다.

수정 파일: `backend/local_vton_server.py`, `backend/tests/test_local_vton_server.py`, `fashion_app/app/src/main/java/com/jeiu/fashionproject/data/LocalVtonRepository.kt`, `TryOnRepository.kt`, `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/LocalVtonScreen.kt`, `VirtualFittingScreen.kt`, `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/TryOnViewModel.kt`, Android 테스트 `LocalVtonFlowTest.kt`, `LocalVtonRepositoryTest.kt`.

## 검증 기록

백엔드 자체 AI 계약 테스트 **46/46 통과**. 로컬 백엔드 전체는 `DATABASE_URL=''`, `TEST_POSTGRES_URL=''`로 **148 통과·3 PostgreSQL 전용 테스트 건너뜀**; CI는 별도 로컬 PostgreSQL 서비스에서 3개까지 실행한다. Android 자체 AI/유료 AI 생명주기 집중 테스트 **24/24 통과**, 자체 AI 진입 및 P12 피팅 화면 집중 테스트 **25/25 통과**, 손상 PNG 검사 포함 저장 테스트 **6/6 통과**. Preview 샘플 의류 입력 검사는 실제 Preview 패키지에서 통과했다. 최종 코드의 Android 전체 **413/413 통과**, Preview 선택 회귀 **177/177 통과**, Debug·계측 코드 컴파일·Lint 통과. 이들은 모의 엔진/서버 테스트이며 실제 모델 추론 성공을 증명하지 않는다.
최신 CI 백엔드 Job은 PostgreSQL 포함 **151 passed, 하위 테스트 8개 통과**, APK 배포 안전성 테스트 **3개 OK**를 기록했다.

로컬 Preview APK는 `tools/apk_release.py verify --preview`에서 패키지 `com.jeiu.fashionproject.preview`, 코드 SHA `26391fe`, 필수 기능 표식, SHA-256 `4946ddc01c410f752f806ada759c4df971463de74c2c2bb3fbe98dfc5f8510da`를 확인했다. **로컬 임시 인증서와 versionCode 4이므로 기존 Preview 업데이트에 사용하지 않는다.** CI가 원래 서명과 증가한 버전으로 게시한 APK만 배포 후보이다.

## 최종 CI 및 배포용 Preview APK

- [Android debug APK 실행 41682578374](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41682578374)은 코드 SHA `26391febeccdb5f10d49cd2429cc42f5dc807200`에서 **7/7 Job 성공**했다. 백엔드/로컬 PostgreSQL, Windows·Linux 서버 시작, Android Debug/전체 단위/계측 코드 컴파일/Lint/Preview 선택 회귀, CPU 메시 비교를 통과했다. 중간 실행 41680211525·41681794709는 후속 코드로 대체되어 취소됐으므로 성공 증거로 사용하지 않는다.
- [배포용 Preview APK](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41682578374/artifacts/13254723728): `fashionai-1.1.74.1-preview-vc17401-26391feb.apk`. 패키지 `com.jeiu.fashionproject.preview`, `versionCode=17401`은 P12 `17101`보다 높다. 서명 인증서 SHA-256 `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`는 기존 Preview와 같다. APK SHA-256 `22d093620ee9fa258199cc4ca52acd63cb340fe6a4436a5bb12465f54ca7d4bc`.
- [BUILD_INFO·SHA256SUMS](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41682578374/artifacts/13254988137) · [테스트·Lint 보고서](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41682578374/artifacts/13254509057). CI의 `tools/apk_release.py verify --distribution --preview`가 실제 APK의 manifest·내장 코드 SHA·서명·버전·DEX 기능 표식·ZIP/비밀 설정 검사를 통과하고 `distributionVerified=true`를 반환했다. 같은 패키지/서명/상향 버전의 Android 업데이트 사전 조건은 충족하지만 S25+ 설치·데이터 유지 결과는 실기기 미검증이다. 기존 Preview를 삭제/초기화하지 않는다.

실제 모델 추론은 **0회**, 실제 생성 결과 PNG **0개**다. 생성 시간·peak VRAM·화질 실측은 **없음**, GPU/유료 API 실행 비용 **0**. 앱에서 실제 GPU 생성 결과를 확인했다고 주장하지 않는다.

## GPU 준비 후 다음 실행 순서

1. 학교·팀 소유의 사용 허가된 NVIDIA GPU, 호환 CUDA/PyTorch/ONNX Runtime, 최소 약 8GB VRAM 지침을 확인한다. Human Parser/SegFormer 라이선스 조건을 운영자가 승인하고 모델 가중치·파서 캐시는 Git 밖의 비공개 경로에 준비한다. 유료 GPU·API는 별도 승인 전 사용하지 않는다.
2. GPU 서버에서 `/api/local-vton/config`가 `ready=true`이고 별도 모델 경로/토큰/비공개 저널이 준비됐는지 확인한다. 이 상태만으로 실제 생성 성공을 주장하지 않는다.
3. 허가된 비개인 샘플 전신 사진과 상의 한 벌로 명시적 동의 → `/jobs` 단일 POST → 같은 ID 상태 조회 → `/result` PNG → Android 비교/로컬 저장까지 종단 간 실행한다. 결과 PNG SHA-256, 크기, 모델 로딩·추론 시간, peak VRAM, GPU 종류·비용을 기록한다.
4. 색상, 목선·소매, 팔·손·얼굴, 경계, 배경을 원본과 비교한다. 실패 시 먼저 입력 구도/누끼/파서·의존성 문제를 확인하고, 하의·원피스는 상의 성공 뒤 검증한다. 실기기 S25+의 네트워크·사진 URI·갤러리 저장은 별도 확인한다.

원본 사용자 사진, 접속 토큰, 가중치, 생성 결과는 GitHub에 올리지 않는다. 기존 옷장·체형·사진·P12 수동 배치 데이터는 초기화하지 않는다.
