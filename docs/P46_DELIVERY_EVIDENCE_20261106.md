# P46 실제 결과와 출시 차단

**정식 인수 NO-GO.** 상의·하의·신발이 함께 생성된 새 연구 PNG와 P45 대비 시각 개선은 확보했다. 사용자의 개인 사진을 S25+ 마법봉에서 생성·비교·저장·재조회하는 최종 계약 흐름은 완료하지 못했다. 외부 연구 데모를 온디바이스 엔진이나 정식 서비스로 바꾸지 않았다.

## 실제 수행

| 항목 | 상태 | 증거 / 제한 |
|---|---|---|
| 세 품목 신규 생성 | 실제 외부 연구 실행 PASS | 고정 공개 입력4장, 새 생성1회, PNG768×1024 |
| P45 품질 개선 | 제한된 관찰 PASS | 굽힌 다리/발 방향과 패딩 퀼팅/소매 볼륨 개선 |
| 원본·상품 정밀 보존 | 미충족 | 관절 위치·얼굴·손·상품 세부·배경의 정확한 보존 미입증. 단일 예제이며 통계 평가 아님 |
| PC PNG 저장·재열기 | PASS | 실제 디코딩·해시·독립 육안 검수 |
| Android 마법봉 | 코드 수정 / 집중 검사15개 PASS | 동적 엔진 준비 상태 경합2종 재현 후 단일 상태 검증/사용으로 수정. 실제 모델 연결·실기기 검증 아님 |
| 외부 작업 시간 초과 | 코드 구현·로컬 시험 | 실제 SDK의 Future 대기 종료 후 worker 잔존 확인, 별도 자식 프로세스 종료·대기 보호 |
| S25+ | BLOCKED | ADB 읽기 조회25초 시간 초과, 기기 선택·설치·앱 데이터 접근 없음 |
| 온디바이스 세 품목 엔진 | 미완료 | 현재 확보 자산에 배포 가능한 전체 Android 모델/런타임·신발 경로 없음 |
| 상용/운영/Release | BLOCKED | 연구 모델 권한, 운영 실행 방식 승인, 정식 서명·E2E 미확보 |

## 신규 PNG와 원인별 개선

로컬 증거 루트: `C:/Users/USER/Documents/ChatGPT/capston/experiments/P46-20261106/`.

- `pose-anchored-trial-01/new-generated-outfit.png`
- SHA256 `77bdd3642fe2bfa4a7af16681084b44322385e55c1c5547529e8a7d5b21ecdc9`, 1,507,028bytes, 768×1024.
- 공식 `Qwen/Qwen-Image-2.1` 공개 연구 Space. 사용자 계정·키·결제 없이 승인된 공개 입력만 전송. 개인 자료 전송0, 이번 생성 submit1, 자동 재시도0.
- P45와 같은4입력 해시·seed42·출력 크기. P45의 자세 변화에 대응해 이미지1의 관절/프레이밍을 우선하고, 이미지2의 인물 자세를 복사하지 않도록 지시를 강화했다. 단순 동일 조건 재시도가 아니다.
- 2026-11-06 KST06:45:43.173 제출→06:46:17.049 수신→06:46:20.427 저장. 준비/업로드 포함97.578초; 제출→수신33.876초는 순수 GPU 시간이나 S25+ 속도가 아니다.
- `execution.json`, `experiment-plan.json`, `quality-review.json`, `PROVENANCE_NOTES.json` 보존. Space **서버 코드** revision은 `5694809f4c56a4e8c40787b5392cecac36493c5d`. 실제 로드된 가중치 commit/hash·GPU·VRAM은 API 미제공으로 미확인이다.
- 실제 실행은 P45 commit `1a1e9dc0268c95af1863b2da3f3cb303a9ef3365` 클라이언트와 별도 `pose_trial_once.py`였다. 이후 추적 가능한 `backend/p46_pose_trial.py`에 동일 prompt SHA를 옮기고 기본값을 네트워크 없는 preflight로 바꿨다. 이 포장 변경 후 새 생성하지 않았다.
- P45 PNG는 원래 SHA `b3fe8f29106e83e259337543bc801d85c95912af8ed09140c24b0d23a7233682` 유지. P45 journal/기록/이미지를 지우거나 이번 결과로 덮어쓰지 않았다.

## 변경 코드와 검증

