# P45 — 결제 없는 4참조 신규 생성 실제 결과

**공식 공개 연구 데모에서 새 상의+하의+신발 이미지 1장 생성·PC 저장/재열기 성공. 제품 출시 NO-GO.** 앱에서 시작한 요청이 아니며 Android/S25+ 수신·온디바이스 추론은 미검증이다.

## 실제 수행과 품질

| 항목 | 결과 / 근거 |
|---|---|
| QwenCloud Turbo | 사용자 확인에 따라 무료 미지원·계정 미확보로 중단. 호출0회 |
| 대체 실행 | 공식 `Qwen/Qwen-Image-2.1` 공개 ZeroGPU Space, 익명·키 없음·가입/결제 없음 |
| 서버 소스 | revision `5694809f4c56a4e8c40787b5392cecac36493c5d`; 공개 API 계약·RUNNING 확인 |
| 입력 | 고정된 공개 예제 인물·분홍 패딩·은색 메리제인 + Met 공개 도메인 크림 바지, 4개 SHA 고정. 개인 자료0 |
| 신규 생성 | 클라이언트 생성 submit1회, 자동 재시도0. prepare placeholder는 결과에서 제외 |
| 시각 | 세 의류가 한 장에 생성되고 기존 옷/운동화는 육안상 교체됨. 자연스러운 연구 시연 수준 |
| 엄격한 품질 | **FAIL**: 다리 자세·손/상체 프레이밍·배경 정렬 변경, 패딩 퀼팅/볼륨 불일치. 얼굴·손·신발 세부 보존 부분 확인 |
| Android/S25+ | 이번 생성/표시/저장/재열기 미검증. 기기 접근·설치 없음 |
| 정식 사용 | 연구용 모델/데모이며 상용 권리·운영 서비스 권한 미확보. NO-GO |

