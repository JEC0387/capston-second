# P42 — 신발 생성 경로와 Android 마법봉 보존 검수

2026-11-06 KST. 이 문서는 P42의 독립 읽기 전용 검수다. Android 실행 코드·개인 데이터·기기·APK를 변경하지 않았다. 신규 모델 다운로드·외부 추론·유료 호출·전체 CI도 수행하지 않았다. PC FASHN 실험 결과는 P42 총괄 기록을 우선하며, 여기의 자료 검토를 실제 신발 생성 또는 S25+ 실행 성공으로 계산하지 않는다.

## 신발 후보를 실행할 수 있는지 확인한 결과

| 후보 | 확인한 실제 공개 자료 | 현재 실행·출시 판단 |
|---|---|---|
| FASHN v1.5 | P40/P41 고정 공식 코드·체크포인트, 상의·하의·원피스 범주 | 신발 조건 생성은 공식 지원 범위 밖이다. 상의/하의 성공을 3종 전체 지원으로 등록하지 않는다. |
| ShoeFit | 공식 연구 페이지는 신발 이미지·인물·마스크·발 자세를 사용하는 생성 구조를 설명한다. 공개 페이지에서 실행 코드/가중치 링크는 확인되지 않았다. 데이터는 신청·심사 후 비상업 학술 연구용 제공이다. | 자료 신청·라이선스 동의를 임의 진행하지 않았다. 실행 자산·상용 배포 권한·Android 경로 미확보. |
| Oxygen-TryOn | 공식 저장소는 신발 포함 다중 의류 참조를 설명하지만 inference code/model weights가 로드맵에 남아 있고, 표시된 파일은 문서·이미지·LICENSE·README다. 저장소에는 Apache-2.0 표기가 있다. | 저장소 라이선스만으로 아직 공개되지 않은 모델 자산의 권리를 확정하지 않는다. 실제 가중치/실행기 미확보, Android 미검증. |
| FLUX.2-klein-4B | 개발사 공식 공개 가중치와 다중 참조 이미지 편집 코드, Apache-2.0 라이선스가 존재한다. | 합법적 **일반 이미지 편집 연구 후보**지만 신발 전용 품질/좌우 발/기존 신발 제거/착장 일관성은 실행하지 않았다. 현재 PC에서 안전하게 바로 실행할 후보로 선정하지 않았다. |

