# P31 — 실제 실행 준비·합성 분석·DB 이력 보호

## 최신 상태 — 코드 개선·로컬 집중 검증 완료, 실제 DB/GPU/실기기는 미검증

- 전용 브랜치: `codex/fashionai-p31-validation-db-20261105`.
- 코드 체크포인트 SHA: `1eccb83999697a0dac3c7fe9b50b10c1424d2f9e`. 당시 Android 대기로 저장했으며, 이후 같은 코드의 검사 완료를 확인했다. 최종 후속 커밋은 이 문서만 변경한다.
- 시작 P30 HEAD: `8c6f94dccc4c22e0501460e84d57da8103872c51` (로컬/원격 일치, 시작 시 clean).
- 실제 담당은 총괄 1명과 병렬 하위 에이전트 3명이다. 요청한 10개 전문 책임을 세 트랙으로 나눴으며 10개 에이전트를 실행했다고 주장하지 않는다.
- 팀 main 읽기 전용 재확인 SHA: `411d008ebf542ec04c4966936303a686b4f784e5`. 팀 브랜치에 쓰지 않았다. P31 Actions 실행 조회 `total_count=0`.
- 시작 C: 여유 공간 약 16.92 GiB. 한 담당자만 Gradle 실행. Actions/PR/Artifact 업로드/운영 배포/DB 연결/새 APK 설치 없음.
- 읽기 전용 ADB 목록은 비어 있었다. `TEST_POSTGRES_URL`도 미설정이므로 실기기 설치 정보·데이터 기준 및 실제 PostgreSQL 검증은 수행하지 않았다.
- 기존 18602 원본 APK SHA256 `2a1d28749f1839f59906ec4972b263ca1d522fa745e4920f63c2849655c67a63`, 18603 원본 APK SHA256 `efaaed2a811911ec79ad5e5a2154502ded5879c0e439c5cfe9cbec7b3977c20f` 재확인 일치. 기존 납품 폴더는 쓰지 않았다. 기기 데이터 보존을 새로 검증한 것은 아니다.

## A. 실제 구현

### VTON Parser/SegFormer

