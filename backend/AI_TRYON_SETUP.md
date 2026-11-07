# AI 가상피팅 서버 설정

FASHN 키는 서버에만 둡니다. Android BuildConfig, local.properties, Git, APK에 넣지 않습니다. 운영 DB/Supabase 없이 작동합니다. 유료 생성은 기본 비활성화이며 다음 설정 중 하나라도 없거나 사용량 파일을 안전하게 열 수 없으면 503으로 거절합니다.

## 운영자 설정 (Linux, HTTPS, 단일 worker)

1. 서버에서 `python tryon_tokens.py`로 사용자별 무작위 개인 접속 토큰을 발급합니다. 출력된 원문 토큰은 해당 사용자에게 안전하게 전달하고 서버 환경에는 SHA-256 해시만 넣습니다. 공동 공개 토큰이나 APK에 내장한 토큰은 사용하지 않습니다.
2. 서버의 비공개 환경변수 또는 비공개 `.env`에 설정합니다. 아래 값은 예시 자리 표시자이며 실제 키가 아닙니다.

```dotenv
TRYON_ENABLED=true
FASHN_API_KEY=<FASHN 운영 계정 API 키>
TRYON_CLIENT_TOKEN_HASHES=<사용자1 토큰 SHA-256>,<사용자2 토큰 SHA-256>
TRYON_USER_DAILY_LIMIT=3
TRYON_GLOBAL_DAILY_LIMIT=20
TRYON_JOURNAL_PATH=/var/lib/fashion-private/tryon-journal.json
```

`main.py`의 기존 dotenv 로딩을 사용합니다. 외부 환경변수도 지원합니다. 환경파일·사진·토큰을 저장소에 넣지 마세요. 환경변수를 바꾸면 서버를 재시작합니다.

3. 전용 비공개 영속 디렉터리를 서버 실행 사용자 소유로 만드세요. `/uploads` 같은 정적 공개 디렉터리를 사용하지 마세요. 이 파일은 운영 DB가 아니라 중복 과금·사용량을 막기 위한 로컬 메타데이터 저널입니다. 사진·결과·API 키·토큰 원문은 쓰지 않습니다. 파일 모드는 0600이며 각 변경을 fsync/원자적 교체합니다. 삭제하거나 이전 버전으로 되돌리면 보호 이력이 사라지므로 임의 초기화하지 마세요.
4. 다음과 같이 **worker 1개**로 실행하고 HTTPS 역방향 프록시 뒤에 둡니다.

```bash
python -m uvicorn main:app --host 127.0.0.1 --port 8000 --workers 1 --proxy-headers --forwarded-allow-ips=127.0.0.1
```

TLS 프록시는 외부의 X-Forwarded-* 헤더를 제거/덮어쓰고 올바른 `X-Forwarded-Proto: https`를 전달해야 합니다. 신뢰 IP를 `*`로 열지 마세요. ASGI scheme이 HTTPS가 아니면 생성·조회가 거절됩니다. 프록시에서 `/api/tryon/`에 요청 본문 최대 22 MiB, 업로드 시간 제한, IP별 속도 제한을 함께 적용하세요. HTTP 로컬 주소/기본 에뮬레이터 주소에는 토큰과 사진을 전송하지 않습니다. 개발 Mock은 pytest와 Android 주입 gateway를 사용합니다.
5. 앱의 **마법봉 → AI 서버 연결 설정**에 HTTPS 서버 루트 주소와 개인 접속 토큰을 입력합니다. 토큰은 프로세스 메모리에만 있고 종료 후 다시 입력해야 합니다. FASHN API 키를 앱에 입력하지 마세요. 연결 설정만으로 유료 요청은 하지 않으며, 사용자가 매번 외부 전송·비용 안내를 확인한 뒤 생성해야 합니다.

## 보호 및 동작

