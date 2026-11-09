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
import android.widget.Toast
import com.jeiu.fashionproject.R
import com.jeiu.fashionproject.data.AuthStore
import com.jeiu.fashionproject.data.BackupRestore
import com.jeiu.fashionproject.data.AuthStore.Provider
import com.jeiu.fashionproject.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 로그인 화면 — 카카오 · Google (Bearer 계정에 연결, Facebook · Apple 은 없음).
 * 처음 로그인하면 지금 기록이 그 계정에 그대로 연결되고, 다른 폰에서 쓰던 계정이면 그 기록을 받아 와 앱을 다시 연다.
 * 로그인 없이 둘러보기는 기존 기록을 그대로 둔다.
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
            is AuthStore.Result.Success -> {
                val who = r.account.name.ifBlank { r.account.provider.label }
                if (r.restart) {
                    Toast.makeText(context, "$who 계정의 기록을 받아 왔어요. 앱을 다시 열게요.", Toast.LENGTH_LONG).show()
                    BackupRestore.restartApp(context)
                } else {
                    Toast.makeText(context, "$who 님, ${r.account.provider.label} 계정으로 로그인했어요.", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            }
            AuthStore.Result.Cancelled -> Unit
            AuthStore.Result.OpenedBrowser -> Unit
            is AuthStore.Result.Failed -> error = r.message
        }
    }

    // 카카오 로그인(브라우저)에서 돌아왔을 때
    LoginRedirectEffect { uri ->
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

    fun available(p: Provider): Boolean =
        serverProviders?.get(p.key) == true && (p != Provider.GOOGLE || AuthStore.isGoogleConfigured())
    fun enabled(p: Provider): Boolean = busy == null && available(p)
    val signedIn by AuthStore.account.collectAsState()

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
            val anyAvailable = AuthStore.ENABLED_PROVIDERS.any { available(it) }
            when {
                signedIn != null -> Text(
                    "지금 ${signedIn!!.provider.label} 계정으로 로그인돼 있어요." + (signedIn!!.email.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MediumGray, modifier = Modifier.testTag("loginCurrent"))
                serverProviders != null && !anyAvailable -> Text(
                    AuthStore.UNAVAILABLE, style = MaterialTheme.typography.bodyMedium, color = MediumGray,
                    modifier = Modifier.testTag("loginUnconfigured"))
                else -> Text(
                    "카카오나 Google 계정으로 로그인하면 지금 폰의 옷장 · 기록이 그 계정에 저장되고, 다른 폰에서도 이어서 쓸 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium, color = MediumGray, modifier = Modifier.testTag("loginIntro"))
            }
            Spacer(Modifier.height(6.dp))

            LoginButton("카카오로 계속하기", Provider.KAKAO, busy, enabled(Provider.KAKAO),
                container = Color(0xFFFEE500), content = Color(0xFF191919)) { start(it) }
            LoginButton("Google 계정으로 계속하기", Provider.GOOGLE, busy, enabled(Provider.GOOGLE),
                container = White, content = CharcoalBlack, border = BorderStroke(1.dp, LightGray)) { start(it) }

            val off = if (serverProviders == null) emptyList()
                      else AuthStore.ENABLED_PROVIDERS.filter { !available(it) }.map { it.label }
            if (anyAvailable && off.isNotEmpty()) {
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
                    "로그인하지 않아도 기존 기록은 이 기기에 그대로 있어요.",
                    style = MaterialTheme.typography.labelSmall, color = MediumGray, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 로그인 버튼 한 개 — 각 회사 버튼 색(카카오 노랑 · Google 흰색) */
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
