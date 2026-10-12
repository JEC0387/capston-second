package com.jeiu.fashionproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jeiu.fashionproject.data.DbSync
import com.jeiu.fashionproject.navigation.AppNavigation
import com.jeiu.fashionproject.ui.theme.FashionProjectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 서버 DB에서 이 폰의 데이터를 구분할 기기 번호 준비 (서버 호출보다 먼저)
        DbSync.init(applicationContext)
        setContent {
            FashionProjectTheme {
                AppNavigation()
            }
        }
    }
}