- 개인 bearer 토큰 인증, 사용자 소유권 검사, 사용자/전역 UTC 일일 **생성 횟수** 한도, 사용자별 90회/분 조회·요청 제한. 1벌=최대 1회 FASHN POST, 상의+하의=최대 2회. 통화 금액 한도는 아니므로 운영자가 현재 크레딧 요금을 확인해야 합니다.
- 동시 생성 최대 2개, 사용자당 진행 작업 1개. 모델은 `tryon-v1.6`, 샘플 1개, JPEG, balanced로 서버 고정. 지원 카테고리는 tops/bottoms/one-pieces, 원피스는 단독입니다.
- 입력은 data URI만 허용하고 외부 URL을 서버에서 받지 않습니다. 크기·픽셀 수 제한, EXIF 보정, 긴 변 최대1296px, 투명 배경 흰색 처리, JPEG 재인코딩으로 메타데이터를 제거합니다. 상의→하의 순차 생성 시 중간 결과를 다음 인물 입력으로 사용합니다.
- `POST /api/tryon/jobs`에 UUID request_id, person, garments, consent=true를 전달합니다. 동일 ID·동일 입력은 기존 작업만 반환하고 다른 입력은409입니다. 사용량 예약과 유료 POST 의도를 먼저 기록하고 **유료 POST는 자동 재시도하지 않습니다**. 응답 유실 시 같은 ID로 GET만 사용합니다.
- 서버는 고정 호스트 `POST https://api.fashn.ai/v1/run` → ID → `GET /v1/status/{id}`를 사용합니다. `return_base64=true`로 공개 CDN URL 대신 이미지 바이트를 받아 인증된 `/api/tryon/jobs/{id}/result`로 제공합니다. 임의 결과 URL은 따라가지 않습니다.
- 상태 조회는3초 간격, 의류별 대기180초(+진행 중 HTTP 응답 대기 최대25초). timeout/서버 재시작/응답 불확실성에 대해 재POST하지 않습니다. 이미 시작된 작업은 계속 실행·과금될 수 있습니다. 취소는 후속 의류 요청과 앱의 대기를 중단하며 FASHN 환불·작업 취소를 보장하지 않습니다.
- 불명확한 결과를 포함해 사용량 예약은 자동 환급하지 않는 보수적 한도입니다. 실패/미사용분도 한도를 차지할 수 있습니다. 운영자가 FASHN 사용량을 확인하세요.
- 사진·중간 결과는 메모리에서만 처리합니다. 최종 결과는 완료 후 최대30분 메모리 보관 후 제거하며 재시작하면 사라집니다. 앱은 받은 결과를 메모리에서 비교하고 Android 문서 선택기로 사용자가 명시적으로 JPEG 저장합니다. FASHN 자체 처리·보관은 해당 서비스 정책을 따릅니다.
- 단일 worker 파일 잠금으로 다른 worker는 안전하게 비활성화됩니다. 서버 재시작 시 미완료 요청은 interrupted가 되어 자동 재전송하지 않습니다. 저널 손상·권한 문제·10,000개 요청 이력 한도 도달 시도 비활성화합니다. 다중 서버 배포는 별도 공유 원자적 저장소/사용량 관리 구현 전에는 지원하지 않습니다.
- 로그에 Authorization, 요청 본문, 사진 data URI, FASHN 응답 본문을 출력하지 마세요. 프록시·APM의 본문/헤더 수집도 비활성화하세요. 토큰 폐기는 해당 해시 삭제 후 재시작으로 수행하며, 완료 전 사용자의 토큰을 바꾸면 이전 작업 소유권은 유지되지 않습니다.

## 실제 유료 호출 없는 검증

```bash
pip install pytest httpx
python -m pytest -q tests/test_tryon_proxy.py
python -m pytest -q
```

테스트는 Fake provider와 네트워크 차단을 사용합니다. 앱의 Mock gateway는 테스트 소스에만 있습니다. 실제 앱에는 가짜 성공/수동 합성 대체 결과가 없습니다.

공식 문서 대조(2026-10-19):
- https://docs.fashn.ai/api-reference/tryon-v1-6
- https://docs.fashn.ai/api-overview/api-fundamentals

실제 FASHN 키, HTTPS 운영 서버, 크레딧, 실기기/외부 서비스 성공률은 이 작업에서 검증하지 않았습니다.
