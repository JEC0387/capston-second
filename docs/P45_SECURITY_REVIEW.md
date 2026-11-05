# P45 Qwen 공개 시험 어댑터 — 보안 연결 검수

2026-11-06 KST. 기존 코드 읽기 검수이며 실제 계정·무료 할당량·외부 생성·기기 검증 결과가 아니다. 이 담당자는 코드/테스트 실행, API 요청, Git, ADB, 인증 설정 변경을 하지 않았다. 작업 트리와 상위 디렉터리에서 적용할 `AGENTS.md`는 발견하지 못했다.

## 기존 구현에서 재사용할 계약

| 코드 | 재사용할 원칙 | 그대로 가져오면 안 되는 부분 |
|---|---|---|
| `backend/tryon_proxy.py` | 인증 후 본문 읽기, owner별 작업 조회, request UUID/fingerprint 충돌 거부, POST 전 영속 예약, 응답 유실 시 재전송 금지 | 유료 FASHN의 의류별 순차 POST, inline 개인 사진 입력, Unix 전용 `fcntl` journal |
| `backend/local_vton_server.py` | Windows/Unix 단일 worker lease, fsync+atomic replace, 재시작 중단 상태, 결과 TTL, 작업 소유권 검사 | local engine명, 한 벌 category 계약, research/GPU readiness, 24시간 뒤 작업 삭제를 무료 요청 ledger에 적용하는 것 |
| `OfficialDemoRepository.kt` | 공개 예제 ID만 받기, 별도 HTTP client, 계정/자체서버 token 전달 금지, 무조건 POST 재시도 금지, URL·decode 검사 | FASHN Space 특정 endpoint/예제/정책을 Qwen 정책으로 재사용하는 것 |
| `OnDeviceVtonEngine.kt` | 입력/의류 고정, 결과와 요청 결합, 취소 후 실제 작업 종료까지 실행 잠금 | **Qwen은 외부 서비스이므로 이 native/on-device 엔진으로 등록하면 안 됨** |

기존 VTON의 journal은 SQLite가 아닌 JSON 파일이다. 기존 `auth_api`는 별도 DB/Bearer 계정 체계다. P45 무료 실험 때문에 DataAuth·DB·기존 journal·옷장·운영 서비스를 변경할 이유가 없다.

## 최소 실행 경계

별도 미배포 FastAPI 앱 또는 오프라인 생성 가능한 어댑터로 시작한다. 새 provider 식별자는 Qwen 외부 시험임을 드러내고, 기존 `/api/local-vton`이나 FASHN API를 대체하지 않는다. 품질 합격 전 Android 마법봉을 준비 완료로 표시하지 않는다.

1. **무료성 선행 조건:** API key 존재만으로 호출하지 않는다. 실제 계정의 해당 model/region/scope, 남은 무료 수량·만료 시각, provider의 `Free Quota Only` 활성 상태가 확인돼야 한다. 환경변수 `FREE_ONLY=true`는 provider 상태의 증거가 아니다. 공식 조회 API가 없으면 콘솔에서 확인된 사실과 확인 시각을 별도 증거로 관리하고 미확인 상태는 `blocked`로 남긴다. 클라이언트 본문으로 이 증거를 갱신할 수 없어야 한다. 실제 호출 담당자는 요청 직전 상태를 확인해야 한다.
2. **입력:** 승인된 공개 인물/상의/하의/신발 네 역할의 예제 ID만 허용한다. 서버가 출처·허가·해시가 검토된 고정 자산을 해결한다. 임의 URL, local path, base64, 이미지 bytes, 갤러리/옷장 식별자 및 추가 필드는 거절한다. 기본 allowlist는 빈 상태도 허용하며 그때 생성은 비활성이다. 공개 이미지라는 사실만으로 제3자 재전송 권리가 입증되지는 않는다.
3. **인증:** 서버 측 API key와 앱 접근 Bearer token을 분리한다. inbound authorization을 provider 요청에 복사하지 않는다. key는 환경/보안 저장에서만 읽고 repr·예외·request log·응답·Android BuildConfig에 넣지 않는다. 요청 본문을 읽기 전 자체 API 인증을 적용한다. 외부 운영 공개는 별도 배포 범위다.
4. **단일 생성:** 공식 계약에 맞는 네 참조 입력을 한 요청에 고정하고 output n=1로 제한한다. endpoint/model/region/prompt와 네 역할/자산 hash를 fingerprint에 포함한다. 초기 시험은 전체 durable attempt 상한 1, 동시 요청 1이 적절하다. 의류 한 벌마다 별도 POST를 보내는 기존 paid FASHN 루프를 재사용하지 않는다.
5. **영속 중복 방지:** request ID·owner·fingerprint와 `submitting` 상태를 네트워크 호출 전에 원자적으로 저장한다. 같은 ID/동일 입력은 기존 상태만 반환하고, 다른 입력은409, 다른 owner는404다. 새 ID로 같은 승인 1회 한도를 우회할 수 없어야 한다. 결과 만료/로컬 취소/재시작으로 시도 횟수를 환급하거나 tombstone을 삭제하지 않는다. JSON lease 패턴 또는 별도 로컬 SQLite `BEGIN IMMEDIATE`/unique constraint가 가능하지만 기존 앱 DB와 분리한다.
6. **응답 유실·취소:** POST timeout/connection loss/프로세스 중단은 실제 생성 여부 `unknown`이다. 자동 재POST를 하지 않는다. provider가 실제 취소를 지원·확인하지 않으면 로컬 대기 종료로만 표시한다. 작업 취소 후 thread가 계속 통신할 수 있으므로 잠금·시도 기록은 유지한다.
7. **결과:** 공식 계약에서 확인한 HTTPS 결과 출처만 허용하고 key/auth header를 download에 전달하지 않는다. 리디렉션·크기·pixel 수·실제 형식·PNG/JPEG decode를 검사한다. PNG 저장 시 실제 decode/re-encode하고 CRC/재열기/hash를 기록한다. signed URL·raw provider error가 로그에 남지 않도록 코드형 오류만 노출한다. 로컬 저장 실패를 생성 성공 또는 저장 성공으로 묶지 않는다.
8. **품질:** 이미지 파일 수신은 `received_pending_review` 수준이다. 얼굴/무늬/신발/손/배경 품질 합격과 실제 Android 표시·저장·재열기 성공은 별도 항목이다. 이전 PNG를 새 request ID 결과로 재사용하지 않는다. 공개 예제 시험 성공은 개인 사진 사용 승인이나 P39 온디바이스 성공이 아니다.

