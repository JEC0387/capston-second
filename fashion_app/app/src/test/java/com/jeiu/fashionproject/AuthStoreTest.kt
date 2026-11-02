package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import com.jeiu.fashionproject.data.AuthStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 구글 로그인 서버 응답 읽기 · 오류 안내 (org.json 이 실제로 동작하도록 Robolectric) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AuthStoreTest {
    @Test fun loginResponseIsReadSafely() {
        val ok = AuthStore.parseLogin("""{"accountKey":"3f1c2a9e-0000-4000-8000-123456789abc","email":"a@example.com",
            "name":"계정A","isNewAccount":false,"switched":true}""")!!
        assertTrue(ok.switched); assertFalse(ok.isNewAccount); assertEquals("계정A", ok.name)
        assertNull(AuthStore.parseLogin("""{"accountKey":"../../etc","email":""}"""))
        assertNull(AuthStore.parseLogin("not json"))
        assertTrue(AuthStore.messageFor(-1, null).contains("서버"))
        assertEquals("서버에 GOOGLE_CLIENT_ID 가 없어요", AuthStore.messageFor(503, """{"detail":"서버에 GOOGLE_CLIENT_ID 가 없어요"}"""))
    }

    @Test fun providerAndBrowserRedirect() {
        val k = AuthStore.parseLogin("""{"accountKey":"3f1c2a9e-0000-4000-8000-123456789abc","provider":"kakao",
            "email":"","name":"카카오유저","isNewAccount":true,"switched":false}""")!!
        assertEquals(AuthStore.Provider.KAKAO, k.provider)
        // 다른 주소로는 로그인 마무리를 시작하지 않는다
        AuthStore.handleRedirect(Uri.parse("https://evil.example/login?code=x"))
        assertNull(AuthStore.redirect.value)
        AuthStore.handleRedirect(Uri.parse("fashionai://login?code=abc&provider=apple"))
        assertEquals("abc", AuthStore.consumeRedirect()!!.getQueryParameter("code"))
        assertNull(AuthStore.redirect.value)
    }
}
