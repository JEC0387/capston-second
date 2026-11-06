# P48 — 별도 서버형 3종 AI 연동

기준 `1afd671d9fcc2d20424450594a902823938e3fb8`, 로컬 브랜치 `codex/fashionai-p48-server-integration-20261106`. 기존 P39/P46 온디바이스 마법봉을 보존하고 서버 모드를 별도 진입점으로 추가했다. **정식 출시 NO-GO**. 이번 작업에서는 실제 GPU 생성, 개인 사진 전송, 기기 조작, 배포, Push를 하지 않았다.

| 필수 조건 | 구현 | 실제 검증 | 차단 원인 |
|---|---|---|---|
| 생성형 서버 API | 별도 FastAPI 서비스 | 합성 입력·격리 계정 DB 자동 검사 | 운영 배포 미승인 |
| 실제 모델 연결 | 로컬 Qwen2511 CUDA worker·검증 loader | 모의 런타임 호출 계약만 | 승인 GPU·완전한 모델 번들 없음 |
| 상의 AI 생성 | 상의 참조 입력·배치 계약 | 이번 실제 생성 없음 | GPU·모델·품질 검수 |
| 하의 AI 생성 | 하의 참조 입력·배치 계약 | 이번 실제 생성 없음 | 동일 |
| 신발 AI 생성 | 신발 참조 입력·양발 생성 지시 | 실제 지원/품질 미검증 | 프롬프트는 지원 증거가 아님 |
| 3종 전체 착장 PNG | 4참조 단일 모델 호출·PNG 반환 계약 | 모의 출력만 | 실제 추론·자연스러움 미검증 |
| Android 마법봉 연동 | 명시적 별도 서버 버튼·상태 관리 | 로컬 검증 결과 아래 기록 | 기본 비활성, Preview/QA 전송 차단 |
| PNG 저장·재열기 | 기존 검증 저장/파일 재열기 재사용 | 합성 자동 검사 대상 | 실제 서버 결과·SAF 실기기 미검증 |
| S25+ 실기기 | 기존 설치본 유지 | 이번 NOT TESTED | 새 버전 설치 승인/실제 엔진 없음 |
| 상업 배포 권한 | 구성요소별 검토 기록 없으면 차단 | 공식 모델 카드만 확인 | 실제 번들/종속성·개인정보 운영 승인 |
| 정식 출시 | NO-GO | 필수 E2E 미달 | 엔진·품질·기기·운영·정식 서명 |

## 실제 변경

- `backend/outfit_vton_api.py`: 기존 `auth_api.current_user` Bearer 인증, 계정별 소유권, HTTPS, 동의/정책/이미지 검증, UUID와 원문 body SHA 고정, 영속 중복 방지, 일일 사용량/업로드 제한, 단일 worker lease, 작업 상태/취소/만료/결과 API. 승인 전 모델 접근과 업로드를 차단한다.
- `backend/outfit_vton_provider.py`, `outfit_vton_worker.py`: 기본 DisabledProvider. 승인된 로컬 번들 전체 파일 해시, 리뷰/런타임 버전, CUDA 검사 후에만 실제 `QwenImageEditPlusPipeline`을 로드한다. 원격 다운로드/공개 Space/유료 API/2D 대체 성공 없음. person/top/bottom/shoes 순서와 요청/모델 ID를 고정한다. 소유한 자식 프로세스만 정리한다.
- `backend/outfit_vton_app.py`, `outfit-api.env.example`, `outfit-vton.env.example`: 기존 운영 main 대신 독립 실행 진입점, 양쪽 기본 비활성 설정. 기존 DB 스키마·인증 경로·Railway를 변경하지 않았다.
- `ServerOutfitVtonRepository.kt`, `ServerOutfitVtonViewModel.kt`, `ServerOutfitVtonScreen.kt`: 서버별 DataAuth 자격, 준비 확인 때의 서버/계정에 동의 바인딩, 사진/3종/배치 고정, POST 전에 사진·토큰 없는 AtomicFile 작업 저널, 재시작 후 동일 ID GET-only 복구, 취소/대기 종료 구분, 결과 ID/입력 SHA/모델 SHA/PNG 검증, 이전 결과 표시, 비교/저장/재열기.
- `VirtualFittingScreen.kt`: 별도 서버 진입, 지연 카드 읽기와 드롭 무효화. 기존 온디바이스/수동/아바타 경로 보존. 사진 배치는 ContentScale.Fit의 실제 표시 영역으로 정규화한다.
- `app/build.gradle.kts`: 기본 false인 `SERVER_OUTFIT_APPROVED`. Preview/QA에서는 빌드 속성으로 켜려 해도 false. 서명·패키지·기존 버전 정책은 변경하지 않았다.
- `OnDeviceVtonImages.kt`: 기존 입력 검증을 재사용하기 위해 `validateSelection`만 private에서 internal로 변경했다. 기존 검증 본문은 그대로다. 서버 화면도 3종 입력이 유효하지 않으면 생성 버튼을 비활성화하며 준비 중 취소를 지원한다. `/config`의 404는 작업 없음과 구분해 API 미배포 안내로 표시한다.
- 검사 파일: `test_outfit_vton_api.py`, `test_outfit_vton_provider.py`, `P48ServerEntryTest.kt`, `P48ServerOutfitContractTest.kt`, `P48ServerOutfitControllerTest.kt`, `P48ServerOutfitUiTest.kt`.

