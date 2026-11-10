# 상윤 담당자용 기존 Railway 서비스 인수

이 문서 자체는 배포 승인이나 운영 복구 완료 증거가 아니다. PR #23의 팀 리뷰·필수 검사 및
병합이 완료된 **정확한 main SHA**에서 서버 ZIP을 생성한다. 이전 `ce0a307` ZIP을 최종본으로
사용하지 않는다. 병합 전 후보 파일은 REVIEW 전용으로 표시하며 운영에 업로드하지 않는다.

## 담당자가 확정할 항목

- 기존 프로젝트·환경·서비스 ID/콘솔 URL, 현재 배포 ID/SHA와 비용 한도.
- 기존 DB 및 결과/예산 저널의 백업·복구 지점과 접근 제한된 지속 볼륨.
- `DATABASE_URL`의 대상 및 적용 이력. `DB_AUTO_CREATE=false` 유지.
- `MIGRATION_PREFLIGHT.md`, `migrations/README.md`의 팀/개인 계보 및 005 번호 충돌 확인.
  가입 허용 전 팀 005/006/007 테이블·열·제약조건과 실제 적용 이력을 검사한다.
  운영 SQL 자동 적용·초기화·이력 덮어쓰기는 하지 않는다.
- Release 키 담당자와 Google/Kakao 콘솔 관리자는 서버 관리자와 별도로 지정한다.

## ZIP 검증 및 기존 서비스 배포

1. 제공된 SHA256과 ZIP 압축 무결성을 검증한다. `SOURCE_MANIFEST.json`의 main SHA가
   실제 PR #23 병합 SHA와 같은지 확인한다. 실제 `.env`, 키·토큰, DB, 이미지, 모델, 로그,
   인증서·서명키가 포함된 ZIP은 배포하지 않는다.
2. 새 빈 작업 폴더에 압축을 풀고 기존 서비스 설정을 읽기 전용으로 확인한다.
   `backend/Dockerfile`은 Python 3.11, `uvicorn main:app`, 단일 worker를 사용한다.
   `requirements.txt`와 `requirements-genlook.txt`가 모두 필요하다.
3. Dockerfile의 기존 의류 탐지 기능은 CPU torch/torchvision 및 탐지 모델 다운로드를 포함한다.
   컨테이너 빌드 시간·용량·라이선스와 기존 Railway 요금 범위는 담당자가 확인한다.
   로컬 단위 검사 통과는 Docker 이미지 빌드·운영 배포 성공이 아니다.
4. 상윤이 기존 프로젝트/환경/서비스를 명시하여 연결한다. 새 프로젝트를 만들지 않는다.
   다음은 담당자의 실제 ID를 넣고 배포 승인을 받은 뒤 사용하는 명령 형식이다.

   ```text
   railway link --project <existing-project-id> --environment <existing-environment-id> --service <existing-service-id>
   railway status
   railway up backend --path-as-root --service <existing-service-id> --environment <existing-environment-id>
   ```

   `--path-as-root`는 backend만 업로드하는 방식이다. 이 방식의 업로드 루트에 Dockerfile과
   railway.json이 위치하므로 서비스 Root Directory를 다시 `/backend`로 중복 지정하지 않는다.
   기존 서비스가 저장소 루트 기준 `/backend`를 사용한다면 담당자가 업로드 경계와 설정을 먼저
   일치시킨다. 연결된 프로젝트의 상위 폴더·개인 작업 디렉터리를 업로드하지 않는다.
   `railway up` 실행 전 업로드 대상에 실제 `.env`가 없는지 다시 확인한다.