1. `OnDeviceVtonViewModel.kt`: `start()`가 readiness를 두 번 읽던 문제 수정. Ready→Unavailable에서 ClassCastException, Ready-A→Ready-B에서 검증하지 않은 모델 대입을 RED로 확인했다. 검증·입력 준비에 같은 불변 상태를 사용한다. 현재 기본 엔진은 계속 Unavailable이며 native 실행 실패를 성공으로 바꾸지 않는다.
2. `P46OnDeviceAvailabilityTest.kt`: 해당2개 실패 + 미준비/잘못된 모델 차단2개. RED4개 중2 FAIL·2 PASS, 원래 기준 유지. 이는 동적 엔진 경계 검사이며 설치된 S25+에서 발생한 오류라는 뜻이 아니다.
3. `backend/p46_research_supervisor.py`: 명시적 로컬 연구 실행의 자식 프로세스만 관리. timeout/cancel 시 해당 handle 종료·wait, 원격 상태 UNKNOWN, 환급/재시도0. P45의 이미 사용된 journal은 실행 전 차단한다. HTTP 라우트·Android 엔진·멀티유저 서버가 아니다.
4. `backend/tests/test_p46_research_supervisor.py`: 로컬 프로세스 보호15개 + 실제 SDK Future/close 동작1개. 최종 고유16개 PASS. 초기 process.wait 시연 사례는 SDK 사례로 교체했고 성과 수에 이중 합산하지 않았다.
5. `backend/p46_pose_trial.py`, `backend/tests/test_p46_pose_trial.py`: 실제 실행한 프롬프트 해시 고정, 기본 preflight, 생성 명시 opt-in, 고정 P46 증거가 있으면 중단. P45 예산·서비스 할당을 우회하지 않는다.
6. `backend/p45_public_demo_trial.py`: prompt를 함수 인자로 전달해 P46 프로필 import가 P45 전역 프롬프트를 바꾸지 않게 했다. 고정 입력·토큰 차단·무료 연구 전용 경계는 유지. 프로필1개+P45 결과 처리7개 PASS.

Backend 최종 고유 검사24개 PASS: `supervisor-tests-01.xml`의 변경되지 않은15개, `supervisor-sdk-02.xml`의 교체 SDK1개, `profile-regression-01.xml`8개. 실제 공개 생성 성공은 이 모의/로컬 검사 수와 별도로 위 PNG/실행 기록만으로 판단한다.

Android 증거는 `fashion_app/build/p46-private/red01`, 수정 후 `green01`에 분리했다. GREEN은 2026-11-06 KST07:11:07 종료, exit0 / BUILD SUCCESSFUL(13분48초), 실행 중 소스 해시 동일이다. 이번 실행에서 생성된 최종 XML은 다음과 같다. 전체 CI·APK 빌드·실기기 검사는 아니다.

| 클래스 | 통과 | 실패/오류/건너뜀 |
|---|---:|---|
| P46OnDeviceAvailabilityTest | 4 | 0/0/0 |
| P39OnDeviceControllerTest | 6 | 0/0/0 |
| P39OnDeviceVtonUiTest | 5 | 0/0/0 |

총15개 PASS. RED의 반복 검사를 합산하지 않는다. 새 검사4개는 동일 테스트 소스로 수정 전2개 실패→수정 후4개 통과를 확인했다. 기존11개는 중복 실행·취소·오류 및 UI 경계의 관련 회귀 검사다.

## 정확한 차단과 다음 조건

- 기존 FASHN 본체971,813,808params /1,943,668,048bytes는 존재하지만 신발 공식 미지원이다. 2/20 CPU·DirectML 단일 어텐션 성공을 전체 Android 추론으로 확대하지 않는다. 메모리 보호를 낮추거나 전체 CPU 추론을 다시 시작하지 않았다.
- 공식 Mobile-VTON의3.76GB 체크포인트·CC BY-NC-SA4.0 상태를 재확인했으나, 현재 프로젝트의 배포 가능한 Android 전체 자산·신발 지원·S25+ 실행 증거는 없다. 다운로드하지 않았다.
- [Qwen 연구 라이선스](https://huggingface.co/Qwen/Qwen-Image-2.1/blob/main/LICENSE)는 상용 사용에 별도 권리를 요구한다. [무료 ZeroGPU](https://huggingface.co/docs/hub/spaces-zerogpu)의 연구 체험을 무제한 정식 운영 서비스로 사용할 수 있다고 보증하지 않는다.
- 정식 개인 사진 흐름에는 **배포 권한이 있는 전체3종 엔진과 실행 환경**이 먼저 필요하다. 외부 실행을 택한다면 사용자 사진 전송/보관·인증·운영 방식 승인이 별도로 필요하다. 현재 승인으로 그 조건이 충족됐다고 볼 수 없다.
- 권한과 자산이 확보된 뒤 전체 추론 벤치마크, 여러 의류/자세 품질 검수, 승인된 새 QA APK의 S25+ 요청→비교→저장→재조회가 필요하다. 지금 자료만으로 완료 날짜나 온디바이스 속도를 약속할 수 없다.

## 보존

독립 로컬 브랜치 `codex/fashionai-p46-delivery-engine-20261106`, 시작 HEAD `1a1e9dc0268c95af1863b2da3f3cb303a9ef3365`. 원격 Git 작업·Push·CI·운영 DB·Railway·기기 설치·사용자 자료 접근 없음. P38~P45 및 기존 Preview18605/QA18607 납품 파일을 변경하지 않았다. 이번에 기기 데이터 해시를 비교한 것은 아니다. 새 APK를 만들지 않았으며 이번 코드 수정은 설치본에 미포함이다.

실제 하위 담당4명+총괄, 동시 최대4명 범위에서 AI/모바일·Android·Backend·품질/기기 검수를 분담했다. 동일 파일 편집과 Gradle 실행은 겹치지 않았다. 신규 이미지·로그·모델·기기 식별값·키는 Git/APK에 포함하지 않는다.
