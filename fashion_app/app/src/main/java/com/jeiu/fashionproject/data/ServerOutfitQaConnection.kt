package com.jeiu.fashionproject.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jeiu.fashionproject.BuildConfig
import java.net.URI

/** Explicit, RAM-only QA backend credentials. Never uses a Preview/production account or vendor key. */
object ServerOutfitQaConnection {
    val supported: Boolean get() = BuildConfig.DEBUG && BuildConfig.PREVIEW_BUILD && BuildConfig.DEVICE_QA_BUILD
    var revision by mutableStateOf(0)
        private set
    @Volatile private var captured: OutfitServerSession? = null

    internal fun validate(base: String, token: String, approved: Boolean, eligible: Boolean,
        productionBase: String): OutfitServerSession? {
        if (!eligible || !approved) return null
        val normalized = base.trim().trimEnd('/')
        if (!ServerOutfitVtonRepository.validBase(normalized)) return null
        val host = runCatching { URI(normalized).host.lowercase() }.getOrNull() ?: return null
        val productionHost = runCatching { URI(productionBase).host?.lowercase()?.trimEnd('.') }.getOrNull()
        // A trailing DNS root dot is the same host, not a different QA origin.
        if (host.endsWith('.') || host == productionHost || host == "fashion-backend-production-aba0.up.railway.app" ||
            host == "api.genlook.app") return null
        val secret = token.trim().removePrefix("Bearer ")
        if (!Regex("[A-Za-z0-9_-]{40,100}").matches(secret) || secret.startsWith("gk_") || secret.startsWith("sk_")) return null
        // Repository owns the single Authorization scheme prefix (same contract as DataAuth).
        return OutfitServerSession(normalized, secret)
    }

    fun configure(base: String, token: String, approved: Boolean): Boolean {
        val next = validate(base, token, approved, supported, BuildConfig.SERVER_URL) ?: return false
        captured = next
        revision++
        return true
    }

    fun sessionOrNull(): OutfitServerSession? = if (supported) captured else null

    fun clear() {
        captured = null
        revision++
    }
}