5. 기존 start command override가 Dockerfile을 무시하지 않는지 확인한다. `PORT`는 Railway가
   지정한다. `/health`는 liveness이며 DB·로그인·Genlook 준비 완료를 의미하지 않는다.
   `FASHION_TRUSTED_PROXY_IPS`를 비워 두면 Railway(플랫폼이 `RAILWAY_PROJECT_ID` 주입)에서는
   edge 프록시를 신뢰(`*`)하고, 그 밖에서는 loopback만 신뢰한다. 근거: Railway edge는 고정 IP 대역이
   없고 HTTPS를 종료하며 `X-Forwarded-Proto`(항상 https)·`X-Forwarded-For`·`X-Real-IP`를 덮어쓴다.
   신뢰하지 않으면 모든 요청이 http로 보여 옷 분류·가상 피팅이 `403 https_required`(앱: "서버 오류 (403)")가 된다.
   Railway 앞에 다른 프록시를 두거나 컨테이너 포트를 직접 공개하면 이 값을 실제 범위로 명시한다.

공식 근거(2026-11-09 확인): [CLI link](https://docs.railway.com/cli/link),
[CLI up와 업로드 루트](https://docs.railway.com/cli/up),
[Healthcheck](https://docs.railway.com/deployments/healthchecks).

## 1단계: 생성 없이 연결 확인

기존 비밀변수는 출력하거나 교체하지 않는다. 원문은 Railway Variables에서만 관리한다.
`OUTFIT_VTON_PROVIDER=genlook`, `OUTFIT_VTON_ENABLED=false`, `GENLOOK_ENABLED=false`,
`GENLOOK_PAID_CALLS_APPROVED=false`로 연결 검증과 과금 승인을 분리한다.

- `/health`: HTTPS 200. `/api/outfit-vton/config`: 미인증/잘못된 Bearer 401.
- 기존 합법적 테스트 Bearer의 설정 조회: 200, `ready=false`. X-Device-Id로 인증을 대체하지 않는다.
- `/api/auth/providers`: 응답 자체와 실제 OAuth 성공을 구분한다.
- DB readiness와 사용자의 기존 기록을 허용된 비민감 범위에서 비교한다. 원문을 로그에 남기지 않는다.
- 서버 재시작 후 저널/결과 접근 제어·보관 기한·미확정 작업 차단을 확인한다.
  일회성 파일시스템에 예산 저널을 두거나 재배포 때 저널을 초기화하지 않는다.

## 2단계: 별도 승인 후 Genlook·로그인

`genlook.env.example`, `outfit-api.env.example`와 프로젝트의
`docs/GENLOOK_RESTORE_20261109.md` 설정 목록을 사용한다. 키 존재 여부만 기록하고 값은 출력하지 않는다.
실제 잔액·자동 충전 OFF·요청당/누적 비용 상한·동의·보관/삭제·운영 승인 ID가 준비되어야 한다.
P54의 2회 경로는 `clothing-pair-then-shoes`이며 중간 검수에는
`GENLOOK_OPERATOR_REVIEW_SECONDS`와 충분한 provider/service timeout을 함께 설정한다.
기존 예산 저널의 계약 불일치를 파일 삭제로 우회하지 않는다.

새 생성은 별도의 명시적 크레딧 승인 후 승인 입력 1세트만 실행한다. 품질이 심각하게 불합격이면
신발 단계를 중단한다. 자동 재호출 금지. P54의 E2E PASS와 상품 세부 품질 FAIL은 유지된다.

의찬 담당자는 실제 정식 패키지/승인 인증서와 Google web audience, Kakao callback을 확인한다.
신규 계정 연결·동일 계정 재로그인·빈 다른 계정 전환을 구분하고, 다른 계정으로 기존 옷장/대기열을
전송하지 않는지 확인한다. 다른 기기의 동일 계정 로그인은 현재 단일 활성 Bearer 정책상 이전 기기의
토큰을 무효화한다. 다중 기기 동시 로그인 성공으로 설명하지 않는다.

## 실패·복구

배포 ID, 코드 SHA, HTTP 상태 및 비민감 오류 코드만 기록한다. 키·사진·DB 원문·OAuth code를
로그/PR에 올리지 않는다. 이전 배포로 복귀하더라도 DB/예산 저널/사용자 결과를 과거 파일로 덮어쓰지
않는다. 코드 롤백과 DB 복구는 별개로 승인한다. 서명·운영·실제 OAuth/E2E·상품 품질이 미충족이면 NO-GO다.
