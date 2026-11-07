# P29 — Actions 사용량 보호 (2026-11-05 KST)

## 확인한 상태

- 작업 브랜치: `codex/fashionai-p17-camera-personal-vton-20261104`.
- 기본 브랜치: `integration/team-merge`, 확인 SHA `016dbacc14cd65ef75a79518536de2bd5024d34d`.
- 해당 SHA의 전체 Git Tree(`truncated=false`)에 `.github/workflows/` 파일이 하나도 없음. 대상 파일 Contents API도 404.
- Actions 등록 목록에는 과거 `android-debug-apk.yml`과 `body-analysis-debug-apk.yml`이 active로 남아 있음. 이는 기본 브랜치 파일 존재나 수동 실행 성공의 증거가 아님.
- 검사 당시 in_progress/queued/waiting/requested/pending 실행 0개, 최근 실행 #86은 종료 상태.
- 열린 PR 0개. 필수 검사/보호 규칙 조회는 앞선 검사에서 요금제 제한 403이었음. 보호 규칙을 변경하거나 우회하지 않음.
- 사용량 90%는 사용자 알림 기준. 잔여 분·요금·예산을 직접 확인한 값으로 취급하지 않음.

## 작업 브랜치에 적용할 비용 보호

- Push 트리거 제거. PR 경로 필터, `build`, `backend`, `server-start` Job ID와 기존 전체 검증 유지.
- 수동 `check_level=light`가 기본: Ubuntu 1개 Job, 최대 5분, 표준 라이브러리 기반 릴리스 도구 검사만 수행. Android/Backend 검증이나 APK 배포 성공으로 표현하지 않음.
- `check_level=full`: 기존 Android/Preview/Backend/Lint, Windows Python 4종 + Linux 시작 검사와 서명·버전·정렬·배포 게이트 유지.
- 경량 경로에 서명 비밀키, APK 빌드·업로드, 의존성 설치 없음. 경량 실행과 전체/PR 실행의 concurrency를 분리.
- 이번 커밋은 사용자의 지시대로 `[skip ci]`를 사용. 실행/대기 취소, 이력·Artifact 삭제, 기본 브랜치 변경 없음.
- 이 변경을 포함하지 않는 다른 브랜치의 과거 Push 트리거까지 비활성화하는 저장소 전역 설정은 아님.

## 수동 실행 — 현재는 준비 미완료

[GitHub 공식 문서](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)는 `workflow_dispatch`가 기본 브랜치에 존재해야 한다고 명시한다. 현재는 조건 미충족이다. API 실행 요청으로 확인하지 않았으며, 경량/전체 수동 실행을 사용 가능하거나 성공했다고 보고하지 않는다.

### 별도 승인 후 최소 등록안 — 아직 적용하지 않음

기본 브랜치의 정상 변경 절차로 **아래 파일 하나만 새로 추가**한다. 독립된 작업 브랜치의 이력을 merge/cherry-pick하거나 팀 소스, 기본 브랜치 지정, 보호 규칙을 바꾸지 않는다. 기존 등록 파일과 동일한 경로를 사용한다.

대상: `.github/workflows/android-debug-apk.yml`

```yaml
name: Android debug APK
run-name: Registration only on default branch — select the reviewed work branch
on:
  workflow_dispatch:
    inputs:
      check_level:
        description: 'Select the reviewed work branch; default branch performs no validation'
        required: true
        type: choice
        default: light
        options:
          - light
          - full
permissions:
  contents: read
jobs:
  registration-only-no-validation:
    if: ${{ false }}
    runs-on: ubuntu-24.04
    steps:
      - run: echo 'Registration only. No validation or APK publication.'
```

등록 전용 기본 브랜치 파일은 runner를 실행하지 않으며 검사도 하지 않는다. 기본 브랜치를 잘못 선택했을 때 표시되는 skipped 결과는 검증 성공이 아니다. 실제 검증은 검토된 작업 브랜치를 선택해야 그 브랜치의 전체 Workflow 정의로 수행된다. 다른 과거 브랜치의 동일 파일은 전체 검사를 기본으로 실행할 수 있으므로 임의의 ref를 선택하지 않는다.

승인 후 적용 순서:

1. 기본 브랜치 최신 SHA와 등록 파일 부재를 다시 확인하고 보호 규칙을 따르는 단일 파일 변경을 검토한다.
2. 기본 브랜치 등록 파일 및 `workflow_dispatch`/입력 스키마를 읽기 전용으로 확인한다.
3. 실제 실행 비용 승인을 받은 후에만 Branch를 위 작업 브랜치로 지정하고 `light` 또는 `full`을 선택한다. 이 문서는 실행·유료 사용 승인이 아니다.
4. PR 검사는 경량 검사나 수동 실행으로 대체하지 않는다. 현재 `[skip ci]` HEAD를 나중에 PR로 제출하면 필수 검사가 pending일 수 있다. 병합을 강행하지 않고, 승인된 검증 시점에 skip 표시 없는 후속 커밋으로 원래 PR 검사를 수행한다.

[GitHub skip 문서](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/skip-workflow-runs)는 skip된 필수 검사가 pending 상태로 병합을 차단할 수 있음을 설명한다. 이번에는 PR를 생성하지 않는다.

## 로컬 검증과 보존

- 릴리스 도구/정렬/Artifact 보존/실행 정책 검사 **17개 통과**. Git diff 공백 검사 통과.
- 기존 full Job 본문은 라우팅 조건과 추가 정책 테스트 호출을 제외하고 변경하지 않았으며, PR paths는 원본과 동일함을 별도 비교한다.
- GitHub 실행 환경의 구문/동작 검증은 새로 수행하지 않음. 전체 CI·APK 재빌드 불필요.
- 앱 실행 SHA는 `090384be0d1adda2f8a766ccfd4fc96099c0dffa` 그대로.
- 18602 APK SHA256: `2a1d28749f1839f59906ec4972b263ca1d522fa745e4920f63c2849655c67a63`.
- 휴대폰/ADB·사용자 데이터·운영 DB에 접근하거나 수정하지 않음. P28 데이터 보존 결과를 이번에 새로 검증한 것으로 주장하지 않음.