검토 중 수정한 문제는 진행률 0..100/0..1 불일치, 공급자/앱의 배치 허용 범위 불일치, config 확인 후 계정/서버 변경 시 기존 동의 재사용 위험, 종료 확인 전 취소 완료 표시, worker 종료 불확실성, 최종 저널 쓰기 실패 후 PNG 잔존이다. 해당 경계를 합성 회귀로 검사한다. 실제로 개인정보가 전송/노출됐다는 보고는 아니다.

추가로 실제 `ProviderFailure`의 등록된 코드만 전달하고, 일반 예외의 임의 `code`나 원문은 전달하지 않도록 했다. 프로세스 비정상 종료 후 작업 디렉터리에 입력 파일 등이 남으면 startup/idle/새 작업 직전 `workdir_review_required`로 차단한다. 현재 소유한 활성 작업은 오탐하지 않으며 잔존 파일을 자동 삭제·재채택하지 않는다.

## API와 운영 준비

정확한 요청/응답·환경·manifest·실행 명령은 [서버 계약](../backend/OUTFIT_VTON_SERVER.md)에 있다. 라우트는 `/api/outfit-vton/config`, `/jobs`, `/jobs/{id}`, `/jobs/{id}/result`. 동일 승인 HTTPS/account origin의 reverse proxy에서 별도 단일 worker로 연결해야 한다. 이 문서는 배포 승인이 아니다.

`model_sha256`는 전체 리뷰 manifest의 digest다. 모든 파일의 개별 SHA도 manifest에 포함된다. 배치값은 사진 영역 기준의 근사 지시이며 실측 신체 치수나 정확한 메시 변형을 의미하지 않는다. 진행률 0/10/100은 접수/작업/완료 단계이지 실제 diffusion 단계 측정치가 아니다. 생성 결과에는 `quality: NOT_EVALUATED`를 유지한다.

입력은 worker 작업 폴더에서만 일시 사용하고 관리되는 종료 시 정리한다. 강제 종료·정전 후의 잔존 입력은 차단·운영자 검토 대상이며 자동 TTL 삭제를 보장하지 않는다. 결과는 운영자가 승인한 TTL에 따라 삭제하지만 정지된 서버는 재실행 전까지 삭제를 수행할 수 없다. 사진 없는 소유권/요청/사용량 tombstone은 중복 재생성을 막기 위해 유지하며 10,000개 용량에 도달하면 새 작업을 차단한다. metadata/백업 보관·아카이브 정책과 Windows NTFS ACL은 운영자가 확정해야 한다. 임의로 저널을 지워 사용량을 초기화하면 안 된다.

현재 Android 작업 기록은 자격 증명 자체가 아닌 server/token의 해시에 바인딩된다. 계정 변경뿐 아니라 토큰 교체도 기존 작업의 자동 조회를 차단한다. 안전한 동일 계정 복구 계약을 별도 검증하기 전에는 이를 우회하지 않는다. 프로세스 종료 후 미저장 원본 사진은 복구하지 않으며 결과만 조회할 경우 원본 비교 불가를 표시한다. 저장은 사용자가 선택한 SAF 파일로 수행하므로 실제 문서 공급자의 부분 쓰기/취소/재열기와 서버↔휴대폰 E2E는 실기기 인수가 필요하다.

## 로컬 검증 결과

