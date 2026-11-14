package com.jeiu.fashionproject.data

/** Local measurement records follow the existing verified account binding. Unowned
 * records from older versions remain in the device-local (guest) record. */
internal object BodyMeasurementScope {
    fun current(): String? {
        val mode = AuthStore.mode.value
        val bearer = DataAuth.accessToken.takeIf { mode == AuthStore.Mode.SIGNED_IN && DataAuth.canSync }
        return resolve(mode, DataAuth.isRestorePending, bearer, BackendApi.BASE_URL)
    }

    internal fun resolve(mode: AuthStore.Mode, restoring: Boolean, bearer: String?, server: String): String? = when {
        restoring -> null
        mode != AuthStore.Mode.SIGNED_IN -> ""
        bearer.isNullOrBlank() -> null
        else -> "account_${deviceSha256("$server\u0000$bearer".toByteArray())}_"
    }
}
