package com.jeiu.fashionproject

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jeiu.fashionproject.data.AuthStore
import com.jeiu.fashionproject.navigation.AppNavigation
import com.jeiu.fashionproject.ui.theme.FashionProjectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 카카오 · 페이스북 · Apple 로그인 뒤 fashionai://login 으로 열렸으면 로그인 화면이 마무리한다
        if (savedInstanceState == null) AuthStore.handleRedirect(intent?.data)
        enableEdgeToEdge()
        setContent {
            FashionProjectTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AuthStore.handleRedirect(intent.data)
    }
}