- **Backend 최종 `backend06`: 60 PASS**, 실패/오류/건너뜀 0, pytest 6.71초, exit 0, 실행 전후 소스 변경 0. API 31건과 provider/worker 29건. Bearer 인증은 격리 SQLite에서 확인하고 생성에는 합성 provider/모의 런타임을 사용했다. Starlette/AnyIO deprecated alias 경고 1건. 실제 모델 로드·GPU 추론 증거가 아니다.
- 앞선 `backend03` 49 PASS 이후 잔존 파일·오류 코드 회귀를 추가했다. `backend04`는 59 PASS/1 FAIL: 오류 코드 케이스의 테스트 helper에서 HTTP 응답 상태를 기록하기 전에 `status` 접근으로 KeyError가 발생했다. HTTP 상태 진단을 보강한 `backend05` 해당 4건 및 `backend06` 전체에서 재현되지 않았다. **최초 실패의 원인은 미확정이며 메모리 때문이라고 단정하거나 해결 완료로 바꾸지 않는다.** 생산 코드 변경으로 이 실패를 감춘 것이 아니다.
- 첫 Python 실행은 최종 결과 없이 중단, 다음 sandbox 실행은 240초 timeout. 성공으로 계산하지 않는다. `disabled-entry-smoke02.log`에서 기본 비활성 앱의 안전한 import를 확인했다(모델 런타임 import·저장 폴더 생성 없음).
- **Android 최종 `focused04`: 32 PASS**, 실패/오류/건너뜀 0, `BUILD SUCCESSFUL`, exit 0, 실행 전후 소스 변경 없음. main/test Kotlin 컴파일 성공. P48 진입 2·계약 7·상태 7·UI 1 = 신규 17건, 기존 P39 handoff 3·이미지 12 = 15건. `--offline`, 단일 Gradle worker, compile heap 768MiB, test heap 384MiB와 클래스별 JVM 분리를 사용했다. 테스트 기대값·검증 조건은 낮추지 않았다.
- `focused04`의 요청한 별도 XML 경로 대신 Gradle 표준 `app/build/test-results/testDebugUnitTest`에 실제 보고서가 생성됐다. 6개 클래스의 테스트 시각과 파일 수정 시각이 실행 시작/종료 안에 있는지 확인 후 `focused04/recovered-xml`에 별도 복사했다. 경로 문제 때문에 테스트를 재실행하지 않았다. `report-recovery.json`에 원본 경로·시각·SHA를 기록했다. 로그상 총 경과 3시간 22분은 이 PC의 해당 실행 경과이며 앱/모델 성능 수치가 아니다.
- Android 초기 `focused01`은 캐시 lock 접근 거부. `focused02`는 XML 쓰기 실패로 전체 exit 1이었지만 실제 생성된 9개 XML은 49 PASS였다. 이 중 변경되지 않은 P36 드래그 7/P39 controller 6/UI 5/P46 availability 4의 **22건은 별도 회귀 증거로 재사용**한다. 이를 최종 32건과 단일 실행인 것처럼 합치지 않는다. 누락된 P39 handoff는 focused04에서 검증했다. `focused03`은 compile heap 512MiB에서 명시적인 Kotlin OOM으로 실패했으며 테스트가 실행되지 않았다. focused02의 XML 오류 원인은 확정하지 않았다. 기존 실패 로그는 모두 보존했다.

Android와 API의 요청/응답 계약을 교차 검토했지만, 이 검사는 Android 모의 gateway 및 ASGI TestClient 기반이다. **실제 Android↔배포 서버↔GPU 전체 네트워크 E2E가 아니다.** 합성 저장·재열기 자동 검사가 실제 문서 공급자/기기에서의 저장 성공을 대신하지 않는다.

집중 검사에 사용한 명령(동일 결과를 다시 만들 필요 없음):

```text
python -m pytest backend/tests/test_outfit_vton_api.py backend/tests/test_outfit_vton_provider.py -q
gradlew.bat :app:testDebugUnitTest -PfashionPreview=true -PfashionDeviceQa=true --offline --no-daemon --max-workers=1 --tests com.jeiu.fashionproject.P48* --tests com.jeiu.fashionproject.P39FittingHandoffTest --tests com.jeiu.fashionproject.P39OnDeviceImagesTest
```

실제 실행은 기존 격리 Python 환경과 private runner의 환경/heap/init-script 설정을 사용했다. 저메모리 PC에서는 두 명령을 병렬 실행하지 않는다. 이번에는 전체 Android/Backend 회귀, Lint, CI, APK 빌드·설치·서명 재검증을 실행하지 않았다. 패키지/서명 정책을 변경하지 않았지만 P48 코드가 들어간 새 APK의 설치 호환성을 실제 검증했다고 주장하지 않는다.

