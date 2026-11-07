# P14 실제 생성 증거 및 11월 10일 시연 준비

## 가장 먼저 구분할 결과

**공식 FASHN VTON v1.5 공개 데모의 무료·비로그인 요청으로 상의 착장 이미지 1장 생성에 성공했다. 자체 FastAPI → Android → 실기기 생성 성공은 아니다.**

- 기준 브랜치 `codex/fashionai-p13-ai-vton-20261104`, HEAD `4e9a84b56c3fbaaef8ed7e1de90b2f5cff11bd3b`를 fetch하여 정확히 확인했다.
- 별도 worktree `C:\Users\USER\Documents\ChatGPT\capston\p14-demo-20261104`, 신규 브랜치 `codex/fashionai-p14-ai-demo-readiness`.
- 기존 worktree/미추적 파일/백업/사진/옷장/P12/P13/서명키를 보존했다. 팀 저장소·운영 Railway·운영 DB·유료 AI 경로는 변경하지 않았다.
- 이 단계는 앱/모델/서버 구조를 다시 만들지 않는다. 앱 실행 코드는 기준 P13 그대로이며, 증거 기록과 시연 절차만 추가했다.

## 실제 생성 1회

2026-11-04 03:12 KST에 [공식 Space](https://huggingface.co/spaces/fashn-ai/fashn-vton-1.5)의 공개 `try_on` API를 호출했다. 사용자가 공식 제공 예제 이용을 명시적으로 허용했고, 데모 소스가 `Complete Examples`에 지정한 `person3.png` + `garment3.jpeg` 쌍을 사용했다. 실제 사용자 사진/옷장/촬영 기록은 읽거나 전송하지 않았다.

| 항목 | 실제 확인값 |
|---|---|
| 입력 | 공식 정면 전신 거울 사진 + 남색 상의 flat-lay 예제 |
| 모델/호출 | 공식 Space의 `TryOnPipeline`, CUDA 강제 경로, `tops`, 50 steps, guidance 1.5, seed 42, segmentation-free |
| Space revision | `1c49e7328d65aab0b784637275dbe17d4c0fa1b3` |
| 결과 | 서비스가 WebP 반환 → RGB 픽셀이 동일한 PNG로 보존, 576 × 823 |
| PNG SHA-256 | `400c33358acb68e32fe085d665e7d6132bbc2d18b083433360ee7f21fa81b310` |
| 원본 WebP SHA-256 | `664ca1d27102ec9b8525bfb6465a8f1b01152f817796106cb84a568e19209772` |
| 클라이언트 경과 시간 | 33.078초: 제출/대기열/추론/다운로드/PNG 변환 포함. 순수 추론 시간 아님 |
| GPU 모델/peak VRAM | 공개 API가 제공하지 않아 미확인. 런타임 `zero-a10g` 표시는 실제 device_name 또는 실측 VRAM이 아님 |
| 실제 과금 | 0 USD. 비로그인 무료 경로만 호출, 결제/크레딧/유료 API 사용 없음 |
| 요청 수 | 추론 POST 1회. 결과 WebP 추가 다운로드는 같은 결과 보존용이며 재추론 아님 |

Pillow 전체 디코딩과 PNG verify, SHA-256, 원본 WebP/PNG 픽셀 동일성 검사를 통과했다. SSE `complete` 결과와 요청 증적은 로컬에 보존했다. 공식 소스의 실제 파이프라인 호출과 새 요청의 결과를 확인했으며, 테스트용 단색 이미지·오버레이·이전 샘플 결과를 생성 증거로 사용하지 않았다. 서비스 내부의 체크포인트 checksum/실제 GPU 로그는 접근할 수 없어 자체 manifest 검증 성공으로 주장하지 않는다.

### 품질 검수

- 흰 티셔츠가 선택한 남색 짧은 상의로 교체되고, 목선/소매/노출된 허리 부분이 새로 생성됐다.
- 전반적 자세, 휴대전화, 반바지, 배경 구도는 남았지만 세부 배경과 신체 경계가 변했다.
- 의류 중앙의 세밀한 스모킹 질감은 크게 단순화됐다. 소매 무늬 일부만 남았다.
- 얼굴·신체 정체성의 완전한 보존, 정확한 핏/사이즈, 사진 실측 정확도를 보장하지 않는다. 이번 결과는 연구용 실제 생성 예시이지 품질 인증이 아니다.

### 비공개 보관

PNG: `C:\Users\USER\Documents\ChatGPT\capston\p14-demo-20261104\backend\local_vton_private\p14-evidence\official-demo-tops-result.png`

같은 폴더에 `official-demo-original-output.webp`, 원본 공개 예제 2장, `attempt-1.json`, SSE 기록, 당시 Space 소스, 실제 1회 호출 스크립트를 보존했다. 사용자 계정 작업 폴더이며 `.gitignore`로 제외되고 웹 정적 경로로 제공하지 않는다. Windows 사용자/관리자 및 Codex 작업 계정이 접근할 수 있는 로컬 파일이며 별도 암호화 저장소라고 주장하지 않는다. GitHub/APK에는 이미지·결과 URL·작업 이벤트 ID를 넣지 않는다. 서버의 임시 결과 URL은 비공개 보관소로 간주하지 않는다.

## GPU 선택지의 실제 상태와 사용 조건

| 경로 | 실제 접근/실행 | 제한 및 판단 |
|---|---|---|
| 공식 FASHN 공개 데모 | 공개 config/API/소스 접근, 무료 요청 1회 성공 | 공용 Gradio/ZeroGPU. 우리 인증 서버가 아니며 개인 사진 전송 금지 |
| 개인 무료 Hugging Face ZeroGPU Space | 공식 문서만 확인, 사용자 계정/자체 Space/GPU 미확보 | 무료 호스팅은 계정 조건·할당량·대기열 적용. Gradio 전용이므로 기존 FastAPI를 그대로 배포할 수 없음 |
| 학교·팀 GPU | 사용자가 확보한 GPU 없음이라고 확인 | 허가된 접속정보·실행 환경 없음. 임의 접근/계정 생성 안 함 |
| Colab 무료 | 공식 FAQ 접근, 사용자 GPU 세션 미확보 | GPU/사용시간 보장 없음. 무료 런타임을 SSH/웹 UI 우회 서버로 사용하지 않음. 향후 승인된 계정의 notebook 직접 실험 경로만 검토 |
| 최소 비용 후보 | Runpod 공식 가격만 확인, 계정/재고/실행 미확인 | RTX A5000 24GB 공시 $0.27/h 예시. 저장소·부가비용 및 실제 가격/재고 별도 확인, 승인 전 실행/결제 금지 |

현재 PC 하드웨어는 Intel Iris Xe만 확인됐고 `nvidia-smi`가 없다. 자체 CUDA 추론 환경으로 사용할 수 있다고 주장하지 않는다. SDK ADB daemon은 실행됐지만 `adb devices -l`에 기기가 없어 S25+ 실기기 작업은 하지 않았다.

출처: [ZeroGPU 조건·할당량](https://huggingface.co/docs/hub/spaces-zerogpu), [Colab FAQ](https://research.google.com/colaboratory/faq.html), [Runpod 가격](https://www.runpod.io/pricing). 2026-11-04 확인값이며 무료 슬롯·가격·재고는 추후 달라질 수 있다.

### 예제/모델/개인정보 범위

[Space README](https://huggingface.co/spaces/fashn-ai/fashn-vton-1.5/blob/main/README.md)는 Apache-2.0을 표기하며 [공식 소스](https://huggingface.co/spaces/fashn-ai/fashn-vton-1.5/blob/main/app.py)는 해당 사진을 직접 실행용 예제로 제공한다. 이번에는 그 데모의 의도된 예제 테스트 범위로만 이용했다. 별도 초상권·이미지의 무제한 재배포 허가를 확인한 것으로 확대하지 않으며 이미지/생성물을 GitHub/APK에 배포하지 않는다.

[본체 라이선스](https://github.com/fashn-AI/fashn-vton-1.5/blob/main/LICENSE)와 [Human Parser 라이선스](https://github.com/fashn-AI/fashn-human-parser/blob/main/LICENSE)는 구분한다. 자체 서버 운영 시 Parser/SegFormer의 연구·평가용 조건을 운영자가 확인해야 한다. 공개 데모 운영자의 수락을 우리 자체 운영의 수락으로 대신하지 않는다.

[Hugging Face 개인정보 정책](https://huggingface.co/privacy)은 서비스 제공 등에 필요한 기간 정보를 보관한다고 설명하며 우리 서버의 5분 TTL과 같은 삭제 보장은 없다. 데모 소스에서도 우리와 같은 소유자별 결과 인증/삭제 계약을 확인하지 못했다. 따라서 공개 예제만 사용했고 실제 사진은 보내지 않았다.

## Android 연결과 시연의 경계

기존 `LocalVtonRepository`/`LocalVtonScreen`/`TryOnViewModel`의 사진·의류 선택, 동의, 인증, 단일 POST, 상태 조회·취소, PNG 검증, 원본 비교·저장을 재사용한다. 공개 Space를 `/api/local-vton` 서버로 위장하거나, 받은 PNG를 가짜 worker 결과로 주입하지 않았다. **공식 데모 실제 생성 성공과 자체 서버·Android 모의 연동 테스트 성공은 서로 다른 증거다.**

자체 API 실제 GPU 생성/Android 실제 결과 표시·저장/S25+ 실기기: 미검증. 이를 완료하려면 사용자 소유·허가된 GPU에 기존 [LOCAL_VTON_SERVER.md](../backend/LOCAL_VTON_SERVER.md)의 가중치/checksum/Parser/인증/HTTPS/비공개 저널을 준비하고 상의 한 벌 실제 요청부터 진행한다. 새 모델이나 공개 데모 프록시는 추가하지 않는다.

## 11월 10일 시연 순서 (실기기 체크는 아직 미완료)

1. 검증된 **FashionAI Preview**만 기존 Preview 위에 업데이트한다. 기존 앱을 삭제/초기화하지 않는다. 패키지 `com.jeiu.fashionproject.preview`, 기존 인증서 일치, 상향 versionCode를 먼저 확인한다. 설치 거부 시 삭제로 우회하지 않는다.
2. 마이페이지 버전/SHA 확인 → 기존 옷장과 체형/배치 데이터 확인. Preview 격리를 유지하고 운영 동기화를 시연 준비 때문에 켜지 않는다.
3. 3D 아바타 진입 → 정면/측면/후면/핀치 확대 → 7부위 cm와 0.5cm 조절 → 저장하지 않고 취소/저장 후 재진입 확인. 화면 회전·큰 글꼴에서도 확인한다.
4. 옷장 의류로 수동 피팅 → 한 손가락 이동 → 두 손가락 확대·회전 → 마법봉 자동핏 → 이전 배치 되돌리기 → 저장/앱 재실행 후 복원. 마법봉은 2D 자동 정렬이라고 설명한다.
5. 자체 AI 화면 진입 → 의류 한 벌 선택/전신 사진 선택 흐름 설명 → 서버 미준비 안내와 생성 비활성 확인. GPU 미연결을 숨기거나 유료 AI로 자동 전환하지 않는다.
6. 별도 로컬 결과 화면에서 공식 예제 원본/상의/실제 생성 PNG와 위 검증값을 보여준다. **“공식 데모에서 별도로 검증한 생성 결과이며 앱의 실시간 생성은 아직 연결 미검증”**이라고 밝힌다. 네트워크에 의존하는 공개 데모 실시간 재실행은 발표의 필수 경로로 두지 않는다.

| 시연해도 되는 범위 | 시연/설명해서는 안 되는 주장 |
|---|---|
| 기기 리허설을 통과한 옷장·체형 분석·3D·cm·수동 배치·자동핏·저장 복원 | 기기 리허설 없이 실기기 안정성 완료라고 주장 |
| 자체 AI 입력·동의·미준비 안내 UI | 공개 데모를 자체 인증 서버처럼 사용하거나 APK가 실제 생성했다고 주장 |
| 출처를 명시한 공식 예제 실제 생성 1회 결과 | 더미/기존 이미지를 새 추론 결과로 표시, GPU/VRAM/속도 수치 추정 |
| 시각적 3D 아바타와 2D 자동 정렬 | 정확한 신체 스캔·의류 물리 시뮬레이션·정확한 착용 핏 보장 |

## 자동 검증 및 GitHub

P13 기준 CI `41682578374` 및 APK17401은 보존했다. P14 [전체 CI 41692637427](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41692637427)의 **7개 Job 모두 성공**을 확인했다. 실제 생성 이미지는 CI fixture나 APK 자산으로 사용하지 않았다.

| 검증 | 실제 결과 |
|---|---|
| Android 전체 단위 테스트 | 413개, 실패/오류/건너뜀 0 |
| Preview 회귀 테스트 | 177개, 실패/오류/건너뜀 0 |
| Backend | 151개 및 하위 검사 8개 통과, 경고 7개 |
| APK 배포 안전성 단위 테스트 | 3개 통과 |
| Lint | 오류 0, Warning 168, Hint 2. 경고가 없는 프로젝트라고 주장하지 않음 |
| Android 기기 테스트 코드 | Kotlin 컴파일 성공. 실제 기기 실행 아님 |
| 서버 시작 | Windows Python 3.11/3.12/3.13/3.14 및 Linux 3.12 모두 성공 |
| 아바타 비교 렌더링 | CI CPU 렌더링 성공. S25+ OpenGL 실행 아님 |
| 실제 자체 GPU/Android 결과 흐름 | 미검증. CI 모의 추론 성공으로 대체하지 않음 |

XML 보고서를 다운로드하여 실제 개수를 집계했다. 로컬 보고서: `fashion_app/build/p14-ci-reports/` (Git 제외). 이미 통과한 테스트를 로컬에서 중복 실행하지 않았다.

### 검증된 Preview APK

- [APK 다운로드](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41692637427/artifacts/13262065991): 비공개 저장소 접근 권한 필요. Artifact 만료 예정 2026-12-03 UTC.
- 버전 `1.1.75.1-preview`, versionCode **17501**, 패키지 `com.jeiu.fashionproject.preview`.
- APK 코드/CI SHA: `384b0dcb90be3b8ebfa30b0b6a1c751e2cf0a0f5`.
- 인증서 SHA-256: `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`.
- APK SHA-256: `a53712afdf81f16fbd98d1e27eb1d47cb6c42457c5c7b5c6c572045aaa512e3e`.
- 정확한 로컬 APK: `C:\Users\USER\Documents\ChatGPT\capston\p14-demo-20261104\fashion_app\build\p14-final-apk\fashionai-1.1.75.1-preview-vc17501-384b0dcb.apk`.
- 같은 폴더의 `BUILD_INFO.json`, `BUILD_INFO.txt`, `SHA256SUMS.txt` 보존. CI의 `distributionVerified=true`, `sourceDirty=false` 확인.

보존한 P13 APK17401 파일과 직접 비교하여 동일 패키지·동일 인증서·상향 버전을 확인했다. 다운로드 APK의 apksigner 검증, ZIP CRC, SHA-256, manifest와 내장 `assets/build_identity.json` 및 BUILD_INFO 일치, 필수 DEX 기능 표식, 개인 설정/키 파일 제외 검사를 통과했다. 공식 예제/생성물 4개 파일과 APK 이미지 엔트리의 해시를 비교하여 해당 증거 이미지가 포함되지 않았음을 확인했다. 별도 로컬 검증 모드의 임시 메타데이터는 `distributionVerified=false`로 출력하지만 원본 CI 배포 메타데이터는 수정하지 않았다.

**업데이트 방법:** ZIP을 풀어 위 APK를 기존 **FashionAI Preview**에 업데이트한다. 기존 앱 삭제/초기화는 하지 않는다. 설치 후 마이페이지에서 `1.1.75.1-preview / 17501 / 384b0dcb`를 확인하고 위 시연 순서의 데이터 보존·저장 복원을 먼저 점검한다. 이 비교는 P13 Preview17401 기준이며, 과거 다른 서명으로 설치된 기본 FashionAI 앱을 덮어쓸 수 있다는 의미가 아니다. 설치 거부 시 중단하고 설치본 패키지·서명·버전을 읽기 전용으로 확인한다. 이번 세션에서는 설치/업데이트/복원을 실행하지 않았다.

### 최종 인수인계 및 다음 순서

수정 파일은 이 문서와 `docs/README.md` **2개뿐**이다. 앱/백엔드/배포 스크립트는 기준 P13과 동일하다. 실제 추론 스크립트와 원본 응답은 위 비공개 증거 폴더에만 보존한다. P14 브랜치에 생성 증거 커밋 `384b0dc`를 Push하고 전체 CI를 완료했으며, 이 검증 결과를 추가한 후속 커밋은 문서만 변경한다. 최종 문서 커밋 SHA는 `git rev-parse HEAD` 또는 브랜치에서 확인하며 APK 내부 SHA와 구분한다. 문서만 갱신한 뒤 이미 성공한 전체 CI를 다시 실행하지 않는다.

1. S25+가 다시 ADB 인증된 상태에서 연결되면 설치본 버전/서명을 읽기 전용 확인한다. 기기 작업을 허용받은 범위에서만 Preview 업데이트와 위 리허설을 진행한다.
2. 발표에서 공식 데모의 실제 생성 결과와 앱의 자체 서버 미연결 상태를 분리해 설명한다. 공용 데모의 실시간 응답이나 무료 GPU 할당을 필수 시연 경로로 삼지 않는다.
3. 학교·팀의 허가된 GPU를 확보하면 기존 자체 서버 배포 문서에 따라 가중치/checksum, Parser 연구·평가 라이선스, HTTPS/인증/작업 격리/만료 정책을 확인한다. 비용 발생은 별도 승인 대상이다.
4. 공식 예제의 허용 범위 안에서 상의 한 벌 자체 GPU 추론을 검증하고 실제 GPU명/peak VRAM/모델 로딩·추론 시간/PNG 해시를 기록한다. 그 뒤 Android의 요청→조회→표시→비교→저장 및 취소/실패를 실기기 검증한다.

남은 위험: 자체 GPU 미확보, 실제 앱 생성·저장 미검증, S25+ 리허설 미실시, Lint 경고, 공용 데모의 할당량·보관 정책, 결과의 의류 질감/신체 경계 변화. 운영 배포·DB 변경·사용자 사진 전송·데이터 이전·유료 호출은 하지 않았다.
