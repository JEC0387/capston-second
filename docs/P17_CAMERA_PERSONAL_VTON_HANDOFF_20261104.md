# P17 카메라·기존 AI 결과 검증 인수인계 — 2026-11-04

## 최신 완료 상태

**S25+에서 사람 없는 가로 촬영, 홈 이동 후 카메라 복귀, 재촬영 진입·취소를 확인했다. 기존 P15 공식 예제 결과를 P16 Preview에서 새 생성 없이 조회·표시·원본 비교·새 PNG 저장하고 실제 디코딩/해시를 검증했다. 유효 전신 분석·측정 정확도·개인 사진 VTON 성공을 뜻하지 않는다. 실행 코드 수정과 새 APK는 없다.**

- 저장소: `jimnnieo-svg/capstone-integration-backup`
- 현재 브랜치: `codex/fashionai-p17-camera-personal-vton-20261104`
- 사용 worktree: `p16-device-20261104` 디렉터리. 중단 전에 생성된 P17 브랜치를 그대로 사용했다. P16 브랜치 ref는 변경하지 않았다.
- 재개 HEAD / 확인한 원격 P16 HEAD: `a8495114a581442e30fecd59294ef68b08bd3bba`. 재개 시 tracked diff/untracked 목록은 비어 있었다. 원격 P17은 아직 없었으며 강제 초기화하지 않았다.
- 검증된 실행 SHA: `94e4e3a7b5b0a5017f934e50ab840260b067fba7`. 해당 SHA와 `fashion_app`, `backend`, `tools`, `.github`의 차이 없음.
- 문서 백업 커밋의 정확한 SHA는 `git log -1 --format=%H -- docs/P17_CAMERA_PERSONAL_VTON_HANDOFF_20261104.md`로 확인한다. 최종 응답에도 전체 SHA를 제공한다.
- Preview: `com.jeiu.fashionproject.preview`, `1.1.79.1-preview`, **17901**. 기존 설치를 사용했고 재설치하지 않았다.
- 별도 QA: `com.jeiu.fashionproject.preview.qa` / FashionAI QA P16. 기존 사용자 Preview와 분리했다.

## 중단 전 기록과 재개 후 기록

중단 전에는 QA 카메라 권한 거절 → 재요청 → 일회 허용, 실제 세로 촬영, 사람 없는 입력의 `가이드 안에 한 명이 서주세요` 안내, 임시 촬영 파일 정리를 확인했다. 당시 기존 체형에 저장하지 않았고 촬영물을 외부 전송하지 않았다. 이 검사는 반복하지 않았다.

재개 시 C: 여유 공간은 **31,750,668,288 bytes (약 29.57 GiB)**였다. 새 고유 이름의 임시 파일 생성·읽기·일치 확인 후 그 파일만 삭제했다. 명령 실행과 자동 승인 검토가 정상 재개됐고, 확인 시 Java/Gradle/Python 빌드·테스트 프로세스가 없었다. 추가 일괄 정리, 모델/AVD 다운로드, 전체 빌드는 하지 않았다. 이후 관측 여유 공간은 23,216,275,456 bytes였다. 감소 원인은 조사/추정하지 않았으며 작업 중 대형 산출물 생성은 없었다.

기존 JSON 2개와 XML 4개는 파싱에 성공했다. `portrait-capture-metadata.txt`는 기존부터 0바이트였다. 촬영 파일이 정리된 뒤 메타데이터를 수집했으므로 사진 방향·해시의 증거로 사용하지 않는다. 디스크 부족으로 훼손됐다고 단정하거나 내용을 채우지 않았다. 기존 파일은 보존했고 새 기록은 `resume-*` 이름/새 디렉터리로 분리했다.

