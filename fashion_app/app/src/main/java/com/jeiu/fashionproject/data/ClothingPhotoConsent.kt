package com.jeiu.fashionproject.data

import android.net.Uri
import java.net.URI

/** One explicit confirmation for one selected image and one HTTPS destination.
 * Not persisted and never inferred from body-analysis consent or selecting a file.
 */
class ClothingPhotoConsent private constructor(val photoUri: String, val server: String, val purpose: String) {
    private val used = java.util.concurrent.atomic.AtomicBoolean(false)
    val host: String get() = URI(server).host
    internal fun matches(uri: Uri, currentServer: String): Boolean =
        photoUri == uri.toString() && server == normalize(currentServer)
    internal fun claimOnce(): Boolean = used.compareAndSet(false, true)

    companion object {
        const val CLASSIFY = "clothing-classification-v1"
        const val DETECT = "object-detection-v1"
        fun confirm(photoUri: String, server: String, purpose: String = CLASSIFY): ClothingPhotoConsent? {
            val normalized = normalize(server) ?: return null
            if (photoUri.isBlank() || purpose !in setOf(CLASSIFY, DETECT)) return null
            return ClothingPhotoConsent(photoUri, normalized, purpose)
        }
        private fun normalize(server: String): String? = runCatching {
            val value = server.trim().trimEnd('/')
            val parsed = URI(value)
            require(parsed.scheme == "https" && !parsed.host.isNullOrBlank() && parsed.rawUserInfo == null &&
                parsed.rawQuery == null && parsed.rawFragment == null)
            value
        }.getOrNull()
    }
}