## 집중 회귀의 핵심

- key·quota 확인·Free Quota Only·공개 자산 중 하나라도 빠지면 provider 호출 0회.
- 잘못된 필드·역할 중복·임의 URL/파일/이미지·동의 없음·인증 실패·다른 owner는 요청 전에 거절.
- 동일 ID 반복, 다른 ID 중복/한도 초과, 동시 실행, 프로세스 재시작에서 실제 POST 최대1회.
- 전송 전 journal 실패는0회, 전송 후 timeout/500/응답 유실은 `unknown`/실패이고 자동 재시도0회.
- 만료·cancel·결과 저장 실패로 시도 예약을 삭제하지 않음.
- 네 이미지 순서/역할과 prompt·request fingerprint 고정, 의류 변경 시 이전 결과를 새 결과로 표시하지 않음.
- result URL 출처·redirect·과대 파일·위조형식·decode 실패를 거부; 정상 mock PNG는 모의 검사로만 분류.
- 입력/응답/예외/repr/상태 응답에 API key, inbound token, signed URL 원문이 포함되지 않음.

이 검수만으로 비용0, 실제 생성 또는 출시 GO를 보장하지 않는다. 계정의 실제 무료 상태와 승인된 네 이미지가 없는 경우 네트워크 실행만 차단하고, payload·journal·오류 처리의 로컬 구현/검증은 진행할 수 있다.

## 추가 Android 방어 검사 — 수정 전 실패 확인

계정·키·무료 할당량이 준비되지 않아 Qwen 호출을 중단한 뒤, 현재 모델 미준비 화면에서도 접근 가능한 저장 이미지 재열기 경로를 검수했다. `LocalVtonRepository.openSavedImage`는 `read(buffer)==0`을 처리하지 않아, 계약을 위반하는 스트림에서 취소 전까지 진행 없는 읽기를 반복할 수 있었다. 표준 Android 제공자에서 실제 발생한 현상은 아니며, 합성 `ContentResolver` 스트림으로 검사했다.

총괄이 실행한 수정 전 `fashion_app/build/p45-private/red02`의 결과는 6개 중 2개 실패/4개 통과다. 연속0읽기는 네 번째 읽기의 유한 sentinel 예외에 도달했고, 0읽기 뒤 정상 PNG를 주면 잘못된 무진행 상태를 그대로 통과했다. 이 두 검사는 첫0읽기에서 명시 실패하고 스트림을 닫는 것을 요구한다. 정상 부분읽기·EOF·8MiB 초과·취소의 네 인접 검사는 수정 전 통과했다.

최소 수정은 첫0읽기에서 `TryOnFailure`를 발생시키는 한 줄이다. 기존 크기 제한·취소 검사·`use`의 스트림 닫기·사진/모델/저장 데이터 구조는 변경하지 않았다. `P45SavedImageReadTest.kt` 외 기존 테스트는 수정하지 않았다. 최종 `green01`에서 해당6개와 P39 화면5개 PASS, `green02-images`에서 관련 이미지12개 PASS를 확인했다. 이 방어 수정은 실제 생성형 AI나 실기기 인수 완료의 근거가 아니다.

공개 연구 CLI는 Cloud/기존 인증 서버와 별개다. 고정4입력·토큰 없는 호출·영구1회 journal·엄격한 결과 URL·디코딩/PNG 재열기를 검수했고, 실제 공개 데모 결과 수신 후에도 자세/배경/의류 세부 품질과 상용 권리는 불합격/미확보로 남겼다. 운영 FastAPI 라우트와 P39 온디바이스 엔진은 활성화하지 않았다.