| 항목 | 실제 수행 | 결과 | 근거 | 남은 조건 |
|---|---|---|---|---|
| 공간·환경 | 실제 용량, 작은 파일 쓰기/읽기, 프로세스, ADB 확인 | 정상 재개 | Git 상태, probe PASS, 새 evidence-integrity.json | 대형 작업 전 용량 재확인 |
| 가로 촬영·복귀 | 사용자가 가로·사람 없는 장면 준비 확인 후 QA 카메라 진입, 홈 이동·복귀, 수동 촬영 1회 | 회전 3, 가로 UI 복구 및 촬영 동작; 사람 없음 안내로 종료 | resume-landscape-state.txt, resume-camera-after-background.xml, resume-landscape-result.xml | 저장 사진 픽셀의 방향·좌우와 실제 측정 좌표는 별도 미검증 |
| 재촬영·취소 | 오류 화면에서 다시 촬영하기 → 닫기 | 입력 화면으로 복귀, 임시 body_*.jpg 없음 | resume-retry-cancel.xml, 기기 내 캐시 조회 | 반복 장시간/여러 기기 스트레스 미검증 |
| 권한 | 중단 전 거절·재요청·일회 허용 기록 재사용; 재개 후 카메라 진입 | 허용 상태 재진입 성공 | camera-permission-denied.xml, camera-preview-ready.xml | 시스템 설정에서 권한을 철회한 뒤 재진입은 미검증; adb 강제 grant/revoke 안 함 |
| 검은 화면·중복 연결·멈춤 | 실제 복귀→촬영→분석 오류→재진입→취소 동작 관찰 | 해당 흐름에서 사용자에게 보이는 카메라 오류/멈춤 재현 안 됨 | 위 UI 기록 및 정상 결과 전환 | 미리보기 픽셀의 시각 검수/전체 크래시 로그 분석 완료를 주장하지 않음 |
| 유효 전신 분석 | 사람 없는 승인 입력만 사용 | **미검증** | 유효하지 않은 입력을 거절한 기록 | 별도로 허가된 유효 전신 입력과 촬영 준비 |
| 좌표·측정 정확도·결과→3D | EXIF 처리, CameraX rotation/ViewPort/CoordinateTransform, 저장 전 품질 게이트 코드 확인 | **실물 미검증**; 정면 너비를 둘레로 바꾸는 수정 없음 | 기존 CameraPreviewScreen/BodyAnalysisViewModel | 유효 입력·실측 기준·분석선 비교; 오류 입력으로 정확도 수치 만들지 않음 |
| 기존 AI 결과 | 동일 작업 조회 → 이전 결과 표시 → 원본/결과 전환 → 새 파일 저장 → 화면 나갔다 재진입 | **S25+ 실제 성공** | resume-previous-result-status.xml, resume-original-compare.xml, resume-saved-status.xml, resume-second-reentry.xml, PNG 디코딩 | 공개 서비스의 향후 보관/만료 보장 없음 |
| 새 외부 생성 | start/reset/생성 버튼 사용 안 함; resume 경로만 사용 | **0회** | 기존 eventId digest·startedAt·engine 유지, resume 코드에 submit 없음 | 패킷 캡처 기반 계측은 아님; 조회 GET은 실제 실행 |
| 기존 데이터 | 중단 전 해시와 재개/종료 비교 | 기준 범위 내 누락 없음; 상세 아래 | preview-before.json/qa-before.json과 새 비교 JSON | 해시 목록 밖 파일/논리 DB 전체 무결성을 포괄 보증하지 않음 |
| 개인 사진 VTON | 기존 local-vton 구조 보존, 새 자원 조사/업로드 없음 | **실제 모델 실행·Android 개인 결과 수신 미검증** | 기존 실행 환경 부재 및 LOCAL_VTON_SERVER.md | 허가된 CUDA 서버와 접속·라이선스·사진 처리 동의 |
| 16KB | P16 ELF/ZIP 정적 결과 재사용 | 정적 통과, 실제 16KB 실행 **미검증** | P16 native gate; S25+는 4096-byte pages | 기존 사용 가능한 16KB 환경 없음; 새 AVD 설치 안 함 |
| 실행 코드·마네킹 | 차이 확인 | 변경 없음 | 실행 SHA 대비 diff 없음 | 손목/골반 개선은 이번 필수 범위에서 제외 |

## 기존 AI 결과와 데이터 보존 상세

