# P44 실제 GPU 검사 결과 — 출시 NO-GO

2026-11-06 KST. 기준 `37919453879f1ea8ee37fd76b59f878ab7aad105`, 독립 로컬 브랜치 `codex/fashionai-p44-accelerator-final-20261106`.
확인한 로컬 작업 트리·진행 프로세스·Codex 채팅 범위에서 P43 실행이나 미커밋 변경은 발견하지 못했다. 총괄 1명과 하위 담당 3명이 코드/GPU/Android/모델 검토를 분담했고 실제 프로세스 실행은 총괄 한 명만 했다.

## 필수 인수 결과

| 항목 | 실제 결과 |
|---|---|
| 공식 FASHN 가중치 | 기존 P40 본체/DWPose 보존·재사용, 재다운로드 없음 |
| PC GPU 실행 | Intel Iris Xe + DirectML에서 실제 가중치의 부분 연산 성공 |
| 전체 모델/정규 20단계 | P44 미실행. P42의 2/20 진행률을 늘린 것으로 계산하지 않음 |
| 자연스러운 상의/하의 PNG | 새 파일 없음; 기존 진단 PNG는 완성품 아님 |
| 생성형 신발/3종 조합 | 실행 자산·통합 경로 미확보 |
| Android/S25+ 전체 추론 | 미검증; DirectML은 Windows 실험이며 Android 런타임이 아님 |
| 마법봉 실제 생성·비교·저장 | 실제 모델 미연결. 기존 unavailable/입력 고정/취소/저장 계약 유지 |
| 기존 APK/개인정보 | APK 생성·설치·ADB·개인 데이터 접근·사진 전송 없음 |
| 정식 출시 | **NO-GO** — 필수 실제 생성·품질·S25+·3종 처리·배포 권리 게이트 미충족 |

## 이번에 실제 실행한 경로

공식 FASHN revision `7c0f10af3f91ad4048fe9729c470a13ef905d25a`, 본체 SHA256 `d6cd38286885bc29fa487ea9383f80ffeb95862e7747c630d42c5d3c05bdd35a`를 기존 위치에서 재사용했다.
P42 ONNX 부분 그래프 SHA256 `59593a1748881b4f040b476b64f79da42ac4379f4dbb951c6af8fb385f6f632a`의 실제 6개 initializer를 사용했다. 입력 토큰은 재현 가능한 합성 수치이며 사진/착장 조건은 아니다.

| 실험 | 실행 결과 | CPU/GPU 연산 구간 | 원소별 수치 게이트 |
|---|---|---|---|
| directml01 | 16토큰 원래 ONNX GPU 통과 | 작은 그래프 기능 확인만 사용 | max abs 1.430512e-6 / 1.251698e-6 |
| linear01 | 시작 전 보호 차단 | 가용 RAM 500,178,944B < 512MiB | 모델 미실행 |
| linear02 QKV | 6912토큰, 128씩, 실제 runtime weight 입력 통과 | CPU 2.1161s / GPU 0.8612s | max abs 6.198883e-6, 위반 0 |
| linear02 linear1 | 같은 길이/분할 통과 | CPU 6.7240s / GPU 2.1504s | max abs 9.059906e-6, 위반 0 |
| chunked01 | CPU 그래프 최적화 실패 | 중복 생성 노드명 충돌 | 최초 실패 증거 보존 |
| chunked02 | 6912토큰 단일 SelfAttention 통과 | qkv CPU 4.9628s / GPU 0.8006s; attention+proj CPU 25.2840s / GPU 11.0444s | 최종 max abs 8.046627e-7 |

모든 GPU 실행은 CPU fallback을 비활성화하고 실제 profile의 Node provider가 `DmlExecutionProvider`뿐인지 검사했다. `CPUExecutionProvider`가 등록 목록에 있다는 것과 실제 CPU 연산 실행은 구분했다. 같은 rtol=1e-4/atol=1e-5 기준을 유지했다. DirectML timing에는 실제 Run 제출/결과 수신이 포함되며 warm-up/compile은 별도 기록이다. 시스템 부하가 있는 한 PC의 측정으로 전체 모델 속도를 보장하지 않는다.

새 분할 구현은 먼저 변경되지 않은 P42 ONNX와 CPU 출력을 직접 대조했다(두 공식 PE 입력: max abs 4.768372e-7 / 3.576279e-7). 독립 NumPy 참조도 통과했다. query만128개씩 분할하고 모든 K/V를 유지하여 score 최대값을 35,389,440B로 제한했다. image/garment 합동 MMDiT·FFN·조건 입력·샘플러 전체는 이 블록 검증에 포함하지 않는다.

### 재현 결함과 수정

이름 없는 Gather 노드 그룹으로 새 ONNX를 만들면 ORT 1.21 기본 최적화가 `/GatherSliceToSplitFusion/` 이름을 중복 생성해 로딩을 거절했다. 각 노드에 그래프/인덱스/연산자로 고유 이름을 부여했다. 수식·가중치·오차 기준·최적화 설정은 바꾸지 않았다. `chunked02`의 실제 ORT 로딩·원본 수치 대조·전체 길이 검사 및 집중 회귀가 통과했다.

