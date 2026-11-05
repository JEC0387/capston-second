# P35 정식 출시 인수 — NO-GO

P34 실행 코드 `59982bef545862aa97945998521837b84805a4ec`, 문서 기준
`f0095616e58a023d67303eb2089e8241594e97e6`에서 독립 브랜치
`codex/fashionai-p35-release-acceptance-20261105`로 진행한다.
Android/Backend 제품 코드는 변경하지 않는다. 완료한 P34 테스트·Lint·Preview 빌드는 재실행하지 않는다.

## 데이터 검수 (P35 실제 읽기 전용 수행)

2026-11-05 18:25 KST, S25+ Preview 18605 / QA 18001 확인.
기기 조작·설치·삭제·초기화·사진 읽기/전송·새 AI 생성 요청 없음.
기기 식별값, 원문 XML/DB/토큰/사진은 출력하거나 파일에 저장하지 않았다.
원문은 필요한 고정 파일만 메모리에서 해석하고, 출력은 집계/형식 검사뿐이다.

증거(비공개 Git 제외): `fashion_app/build/p35-private/audit03/`.
`AUDIT.json`, 경로를 해시 처리한 `before-hashes.json`, `after-hashes.json`.
audit01/02는 검사 도구의 명령 인수/목록 형식 문제로 차단된 빈 증거 경로이며 보존했다.
최종 도구는 shell 중첩 대신 직접 읽기 명령을 사용한다. 앱 결함이 아니다.

| 범위 | 결과 |
|---|---|
| P35 검사 전/후 24개 파일 | 24 동일, 변경/누락/신규 0 |
| P34 사용 종료 → P35 | 24 동일, 변경/누락/신규 0 |
| P34 설치 직전/직후 → P35 | 19 동일, 알려진 5 변경, 누락/신규 0 |
| 옷장 | 12개 레코드, 중복/잘못된 ID 0, 참조 형식 오류 0; 파일은 설치 전과 동일 |
| 아바타 프로필 | 파일 존재, 설치 전과 동일 |
| 수동 신체 치수 파일 | 설치 전/현재 모두 없음; 이번 업데이트로 누락된 것으로 판정하지 않음 |
| 공식 예제 생성 저널 | 존재, 설치 전과 동일; 결과 이미지 재표시/외부 저장 파일 확인과 다름 |
| 옷장 이미지 참조 | 중복 제거된 18개가 앱 리소스 URI; 외부/내부 사진 파일 참조 0. 리소스 해석/표시는 이번에 미검증 |

### 변경된 5개 파일

- `weather_alert.xml`: 항목 8 / 기억된 날짜별 기온 6. XML·타입·날짜·좌표 범위 오류 0.
  좌표·기온·날짜 원문 출력 없음. WeatherViewModel → WeatherAlertScheduler 저장 경로와 일치하는 형식.
- `recommendation_log.xml`: 40개 기록. 형식 오류/중복 조합 0, 1,000개 보존 상한 이내,
  현 옷장 밖 참조 0. HomeScreen의 추천 노출 기록 경로와 일치하는 형식.
- `profileInstalled`: 24바이트, schema 1. 캐시된 AndroidX profileinstaller 1.4.0의
  Cache 직렬화 형식(int/int/long/long)을 javap으로 대조했다.
  결과 327680은 `RESULT_CODE_ERROR_NO_PROFILE_EMBEDDED`이다.
  **사용자 체형 프로필이 아니라 ART 성능 프로파일 상태**이며, 성능 프로파일 설치 성공으로 표시하지 않는다.
- `androidx.work.workdb-wal`: 4KB 페이지, 커밋 프레임 37 / 논리 DB 26페이지.
  WAL 헤더·프레임 체크섬/커밋 경계 통과.
