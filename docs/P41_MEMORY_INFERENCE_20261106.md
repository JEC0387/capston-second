# P41 — 실제 모델 진단 PNG 생성, 자연스러움/제품 출시 미완료

2026-11-06 KST. P40 `e353824d0495e183034e3ecbc39a42ede63c550b`에서 독립 로컬 브랜치 `codex/fashionai-p41-memory-inference-20261106`으로 진행했다. 실행 코드 체크포인트는 `d67b34f2599a83d3e84e369650b89d437cb798a5`다. P38/P39/P40, 원본 가중치, 기존 APK를 수정하지 않았다. **실제 CPU 모델이 만든 상의·하의 PNG를 확보했다. 둘 다 1단계 진단 출력이며 자연스러움은 FAIL, 정식 출시는 NO-GO다.**

| 필수 항목 | 결과 | 실제 증거 |
|---|---|---|
| 메모리 병목 확인 | PASS, P40의 물리 RAM 보호 종료와 Commit 여유를 분리 | `MEMORY_AUDIT.json`, 기존 P40 로그 |
| 전처리 분리 | PASS, 실제 DWPose 별도 프로세스 → 수치 파일 → 종료 → 생성 프로세스 | `prepared-tops01`, `prepared-bottoms01`, 각 supervisor |
| 메모리 절감 효과 | 전체 모델 복사 없이 144 Linear 순차 로딩, 생성 peak RSS 약 812/814MiB | 두 생성 supervisor의 OS lifetime peak |
| TryOnModel 로딩 | PASS, 원본 해시·전체 schema 검증 후 실제 366개 텐서 읽고 실행 | `tops01`/`bottoms02` events |
| 실제 AI 샘플링 | 상의·하의 각각 1단계 완료. 정규 20단계는 별도 RAM 보호 중단 | 각 `final.json`, `standard20-step1-supervisor` |
| 상의 PNG 생성 | PASS(진단), 자연스러움 FAIL | `tops01/diagnostic-model-result.png` |
| 하의 PNG 생성 | PASS(진단), 자연스러움 FAIL | `bottoms02/diagnostic-model-result.png` |
| 신발 생성형 지원 | BLOCKED, FASHN 비지원 | P40 공식 지원 범위 검토 재사용 |
| S25+ 실행 | NOT TESTED, 실제 Android 엔진 미등록 | 기기 접근/설치 없음 |
| 정식 출시 | **NO-GO** | 정규 품질·3종 생성·Android E2E 미검증 |

## 보존 위치와 실제 PNG

실험 루트는 `C:\Users\USER\Documents\ChatGPT\capston\experiments\P41-20261106`이다. 원본 모델은 P40 폴더의 것을 읽기 전용으로 사용했다. 모델 후보 조사/가중치 재다운로드/외부 추론/유료 사용은 하지 않았다. 공식 공개 예제만 로컬에서 사용했다. 이미지·중간 상태·원문 로그는 Git/APK에 넣지 않는다.

| 실행 | PNG SHA256 | 크기 | 전체 프로세스 시간 | 최대 RSS | 최대 process Commit |
|---|---|---|---:|---:|---:|
| 상의 `tops01` | `c67526946f7141bff448da04cd24257991ebe4880687dabd0d615705bf45e91e` | 576×823 | 356.422초 | 851,771,392 B | 1,546,825,728 B |
| 하의 `bottoms02` | `cecc6ddf36f35721a5543c02be16c1d3d01f44d568cbc6d7c364e963c048f374` | 576×823 | 361.343초 | 853,831,680 B | 1,546,424,320 B |

원본→모델 입력은 864×576, 모델 출력은 576×864이며, 저장 PNG는 공식 전처리 padding(상20/하21)을 제거해 576×823이다. 입력 해상도를 임의로 낮추지 않았다. 모델 추론 뒤 PNG 인코딩, `verify()`, 완전 디코딩, RGB/크기/해시를 검사했다. 기존 P14 결과나 2D 합성 PNG를 넣지 않았다. 두 실행 모두 366개 원본 tensor를 읽었고 실제 read bytes는 1,943,628,480(헤더 제외)다.

