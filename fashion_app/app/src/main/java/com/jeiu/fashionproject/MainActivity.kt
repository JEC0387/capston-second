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
        enableEdgeToEdge()
        if (savedInstanceState == null) AuthStore.handleRedirect(intent?.data)
        setContent {
            FashionProjectTheme {
                AppNavigation()
            }
        }
    }

    /** Kakao browser login returns here (fashionai://login?...); AuthStore validates the link strictly. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AuthStore.handleRedirect(intent.data)
    }
}
