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

## P6-3 Android 통합과 추가 회귀

- 자체 AI 화면·메모리 전용 별도 서버/토큰·사진 품질 검사·명시적 전송 동의·상의/하의/원피스 한 벌·진행/취소/재조회·원본/결과 확대 비교·JPG/PNG SAF 저장을 연결했다. 기존 유료 API와 같은 gateway 인터페이스를 쓰되 별도 ViewModel key/connection/URL 경로로 분리하며 유료 fallback을 두지 않는다.
- 화면/프로세스 복원은 GET으로만 같은 작업을 조회한다. 서버 기록을 잃은 경우 명시적 추적 종료 기능이 있으며 취소 성공으로 표시하거나 자동 POST하지 않는다. 기존 수동 마법봉, 기존 유료 FASHN, 옷장/3D 저장 구조는 유지한다.
- 서버 강제 종료 후 전용 임시 사진 폴더를 다음 시작 때 안전하게 정리한다. 서버 테스트26개 + manifest4개 통과. Windows 백엔드 회귀105개 통과/3개 PostgreSQL 전용 skip/8 subtests 통과. Linux 전용 proxy/quality와 PostgreSQL은 CI에서 검증한다.
- Android Kotlin 및 테스트 컴파일 성공. 자체 AI/기존 AI/수동 UI 선택35개 테스트 통과 후 복구 테스트를 추가했고, 최종 자체 AI15개(화면9+repository6)도 통과했다. 실제 GPU 모델이 아닌 합성 gateway 테스트다. 전체 회귀/Preview/Lint/APK 검증 결과는 최종 CI 후 아래에 기록한다.

## 완료 범위와 남은 한계

| 단계 | 실제 상태 |
|---|---|
| P6-1 | 공식 API/파일/체크섬/라이선스 검토 및 준비 도구 완료. CUDA 없음, 실제 가중치 로드·생성 PNG 미완료 |
| P6-2 | 별도 인증·작업·결과·만료·취소 서버 구현 및 합성/프로세스 테스트 완료. 실제 CUDA 작업은 미검증 |
| P6-3 | Android 자체 AI 모드 연결·동의·재조회·비교·확대·PNG/JPG 저장 구현. 실서버/실기기 생성은 미검증 |
| P6-4 | 기존 전신 포즈 검사를 재사용하고 서버 EXIF·알파·비율 유지·크기·형식 검증 추가. 얼굴·손·의상 경계·무늬 품질 평가는 GPU 확보 후 |
| P6-5 | 실제 실행 시 로딩/추론 시간·출력 크기·PyTorch peak VRAM을 기록하는 계측 구현. 관측값/성공률/OOM 벤치마크 없음 |

실제 생성 결과가 없으므로 처리 시간·실측 해상도·성공률을 제시하지 않는다. 공식 해상도 안내와 실측은 구분한다. 서버가 준비되지 않으면 생성 버튼을 비활성화하고 가짜 이미지나 수동 합성을 AI 성공으로 표시하지 않는다. 반복 모델 로딩·총 GPU VRAM 상한·모델 준비 시 파서 캐시 누락·실제 GPU 강제 종료는 후속 검증 항목이다. 전체 런타임의 상용 권한을 주장하지 않는다. 무료 GPU 조건과 설치 순서는 서버 문서를 따른다.

## 11월3일 S25+ 확인 순서

1. 기존 앱/안정 Preview를 삭제하거나 데이터를 지우지 않는다. 마이페이지의 현재 버전·옷장 수·저장 치수·아바타를 먼저 기록한다. 기존 백업 파일도 보존한다.
2. 최종 CI 성공 아티팩트만 다운로드해 패키지/서명/버전 비교 후 Preview에 **업데이트**한다. 서명 오류가 나면 삭제로 우회하지 않는다. 이번 세션은 설치/USB reverse/데이터 이전을 실행하지 않았다.
3. 마이페이지에서 새 versionCode와 코드 SHA를 확인하고 기존 옷장·사진·체형 숫자/저장값·3D 아바타 복원·수동 피팅·코디 추천을 확인한다. 실제 OpenGL/카메라/갤러리는 자동 테스트만으로 검증되지 않는다.
4. 사진 가상피팅 → 마법봉/AI로 입어보기 → **모드 B 자체 AI**로 진입한다. 모드 A는 2D 자동 배치, 모드 C는 기존 유료 FASHN이며 별도 동의 없이는 C 생성 버튼을 누르지 않는다.
5. 서버 미설정/인증 오류/가중치 또는 GPU 미준비 시 구체적 안내와 생성 차단을 확인한다. 해당 화면에서 유료 서버로 자동 전환되지 않아야 한다.
6. GPU 담당자가 연구 라이선스를 검토하고 공식 가중치/파서 캐시/HTTPS/개인별 토큰을 준비한다. 서로 다른 사람에게 동일한 토큰을 배포하면 같은 소유자로 취급되므로 토큰을 분리한다. 운영 Railway/DB에 연결하지 않는다.
7. 승인된 정면 전신 사진과 옷 한 벌을 선택하고 사진 유형을 맞춘 뒤 동의한다. 상의/하의/원피스 각각 검증한다. 옷 여러 벌은 한 벌 선택 안내를 확인한다.
8. 첫 실제 결과 PNG를 저장하고 원본/결과/확대 비교에서 얼굴·손·목/어깨·원래 옷 경계·무늬·색·길이·배경을 평가한다. 모델은 실제 착용 크기나 정체성 보존을 보장하지 않는다.
9. 생성 중 화면 이동/재진입·중복 탭·네트워크 끊김·앱 프로세스 복원·취소·시간 초과·서버 재시작·5분 만료를 확인한다. 복원 후 같은 토큰을 다시 입력하고 기존 작업 GET만 실행해야 한다. 응답 유실 시 새 생성부터 시작하지 않는다.
10. PNG/JPG를 사용자가 지정한 위치에 저장하고 갤러리에서 연다. 저장한 파일은 서버 만료와 별개로 사용자가 관리한다. 지표는 실제 GPU 응답에서 기록하고 공개 저장소에 개인 사진을 올리지 않는다.

