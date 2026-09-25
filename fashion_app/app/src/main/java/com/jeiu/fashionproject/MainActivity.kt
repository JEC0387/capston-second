package com.jeiu.fashionproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jeiu.fashionproject.navigation.AppNavigation
import com.jeiu.fashionproject.ui.theme.FashionProjectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FashionProjectTheme {
                AppNavigation()
            }
        }
    }
}
