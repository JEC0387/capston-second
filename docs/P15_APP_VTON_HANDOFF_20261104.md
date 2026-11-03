# P15 공식 예제 전용 앱 연결

**최종 결과: Galaxy S25+의 Preview 앱에서 시작한 새 공식 예제 요청 1회로 실제 착장 이미지를 수신했고, 화면 표시 → 원본 비교 → Android 시스템 파일 선택기로 로컬 PNG 저장까지 확인했다. 개인 사진을 처리하는 자체 GPU 서버 성공은 아니다.** 실행 코드 `14beaef75b8504c4ca985a0550c89a49cbf9aa79`와 후속 문서 커밋을 구분한다. 최신 문서 커밋은 `git log -1 --format=%H -- docs/P15_APP_VTON_HANDOFF_20261104.md`로 확인한다.

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

최종 실행 코드: `14beaef75b8504c4ca985a0550c89a49cbf9aa79`. [최종 CI 41709293034](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41709293034)의 7개 Job 전체 성공. 내려받은 XML에서 Android 431개, Preview 195개 테스트 모두 실패/오류/건너뛰기 0개로 확인했다. 신규 OfficialDemoTest 18개가 포함된다. Backend 151개 및 하위 검사 8개 통과(경고 7개), APK 배포 안전성 3개 통과. Windows Python 3.11~3.14 및 Linux 3.12 서버 시작 검사 성공. Lint 오류 0개, 경고 168개, 힌트 2개. 기기 테스트 APK는 컴파일만 했고 S25+에서 전체 instrumentation suite를 실행하지 않았다.

[Preview APK artifact 13268785679](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41709293034/artifacts/13268785679): `1.1.77.1-preview`, versionCode `17701`, 패키지 `com.jeiu.fashionproject.preview`. 인증서 SHA-256은 위 설치본과 동일하다. APK SHA-256: `6776644ac1d8a5a17e41824b2f081f840399bd98ccb42973c50e0f574005642f`. ZIP CRC, APK 서명, 내부 build_identity/DEX 기능 문자열, BUILD_INFO/SHA256SUMS, Preview 설정 격리, 소스 SHA를 검증했다. CI 원본 BUILD_INFO의 distributionVerified=true를 확인했고 로컬 재검사는 배포를 재승인하지 않는 validation-only 방식이다. Artifact 만료 예정일은 2026-12-03이다.

로컬 APK: `C:\Users\USER\Documents\ChatGPT\capston\p15-app-vton-20261104\fashion_app\build\p15-release\fashionai-1.1.77.1-preview-vc17701-14beaef7.apk`.

S25+ 기존 APK의 현재 기기 SHA와 보관한 설치본 SHA 일치를 재확인했다. 업데이트 직전 사용자 파일 21개의 해시만 기기에서 기록했다. 같은 서명/패키지 및 상향 버전 검사 후 `adb install -r`가 Success를 반환했고 기기 설치 버전 17701을 확인했다. 삭제/초기화/다운그레이드/사용자 데이터 복원은 하지 않았다. 런처의 실제 클래스는 `com.jeiu.fashionproject.preview/com.jeiu.fashionproject.MainActivity`이다(패키지 상대 `.MainActivity` 명령은 잘못된 클래스여서 실패했고 올바른 클래스로 실행을 확인했다).