## 메모리와 중단 판단

- PC: i5-1155G7, Intel Iris Xe, RAM 7.73GiB. WMI NPU 목록 없음. GPU의 WMI 1GiB 값은 전용 VRAM 보증이 아니며 내장 GPU는 시스템 RAM을 공유한다.
- chunked02: 총 82.531s(감독 프로세스), peak RSS 598,253,568B, peak private Commit 504,139,776B, 관측 최소 가용 RAM 370,081,792B. 종료 후 해당 프로세스 메모리 반환도 기록했다.
- linear02: 총30.14s, peak RSS345,755,648B, peak Commit314,499,072B.
- 사용자가 앱 정리를 알린 후 가용 RAM이 회복돼 작은 분할 검사를 재개했다. 마지막 전체 모델 시작 점검은 가용 RAM913,371,136B(약871MiB), free Commit9,137,262,592B였다. **P42 전체 모델 시작 기준 2GiB RAM/3GiB Commit 중 RAM 미달**이다.
- 기존 보호 기준을 낮추거나 페이지 파일·드라이버·다른 프로그램을 변경하지 않았다. 전체 FASHN을 같은 CPU 조건으로 재실행하지 않았다.
- GPU 분할 연산을 실제 전체 모델에 연결하려면 Linears/SDPA 전체 계약, 전송·메모리, CFG/샘플링/체크포인트 수치 검증이 추가로 필요하다. 기존 CPU checkpoint-002를 다른 backend로 검증 없이 이어 붙이지 않았다.

## 실제 파일과 검사

로컬 증거 루트는 기존 worktree의 형제 폴더 `experiments/P44-20261106`이다. 원본 실험 결과는 Git에 넣지 않았다.

- `P44_RESULTS.json`: 각 실행 exit/guard/메모리/해시 및 최종 시작 조건.
- `directml01`, `linear02`, `chunked01`, `chunked02`: 결과 JSON, ONNX/실제 GPU profile. 실패 폴더와 성공 폴더를 분리했다.
- 각 `*-supervisor`: 실행 소스 스냅샷, invocation, stdout/stderr, process/system 메모리. 재현 명령은 invocation의 probe 인자와 같은 의존성 경로를 사용하되 새 출력 폴더로 실행한다.
- `chunked02/gpu-output.npy`: 실제 부분 연산 결과이며 PNG가 아니다. SHA256 `07550d34bad8ab6b0f59cf6cb8a9237ef1e479553e67850ec6947059e62770fe`.
- `focused01/results.xml`: **30 passed / 0 failure / 0 error / 0 skipped**, exit0, 검사 전후 소스 해시 일치. 새 P44 코드만 검사했다. 기존 P39 Android 고유47개 결과는 소스10개 해시 대조 후 재사용했으며 새 실행으로 합산하지 않았다.
- 신규 의존성은 격리 target의 `onnxruntime-directml==1.21.0` 하나(24,031,986B wheel, SHA256 `d0f00792b05638d6ecd4dac461ba825056931ae70c041e225a5520de6b529ce0`). MIT LICENSE를 확인했고 기존 Python 환경/가중치/시스템 드라이버는 변경하지 않았다. APK에 포함하지 않았다.
- 종료 전 총괄이 기존 로컬 APK 두 파일을 다시 해시했다. Preview18605=`820f62c3c98602a0ff8dfbd1b3da3e61092444eb6d991622f72a726612eb51ce`, QA18607=`651e901d061eaeb01428fb95b61413892572fc02b44dd465a18d01a9c402f07c`, 모두 기존 기록과 일치했다. 기기 설치본/개인 데이터의 새 해시 검사는 수행하지 않았다. C: 가용 공간은 8,558,432,256B였다.

## 다음 실제 실행에 필요한 조건

1. 전체 모델용 안전 메모리 여유 확보 및 실제 전체 GPU backend 연결/동일 수치 계약 검증. 부분 시간 합산으로 정규20단계 완료 시간을 약속하지 않는다.
2. 공식 FASHN 상의/하의의 정규 출력과 자연스러움 검수. 신발은 별도 사용 권리가 확인된 조건부 생성 자산과 경계 처리/3종 일관성 검증이 필요하다.
3. Windows DirectML과 별개로 전체 Android 실행 자산·지원 연산·메모리/시간/취소 검증이 필요하다. P39 엔진을 임의로 Ready로 전환하지 않는다.
4. 새 버전 설치 승인 후 S25+에서 실제 생성→비교→저장→재열기를 검증해야 한다. 기존 Preview18605/QA18607은 P39 이전 코드이므로 이 계약의 실기기 성공 근거가 아니다.
5. 모델 본체의 Apache-2.0을 Human Parser 등 다른 구성요소의 배포 권리로 확대하지 않는다. Mobile-VTON 비상업용 자산을 정식 앱에 전용하지 않았다.

정식 요구사항을 2D 합성으로 변경하지 않았다. 새 APK·Push·CI·유료 호출·기기 설치는 없으며, 현재 조건에서 2시간 내 전체 계약 충족을 보장할 근거는 없다. 추가 구현/검증 시간을 확보하기 전 정식 출시는 강행하지 않는다.