## 후속 개발 순서

허가된 CUDA 머신 확보 → 공식 도구로 가중치/파서 준비 → 서버 준비 응답 확인 → 승인된 사진으로 첫 실제 PNG → 세 카테고리 품질 비교/최소3회 실제 성능 기록 → S25+ 연결/데이터 보존 회귀 → 근거가 확보된 경우에만 warm-worker/VRAM 최적화. GPU 테스트가 끝나기 전 자체 AI 완성/배포 준비 완료라고 표시하지 않는다. 운영 배포·DB 변경·유료 자원 사용은 별도 승인 대상이다.

## 최종 CI 및 APK 증거

- APK 실행 코드: `ba5b0573a94c0ba565ae4a0c708cd3009a0030e5`. 뒤의 문서 정리 커밋은 이 APK의 소스 SHA가 아니다. 현재 문서 포함 최종 HEAD는 `git rev-parse HEAD`로 확인한다.
- [P6-1 CI 성공](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41401877691), [P6-2 CI 성공](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41405511349), [최종 P6 통합 CI #35 성공](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41406835524).
- Android 전체 단위/Compose Robolectric 회귀 **332개 통과**, Preview 전용 **77개 통과**(전체 테스트 일부를 Preview 패키지에서 재검증하며 합산해 고유 테스트 수로 표시하지 않는다). Kotlin/단위 테스트/계측 테스트 Kotlin 컴파일, Debug/Preview APK 빌드, Lint 성공. Lint는 **Error/Fatal 0, Warning 166, Hint 2**다. 수정한 자체 AI/AI 화면/ViewModel 파일에 Lint 이슈는 없지만 전체 경고는 남아 있다. 계측 테스트는 컴파일했으며 휴대폰/에뮬레이터에서 실행하지 않았다.
- Linux 백엔드 전체 pytest **151개 통과, skip 0, 8 subtests 통과**, APK 버전/서명 안전성 도구 테스트3개 통과. 전용 PostgreSQL 테스트 DB만 사용했고 운영 DB에 연결하지 않았다. Windows Python3.11/3.12/3.13/3.14 및 Linux3.12의 기존 서버/독립 VTON 비활성 기동 확인5개 조합 성공. 실제 GPU 추론 테스트는 포함하지 않는다.
- [Preview APK 다운로드](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41406835524/artifacts/13124291870) — 비공개 저장소 로그인 필요, 30일 보관.
- 로컬 APK: `C:\Users\USER\Documents\ChatGPT\capston\p6-vton-20261102\fashion_app\build\p6-evidence\preview-vc13501\fashionai-1.1.35.1-preview-vc13501-ba5b0573.apk`
- 실제 manifest: `com.jeiu.fashionproject.preview`, `1.1.35.1-preview`, `versionCode=13501`, build `35.1`. assets/build_identity.json·BUILD_INFO.json·Git SHA와 일치한다.
- 서명 SHA-256: `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`. 안정 APK `12701`을 직접 내려받아 인증서를 대조했고 동일하다. 새 키를 생성/교체하지 않았다. 패키지/서명 동일 +13501>12701로 업데이트 사전 검사 통과. 실제 기기 설치/데이터 보존 결과는 미검증이다.
- APK SHA-256: `8aa0b8cd164b8a3214e9f217c40b376c1556fd8dcaf5cd7d3d8451cf9e8b2fc5`. APK ZIP CRC 정상. 3D/치수 직접 입력/기존 AI/새 자체 AI 클래스·엔진 등10개 필수 DEX 표식 확인. 기존 데이터 마이그레이션/저장소 코드는 수정하지 않았다.
- APK에서 서명키/.env/모델 가중치 파일 및 검사한 개인키/GitHub/cloud-key 패턴 미검출. CI는 실제 서명 파일/비밀번호 바이트가 APK에 섞이지 않았는지도 검사했다. Gradle BuildConfig에 새 외부 API 키를 추가하지 않았다. 모든 종류의 비밀정보를 탐지하는 보증은 아니며 기존 서명·토큰 관리 정책을 유지한다.
- 로컬 증거 `fashion_app/build/p6-evidence/P6_APK_VERIFICATION.json`, `ci-reports/`, `preview-vc13501/BUILD_INFO.json`은 ignored build 디렉터리에 있으며 Git에 올리지 않는다. 안정 APK·기존 백업도 유지했다.

## 기준 P5 대비 전체 변경 파일

```text
.github/workflows/android-debug-apk.yml
.gitignore
README.md
backend/LOCAL_VTON_SERVER.md
backend/LOCAL_VTON_SETUP.md
backend/create_local_vton_token.py
backend/local_vton.env.example
backend/local_vton.py
backend/local_vton_server.py
backend/local_vton_worker.py
backend/prepare_local_vton_weights.py
backend/tests/test_local_vton_manifest.py
backend/tests/test_local_vton_server.py
docs/P6_VTON_HANDOFF_20261102.md
docs/README.md
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/LocalVtonRepository.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/AiTryOnScreen.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/LocalVtonScreen.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/TryOnViewModel.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/LocalVtonFlowTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/LocalVtonRepositoryTest.kt
tools/apk_release.py
```
