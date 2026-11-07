package com.jeiu.fashionproject.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.jeiu.fashionproject.BuildConfig
import java.security.MessageDigest

/** Read the installed package, not a UI version label or a downloaded APK's filename. */
data class AppBuildInfo(val packageName: String, val versionName: String, val versionCode: Long,
                        val gitSha: String, val branch: String, val buildNumber: String,
                        val certificateSha256: String, val sourceDirty: Boolean) {
    val shortSha: String get() = gitSha.take(8)
    companion object {
        @Suppress("DEPRECATION")
        fun installed(context: Context): AppBuildInfo {
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val info = context.packageManager.getPackageInfo(context.packageName, flags)
            val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            val digest = certificates?.firstOrNull()?.toByteArray()?.let {
                MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
            } ?: "확인 불가"
            return AppBuildInfo(context.packageName, info.versionName.orEmpty(),
                if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
                BuildConfig.GIT_SHA, BuildConfig.BUILD_BRANCH, BuildConfig.BUILD_NUMBER, digest, BuildConfig.SOURCE_DIRTY)
        }
    }
}
