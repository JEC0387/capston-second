# 문서 모음

저장소 첫 화면을 깔끔하게 하려고 보고서 · 인계 문서를 이 폴더로 모았습니다. 실행 방법과 기능 목록은
저장소 첫 화면의 [`README.md`](../README.md)와 [`수정사항.md`](../수정사항.md)를 보세요.

## 발표 준비

최신 빌드 오류 수정·검증·APK는 [2026-11-02 Preview 빌드 인수인계](PREVIEW_BUILD_FIX_HANDOFF_20261102.md)를 확인하세요.
서명·백업 배경은 [APK 업데이트 인수인계](APK_UPDATE_HANDOFF_20261026.md)에 보존했습니다. 배포 패키지는 **FashionAI Preview**입니다.

| 문서 | 내용 |
|---|---|
| [PRESENTATION_TEST_CHECKLIST.md](PRESENTATION_TEST_CHECKLIST.md) | 발표 전 실제 폰에서 눌러 볼 순서 (점검표) |
| [PRESENTATION_QUALITY_REPORT.md](PRESENTATION_QUALITY_REPORT.md) | 발표 버전 품질 점검 결과 |
| [APK_DOWNLOAD.md](APK_DOWNLOAD.md) | 테스트용 APK 받는 법 (이지민 저장소 기준 — 이 저장소는 Actions 탭의 `fashion-app-debug-apk`) |

## 개인정보 · 외부 전송
| 문서 | 내용 |
|---|---|
| [BODY_DATA_PRIVACY.md](BODY_DATA_PRIVACY.md) | 체형 사진 · 측정값 처리, 저장 동의, 서버 기록 삭제, 백업 코드 |
| [AI_TRYON_STATUS.md](AI_TRYON_STATUS.md) | 이전 클라이언트 직접 호출 방식의 기록. 현재 방식은 [서버 설정](../backend/AI_TRYON_SETUP.md) 참고 |
| [P0_AUTH_HANDOFF_20261025.md](P0_AUTH_HANDOFF_20261025.md) | P0 인증·개인정보 보호 후속 구현과 최신 검증 결과 |
| [P1_CONNECTION_HANDOFF_20261025.md](P1_CONNECTION_HANDOFF_20261025.md) | 자동 서버 연결·DB 동기화·장애 복구와 출시 전 확인 |
| [PHASE1_REPORT.md](PHASE1_REPORT.md) | 최신 ZIP과 보안 브랜치 비교, P0~P2 통합·검증 결과 |

개인 DB API 인증 전환과 레거시 데이터 이전 절차: [backend/AUTH_MIGRATION.md](../backend/AUTH_MIGRATION.md).

## 체형 분석 · 실기기 수정 기록 (이지민, 10/18)
| 문서 | 내용 |
|---|---|
| [BODY_ANALYSIS_FIX_REPORT.md](BODY_ANALYSIS_FIX_REPORT.md) | 체형 분류 결함 원인과 수정 (판정 보류 · 실루엣 측정 개선) |
| [BODY_ANALYSIS_FIX_HANDOFF.md](BODY_ANALYSIS_FIX_HANDOFF.md) | 위 작업 인계 |
| [BODY_ANALYSIS_TEST_CHECKLIST.md](BODY_ANALYSIS_TEST_CHECKLIST.md) | 체형 분석 실기기 점검표 |
| [REAL_DEVICE_FIX_REPORT.md](REAL_DEVICE_FIX_REPORT.md) | 실기기 오류 수정 (촬영 버튼 · 가상 피팅 분리 · 옷장 스크롤) |
| [REAL_DEVICE_FIX_REQUIREMENTS.md](REAL_DEVICE_FIX_REQUIREMENTS.md) | 실기기 수정 요구사항 |
| [REAL_DEVICE_FIX_HANDOFF.md](REAL_DEVICE_FIX_HANDOFF.md) | 실기기 수정 인계 |
| [RESUME_PROMPT.md](RESUME_PROMPT.md) | 이어서 작업할 때 쓰던 안내 |

## 통합 · 인계

최신 신체 측정·3D 연결: [P7_BODY_3D_HANDOFF_20261102.md](P7_BODY_3D_HANDOFF_20261102.md). 출처별 승인·저장 및 측정 안정성 구현과 실기기/실사진 미검증 범위를 구분한다.

최신 자체 AI 개발: [P6_VTON_HANDOFF_20261102.md](P6_VTON_HANDOFF_20261102.md). 실제 GPU 생성은 미검증이며 수동·유료 AI와 분리한 개발용 경로다.

| 문서 | 내용 |
|---|---|
| [P2_P6_HANDOFF_20261025.md](P2_P6_HANDOFF_20261025.md) | P2~P6 실제 구현·부분 구현·자동 검증·후속 시작 지점 |
| [P2_P6_DEVICE_TEST_20261026.md](P2_P6_DEVICE_TEST_20261026.md) | APK 안전한 업데이트와 사용자 실기기 검증 안내 |
| [CHECKPOINT_HANDOFF_20261025.md](CHECKPOINT_HANDOFF_20261025.md) | P0/P1 완료 상태 중간 저장, 기존 검증 결과와 다음 세션 TODO |
| [HANDOFF.md](HANDOFF.md) | 10/13 통합본 인계 (옷장 관심 옷 필터 · 추천 범위) |
| [INTEGRATION_REPORT.md](INTEGRATION_REPORT.md) | 팀원 작업 통합 보고서 |
| [MERGE_SON_YURI.md](MERGE_SON_YURI.md) | 손유리 작업 병합 기록 (넣은 것 · 뺀 것) |

## 다른 폴더의 문서
- 서버 · DB: [`backend/README.md`](../backend/README.md), [`backend/DB_INTEGRATION.md`](../backend/DB_INTEGRATION.md),
  AI 성능 측정 [`backend/evaluation/README.md`](../backend/evaluation/README.md)
- 앱: [`fashion_app/FEATURES.md`](../fashion_app/FEATURES.md), [`fashion_app/BACKEND_CONNECTION.md`](../fashion_app/BACKEND_CONNECTION.md),
  [`fashion_app/MEDIAPIPE_SETUP.md`](../fashion_app/MEDIAPIPE_SETUP.md), 기획 [`fashion_app/plans/`](../fashion_app/plans/)
