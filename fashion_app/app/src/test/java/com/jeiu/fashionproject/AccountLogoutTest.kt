package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.AccountLogout
import org.junit.Assert.*
import org.junit.Test

/** 로그아웃은 서버에 이 기기 기록이 모두 있을 때만 진행한다 */
class AccountLogoutTest {
    @Test fun logoutRequiresTheServerSnapshotToCoverEveryLocalRecord() {
        assertTrue(AccountLogout.coversLocal(setOf("a", "b"), setOf("a", "b"), 3, 3, 10, 10))
        assertTrue(AccountLogout.coversLocal(setOf("a", "b", "c"), setOf("a"), 5, 3, 12, 10))
        assertFalse(AccountLogout.coversLocal(setOf("a"), setOf("a", "b"), 3, 3, 10, 10))   // 옷 하나 빠짐
        assertFalse(AccountLogout.coversLocal(setOf("a"), setOf("a"), 2, 3, 10, 10))        // 착용 기록 부족
        assertFalse(AccountLogout.coversLocal(setOf("a"), setOf("a"), 3, 3, 9, 10))         // 취향 기록 부족
        assertTrue(AccountLogout.coversLocal(emptySet(), emptySet(), 0, 0, 0, 0))
    }
}