육안 검수: 상의는 몸통·팔 경계와 목선/질감이 심하게 흐리다. 하의는 목표 치마 영역과 허벅지 경계가 심하게 흐리며 층/주름을 식별하기 어렵다. 인물·배경 구도는 대체로 인식되지만 픽셀 보존을 보증하지 않는다. **양쪽 모두 제품 품질 FAIL**. 이 진단 출력으로 연속 상·하의 착장 품질이나 실사진 자연스러움 PASS를 선언하지 않는다. 연속 생성 전체 실행은 미완료다.

상의는 기존 P14 허가 공개 예제 `person3.png`/`garment3.jpeg`를 재사용했다. 하의는 보존된 공식 Space 코드의 flat-lay 조합을 확인하고 revision `1c49e7328d65aab0b784637275dbe17d4c0fa1b3`에서 `person5.png`/`garment5.jpeg` 두 파일만 새로 받았다. `inputs-bottoms/INPUTS.json`에 공식 URL·형식·크기·SHA가 있다. 상의 사진을 하의로 재분류하지 않았다. 개인 사진·갤러리·기기는 접근하지 않았다.

## 수치로 확인한 P40 병목과 측정 오류 정정

기존 환경: i5-1155G7, Iris Xe, 물리 RAM 8,300,711,936 B(7.731GiB), system Commit 한도 26,147,180,544 B(24.351GiB). CUDA 미사용/미지원은 실제 Torch 기록을 재사용한다. WMI AdapterRAM 1GiB는 전용 VRAM의 확정 측정이 아니다. P41은 Windows native 프로세스이며 WSL은 실행하지 않았다. 사용자 `.wslconfig` 파일은 없고 실제 WSL 메모리 제한은 측정하지 않았다. Windows 페이지 파일/WSL/시스템 설정을 변경하지 않았다.

P40 `tops02`의 최소 가용 물리 RAM은 13,676,544 B, 최소 free Commit은 3,607,633,920 B였다. `tops03`은 14,548,992 B / 2,963,951,616 B였다. **Commit 고갈 예외가 아니라 물리 RAM <96MiB가 3회 연속 관측된 보호 조건**이다. BF16은 첫 샘플링, INT8은 44번 Linear 변환 시작에서 멈췄다. INT8 leaf 0→44 사이 process Commit이 1,552,764,928 B 증가했다. 옛 Linear를 dict가 계속 잡는 명시적 누수는 발견하지 못했으며 backing storage/할당기 보존을 확정 원인으로 단정하지 않는다.

중요한 정정: Windows venv의 `Scripts/python.exe`는 실제 worker를 실행하는 launcher였다. P41 최초 전처리에서 supervisor peak가 4MiB인 반면 worker 자체 기록은 약 697MiB여서 발견했다. **P40 supervisor의 종료는 launcher 종료 증거이고 실제 worker의 정확한 종료 시각·전체 최고 메모리는 입증하지 못한다.** 기존 파일은 그대로 보존했다. P40 worker의 이벤트 자체 수치는 유효하지만 `tops02`는 34.27초까지만 있어 뒤 약303초의 peak가 미확인이다.

P41 수정 모니터는 기본 Python을 `-I -S`로 직접 실행하고 승인된 venv site-packages만 명시적으로 추가한다. 같은 프로세스에서 snapshot 코드를 실행하므로 실제 worker handle을 측정·종료한다. `monitor-self01`의 실제 64MiB 할당 시험에서 worker PID와 monitor PID가 일치하고 peak RSS 87,293,952 B를 양쪽에서 확인했다. PID·전체 원문 로그는 Git 문서에 싣지 않는다. 1초 단위 RSS/Commit/CPU, 시작·종료 메모리, 실행 소스 SHA를 기록한다.

## 구현한 최적화

