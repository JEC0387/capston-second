package com.jeiu.fashionproject.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeiu.fashionproject.R
import com.jeiu.fashionproject.data.AuthStore
import com.jeiu.fashionproject.data.BackupRestore
import com.jeiu.fashionproject.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 로그인 화면 — 앱을 처음 열거나 로그아웃하면 나온다.
 *  - 구글 계정으로 로그인: 계정마다 옷장 · 착용 기록 · 취향 학습이 따로 저장되고, 다른 폰에서 로그인해도 이어진다.
 *  - 로그인 없이 둘러보기: 이 폰에만 저장 (나중에 마이페이지에서 로그인하면 지금 기록을 계정으로 옮긴다).
 */
@Composable
fun LoginScreen(onDone: () -> Unit, allowGuest: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val configured = AuthStore.isConfigured()

    Column(
        Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            Image(
                painter = painterResource(R.drawable.onboarding_main), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Cream))))
            Box(
                Modifier.padding(20.dp).align(Alignment.TopStart).clip(RoundedCornerShape(8.dp))
                    .background(Camel).padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("STYLE AI", color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }

        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("내 옷장을 어디서든", style = MaterialTheme.typography.headlineLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            Text(
                "로그인하면 내 옷장 · 착용 기록 · 취향 학습이 계정에 저장돼요.\n폰을 바꾸거나 다른 폰에서 로그인해도 그대로 이어져요.",
                style = MaterialTheme.typography.bodyMedium, color = MediumGray
            )
            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = {
                    if (busy) return@OutlinedButton
                    busy = true; error = null
                    scope.launch {
                        when (val r = AuthStore.signInWithGoogle(context)) {
                            is AuthStore.Result.Success -> {
                                Toast.makeText(context,
                                    if (r.isNewAccount) "${r.account.name}님, 환영해요! 지금 기록을 계정에 저장할게요"
                                    else "${r.account.name}님의 옷장을 불러왔어요", Toast.LENGTH_SHORT).show()
                                if (r.restart) BackupRestore.restartApp(context) else onDone()
                            }
                            AuthStore.Result.Cancelled -> Unit
                            is AuthStore.Result.Failed -> error = r.message
                        }
                        busy = false
                    }
                },
                enabled = configured && !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("googleLogin"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, LightGray),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = CharcoalBlack)
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Camel, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("로그인하는 중…", fontWeight = FontWeight.SemiBold)
                } else {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(CreamDark),
                        contentAlignment = Alignment.Center
                    ) { Text("G", fontWeight = FontWeight.Bold, color = Brown, fontSize = 14.sp) }
                    Spacer(Modifier.width(10.dp))
                    Text("Google 계정으로 계속하기", fontWeight = FontWeight.SemiBold)
                }
            }
            if (!configured) {
                Text(
                    "구글 로그인 설정이 아직 없어요 (개발자: local.properties 에 GOOGLE_WEB_CLIENT_ID 추가).",
                    style = MaterialTheme.typography.labelSmall, color = MediumGray
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            if (allowGuest) {
                TextButton(
                    onClick = { AuthStore.continueAsGuest(context); onDone() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("guestLogin")
                ) { Text("로그인 없이 둘러보기", color = Brown) }
                Text(
                    "로그인 없이 쓰면 기록이 이 폰에만 저장돼요. 나중에 마이페이지에서 로그인하면 지금 기록을 계정으로 옮겨요.",
                    style = MaterialTheme.typography.labelSmall, color = MediumGray, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