공식 출처: [Space](https://huggingface.co/spaces/Qwen/Qwen-Image-2.1), [연구용 라이선스](https://huggingface.co/Qwen/Qwen-Image-2.1/blob/main/LICENSE), [무료 ZeroGPU](https://huggingface.co/docs/hub/spaces-zerogpu), [Met 공개 도메인 입력](https://www.metmuseum.org/art/collection/search/157909). 무료 연구 체험은 영구 무료 운영 서비스/상업 배포 허가가 아니다. 단일 이미지 입력의 다른 공식 데모는 4참조 요구를 충족하지 않아 사용하지 않았다.

## 실제 파일과 시간

실험 루트: `C:/Users/USER/Documents/ChatGPT/capston/experiments/P45-20261106/`

- 신규 PNG: `official-research-trial-01/new-generated-outfit.png`, 768×1024.
- PNG SHA256: `b3fe8f29106e83e259337543bc801d85c95912af8ed09140c24b0d23a7233682`.
- `execution.json`: 요청 지문, 준비/제출/수신/저장 시각, 파일 해시, PC 재열기 PASS.
- KST 제출06:20:07.509, 수신06:20:40.585, 저장06:20:43.672. 제출→수신33.1초는 순수 GPU 추론 시간이 아니다. 메타데이터/업로드/준비 포함 전체88.687초.
- GPU 실제 모델·VRAM·순수 추론 시간은 제공되지 않아 미확인. 결제 수단·계정·유료 API를 사용하지 않았다.
- `quality-review.json`: 독립 육안 검수. `one-shot.sqlite3`: 영구1회 기록. 새 폴더/DB로 바꿔 한도를 우회하지 않는다.
- `public-inputs-20261106-060727/INPUT_MANIFEST.json`: 출처·권한 범위·크기·해시. 역사적 아동 바지는 시각 참조이며 실측 핏 근거가 아니다.
- `FINAL_EVIDENCE_INVENTORY.json`: 실행 코드/결과 해시·SDK 버전. 이미지·로그·DB는 Git/APK 제외.

## 변경 파일과 역할

| 파일 | 구현 |
|---|---|
| `backend/p45_public_demo_trial.py` | 별도 CLI 연구 어댑터. 공개 prepare→generate, 같은 세션 상태, 토큰 없음, enhancer OFF, 최대1회, 오류 UNKNOWN/no-retry, 정확한 HTTPS 출처·무리디렉션·16MiB 제한, 실제 decode→PNG→재열기 |
| `backend/p45_public_inputs.py` | 고정4파일명·SHA·역할·불변 바이트. 임의 이미지/URL/링크 거부 |
| `backend/p45_trial_journal.py` | SQLite 원자적1회 예약, 동시성/재시작/취소/실패 후 중복 차단. RECEIVED와 품질 분리 |
| `backend/requirements-p45-research.txt` | 격리 실험용 SDK 버전. production requirements 불변 |
| `backend/tests/test_p45_public_demo_trial.py` | 공개 계약·placeholder 거부·PNG 변환/재열기/덮어쓰기 방지 |
| `backend/tests/test_p45_public_inputs.py` | 임의 입력·경로·결과 URL 거부 |
| `backend/tests/test_p45_trial_journal.py` | 상태·경쟁 예약·재시작·실패·미확인 상태 회귀 |
| `fashion_app/app/src/main/java/com/jeiu/fashionproject/data/LocalVtonRepository.kt` | 저장 이미지 읽기가 0바이트로 정지할 때 즉시 오류로 종료하는 한 줄 |
| `fashion_app/app/src/test/java/com/jeiu/fashionproject/P45SavedImageReadTest.kt` | 비정상 스트림2종, 부분 읽기/EOF/8MiB/취소 회귀 |
| `docs/P45_QWEN_CONTRACT.md`, `P45_PUBLIC_INPUTS.md`, `P45_SECURITY_REVIEW.md`, 이 문서 | 공식 계약·권한·검수·실행 증거 구분 |

비정상 제공자는 합성으로만 재현했다. 실제 S25+에서 발생했다고 주장하지 않는다. P39 온디바이스 엔진에 외부 연구 데모를 등록하지 않았다. 품질·상용 권리가 미충족이므로 FastAPI 운영 라우트/마법봉 준비 완료 상태도 활성화하지 않았다. 이 연구 어댑터는 앱 인증·멀티유저 격리 서버를 대신하지 않는다.

## 집중 검증

- Python journal22 PASS (`journal-tests-01.xml`).
- 공개 입력·결과 어댑터41 PASS /1 SKIP (`adapter-tests-01.xml`). Windows 계정의 심볼릭 링크 생성 권한이 없어 해당 검사만 SKIP, 링크 차단 구현 유지.
- Android RED: 예상한0-progress2개 FAIL,4개 PASS. `fashion_app/build/p45-private/red02` 보존.
- Android GREEN01: 수정6개+P39 저장/재열기 화면5개 **11 PASS**, sourcesUnchanged=true.
- 이미지 회귀 실제 클래스는 `P39OnDeviceImagesTest`. 첫 filter 오타로 누락돼 이 클래스만 `green02-images`에서 별도 실행: **12 PASS**, sourcesUnchanged=true. 최종 Android 집중 검사 합계는 서로 다른23개 PASS이며 RED의 반복 실행 수는 합산하지 않는다. 두 GREEN 모두 BUILD SUCCESSFUL.

재현: 격리 venv에서 `python -m pytest tests/test_p45_trial_journal.py tests/test_p45_public_inputs.py tests/test_p45_public_demo_trial.py -q`. 연구 CLI의 `--inputs <고정 공개 폴더> --evidence <기존 시험 폴더>`는 네트워크 없는 preflight. 생성 플래그는 `--execute-public-research-once`이며 기존 journal은 재제출 불가. 이미 성공한 결과 확인을 위해 새 생성하지 않는다.

## 보존·남은 조건

브랜치 `codex/fashionai-p45-free-quota-adapter-20261106`, 시작 HEAD `8dc34b5dd66419fce142ed94a7c76af9359942f4`. 원격 조회/Push/CI/기기 설치 없음. P38~P44 자산 삭제·수정 없음.

로컬 Preview18605 SHA `820f62c3c98602a0ff8dfbd1b3da3e61092444eb6d991622f72a726612eb51ce`, QA18607 SHA `651e901d061eaeb01428fb95b61413892572fc02b44dd465a18d01a9c402f07c` 재확인·기존 기록과 일치. 기기 데이터는 접근하지 않았으므로 신규 데이터 보존 비교를 주장하지 않는다. 이번 Android 수정은 설치본에 미포함. 새 APK·전체 CI를 만들지 않았다.

남은 조건: 정식 사용 권리, 지속 가능한 무료 또는 별도 승인 운영 자원, 자세/무늬/배경 품질 개선과 다양한 입력 검수, 인증된 별도 외부 엔진 및 동의/보관 정책, Android/S25+ 요청→표시→저장→재열기 검증. 온디바이스 실제 생성은 계속 미완료다. 상의·하의·신발 요구를 축소하거나2D로 대체하지 않았다.