- 공식 예제 화면의 `같은 작업 결과 조회 · 재생성 없음`만 실행했다. 기존 작업의 결과 주소로 이미지를 다시 받아왔고 PC PNG를 앱에 주입하지 않았다. 결과 화면에서 `이전에 생성한 결과`와 기존 결과 안내를 확인했다. 원본/결과 비교 시 이미지 content description이 각각 바뀌었으며, 결과 화면 스크린샷에서도 실제 착장 이미지 표시를 확인했다.
- 저장은 시스템 파일 선택기의 **다운로드** 폴더에 새 이름으로 실행했다. 저장 전에 해당 이름이 없음을 확인했다.
- S25+ 새 파일: `/sdcard/Download/official-example-1793774349493.png`.
- 형식 **PNG**, 크기 **576×823**, **492,711 bytes**. 실제 PNG signature, 이미지 verify 및 전체 decode 성공.
- SHA-256: `82b030642691c2e58755021ef4e84eb62a0007457d59e2535d51cc8f3ce465e1`.
- 기존 P15 저장 파일 `/sdcard/Download/official-example-1793738524138.png`도 동일 해시로 유지됐다. 재조회 결과의 해시가 기존과 같음은 예상된 결과이며 새 생성 성공으로 계산하지 않았다.
- 재개 직후 Preview의 기준 **22/22 파일 동일**, 누락·추가 0. 종료 시 **21/22 동일**, `no_backup/official-demo-job.json`만 달라졌다. `receivedAt`/`savedAt` 갱신과 동일 eventId digest·startedAt·engine·결과 해시를 확인했다. 저널 삭제/초기화나 24시간 기준 재설정 없음. 화면 재진입은 메모리에 있는 결과를 유지했다.
- Preview 옷장·체형·아바타·저장 코디·취향 등 기준 파일의 변경/누락 없음. 사용자 기록을 테스트 fixture로 덮어쓰지 않았다.
- QA의 중단 전 기준 20개 중 **17개 동일**, 누락 0. 다른 3개는 `shared_prefs/android.app.ActivityThread.IDS.xml`, `files/profileInstalled`, `no_backup/androidx.work.workdb-shm`. 런타임 파일로 분류하지만 이전 내용 원문을 수집하지 않았으므로 각 바이트 변화의 원인을 독립 증명하지는 않았다.
- QA 새 파일 5개는 촬영 동의와 ML Kit 초기화에 해당: `privacy_consent.xml`, `com.google.mlkit.internal.xml`, pose lock, InstallationId, RemoteConfig. 기존 합성 옷장·7부위 프로필·피팅 배치·5개 합성 이미지와 QA 검증 기록의 해시는 동일했다. QA 합성 키 170을 입력했지만 기존 사용자 체형에는 저장하지 않았다.
- 사람 없는 촬영물은 기기 내 분석 후 정리됐고 PC나 외부 서비스로 복사하지 않았다. 공개 예제 결과/스크린샷만 비공개 로컬 검증 폴더에 보관했다. Git/APK에 포함하지 않았다.

## 테스트·APK 판단

[P16 CI 41726982899](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41726982899)의 **7개 Job 성공**을 GitHub 도구로 재확인했다. 기존 검증 범위: Android **440**, Preview **204**, Backend **151 + 8 subtests**, release/native safety **10**, Lint **0 errors / 165 warnings / 2 hints**. 서로 다른 테스트 구성을 합산하지 않는다. 이번에는 위 실기기 흐름·증거 파싱·파일 디코딩·해시 비교를 추가했으며 이미 통과한 전체 테스트는 재실행하지 않았다.

실행 코드/의존성 수정이 없어 새 APK·재설치·전체 CI 실행이 필요 없다. 문서만 커밋하며 기존 workflow의 paths 필터상 docs-only push는 APK 빌드를 유발하지 않는다. 이는 P17 새 CI 통과 주장과 다르다.