1. **전처리 격리:** P40의 실제 DWPose와 공식 resize/pad/normalize 함수를 재사용한다. 전처리 프로세스가 종료된 뒤 고정 shape FP32/int64 NPZ만 읽는다. 저장·재열기 tensor hash가 비트 단위로 일치한다. generation 프로세스의 `dwpose_imported=false`를 확인했다.
2. **Parser 범위:** flat-lay + segmentation-free의 공식 masking 함수는 입력을 그대로 반환한다. 이 제한 경로만 사용하며 Human Parser/SegFormer를 내려받거나 가짜 모듈을 주입하지 않았다. 일반 model-worn/파싱 pipeline 전체 동등성을 검증했다는 뜻은 아니다.
3. **가중치 스트리밍:** 고정 SHA의 [safetensors 형식](https://github.com/safetensors/safetensors#format)을 검증하고 tensor 단위로 읽는다. 전체 BF16 state_dict/전체 FP32 모델/전체 INT8 복제 없이, 필요한 Linear 하나의 원본 BF16을 CPU FP32로 승격해 연산 후 해제한다. 작은 Conv/embedding/norm만 상주한다. 원본 파일을 변경하지 않는다.
4. **순차 CFG:** 인물·의류 조건을 유지한 채 conditional/unconditional을 batch1로 각각 계산한다. 마지막 단계의 실제 사용하지 않는 unconditional 계산만 생략한다. 부동소수점 수치 회귀를 통과했다. INT8을 다시 시도하지 않았다.
5. **query 분할:** 전체 key/value를 보존하고 query128씩 SDPA를 실행한다. 로컬 어텐션/해상도 축소/VAE 타일링이 아니다. FP32 dense score를 명시적으로 펼친다고 가정하면 B2·10heads·6912²는 3,822,059,520 B, B1 query128은 35,389,440 B다. **이는 shape 기반 크기 계산이고 실제 SDPA 내부 할당 측정값은 아니다.** 실제 최대 Linear 출력은 247,726,080 B, 최대 단일 원본 weight read는 22,937,600 B였다.

상의 generation 준비 시 process Commit은 512,811,008 B, 상주 parameter는 2,044,416개, disk Linear는144개다. P40 `tops02`의 동일 준비 단계 Commit 2,519,244,800 B보다 약79.6% 작다. 정밀도/프로세스 구조 등 여러 변화가 함께 적용됐으므로 한 기술만의 독립 효과로 표현하지 않는다. 실제 생성 전체 peak는 위 표를 우선한다.

## 실패와 정규 샘플링

- `bottoms01-supervisor`: 당시 시작 RAM이466,026,496 B여서 512MiB 사전 조건으로 **실행 전 차단**했다. 같은 조건에서 무조건 재시도하지 않았고, 이후 메모리 회복을 확인한 새 `bottoms02`에서 완료했다.
- `standard20-step1`: 정규 20단계/순차 CFG 설정, 첫 단계 완료 후 상태 저장하도록 실행했다. **63초에 실제 worker를 RAM 보호로 종료**했다. 당시 worker peak RSS472,514,560 B, peak Commit1,020,469,248 B였고 `x_patch_mixer.1`까지 도달했다. 0/20단계 완료, 정규 PNG/실제 sampler checkpoint 없음. 최종 free RAM325,697,536 B. 시스템 전체 압력을 모델 자체 peak와 구분한다.
- 정규 중간 상태 코드의 `images.f32`, RNG, 모델/입력/코드/일정 계약과 SHA, 저장 재읽기 검사는 자동 테스트로 검증했다. **실제 정규 checkpoint 저장·복원 후 추론 완료는 아직 미검증**이다. 1단계 진단 PNG를 정규 상태로 승격하거나 상태를 임의 생성하지 않는다.

재현은 각 supervisor의 `invocation.json`과 `sources/`를 우선한다. 새로운 실행 폴더를 사용하고 기존 파일을 덮어쓰지 않는다. 정규 실행은 `p41_infer.py --steps 20 --stop-after-step 1`; 실제 상태가 생성된 이후에만 `--resume <checkpoint-001> --stop-after-step 2`로 이어간다. 모델/입력/source/adapter SHA와 Torch 버전이 다르면 거절한다. 현재 정상 checkpoint가 없으므로 재개가 이미 가능하다고 주장하지 않는다. 장시간 전체 실행은 안정적인 RAM 여유 확보 후 수행해야 한다.

## Android·모바일 경로와 남은 조건

P39 마법봉의 입력 고정·중복 실행 차단·취소·실패·이전 결과 숨김·PNG 저장/재열기 계약을 읽기 전용으로 검토했다. 현재 고정 Unavailable 경로에서 새 재현 결함은 찾지 못했다. 앱 코드는 변경하지 않았다. 기존 47 PASS/Lint 기록은 앱 경계에만 재사용하며 실제 모델/S25+ 성공 근거가 아니다.

실제 모바일 어댑터 연결 전에는 availability를 한 번의 스냅샷으로 사용하고, ViewModel 수명을 넘어 native runtime에도 공유 단일 실행 잠금·종료 합류가 필요하다. 현재 native 엔진은 등록되지 않았다. FASHN 상의/하의를 P39의 TOP+BOTTOM+SHOES 준비 상태로 위장하지 않는다.

모바일 이식은 고정 shape의 단일 denoiser graph → 원본 수치 대조 → SDPA/RoPE/Norm/Conv/Embedding 지원 및 peak 계측 → 전체 sampler/취소 → 승인된 S25+ 설치/E2E 순서다. 전체 모델 export는 아직 수행하지 않았다. P40의 Mobile-VTON 공개 가중치·비상업 라이선스·Android 패키지 미확인 기록을 재사용하고 같은 후보를 다시 조사/다운로드하지 않았다. 신발 모델은 미확보다. 생성형 3종 품질·S25+·정식 서명/출시 조건이 남아 있다.

## 변경과 검증 범위

신규 코드: `p41_attention.py`, `p41_streaming.py`, `p41_preprocess.py`, `p41_infer.py`, `p41_monitor.py`, `p41_sampling_state.py`; 각 대응 `backend/tests/test_p41_*.py` 6개. 모델·기기·프로덕션 API·Android·DB·데이터 포맷은 변경하지 않았다.

로컬 focused01(46개), focused02(7개), monitor02(15개), resume01(32개)의 XML을 보존한다. 반복된 nodeid를 제외한 **최종 고유 81개 PASS / FAIL·ERROR·SKIP 0**을 `P41_RESULTS.json`에 기록했다. pytest exit0이며 일부 종료에서 pyreadline3 콘솔 소멸자의 invalid-handle 경고가 있었고 전역 환경을 바꾸지 않았다. 별도 실제 메모리 probe, 실제 DWPose, 실제 상·하의 모델 PNG를 자동 모의 테스트와 구분한다.

총괄 1명과 실제 병렬 담당3명이 파일 소유권을 분리했다. 모델·테스트 실행과 Git은 총괄 한 명이 진행했다. 전체 CI/Android 재빌드/새 APK/기기 설치/원격 Push/결제/운영 변경은 하지 않았다. 기존 Preview18605·QA18607 데이터는 접근하지 않았으며 이번에 기기 데이터 보존을 새로 검증했다는 주장을 하지 않는다.

종료 검수에서 기존 로컬 Preview18605 APK SHA `820f62c3c98602a0ff8dfbd1b3da3e61092444eb6d991622f72a726612eb51ce`, QA18607 APK SHA `651e901d061eaeb01428fb95b61413892572fc02b44dd465a18d01a9c402f07c`가 기존 기준과 일치했다. P40/P39 브랜치 참조도 원래 커밋을 유지한다. 로컬 연구 도구·테스트·인수인계만 커밋하며 원격 백업은 하지 않는다.
