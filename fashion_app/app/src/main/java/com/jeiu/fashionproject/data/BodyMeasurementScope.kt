package com.jeiu.fashionproject.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stable ownership is issued by the authenticated server, never inferred from a
 * provider email, device ID, recovery code or a newly rotated token. */
internal object BodyMeasurementScope {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    fun current(): String? {
        return DataAuth.bodyScope(AuthStore.mode.value)
    }

    internal fun resolve(mode: AuthStore.Mode, restoring: Boolean, bearer: String?, server: String,
                         accountId: String? = null): String? = when {
        restoring -> null
        mode != AuthStore.Mode.SIGNED_IN -> ""
        bearer.isNullOrBlank() -> null
        validAccountId(accountId) -> accountScope(server, checkNotNull(accountId))
        // Compatibility ONLY until the old server supplies accountId. Keep the exact
        // P69 record accessible; never scan/adopt another token's or guest's data.
        else -> legacyScope(server, bearer)
    }

    fun validAccountId(value: String?): Boolean = value != null && Regex("[1-9][0-9]{0,18}").matches(value)
    fun accountScope(server: String, accountId: String) =
        "account_v2_${deviceSha256("$server\u0000$accountId".toByteArray())}_"
    fun legacyScope(server: String, bearer: String) =
        "account_${deviceSha256("$server\u0000$bearer".toByteArray())}_"

    fun refresh(context: Context) {
        ManualBodyStore.load(context)
        AvatarProfileStore.load(context)
        _revision.value += 1
    }

    fun hasLegacyRecord(context: Context, server: String, bearer: String): Boolean = runCatching {
        val scope = legacyScope(server, bearer)
        context.getSharedPreferences("avatar_visual_profile_v1", 0).contains(scope + "profile") ||
            context.getSharedPreferences("manual_body_measurements", 0).contains(scope + "save")
    }.getOrDefault(false)

    /** Copy-only migration. Ownership proofs are retained in the encrypted auth vault.
     * Existing destination data wins; source records are never removed. */
    fun prepare(context: Context, prefsName: String, keys: List<String>, scope: String): Boolean {
        if (!scope.startsWith("account_v2_")) return true
        return runCatching {
            migrate(context.getSharedPreferences(prefsName, 0), prefsName, scope,
                DataAuth.bodyLegacyScopes(scope), keys)
        }.getOrDefault(false)
    }

    private data class Pending(val values: Map<String, Any?>)
    private val pending = mutableMapOf<Pair<String, String>, Pending>()

    @Synchronized
    internal fun migrate(prefs: SharedPreferences, store: String, scope: String,
                         sources: List<String>, keys: List<String>): Boolean = runCatching {
        val identity = store to scope
        val marker = scope + "migration_v1"
        val retry = pending[identity]
        if (retry == null && (prefs.contains(marker) || keys.any { prefs.contains(scope + it) })) return true
        val candidates = sources.distinct().filter { Regex("account_[a-f0-9]{64}_").matches(it) }
            .map { old -> keys.filter { prefs.contains(old + it) }.associateWith { prefs.all[old + it] } }
            .filter { it.isNotEmpty() }.distinct()
        if (retry == null && candidates.isEmpty()) return true
        if (retry == null && candidates.size != 1) return false // ambiguous histories require review
        val values = retry?.values ?: candidates.single()
        val edit = prefs.edit()
        values.forEach { (key, value) -> put(edit, scope + key, value) }
        edit.putBoolean(marker, true)
        if (edit.commit()) {
            pending.remove(identity)
            true
        } else {
            // A failed commit can mutate the in-memory cache. Never publish it as saved.
            // Roll back only newly copied keys; retain all source records and retry data.
            pending[identity] = Pending(values)
            runCatching {
                val rollback = prefs.edit().remove(marker)
                values.keys.forEach { rollback.remove(scope + it) }
                rollback.commit()
            }
            false
        }
    }.getOrDefault(false)

    private fun put(edit: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is String -> edit.putString(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Float -> edit.putFloat(key, value)
            is Long -> edit.putLong(key, value)
            is Int -> edit.putInt(key, value)
            else -> error("Unsupported stored measurement type")
        }
    }
}