기기 실행 시 Android의 16KB ELF 정렬 호환성 안내가 나타났다. 해당 `libmediapipe_tasks_vision_jni.so`, `libimage_processing_util_jni.so`, `libandroidx.graphics.path.so`, `libxeno_native.so`의 arm64 파일은 기존 16201 APK와 각각 SHA-256이 같다. 기존 네이티브 의존성 문제이며 P15가 새로 도입한 파일은 아니다. 안내의 확인만 눌렀고 영구 숨김이나 시스템 호환성/보안 설정은 변경하지 않았다. [Android 공식 16KB 지원 문서](https://developer.android.com/guide/practices/page-sizes)에 따라 호환 라이브러리 교체/빌드 및 별도 실기기 회귀가 필요하다. 컴파일/CI 통과를 16KB 호환 보장으로 해석하면 안 된다.

실기기 진행 중 USB 연결이 두 차례 끊겼고 사용자가 직접 재연결·인증했다. 최초 안내 이후 런처 화면을 관찰했지만, 재연결 후 앱 프로세스가 살아 있었고 최근 종료 기록에서 이번 실행의 크래시를 확인하지 못했다. HOT 재진입으로 정상 화면에 돌아왔다. 이를 앱 크래시로 단정하지 않는다. 인증 우회·드라이버 변경·보안 설정 해제는 하지 않았다.

## 실제 새 요청·표시·저장 증거

- 실행 환경: 로컬 Windows PC의 ADB에 연결된 Galaxy S25+ SM_S936N, 실제 설치한 Preview 17701. 에뮬레이터/PC 스크립트가 생성한 결과를 앱에 주입한 것이 아니다.
- 앱의 일반 화면: 시작하기(기존 스타일 선택 유지) → 마이 → 가상 피팅 → 자체 AI 화면 → 별도의 공식 공개 데모 연동 → 공식 예제 3 선택 → 공개 API 준비 확인 → 외부 실행 동의 → 실제 생성 1회.
- 엔진: `official-fashn-public-example-only`, 서비스: `fashn-ai/fashn-vton-1.5` 공개 Gradio API. 기존 `fashn-vton-1.5-local`/FastAPI 인증 경로를 통과했다고 주장하지 않는다.
- 작업 저널이 없는 것을 확인한 뒤 한 번만 동의 버튼을 눌렀다. 반환 event ID가 P14의 ID와 다르며 앱 저널의 appInitiated=true를 확인했다. event ID/결과 URL은 비공개 로컬 증거에만 보관한다. 중복 생성·재시도·유료 자원 사용 없음.
- 요청 기록: **2026-11-04 05:39:05.241 KST**, 결과 수신: **05:39:36.346**, 저장 완료: **05:42:42.965**. 요청 기록→수신 **31.105초**, 요청 기록→저장 **217.724초**(USB 재연결, 화면 검수/비교/파일 선택 시간 포함). 순수 GPU 추론 시간은 제공되지 않았다.
- 실제 결과 `576 × 823`, PNG **492,711 bytes**, SHA-256 **`82b030642691c2e58755021ef4e84eb62a0007457d59e2535d51cc8f3ce465e1`**. 실제 WebP 디코딩 후 앱이 PNG로 변환했고, 저장된 파일을 다시 읽어 PNG 서명·전체 디코딩·크기·앱 저널 SHA 일치를 확인했다.
- S25+ 저장 경로: **`/sdcard/Download/official-example-1793738524138.png`**. 실제 CreateDocument의 로컬 다운로드 폴더를 확인하고 기존 파일이 없는 새 이름으로 저장했다. 테스트용 저장 콜백이나 가짜 목적지를 사용하지 않았다.
- PC 보관 경로: **`C:\Users\USER\Documents\ChatGPT\capston\p15-app-vton-20261104\fashion_app\build\p15-private\official-example-1793738524138.png`**. 같은 폴더의 `p15-s25-evidence.json`, `s25-live-journal.json`, `s25-public-result-screen.png`, `s25-public-original-screen.png`에 증거를 보존한다. 모두 Git/APK 제외이며 클라우드에 업로드하지 않았다.
- 실제 화면에서 흰 티셔츠 원본과 남색 상의 생성 결과가 비교 버튼으로 바뀌는 것을 시각적으로 확인했다. 옷 변경과 포즈/배경의 대략적 유지가 보이지만 의류 질감·몸통/팔 실루엣·비율의 완전한 보존이나 실제 핏 정확도는 보장하지 않는다. 결과를 품질 보증 이미지로 취급하지 않는다.
- 화면을 나갔다가 재진입한 뒤에도 같은 결과와 저장 상태를 확인했다. event ID·SHA·시작/수신/저장 시각을 포함한 저널 내용이 동일했다. 재진입에 새 생성 요청을 하지 않았다. 프로세스 강제 종료/재부팅 실기기 시나리오는 추가로 실행하지 않았다.
- 확인 가능한 사용자 결제 **0**, 유료 요청 없음. 공개 익명 서비스가 GPU 모델·peak VRAM·순수 추론 시간·서버측 과금 지표를 제공하지 않아 해당 값은 **미확인**이다.

| 검증 단계 | 결과 |
| --- | --- |
| ADB 연결 / 호환 APK 업데이트 / 앱 화면 실행 | S25+에서 성공, 중간 USB 끊김 후 직접 재인증 |
| 공식 공개 데모 실제 새 생성 | 앱 요청 1회로 성공 |
| 별도 어댑터의 실제 결과 수신 | Android의 OfficialDemoRepository에서 성공 |
| Android 결과 표시 / 원본 비교 / 파일 저장 | S25+ 실제 UI와 시스템 파일 선택기로 성공 |
| 에뮬레이터 실제 생성 | 미검증, 기존 unauthorized 상태를 우회하지 않음 |
| 사용자 개인 사진 자체 GPU 서버 경로 | 미검증, GPU 서버 미확보·개인 사진 전송 없음 |
| 모의 계약/오류/복구 검사 | 자동 테스트 통과, 실제 추론 증거와 구분 |

## 기존 데이터 보존 확인

업데이트 직전 21개 파일과 실기기 검증 후 22개 파일의 해시를 비교했다. **기존 18개 동일, 누락 0개, 신규 1개**(공식 예제 작업 저널). `wardrobe_store.xml`, `profile_store.xml`, `avatar_visual_profile_v1.xml`, `saved_outfits.xml`, 착용 기록/스타일/취향 데이터의 해시가 동일하다. 선택된 기존 스타일은 바꾸지 않았고 실제 옷 등록·체형 편집·의류 배치 편집은 실행하지 않았다.

변경된 3개는 `shared_prefs/android.app.ActivityThread.IDS.xml`, `files/profileInstalled`, `shared_prefs/weather_alert.xml`이다. 앞의 두 파일은 Android/프로파일 설치 관리 파일이다. 날씨 파일은 기존 WeatherViewModel이 위치·예보 캐시를 자동 갱신하는 경로가 있다. 기존 값 본문을 수집하지 않았으므로 날씨 파일의 정확한 변경 필드를 확정하지 않는다. 전체 앱 데이터가 바이트 단위로 모두 동일하다고 주장하지 않는다. 저장된 아바타 의류 배치 파일 `avatar_fitting_overlay_v1.xml`은 검사 전후 모두 없었으므로 해당 저장 배치 복원은 실기기 검증한 것으로 계산하지 않는다.

GPU 모델/순수 추론 시간/peak VRAM은 API가 제공하지 않으면 미확인으로 남긴다. 결과가 P14와 같더라도 새 요청의 event ID와 실행·수신 기록으로 판단하며 파일 차이만을 성공 기준으로 삼지 않는다.

### 전송 1회 보강

최초 CI 코드 `a172c9e` 검토 중 [OkHttp 4.12 공식 구현](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt)에서 `503 + Retry-After: 0`은 연결 재시도 비활성만으로 막히지 않는 것을 확인했다. 실제 외부 요청 전 RequestBody를 `isOneShot=true`로 바꾸고, 503 뒤 성공 응답이 있어도 POST가 정확히 1회인지 MockWebServer 검사로 보강했다. 최초 CI는 중단하지 않되 그 APK는 실기기 설치 대상으로 사용하지 않는다. 수정한 최종 SHA의 전체 CI만 최종 배포 근거로 삼는다.

중단된 AtomicFile 기록의 `.bak`만 남은 경우도 `openRead()`로 복구하여 미전송 상태로 잘못 초기화하지 않도록 보강했다. POST 전 파일 sync와 기록 내용 재읽기까지 성공해야 전송한다. 해당 복구 사례도 모의 회귀 검사로 검증한다.

## 전체 변경 파일

- `fashion_app/app/src/main/java/com/jeiu/fashionproject/data/OfficialDemoRepository.kt`: 고정 예제/공개 계약/단발 POST/SSE/검증된 PNG 어댑터.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/viewmodel/OfficialDemoViewModel.kt`: 지속 작업 기록, 중복 방지, 재조회, 로컬 저장.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/OfficialDemoScreen.kt`: 분리된 예제 선택/동의/결과 비교/저장 화면.
- `fashion_app/app/src/main/java/com/jeiu/fashionproject/ui/screens/LocalVtonScreen.kt`: Preview 전용 별도 진입 버튼, 기존 입력 초안 보존.
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/OfficialDemoTest.kt`: 모의 계약/오류/상태/저장/단발 요청/저널 복구 18개.
- `fashion_app/app/src/androidTest/java/com/jeiu/fashionproject/OfficialDemoLiveTest.kt`: 공식 예제 1회 실행용 opt-in 기기 테스트(실행하지 않음).
- `.github/workflows/android-debug-apk.yml`: Preview 기기 테스트 APK 별도 컴파일/게시. 실제 추론 호출 없음.
- `tools/apk_release.py`: 패키징 기능 검사에 공개 데모 엔진 추가.
- `docs/P15_APP_VTON_HANDOFF_20261104.md`, `docs/README.md`: 구현/검증 경계와 인수인계.

사진·기기 지문 기록·설치본 APK·검증 도구는 Git 제외 `fashion_app/build/p15-private/`에 둔다. 공식 예제/생성물은 APK/Git에 넣지 않는다. P14 PNG를 새 결과로 대체하지 않았다. 개인 사진 피팅에는 별도 허가된 GPU 자체 서버와 입력 사용 동의/라이선스/처리 정책 및 실제 앱 검증이 여전히 필요하다.

## 다음 작업 순서와 제한

1. S25+는 이미 17701로 업데이트됐으므로 같은 APK를 다시 설치하거나 앱을 삭제할 필요가 없다. 받은 결과 파일과 작업 저널을 유지한다. 공개 시험은 기기당 24시간 제한이며 재시도를 위해 기록을 지우지 않는다.
2. 공식 공개 예제 시험은 시연 가능하다. 개인 사진/개인 옷장의 자체 AI 완료, 에뮬레이터 검증, 모든 기기/16KB 호환성 완료로 발표하지 않는다.
3. 16KB 네이티브 의존성 교체는 별도 최소 변경으로 검토하고 MediaPipe 체형 분석/카메라/그래픽 기능 회귀를 거친다. 경고 숨김이나 라이브러리 제거로 해결하지 않는다. Lint 경고 168개도 남아 있다.
4. 개인 사진 경로는 허가된 자체 GPU 환경을 확보한 후 기존 인증된 local-vton 경로를 재사용한다. 별도 사진 전송 동의·라이선스/처리 정책 검증 후 실제 생성/비교/저장을 다시 검증한다. 공개 데모를 개인 인증 서버로 바꾸어 연결하지 않는다.
5. 이번 최종 변경은 검증 기록을 보충하는 문서뿐이다. 실행 코드 SHA가 동일한 전체 CI를 문서 커밋 때문에 다시 실행하지 않는다. 기존 브랜치/운영 서버/DB/팀 저장소는 변경하지 않았다.