- P30의 3파일 manifest 밖에서 Parser가 mutable HF 캐시를 선택하던 경로를 별도 로컬 번들로 제한했다. Parser 자료가 없는데 CLI preflight/서버 ready가 통과하던 합성 반례 2개를 재현했다.
- `parser.manifest.json`으로 config/safetensors/개별 라이선스 검토 파일의 SHA256, 필수 파일 집합·경로·원격 코드 설정을 검사한다. 원래 본체/DWPose manifest 형식은 유지한다.
- 고정 VTON/Parser revision 및 공식 설치 출처를 확인한 뒤 좁은 subclass로 로컬 Parser를 로딩한다. `local_files_only=True`, `use_safetensors=True`, `trust_remote_code=False`; 전역 monkey patch나 cache fallback 없음.
- 실제 worker 직전에도 재검사한다. 준비 검사에는 필수 `transformers`도 포함한다. 준비 도구의 암묵적인 Parser 다운로드를 제거했다.
- 운영자가 제공한 파일/검토 기록의 무결성 검사이며 독립 출처 인증·법적 허가 판정이 아니다. [고정 Parser 구현](https://github.com/fashn-AI/fashn-human-parser/blob/f2771f2fb8655349e87e2869bbde7ace0bd06f2c/src/fashn_human_parser/parser.py)과 [고정 SegFormer 라이선스](https://github.com/NVlabs/SegFormer/blob/65fa8cfa9b52b6ee7e8897a98705abf8570f9e32/LICENSE)를 구분해 문서화했다. 모델·라이선스 다운로드/수락이나 실제 GPU 실행 없음.
- **호환성 영향:** 기존 3개 가중치만 준비된 서버는 이제 준비 완료가 아니다. 검토된 Parser 번들과 고정 설치 revision이 필요하다. 18603의 기존 API 계약은 유지되며, 새 사유는 기존 일반 오류 안내로 표시하고 생성은 차단한다.
- 상세 준비 절차: `backend/LOCAL_VTON_PARSER_BUNDLE.md`, `backend/LOCAL_VTON_SERVER.md`.

### DB 실행 이력과 실패 보호

- 기존 파일별 autocommit·무이력 재실행을 제거했다. 원본 `001`~`006` SQL 파일과 번호는 변경하지 않았다.
- 검토된 전체 계획의 체크섬·적용 prefix·지정된 기존 로컬 시험 스키마를 요구한다. 이력이 없다고 미적용으로 추측하거나 현재 파일로 과거 이력을 만들어 넣지 않는다.
- 적용 ledger와 미적용 DDL을 한 트랜잭션으로 처리하고, 동시 실행 잠금·기존 적용 건너뛰기·변조/누락/순서 검사·커밋 응답 유실의 `unknown` 처리를 추가했다.
- 지원 SQL은 현재 프로젝트의 제한된 DDL 문법이다. 임의 함수·schema 변경·내부 transaction·CHECK/generated/custom type 등을 거절한다. 범용 SQL sandbox나 실제 PostgreSQL 검증을 대신하지 않는다.
- **호환성 영향:** 인자 없는 `python apply_migrations.py`는 이제 DB 접속 전에 차단된다. `.env`/`DATABASE_URL`로 운영 DB에 자동 적용하지 않는다. `--dry-run`은 계속 DB 없이 사용 가능하다. 실행은 독립 검토 manifest와 `--apply-local-test --confirm-local-test --schema ... --review-file ...`가 모두 필요하다.
- API 503/서버 시작 안내와 `DB_INTEGRATION.md`의 오래된 즉시 실행·기기 번호 인증/복구 설명도 현재 보호 절차로 정정했다. 인증·소유권·동기화 로직은 바꾸지 않았다.
- 팀 `005_user_logins.sql`의 임의 번호 변경/이식이나 새 `007` 적용을 하지 않았다. 실제 이력·스키마 확인 후 후속 파일을 추가하는 절차만 구현했다. 최초 SQL은 기존 기본 테이블도 요구하므로 빈 DB에 6개 SQL만 실행하면 초기화 완료가 아니다.
- 상세 승인/실행 절차와 한계: `backend/MIGRATION_PREFLIGHT.md`.

## B. 변경 범위

- Track A: `local_vton.py`, `local_vton_worker.py`, `local_vton_server.py`, 신규 `local_vton_parser.py`, `prepare_local_vton_weights.py`; 관련 VTON 테스트 7개 및 준비 문서 3개.
- Track C: `apply_migrations.py`, 신규 `migration_runner.py`, 신규 `tests/test_migration_runner.py`, `MIGRATION_PREFLIGHT.md`.
- 총괄: `db_api.py`, `main.py`의 안내 문구, `DB_INTEGRATION.md`, 이 인수인계.
- Track B: 신규 `P31SyntheticBodyPipelineTest.kt`만. Android 실행 코드·의존성·저장 형식·서명·워크플로 변경 없음.

## C. 실제 로컬 검사

| 검사 | 결과 | 정확한 범위 |
|---|---|---|
| VTON 집중 묶음 | 82 passed / 1 skipped | 모의 모델/파일·어댑터·서버 보호. 실제 CUDA opt-in 1개 skip |
| Migration preflight/runner | 58 passed | 기존 12 + 신규 46. DBAPI 모의 계약, 기존 SQL 6개 문법 수용. 실제 PostgreSQL 아님 |
| 인증/소유권/복구 | 3 passed | 격리 SQLite, 장치 ID/잘못된 Bearer/계정 간 접근/토큰 교체 보호 |
| 신규 Android 합성 검사 | 3 passed / 0 skipped | API35, 새 클래스만 실행. 실패·오류 0; 최종 XML·exit 0·BUILD SUCCESSFUL 대조 |
| 기존 EXIF/Coil/CameraX | 기존 P18 결과 재사용 | `docs/P18_CAMERA_COORDINATE_VALIDATION_20261104.md`의 API27 4/API35 4, CameraX 1. 새 수행 수에 합산하지 않음 |
| 기존 Preview 계정/대기열/복원 보호 | 기존 P30 P23 계열 결과 재사용 | 해당 Android 실행 코드가 바뀌지 않아 반복하지 않음. 새 실기기 데이터 보존 검증 아님 |

- 검사 로그/XML은 Git 제외 `fashion_app/build/p31-private/`에 보존한다. 개인 사진/토큰/기기 식별값/전체 로그는 커밋하지 않는다.
- VTON 초기 fixture import 오류는 상대 import로 수정했고 최종 82개 통과. 완료된 동일 묶음을 다시 실행하지 않았다.
- 인증 회귀의 의존성 deprecation warning은 성공과 구분해 로그에 남겼다.
- 새 Android 검사는 가로 합성 mask→픽셀/cm/정규화 guide/Fit 여백, 머리·발 잘림/추가 전경/빈 mask의 세션·저장 콜백 보호, 독립 JSON의 기록 parser와 정면 너비/직접 입력 둘레 출처를 검사한다. 실제 detector/DB round trip/실사진 정확도 검사가 아니다.
- Android 실행은 총 27분 41초(컴파일 포함), XML suite는 51.896초다. 메모리 압박 중 기존 실행을 중단/중복하지 않았다. 이 시간은 실제 사진 분석이나 모델 추론 성능 수치가 아니다.
- 최종 Android XML SHA256: `18dd7e98aacfb95388a79953acd711a08683786f6b9852203c8b18813f69d28b`.
- P30 대비 Android 실행 코드/의존성, 원본 SQL, Workflow diff가 없음을 확인했다. 전체 CI·Lint·배포 APK는 다시 만들지 않았다.

## D~H. 실기기·DB·GPU·APK·남은 조건

- **실기기:** 연결되지 않아 설치 상태·신규 데이터 기준·화면 조작 미검증. 카메라·갤러리·사진 접근/설치 승인 요청을 반복하지 않았다. 18603 설치는 별도 승인 대상이다.
- **격리 DB:** 승인된 URL·복사본·독립 적용 이력 미제공. 실제 SQL 제약/락/rollback/팀 DB 통합은 미완료다. DBAPI 모의 결과로 운영 데이터 보존을 보증하지 않는다.
- **GPU:** 승인된 CUDA/모델/입력이 없어 실제 로딩·추론·신규 PNG·Android 결과 수신 없음. 기존 GPU 후보 재조사 없이 준비 코드만 보완했다.
- **APK:** Android 실행 코드가 그대로여서 새 APK가 필요하지 않다. 기존 18603 실행 SHA `27945bc1247702c30ea3aa5466700447c962616e`와 납품 후보를 유지한다. 새 backend 코드는 APK에 포함되거나 운영 서버에 배포된 것이 아니다.
- **다음 실제 실행:** 승인된 GPU+기존 검토 자산/라이선스+비개인 입력으로 독립 실제 worker 시험 → 품질 확인 → 별도 기기/입력 승인 후 Android 수신·비교·저장. DB는 승인된 `fashionai_test` 복사본과 검토 manifest를 먼저 확보한다. 어떤 경로도 이번 지시로 운영 배포·개인 사진 전송을 승인받은 것으로 보지 않는다.
- **정확도:** 단일 사진 추정 너비와 둘레를 구분한다. 실측 기준이 없어 MAE·정확도·성공률을 만들지 않았다.

## 전체 변경 파일 (24개)

- `backend/DB_INTEGRATION.md`
- `backend/LOCAL_VTON_PARSER_BUNDLE.md`
- `backend/LOCAL_VTON_SERVER.md`
- `backend/LOCAL_VTON_SETUP.md`
- `backend/MIGRATION_PREFLIGHT.md`
- `backend/apply_migrations.py`
- `backend/db_api.py`
- `backend/local_vton.py`
- `backend/local_vton_parser.py`
- `backend/local_vton_server.py`
- `backend/local_vton_worker.py`
- `backend/main.py`
- `backend/migration_runner.py`
- `backend/prepare_local_vton_weights.py`
- `backend/tests/test_local_vton.py`
- `backend/tests/test_local_vton_execution_guards.py`
- `backend/tests/test_local_vton_manifest.py`
- `backend/tests/test_local_vton_parser_integrity.py`
- `backend/tests/test_local_vton_real_model.py`
- `backend/tests/test_local_vton_server.py`
- `backend/tests/test_migration_runner.py`
- `backend/tests/vton_parser_fixtures.py`
- `docs/P31_VALIDATION_DB_HANDOFF_20261105.md`
- `fashion_app/app/src/test/java/com/jeiu/fashionproject/P31SyntheticBodyPipelineTest.kt`
