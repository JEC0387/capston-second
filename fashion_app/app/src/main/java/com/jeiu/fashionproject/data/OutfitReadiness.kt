package com.jeiu.fashionproject.data

/** Transport, database, account and provider readiness are independent facts. No provisioning. */
object OutfitReadiness {
    fun lines(check: ServerSettings.Check?, accountState: String, canSync: Boolean,
        appApproved: Boolean, providerReady: Boolean?): List<String> = listOf(
        "서버 연결: " + when (check?.reachable) { true -> "응답 확인"; false -> "응답 확인 실패"; null -> "미확인" },
        "DB·인증 API: " + when {
            check == null || !check.reachable -> "미확인"
            check.dbReady && check.syncReady && check.authProtocol == 2 -> "준비 확인"
            else -> "준비 미확인 · 관리자 확인 필요"
        },
        "계정 인증: " + when {
            canSync -> "저장된 인증정보 있음 · 설정 조회 성공으로 유효성 확인 필요"
            accountState == "server_mismatch" -> "저장 계정과 서버 주소가 달라요"
            accountState == "secure_storage_unavailable" -> "기기의 보안 저장소를 확인해주세요"
            accountState == "registration_required" -> "이 설치검증 앱의 승인된 서버 계정 연결이 필요해요"
            else -> "현재 앱의 유효한 인증정보가 없거나 복구가 필요해요"
        },
        "AI 설정: " + when {
            !appApproved -> "이 빌드에서는 서버 AI 사용이 승인되지 않았어요"
            !canSync -> "계정 인증 전에는 Genlook 준비 상태를 확인할 수 없어요"
            providerReady == true -> "인증된 서버 설정 준비됨 · 실제 생성·품질은 별도 검증"
            providerReady == false -> "서버가 생성 준비 미완료로 응답했어요"
            else -> "설정 조회 전 · 서버 활성화 보고만으로 준비 완료가 되지 않아요"
        }
    )
}