- `androidx.work.workdb-shm`: 헤더 복제·체크섬·WAL salt/프레임 정합 통과.
  기기 DB를 열지 않고 메모리에서 복구한 복사본의 SQLite quick_check 통과, WorkSpec 테이블 존재.
  WAL은 실제 커밋 데이터를 포함하므로 임시파일이라며 삭제하면 안 된다.
  형식 근거: [SQLite WAL](https://www.sqlite.org/fileformat2.html#wal_file_format),
  [WAL/SHM 형식](https://www.sqlite.org/walformat.html).

현재 구조 손상이나 비교 범위의 누락 근거는 없다. 런타임 쓰기 경로와 정합한다.
다만 P34 변경 전 원문이 없으므로 정확한 과거 필드 차이·각 쓰기 주체를 소급 확정하지 않는다.
해시 목록은 비원자적이다. 검사 전후 일치는 경합 위험을 줄이지만 트랜잭션 스냅샷은 아니다.
cache, 외부/SAF 저장 이미지, Keystore, 서버 기록은 비교 밖이다.
로컬 VTON의 메모리 결과와 외부 저장 PNG는 새로 검증하지 않았다.
P34 REVIEW_REQUIRED 기록은 덮어쓰지 않고 본 후속 판단을 추가한다.

## 검사 도구 및 집중 검증

`tools/preservation_structure.py`, `tools/preservation_device_audit.py`,
`tools/test_preservation_structure.py` 추가. 제품 코드/저장 형식 변경 없음.
합성 SQLite가 직접 생성한 WAL/SHM, 체크섬 손상/잘림/헤더 불일치, 잔여 WAL 꼬리,
민감값 비출력, 잘못된 XML, 추천 참조 구분을 5개 테스트에서 검사했다.
첫 실행은 SQLite 연결이 닫히지 않아 Windows 테스트 임시파일 정리가 실패했다(4 통과/1 오류).
명시적 close 적용 후 5/5 통과. 앱·기기 DB에는 쓰지 않았다.

## 실제 생성 및 분석 E2E

새로 승인된 자체 생성 서비스/입력은 현재 제공되지 않았다. GPU 후보 재조사, 가중치 다운로드,
라이선스 수락, 결제, 개인 사진 업로드를 하지 않았다.
공식 공개 예제 모드의 이전 성공은 개인 사진용 자체 모델 E2E 성공으로 대체하지 않는다.
필요 조건: 사용 허가된 실행 서버·GPU/모델/Parser/캐시·라이선스 검증,
인증된 HTTPS 접속과 보관/삭제 정책, 승인된 비개인 입력, 이후 Android 수신/비교/저장/재조회 증거.
기존 `backend/LOCAL_VTON_SERVER.md` 절차 및 P30~P34 manifest/worker를 재사용한다.

체형 분석: 승인된 유효 전신 시험 입력과 실측 기준 미제공.
동일 Android 실행 소스의 분리 QA 후보 준비 중이며, 기존 QA 18001에는 설치하지 않는다.
설치 시 버전별 승인 필요. Preview 기록으로 저장/교체 실험하지 않는다.

## 운영 호환성·정식 서명·팀 통합

18:26 KST 기본 Railway 주소의 GET `/health`, `/api/auth/status`, `/api/tryon/config` 모두 404.
토큰/사진 전송 및 운영 쓰기 0. 해당 주소로 현 프로토콜을 검증할 수 없다.
404만으로 배포 커밋, 환경변수, DB 상태, 정확한 원인을 단정하지 않는다.
운영 담당자의 서비스/도메인/배포 SHA 확인 및 별도 승인된 적용 후 재검증이 필요하다.
소스상 DataAuth는 목적지 바인딩 Bearer, DbSync는 Preview에서 비활성화된다.

팀 main API 재확인: `411d008ebf542ec04c4966936303a686b4f784e5` 유지.
P19 상품 식별/체형 제안, P23 게스트 보존, P31 마이그레이션 보호의 선별 반영 상태를 재사용한다.
팀 전체 통합, OAuth, 운영 SQL 적용 완료 아님. 팀 소스 덮어쓰기/Push/DB 변경 없음.

정식 `com.jeiu.fashionproject`와 Preview `.preview`, QA `.preview.qa`를 구분한다.
Release 서명 설정/승인된 정식 키 담당자/Play App Signing 정책은 여전히 미확정이다.
Preview 키의 전용·새 정식 키 발급을 하지 않는다. 담당자 및 기존 키/Play 상태를 확인하고,
발급 승인 → 접근 제한 보관 → 별도 암호화 백업 2개 → 복구 검증 → 정식 서명 연결 후 진행해야 한다.

## 인수 판정

| 항목 | 상태 | 근거/한계 |
|---|---|---|
| 접근 가능한 데이터 보존/구조 | PASS | 위 24개 범위; 앱 전체/과거 필드 변화 보증 아님 |
| Preview 업데이트·홈·앱정보·선택화면 왕복 | PASS (P34 재사용) | 18605; P35 재실행 없음 |
| 오류·취소·중복 방지·저장 실패 보호 | PASS (자동 검사만) | P34 Android 53 / Backend 9; 실기기 전체 버튼 미검증 |
| 동일 코드 QA 준비 | 진행 중 | 결과 아래 추가; 아직 미설치 |
| 체형 사진→분석→저장→재실행→재조회 | BLOCKED | 승인된 입력 및 새 QA 버전 설치 승인 없음 |
| 실측 정확도 | BLOCKED | 대응하는 실측 기준 없음 |
| 실제 자체 모델→Android→저장/재조회 | BLOCKED | 승인된 실행 서비스/모델/입력 없음 |
| 전체 버튼·재시도·앱 종료 후 복구 실기기 | NOT TESTED | 일부 P34 화면 성공으로 확대하지 않음 |
| 운영 API 호환성 | FAIL | 현재 기본 주소에서 필수 경로 404; 배포 설정 원인 미확인 |
| 정식 Release 서명·설치·실행 | BLOCKED | 키/정책 미확정, Preview로 대체 불가 |
| 16KB 런타임 | NOT TESTED | 기존 정적 검사 통과만 재사용 |

**정식 출시 NO-GO.** 데이터 구조 검사 통과가 모델 실행·측정 정확도·정식 서명 차단을 해소하지 않는다.
