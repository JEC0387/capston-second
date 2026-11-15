package com.jeiu.fashionproject.ui.screens

import android.net.Uri
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.jeiu.fashionproject.data.AuthStore
import kotlinx.coroutines.flow.collect

/** Owns a browser callback while the login screen exchanges its one-time code. */
@Composable
internal fun LoginRedirectEffect(onRedirect: suspend (Uri) -> Unit) {
    val handler by rememberUpdatedState(onRedirect)
    LaunchedEffect(Unit) {
        // Consuming emits null. It must not cancel the exchange that consumed it.
        // Sequential collect also prevents a second callback from interrupting it.
        AuthStore.redirect.collect {
            val uri = AuthStore.consumeRedirect() ?: return@collect
            handler(uri)
        }
    }
}

/** Queue only validated app callbacks, and do not replay this Activity's consumed Intent on rotation. */
internal fun acceptLoginRedirect(intent: Intent?) {
    val candidate = intent?.data ?: return
    AuthStore.handleRedirect(candidate)
    if (AuthStore.redirect.value == candidate) intent.data = null
}
