# P15 공식 예제 전용 앱 연결

## 범위와 두 엔진

기준 원격 P14 HEAD `03788c922db80139c07b6bba4bcfbf2a250fc9c5`를 fetch하여 확인했다. 별도 `codex/fashionai-p15-app-vton-e2e-20261104` 브랜치 및 `C:\Users\USER\Documents\ChatGPT\capston\p15-app-vton-20261104` worktree에서 진행한다. 기존 P12~P14 및 미추적 파일을 보존한다. P14 실제 호출 스크립트와 PNG는 기존 `p14-demo-20261104/backend/local_vton_private/p14-evidence/`에서 확인했다. 기존 PNG를 새 결과로 사용하지 않는다.

- `fashn-vton-1.5-local`: 기존 인증된 자체 GPU 서버. 변경하지 않았다. GPU 실행 환경은 아직 확보되지 않았다.
- `official-fashn-public-example-only`: 새 Preview 개발 시험 모드. Android가 공식 공개 Gradio API에 직접 접속하며 중간 개발 서버·Railway 배포·개인 토큰을 사용하지 않는다. **내 사진·내 옷 AI 피팅 완성이 아니다.**

## 접근 조건과 검토한 계약

2026-11-04 [공식 Space](https://huggingface.co/spaces/fashn-ai/fashn-vton-1.5)의 공개 `/gradio_api/info`에서 `/try_on`의 `api_visibility=public`과 8개 인자 순서를 확인했다. [공식 소스](https://huggingface.co/spaces/fashn-ai/fashn-vton-1.5/blob/main/app.py)의 Complete Examples에 있는 person3/garment3 상의 조합과 P14 성공 스크립트를 재사용한다.

순서: person_image, garment_image, category, garment_photo_type, num_timesteps, guidance_scale, seed, segmentation_free. 값은 고정 공식 예제 주소 2개, tops, flat-lay, 50, 1.5, 42, true이다. 임의 URL/파일/이미지 입력은 없다. 매 실행 전 현재 공개 명세가 일치하는지 확인한다. 계약 변경·접근 거부 시 우회하지 않는다.

[이용 조건](https://huggingface.co/terms-of-service), [개인정보 정책](https://huggingface.co/privacy), [ZeroGPU 제한](https://huggingface.co/docs/hub/spaces-zerogpu)을 검토했다. 공개 서비스 이용에는 할당량·대기열이 적용되고 서비스 변경/중단 가능성이 있다. API 공개가 전체 의류 서비스 재판매나 사진의 무제한 재배포 권한을 의미하지 않는다. 공개 예제 시험 범위로만 사용하며 원본·생성물은 Git/APK에 넣지 않는다. 자체 서버의 인증/5분 TTL/취소·삭제 보장을 공개 데모에 적용했다고 주장하지 않는다. Parser 연구·평가 라이선스는 자체 서버 운영 시 별도 확인 대상이다.

## 구현

- `OfficialDemoRepository`: 별도 HTTPS 클라이언트. 앱 인증/쿠키/기기 ID 전달 없음, 자동 리다이렉트·POST 재시도 금지. 공식 예제 ID 한 개만 허용. 원본 비교 이미지는 공식 해시와 대조한다. 원본 다운로드에 한해 실제 HEAD 응답으로 확인한 `us.aws.cdn.hf.co`의 해당 예제 Xet 경로 또는 Hub cache 경로만 수동으로 허용한다. 임시 서명 query는 메모리에만 두고 저장/로그에 남기지 않는다. 결과는 정확한 공식 Space 호스트의 HTTPS Gradio WebP 경로만 허용한다.
- 크기 제한(입력 응답 8MiB, 디코딩 4MP), 실제 Bitmap 디코딩 후 PNG 변환·SHA-256 계산. 결과 URL을 그대로 이미지 로더에 넘기지 않는다. SSE 대기/완료/오류, 한도 초과, 만료, 통신 종료, 앱 대기 제한을 구분한다.
- `OfficialDemoViewModel`: 별도 Activity scope 및 `noBackupFilesDir/official-demo-job.json` AtomicFile 기록. POST 전 기록이 실패하면 요청하지 않는다. 응답 유실이면 자동/무조건 재전송하지 않는다. event ID 보존 후 같은 작업 조회만 가능하다. 화면 재진입/프로세스 복원에 POST가 없다. 새 시험은 명시적 초기화·재동의가 필요하고 기기별 마지막 전송 후 24시간으로 제한한다(서비스의 실제 할당량과 별개인 보수적 개발 제한).
- 대기 종료는 서버 취소·삭제가 아니다. 전송 중에는 응답 ID 보존을 기다리며, 이미 수신한 작업은 조회를 재개할 수 있다. 스트림 취소 시 네트워크 읽기 종료까지 지연될 수 있다.
- `OfficialDemoScreen`: Preview/debug 전용, 자체 AI 화면에서 별도 진입. 개인 사진/카메라/옷장 입력 컨트롤 없음. 준비 확인→예제 선택→외부 실행 동의→진행 안내→원본/결과 비교→CreateDocument PNG 저장. 재생성 시작 때 이전 결과를 제거하여 새 성공과 혼동하지 않는다.
- 기존 TryOnGateway/LocalVtonSession의 GPU 준비 및 인증 검사를 우회하지 않는다. 기존 체형/줄자/옷장/자동핏/저장 복원 코드는 변경하지 않았다.

## 테스트 실행 경계

`OfficialDemoTest`는 합성 이미지/MockWebServer/가짜 gateway를 사용한 모의 검사다. CI에서는 외부 추론을 실행하지 않는다.

`OfficialDemoLiveTest`는 실제 Android용 명시적 opt-in이다. Preview에서 `officialDemoLive=true`일 때만 실제 API를 호출한다. 기존 시험 저널이나 결과가 있으면 중단하여 재실행에 의한 중복 POST를 막는다. 실제 production 화면/어댑터를 사용하며 저장 테스트는 ActivityResultRegistry로 앱 비공개 파일 목적지를 제공한다. **시스템 파일 선택기 조작은 이 테스트가 검증하지 않는다.** 실제 기기에서는 이 테스트 클래스 하나만 지정한다. 다른 기기 테스트에는 테스트 fixture를 초기화하는 코드가 있으므로 사용자 데이터가 있는 S25+에서 전체 instrumentation suite를 실행하지 않는다.

S25+는 작업 PC의 로컬 SDK ADB에서 `device` 상태로 확인했다. 실행 PC는 LG Windows 노트북이며 클라우드 USB 접근을 가정하지 않았다. 설치본 Preview는 `com.jeiu.fashionproject.preview`, versionCode16201, versionName1.1.62.1-preview, 인증서 SHA-256 `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`이다. 기존 데이터 파일 15개의 해시만 기기에서 계산했다. 사진/기록 본문은 복사하지 않았다. 설치본 APK와 해시 기록은 `fashion_app/build/p15-private/`에 보관하며 Git 제외다.

로컬 첫 빌드는 디스크 부족으로 테스트 패키징에 실패했다. 이번에 시작한 읽기 전용 에뮬레이터가 만든 임시 복제 파일과 새 P15의 불완전 빌드 산출물만 경로를 확인하여 정리했다. 원본 AVD/기존 백업/앱 데이터는 보존했다. 에뮬레이터 ADB는 unauthorized였으며 실기기 검증 대상으로 전환했다. 인증 우회는 하지 않았다.

## 최종 검증 기록

집중 모의 검사: 신규 OfficialDemoTest 15개 및 기존 LocalVtonFlowTest 12개 통과. 저장 확인 실패는 Robolectric의 paused main looper에 IO 완료가 대기한 테스트 구성 문제였고 메인 루퍼를 처리하도록 수정한 뒤 통과했다. Android instrumentation Kotlin 컴파일 및 APK 안전성 단위 검사 3개도 통과했다. 이후 실제 HEAD로 확인한 공식 CDN 경로 제한 검사 1개를 추가했으며 최종 CI에서 전체 회귀와 함께 검증한다.

진행 중: 전체 CI → 서명·버전·무결성 확인 → S25+ 안전 업데이트 → 공식 예제 실제 요청 1회 → 표시/비교/저장 및 데이터 해시 비교. 실제 외부 추론은 아직 이번 P15에서 실행하지 않았다. 아래 결과는 완료 후 실제 증거로 갱신한다.

GPU 모델/순수 추론 시간/peak VRAM은 API가 제공하지 않으면 미확인으로 남긴다. 결과가 P14와 같더라도 새 요청의 event ID와 실행·수신 기록으로 판단하며 파일 차이만을 성공 기준으로 삼지 않는다.

### 전송 1회 보강

최초 CI 코드 `a172c9e` 검토 중 [OkHttp 4.12 공식 구현](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt)에서 `503 + Retry-After: 0`은 연결 재시도 비활성만으로 막히지 않는 것을 확인했다. 실제 외부 요청 전 RequestBody를 `isOneShot=true`로 바꾸고, 503 뒤 성공 응답이 있어도 POST가 정확히 1회인지 MockWebServer 검사로 보강했다. 최초 CI는 중단하지 않되 그 APK는 실기기 설치 대상으로 사용하지 않는다. 수정한 최종 SHA의 전체 CI만 최종 배포 근거로 삼는다.
