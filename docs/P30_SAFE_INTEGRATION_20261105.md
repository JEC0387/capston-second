# P30 — 안전 통합 및 실제 실행 준비 (2026-11-05)

## 현재 상태 — 소스 체크포인트, 최종 Android 검증 전

- 전용 브랜치: `codex/fashionai-p30-safe-integration-20261105`.
- 시작 SHA: `cdbc6236637e1916f755e0f760c6c877137ac03f`. 기존 P17 작업 브랜치와 팀 main은 변경하지 않는다.
- 실제 담당: 총괄 1명 + 병렬 하위 에이전트 3명. 요청한 10개 전문 역할을 세 트랙으로 묶어 수행했다.
- GitHub Actions 실행·PR 생성·Artifact 업로드 없음. P29의 Push 트리거 제거 및 PR/릴리스 게이트 유지.
- 기존 Preview 18602 및 P24/P26 납품본 유지. P30에서 기기 연결 목록은 비어 있었으며 설치·화면·사진 검증을 수행하지 않았다.

## A. VTON

- CLI/실제 worker가 모델 로드 직전에 manifest를 검증하고 Hugging Face/Transformers를 오프라인으로 고정한다. 누락 캐시는 요청 중 다운로드하지 않는다.
- CLI의 `--overwrite` 미지정 출력은 같은 디렉터리의 완성된 임시 PNG를 원자적 hard-link로 게시한다. 추론 중 생긴 기존 파일을 덮어쓰지 않는다. 지원하지 않는 파일시스템은 안전하게 실패한다.
- 준비 상태 검사에 Human Parser 의존성 포함. 자체 AI 화면에서 2D 자동맞춤과 마법봉 설명을 구분했다.
- 실제 production worker/CUDA용 opt-in 시험을 추가했다. 현재 승인된 GPU·모델·입력이 없어 **skip이며 생성 성공이 아니다**.
- 별도 Android Repository → 실제 loopback FastAPI 계약 시험 추가: 인증 401, 정상 인증, 미준비 모델 차단. 이미지/POST/모델 추론/기기 검증이 아니다. 최종 실행 결과는 아래에 추가한다.
- 가중치 manifest 범위는 FASHN/DWPose 3개 파일. Parser/SegFormer 캐시 무결성·개별 라이선스 확인은 남아 있다. 상업적 사용 가능 판정 없음.

## B. 합성 체형 입력

- 원래 코드는 큰 상부 배경 영역이 한 픽셀 폭 연결부로 인물에 연결되면 동일 연결요소로 받아들였다.
- RED02: `connectedExtraUpperForegroundCannotChangeHeightCalibration` 1개 실행/1개 실패. 기대 `EXTRA_FOREGROUND`, 실제 `null`로 재현했다.
- RED01은 테스트 JVM의 `Redirect.DISCARD` 컴파일 호환성 문제이며 앱 결함 증거로 계산하지 않는다. 표준 null 출력으로 테스트 실행 환경만 수정했다.
- 기존 5% 추가 영역 허용 기준과 랜드마크 ±2픽셀 범위를 사용해 어깨 위에서 머리와 분리된 큰 영역을 거절한다. 작은 잡음과 넓은 연결된 목 윤곽은 유지하는 독립 기대값 회귀를 추가했다.
- 실제 사람 수 검출·유효 전신 실사진·실측 MAE 검증이 아니다. P18 합성 방향/좌표 결과와 P22 사진 Fit 변환을 대체하거나 약화하지 않았다.

## C. 팀 통합 및 데이터 보존

- 팀 main 확인 SHA: `411d008ebf542ec04c4966936303a686b4f784e5`.
- 기존 P19 상품/체형 제안 및 P23 게스트 안전 기능 유지. 이번에 새 팀 화면이나 소셜 인증을 이식하지 않았다.
- 팀의 동기화 실패 후 로컬 삭제, `X-Device-Id` 인증, 계약이 다른 ProductRecommender 의존성 일괄 이식은 제외했다.
- `005_auth_credentials.sql`과 팀 `005_user_logins.sql` 동시 적용 위험: 중복 번호를 DB import/접속 전에 차단한다. dry-run은 DB 설정도 로드하지 않는다.
- 선택적 독립 적용 이력 manifest의 파일명/체크섬/번호 충돌을 검사한다. 이력이 없으면 `pendingFiles=null`, `schemaValidated=false`; 비어 있는 DB라고 가정하지 않는다.
- SQL 파일·번호·운영 스키마는 변경하지 않았다. 실제 적용 이력과 격리 PostgreSQL 복사본이 없어 **SQL 통합 완료 아님**. 로컬 Docker 정보 확인도 제한 시간 내 응답하지 않았다.
- 자세한 절차: `backend/MIGRATION_PREFLIGHT.md`. 기존 Bearer/계정 소유권/복구 저널/대기열/Preview 격리 유지.

## 검증 기록

- Backend 최종 집중 묶음: **68 passed, 1 skipped**, exit 0. VTON/manifest/server/실행 가드/마이그레이션 preflight 및 기존 계정 격리·복구 3개 포함. 실제 CUDA 시험 1개 skip.
- 계정 테스트는 격리 SQLite, SQL preflight는 임시 파일 기반이다. PostgreSQL 실행이나 운영 데이터 보존 검증으로 확대하지 않는다.
- Android 최종 집중 검사·Lint·새 후보 APK: 체크포인트 시점 대기. 이전 P16 CI를 새 코드의 성공 증거로 쓰지 않는다.
- 실패 로그/최종 XML은 Git 제외 `fashion_app/build/p30-private/`에 별도 보존한다. 사진·토큰·기기 식별값·전체 로그는 커밋하지 않는다.

## 남은 외부 조건

1. 승인된 CUDA 환경, 고정 모델 revision, 기존 가중치/Parser 캐시·라이선스, 승인된 비개인 입력이 있어야 실제 자체 추론 시험 가능. `backend/LOCAL_VTON_SERVER.md`의 명시적 opt-in 절차를 따른다.
2. 실제 전신 분석·측정 정확도는 지정 입력과 실측 기준, 기기 접근이 필요하다. 개인 사진 외부 전송은 승인되지 않았다.
3. 팀 인증/SQL 통합은 독립 적용 이력·스키마와 보호된 PostgreSQL 테스트 복사본, OAuth 설정/별도 승인 필요.
4. 새 APK가 검증돼도 설치 승인은 별도다. P30은 실제 AI 생성·실기기 전체 검증·팀 전체 통합 완료가 아니다.
