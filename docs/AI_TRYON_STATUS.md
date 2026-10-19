# AI 가상피팅 현재 상태 (2026-10-19)

마법봉은 원본 인물·의류 사진으로 공통 AiTryOnScreen을 열고, 외부 전송·비용을 명시적으로 승인한 뒤 인증된 Backend 프록시에 생성 요청합니다. 관절 기반 자동 정렬은 수동 피팅의 별도 버튼입니다. 수동 드래그 좌표는 AI에 전달하지 않습니다.

FASHN 키는 APK/BuildConfig/local.properties에서 제거했습니다. 서버 설정·개인 접속 토큰·HTTPS·영속 사용량 저널 없이는 유료 생성이 활성화되지 않습니다. 수동 합성을 AI 결과로 대체하지 않습니다.

Android Activity 범위의 TryOnViewModel과 SavedStateHandle로 화면 전환·재구성을 견디고, 요청 ID 기반 서버 중복 방지와 GET 전용 복구를 사용합니다. 실패 시 이전 결과와 수동 선택 상태를 유지하며 다시 만들기는 별도의 동의가 필요합니다.

서버 설정 및 보안 한계: [AI_TRYON_SETUP.md](../backend/AI_TRYON_SETUP.md)

과거 보고서에 기재된 클라이언트 FASHN_API_KEY 설정과 직접 TryOnRepository.tryOn 호출 구조는 이번 변경으로 대체되었습니다. 실제 유료 API와 실기기는 검증하지 않았습니다.