- 기존 [Preview APK artifact 13276820721](https://github.com/jimnnieo-svg/capstone-integration-backup/actions/runs/41726982899/artifacts/13276820721) 유지.
- APK SHA-256: `95060779ed1af0804762b1c039da2e28d51ea9ba01b53a44b90148b0563e0119`.
- 서명 인증서 SHA-256: `f534fd8c6029f5fef665c27d28559a3a8f1866435203885112d7463138c461b9`.
- 기존 LOCAL_VERIFICATION.json의 package/version/source SHA/CRC/identity 검증과 9개 ELF64·ZIP 정렬 통과 결과 재사용. 키 교체·서명 변경 없음.

## 개인 사진 VTON: 한 개의 실행 환경 차단 항목

**사용 권한과 접속 수단이 확인된 CUDA 실행 환경이 아직 없다.** 학교/팀 GPU, 무료 환경, 유료 후보를 반복 조사하지 않았다. 공식 예제 결과 복구 성공은 개인 사진용 서버 성공이 아니다.

해당 환경이 제공되면 `backend/LOCAL_VTON_SERVER.md`의 기존 절차를 실행한다. 아래는 준비 조건과 다음 명령이며 이번에 실행한 명령이 아니다.

1. GPU 담당자가 전용 Python/PyTorch CUDA 및 DWPose/Parser 런타임을 준비하고 실제 GPU/VRAM을 확인한다. 기존 FASHN 코드 검토 revision과 가중치 manifest/checksum을 사용한다. 본체와 Parser 라이선스를 별도로 확인하고 연구/비상업 제한의 적용 범위를 승인받는다.
2. 담당자의 사용 승인·라이선스 검토 후에만 `python prepare_local_vton_weights.py --weights-dir /private/fashionai/weights --accept-parser-research-license`를 실행한다. 승인 전 플래그 실행/가중치 다운로드 금지.
3. 승인된 서버에서 `python create_local_vton_token.py /private/fashionai/access`로 별도 토큰을 준비하고 저장소 밖 설정에 token hash, weights, journal 경로를 설정한다. 기존 앱/계정 토큰을 외부 데모에 전달하지 않는다.
4. `python -m uvicorn local_vton_server:app --env-file /private/fashionai/local.env --host 127.0.0.1 --port 8010 --workers 1 --no-proxy-headers --no-access-log`로 시작한다. Android 원격 접속은 담당자가 유효한 HTTPS 경로를 구성한다. loopback/USB reverse는 별도 허용된 개발 설정으로만 사용하고 운영 Railway/DB를 변경하지 않는다.
5. 서버의 준비 상태·인증·사용자별 작업 격리·임시 이미지/결과 TTL을 확인하고, 해당 환경에서 사용이 허가된 테스트 입력으로 상의 한 벌의 실제 모델 출력을 먼저 검증한다. 이후 개인 사진은 수신 환경·대상 사진·보관/삭제 조건을 제시하고 별도 동의를 받아야 한다.

## 증거와 다음 세션 순서

비공개 증거 루트는 현재 worktree의 `fashion_app/build/p17-private`이며 Git ignore 대상이다. 기기 식별값·원문 저널·사진·전체 로그를 문서/커밋에 넣지 않는다.

- 중단 전: `camera-permission-denied.xml`, `camera-preview-ready.xml`, `portrait-result.xml`, `preview-before.json`, `qa-before.json`; 0바이트 `portrait-capture-metadata.txt` 보존.
- 재개 시작: `resume-20261104T062516Z/{evidence-integrity,data-comparison,preview-hashes,qa-hashes,journal-metadata}.json`.
- 재개 종료: `resume-20261104T064138Z/`의 동일 파일군. 새 integrity 기록에서 XML 16개·JSON 3개 파싱 성공, 기존 빈 파일은 별도 표시.
- 가로 촬영/취소 및 AI 재진입: 표에 적힌 `resume-*.xml`/state 파일. PNG 검증: `resume-saved-result-verification.json`, 새 공식 예제 PNG, `resume-public-result-screen.png`.
- 로컬 검증 helper는 ignored 증거 폴더에만 있다. 기존 기록을 보존하기 위해 `verify_saved_result.py`는 이미 만들어진 결과 파일이 있으면 중단한다. 무작정 재실행하지 않는다. `resume_audit.py`는 새 시간별 디렉터리에 기록한다.

다음 순서:
1. 브랜치/status/여유 공간만 확인하고 이번 완료 항목과 동일 APK를 반복 생성하지 않는다.
2. 사용자가 유효한 전신 입력 사용과 촬영 준비를 승인했을 때만 QA에서 방향·좌우·측정선 정합성·결과→3D 비교/취소를 검증한다. 실측 기준 없이는 정확도 수치 금지. 필요하면 사용자가 시스템 설정에서 QA 권한을 철회한 뒤 재진입을 확인한다.
3. 허가된 CUDA 서버가 제공되면 위 기존 local-vton 절차를 진행한다. 그렇지 않으면 개인 사진 AI는 계속 미검증으로 둔다.
4. 실제 결함 재현 시에만 최소 수정·집중 테스트·최종 코드 CI·호환 APK 검증으로 진행한다. 기존 Preview/QA를 삭제하거나 초기화하지 않는다. 새 외부 생성, 유료 자원, 운영 배포/DB 변경은 이번 검증에 포함되지 않았다.