ShoeFit의 마스크 오류·작은 로고 한계와 자료 접근 조건은 [공식 연구 페이지](https://colorful-liyu.github.io/ShoeFit/)를 확인했다. Oxygen의 공개 파일·로드맵·지원 주장은 [공식 저장소](https://github.com/OxygenVision/Oxygen-TryOn)를 확인했다. 저자의 예제 이미지를 우리 모델 실행 결과로 사용하지 않았다.

FLUX.2-klein-4B는 [개발사 코드](https://github.com/black-forest-labs/flux2)와 [실제 모델 라이선스](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B/blob/main/LICENSE.md)를 확인했다. 9B/Dev의 비상업 라이선스를 4B와 혼동하지 않는다. 공식 [model_index.json](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B/blob/main/model_index.json)은 Qwen3 text encoder·Flux2 transformer·VAE가 함께 필요함을 보여준다.

현재 공식 파일 목록의 반올림 표시 크기는 [transformer 7.75GB](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B/tree/main/transformer), [text encoder 8.05GB](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B/tree/main/text_encoder), [VAE 168MB](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B/tree/main/vae)다. 합계 약 **15.97GB**, tokenizer/라이브러리/실행 산출물 제외다. 루트 전체 23.7GB는 중복 형식도 포함하므로 최소 실행 용량으로 사용하지 않았다. 파일을 다운로드하지 않아 실제 바이트·SHA는 새로 확보하지 않았다.

개발사 [로컬 실행 안내](https://help.bfl.ai/articles/7592221790-how-do-i-generate-quickly-with-flux-2-klein)는 4B에 약13GB VRAM을 제시한다. 이는 개발사 경로의 요구치이며 Android 최소 메모리나 S25+ 측정치가 아니다. 현재 PC의 RAM7.73GiB·CUDA 없음·가용 디스크 약9.9GB라는 총괄 측정에 비추어 **공식 비양자화 전체 자산을 안전하게 확보하고 같은 경로로 실행할 수 없다는 판단**이다. 임의 양자화 품질·속도, CPU offload 성공, S25+ 실행 불가능을 추측해 확정하지 않는다. 이 후보를 받기 위해 기존 모델·로그·APK를 삭제하지 않았다.

다음 신발 실험은 실행 자산/의존성별 권리와 자원 조건이 충족될 때만 한다. 허가된 비개인 인물+신발 참조 → 실제 이미지 조건 추론 → 좌우 발 및 기존 신발 제거 → 바지 경계/얼굴/배경/상품 무늬 비교 → 전체 착장 입력과 일관성 검사 → Android 그래프 수치 대조와 승인된 S25+ 실행 순서다. 일반 편집 모델이 참조 이미지를 받는다는 사실만으로 신발 피팅 PASS를 선언하지 않는다. 현재 **생성형 신발 BLOCKED / S25+ NOT TESTED**다.

## 현재 Android 계약 확인

다음 소스와 대응 P39 테스트를 읽었다. 새 기기·자동 실행 검사를 했다는 뜻은 아니다.

| 계약 | 확인한 코드 | 판단 |
|---|---|---|
| 마법봉 경로 | `VirtualFittingScreen.kt`의 `openOnDevice`, `OnDeviceVtonScreen` 진입 | 기존 수동·외부/자체 서버 진입을 온디바이스 성공으로 바꾸지 않는다. |
| 입력 고정 | `DeviceVtonSelection`, `DevicePreparedJob`, `DeviceImage`, `DeviceModelSpec` 방어적 복사·해시 | 실제 제출한 이미지 바이트·모델·3종 역할에 결과를 결합한다. |
| 미준비 차단 | 기본 `UnavailableDeviceVtonEngine`, `issueFor()`의 세 역할 필수 검사 | FASHN PC PNG를 반환하거나 2D 대체 결과를 생성하는 경로 없음. |
| 중복·취소·시간 초과 | `OnDeviceVtonViewModel`의 operation 잠금·취소 요청·종료 합류 | 자동 재시도 없음. 타임아웃만으로 native 작업 종료를 단정하지 않음. |
| 이전 결과 분리 | 선택 변경 시 `selectionChanged`, `resultMatches`, `resultFromPreviousAttempt` | 의류 변경/실패 이후 이전 결과를 새 결과로 표시하지 않음. |
| 결과·저장 | 작업/입력/모델/역할 대조, PNG 디코딩·CRC, 입력 그대로의 응답 거절, `DeviceSaveTicket` | 저장 도중 선택 변경에도 당시 결과 바이트 유지. PNG 검증이 생성형 출처·자연스러움의 증명은 아님. |
| 저장 재열기 | `OnDeviceVtonImages.saveExact`, `LocalVtonRepository.openSavedImage`, 저장 이미지 안내 | 사용자가 선택한 파일만 열며 재열기를 새 생성으로 표시하지 않음. |
| 외부 전송·사용자 데이터 | 온디바이스 경계는 HTTP 입력/원격 fallback 없이 로컬 URI만 수용 | 앱 전체 네트워크 미사용이나 클라우드 content provider까지 통제한다고 주장하지 않음. 새 데이터 보존 해시 검사는 수행하지 않음. |

P39의 고유47 PASS/Lint 기록은 해당 코드의 기존 검증 근거로만 재사용한다. Android 변경이 없어 전체 빌드/테스트를 반복하지 않았다. 새로 재현된 production 결함은 찾지 못했다.

실제 native 어댑터 연결 전에 남는 두 경계는 P41에서도 기록됐다. `start()`의 availability 검증/모델 선택은 미래 동적 엔진에서 한 스냅샷으로 묶어야 하며, ViewModel 인스턴스 잠금 외에도 실제 런타임 전역의 단일 실행/취소 종료 합류가 필요하다. 현재 고정 Unavailable 구현에서는 그 경로에 도달하지 않으며 이번에 unrelated 앱 변경을 넣지 않았다.

앱 종료 시 미저장 결과/진행 작업 복원은 지원하지 않는다. SAF 저장은 실패/취소 때 부분 파일이 남을 수 있어 성공으로 표시하지 않는다. 새 모델이 생기더라도 이 제한·출처 검증·동의·사용자 명시 저장을 유지해야 한다. Preview18605/QA18607에서 P39 또는 P42 엔진을 실행했다는 주장은 하지 않는다.

**P42 신발·Android 판정: 코드는 기존 안전 경계를 유지하지만, 3종 실제 생성 모델·Android 실행 자산·실기기 품질 증거가 없으므로 최종 마법봉 생성형 기능은 미완료다.**
