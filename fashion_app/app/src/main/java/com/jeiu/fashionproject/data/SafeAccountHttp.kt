package com.jeiu.fashionproject.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Small JSON-only exchanges. Never logs URLs, credentials or remote error bodies. */
internal object SafeAccountHttp {
    class Reply(val code: Int, val body: String)
    fun client(seconds: Long) = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(seconds, TimeUnit.SECONDS)
        .callTimeout(seconds, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()

    suspend fun send(client: OkHttpClient, request: Request): Reply = suspendCancellableCoroutine { c ->
        val call = client.newCall(request)
        c.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (c.isActive) c.resumeWithException(IOException("account_transport_unavailable"))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val reply = response.use {
                        val body = it.body ?: throw IOException("empty_response")
                        val bytes = body.byteStream().use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                if (out.size() + n > 32768) throw IOException("response_too_large")
                                out.write(buffer, 0, n)
                            }
                            out.toByteArray()
                        }
                        Reply(it.code, bytes.toString(Charsets.UTF_8))
                    }
                    if (c.isActive) c.resume(reply)
                } catch (_: Exception) {
                    if (c.isActive) c.resumeWithException(IOException("account_response_unavailable"))
                }
            }
        })
    }
}