증거 루트: `C:\Users\USER\Documents\ChatGPT\capston\experiments\P48-20261106`; Android 로그/XML: worktree의 `fashion_app/build/p48-private`. 모두 Git에서 제외한다. 검사 실행 전후 소스 SHA와 종료 상태를 함께 기록한다. 전체 CI·APK 빌드·기기 설치는 수행하지 않는다.

## 모델과 남은 의존성

[Qwen-Image-Edit-2511 공식 카드](https://huggingface.co/Qwen/Qwen-Image-Edit-2511)는 Apache-2.0 및 다중 이미지 편집 API를 제공한다. [Diffusers 공식 API](https://huggingface.co/docs/diffusers/en/api/pipelines/qwenimage)는 `QwenImageEditPlusPipeline` 이미지 목록 입력을 설명한다. 이것은 4장/3품목 품질, 신발 정확도 또는 S25+ 실행 검증이 아니다. [텍스트 인코더 카드](https://huggingface.co/Qwen/Qwen2.5-VL-7B-Instruct)도 Apache-2.0 표기지만 실제 번들 및 모든 런타임·종속 자산의 배포 검토가 필요하다.

FASHN v1.5 기존 자산과 실험을 보존했다. 신발 공식 미지원 경로를 3종 엔진으로 채택하지 않았다. P46 공개 Qwen2.1 연구 PNG도 그대로 보존했으며 P48 앱 생성 결과로 사용하지 않았다. 신규 GPU/모델/서비스를 확보했다고 가정하지 않는다.

| 남은 조건 | 해결 책임 | 필요한 증거 |
|---|---|---|
| 승인 GPU·정확한 런타임·전체 모델 번들 | 인프라/모델 운영 담당 | 고정 파일/패키지·메모리·실제 로드/추론/취소 검사 |
| 4참조/3종 품질 | AI·QA 담당 | 여러 승인 입력의 새 결과, 의류·얼굴·손·배경 검수 |
| 서버 방식·사진 처리·보관 정책 | 클라이언트/개인정보 운영 담당 | 처리자/접속/보관/삭제/동의·TLS·접근권한 승인 |
| 실제 앱 E2E | Android·QA 담당 | 승인된 새 QA 버전에서 생성→비교→저장→재시작→재열기 |
| 정식 서명/배포 | Release 키 관리 담당 | 승인된 키·운영 구성·설치 인수 |

기존 Preview18605/QA18607 및 납품본을 수정하지 않았다. 보존 APK의 실제 SHA-256을 확인했다: Preview18605 `820f62c3c98602a0ff8dfbd1b3da3e61092444eb6d991622f72a726612eb51ce`, QA18607 `651e901d061eaeb01428fb95b61413892572fc02b44dd465a18d01a9c402f07c`. 상세 위치/크기는 private `retained-apks.json`에 있다. 이번에는 기기 파일을 새로 해시 비교하지 않았으므로 전체 사용자 데이터 보존을 새 실측으로 보증하지 않는다. DataAuth/DbSync/복구 저널/운영 main/기존 Workflow는 수정하지 않았다. 사진·키·토큰·가중치·실행 로그·APK는 이 커밋 대상에 포함하지 않는다.

납품 가능한 것은 기본 차단 상태의 통합 코드·집중 자동 검증·실행 계약이다. 새 P48 PNG, 새 APK, GPU 운영 서버, 실기기 검증 및 정식 출시 완료는 납품 범위에 아직 없다. 로컬 P48 브랜치에 `[skip ci]` 커밋으로 보존하며 Push/Merge는 수행하지 않는다. 최종 커밋 SHA와 clean 여부는 커밋 후 private receipt와 사용자 보고에 기록한다.

## 전체 변경 파일 목록 (20개)

```text
backend/OUTFIT_VTON_SERVER.md
backend/outfit-api.env.example
backend/outfit-vton.env.example
backend/outfit_vton_api.py
backend/outfit_vton_app.py
backend/outfit_vton_provider.py
backend/outfit_vton_worker.py
backend/tests/test_outfit_vton_api.py
backend/tests/test_outfit_vton_provider.py
docs/P48_SERVER_OUTFIT_INTEGRATION_20261106.md
fashion_app/app/build.gradle.kts
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/OnDeviceVtonImages.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/data/ServerOutfitVtonRepository.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/ServerOutfitVtonScreen.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/VirtualFittingScreen.kt
fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/ServerOutfitVtonViewModel.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/P48ServerEntryTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/P48ServerOutfitContractTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/P48ServerOutfitControllerTest.kt
fashion_app/app/src/test/java/com/jeiu/fashionproject/P48ServerOutfitUiTest.kt
```
