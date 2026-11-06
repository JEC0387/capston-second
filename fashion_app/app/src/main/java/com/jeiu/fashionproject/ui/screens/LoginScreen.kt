package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.jeiu.fashionproject.data.AuthStore.Provider
import com.jeiu.fashionproject.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Team provider chooser. Social identity is unavailable until it can be verified
 * against the existing Bearer/restore contract. Local guest entry preserves records.
 */
@Composable
fun LoginScreen(onDone: () -> Unit, allowGuest: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<Provider?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    // Unknown provider configuration must not enable an external authentication action.
    var serverProviders by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    LaunchedEffect(Unit) { serverProviders = AuthStore.serverProviders() }

    fun handle(r: AuthStore.Result) {
        when (r) {
            // A legacy account response cannot prove server storage or restore.
            is AuthStore.Result.Success -> error = AuthStore.UNAVAILABLE
            AuthStore.Result.Cancelled -> Unit
            AuthStore.Result.OpenedBrowser -> Unit
            is AuthStore.Result.Failed -> error = r.message
        }
    }

    // 브라우저 로그인(카카오 · Facebook · Apple)에서 돌아왔을 때
    val redirect by AuthStore.redirect.collectAsState()
    LaunchedEffect(redirect) {
        val uri = AuthStore.consumeRedirect() ?: return@LaunchedEffect
        busy = Provider.of(uri.getQueryParameter("provider")) ?: Provider.KAKAO
        error = null; hint = null
        handle(AuthStore.finishBrowserLogin(context, uri))
        busy = null
    }

    fun start(provider: Provider) {
        if (busy != null) return
        busy = provider; error = null
        scope.launch {
            val r = if (provider == Provider.GOOGLE) AuthStore.signInWithGoogle(context)
                    else AuthStore.startBrowserLogin(context, provider)
            hint = if (r is AuthStore.Result.OpenedBrowser)
                "${provider.label} 로그인을 브라우저에서 마치면 앱으로 돌아와요." else null
            handle(r)
            busy = null
        }
    }

    fun enabled(p: Provider): Boolean =
        busy == null && (if (p == Provider.GOOGLE) AuthStore.isGoogleConfigured() else serverProviders?.get(p.key) == true)

    Column(Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(280.dp)) {
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

        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("내 옷장으로 시작하기", style = MaterialTheme.typography.headlineLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            Text(
                AuthStore.UNAVAILABLE,
                style = MaterialTheme.typography.bodyMedium, color = MediumGray,
                modifier = Modifier.testTag("loginUnconfigured")
            )
            Spacer(Modifier.height(6.dp))

            LoginButton("카카오로 계속하기", Provider.KAKAO, busy, enabled(Provider.KAKAO),
                container = Color(0xFFFEE500), content = Color(0xFF191919)) { start(it) }
            LoginButton("Google 계정으로 계속하기", Provider.GOOGLE, busy, enabled(Provider.GOOGLE),
                container = White, content = CharcoalBlack, border = BorderStroke(1.dp, LightGray)) { start(it) }
            LoginButton("Facebook으로 계속하기", Provider.FACEBOOK, busy, enabled(Provider.FACEBOOK),
                container = Color(0xFF1877F2), content = White) { start(it) }
            LoginButton("Apple로 계속하기", Provider.APPLE, busy, enabled(Provider.APPLE),
                container = Color.Black, content = White) { start(it) }

            val off = buildList {
                if (!AuthStore.isGoogleConfigured()) add("Google")
                serverProviders?.let { m -> Provider.entries.filter { it != Provider.GOOGLE && m[it.key] == false }.forEach { add(it.label) } }
            }
            if (off.isNotEmpty()) {
                Text("${off.joinToString(" · ")} 로그인은 아직 사용할 수 없어요.",
                    style = MaterialTheme.typography.labelSmall, color = MediumGray)
            }
            hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MediumGray) }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            if (allowGuest) {
                TextButton(
                    onClick = {
                        if (AuthStore.continueAsGuest(context)) onDone()
                        else error = "이용 상태를 저장하지 못했어요. 기존 기록은 유지돼요."
                    },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth().testTag("guestLogin")
                ) { Text("로그인 없이 둘러보기", color = Brown) }
                Text(
                    "기존 기록은 이 기기에 유지돼요. 서버 저장·복원은 별도의 인증과 확인이 필요해요.",
                    style = MaterialTheme.typography.labelSmall, color = MediumGray, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 로그인 버튼 한 개 — 각 회사 버튼 색(카카오 노랑 · Facebook 파랑 · Apple 검정 · Google 흰색) */
@Composable
private fun LoginButton(
    text: String, provider: Provider, busy: Provider?, enabled: Boolean,
    container: Color, content: Color, border: BorderStroke? = null, onClick: (Provider) -> Unit
) {
    Button(
        onClick = { onClick(provider) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("login_${provider.key}"),
        shape = RoundedCornerShape(12.dp),
        border = border,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.35f), disabledContentColor = content.copy(alpha = 0.5f)
        )
    ) {
        if (busy == provider) {
            CircularProgressIndicator(Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("로그인하는 중…", fontWeight = FontWeight.SemiBold)
        } else {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}
