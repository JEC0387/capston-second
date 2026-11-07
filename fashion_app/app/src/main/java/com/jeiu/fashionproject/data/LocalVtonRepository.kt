package com.jeiu.fashionproject.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.jeiu.fashionproject.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Separate in-memory credentials. Never defaults/falls back to the paid connection. */
object LocalVtonSession {
    var connection by mutableStateOf<TryOnConnection?>(null)
        private set
    fun validBase(base: String, allowLoopback: Boolean = false): Boolean = runCatching {
        val uri=URI(base)
        uri.host != null && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.path in listOf("", "/") && uri.port in -1..65535 && uri.port != 0 &&
            (uri.scheme == "https" || allowLoopback && BuildConfig.DEBUG && uri.scheme == "http" &&
                uri.host in setOf("127.0.0.1","localhost","10.0.2.2"))
    }.getOrDefault(false)
    private var loopbackAllowed = false
    var garmentPhotoType by mutableStateOf("flat-lay")
        private set
    fun selectPhotoType(value: String) { if (value in setOf("flat-lay", "model")) garmentPhotoType=value }
    fun configure(base: String, token: String, allowLoopback: Boolean = false): Boolean {
        val normalized=base.trim().trimEnd('/')
        if (!validBase(normalized,allowLoopback) || token.trim().length !in 32..256 ||
            token.trim().any { it.isWhitespace() }) return false
        loopbackAllowed=allowLoopback
        connection=TryOnConnection(normalized,token.trim())
        return true
    }
    fun transportAllowed(base: String) = validBase(base,loopbackAllowed)
    fun clear() { connection=null;loopbackAllowed=false;garmentPhotoType="flat-lay" }
}

object LocalVtonRepository : TryOnGateway {
    const val ENGINE="fashn-vton-1.5-local"
    fun selectionIssue(garments: List<TryOnGarment>): String? = when {
        garments.size != 1 -> "자체 AI는 상의·하의·원피스 중 한 벌만 생성해요. 옷 한 벌을 선택해주세요."
        TryOnPlan.categoryFor(garments[0].category) == null -> "자체 AI는 상의·하의·원피스만 지원해요."
        garments[0].image.isBlank() -> "의류 원본 사진을 찾을 수 없어요."
        else -> null
    }
    override suspend fun image(context: Context, source: String) = TryOnRepository.image(context,source)
    override suspend fun prepare(context: Context, source: String, role: TryOnInputRole) =
        TryOnRepository.prepare(context,source,role) // Image/pose preprocessing only; no paid routes.

    override suspend fun check(connection: TryOnConnection) {
        val config=JSONObject(request(connection,"/config").toString(Charsets.UTF_8))
        if (config.optString("engine") != ENGINE || config.optBoolean("paid_api",true) ||
            config.optInt("max_garments") != 1 || config.optDouble("result_ttl_seconds",Double.NaN) !in .001..300.0)
            throw TryOnFailure("자체 AI 전용 서버가 아니거나 안전한 결과 보관 설정이 없어요. 유료 서비스로 전환하지 않았어요.")
        if (!config.optBoolean("ready") || !config.optBoolean("configured"))
            throw TryOnFailure(friendly(config.optString("reason")))
    }
    override suspend fun submit(connection: TryOnConnection,id: String,person: String,garments: List<Pair<String,String>>) {
        requireId(id)
        if (garments.size != 1 || garments[0].first !in setOf("tops","bottoms","one-pieces"))
            throw TryOnFailure("자체 AI는 지원되는 옷 한 벌만 생성해요.")
        val items=JSONArray().put(JSONObject().put("category",garments[0].first).put("image",garments[0].second))
        request(connection,"/jobs","POST",JSONObject().put("request_id",id).put("person",person)
            .put("garments",items).put("consent",true).put("garment_photo_type",LocalVtonSession.garmentPhotoType).toString())
    }
    override suspend fun status(connection: TryOnConnection,id: String): TryOnJob {
        requireId(id)
        val value=JSONObject(request(connection,"/jobs/$id").toString(Charsets.UTF_8))
        if (value.optString("engine") != ENGINE) throw TryOnFailure("다른 생성 서비스의 응답을 차단했어요.")
        return TryOnJob(value.getString("status"),value.optString("error").takeUnless { it.isBlank() || it=="null" },
            value.optInt("step"),value.optInt("count"),value.optBoolean("result_available"))
    }
    override suspend fun result(connection: TryOnConnection,id: String): ByteArray {
        requireId(id)
        return request(connection,"/jobs/$id/result",binary=true).also(::validatePng)
    }
    override suspend fun cancel(connection: TryOnConnection,id: String) {
        requireId(id);request(connection,"/jobs/$id","DELETE")
    }
    private fun requireId(id: String) {
        if (runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false).not())
            throw TryOnFailure("작업 번호 형식이 맞지 않아요.")
    }
    private suspend fun request(config: TryOnConnection,path: String,method: String="GET",body: String?=null,binary: Boolean=false): ByteArray =
        withContext(Dispatchers.IO) {
            if (!LocalVtonSession.transportAllowed(config.base)) throw TryOnFailure("HTTPS 또는 명시적으로 허용한 개발용 루프백 주소가 필요해요.")
            val conn=(URL(config.base+"/api/local-vton"+path).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects=false;requestMethod=method;connectTimeout=5_000;readTimeout=15_000
                setRequestProperty("Authorization","Bearer "+config.token)
                setRequestProperty("Content-Type","application/json")
            }
            try {
                body?.let {
                    val bytes=it.toByteArray(Charsets.UTF_8);conn.doOutput=true;conn.setFixedLengthStreamingMode(bytes.size)
                    conn.outputStream.use { stream -> stream.write(bytes) }
                }
                val code=conn.responseCode
                if (code !in 200..299) {
                    // Do not show raw error bodies, paths, tokens or images.
                    throw TryOnHttpFailure(code,when(code) {
                        401,403 -> "자체 AI 서버의 개인 토큰과 HTTPS 설정을 확인해주세요."
                        404 -> "자체 AI 서버 API 또는 작업을 찾지 못했어요. 기존 유료 서비스로 전환하지 않았어요."
                        410 -> friendly("result_expired")
                        429 -> "자체 AI 서버가 처리 중이거나 사용량 한도에 도달했어요."
                        503 -> "자체 AI 모델·GPU·가중치·요청 기록 저장소가 준비되지 않았어요."
                        else -> "자체 AI 요청에 실패했어요 (HTTP $code)."
                    })
                }
                if (binary && conn.contentType?.substringBefore(';') != "image/png") throw TryOnFailure("자체 AI 결과가 PNG 이미지가 아니에요.")
                val limit=if(binary) 8*1024*1024 else 64*1024
                conn.inputStream.use { input ->
                    val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) {
                        coroutineContext.ensureActive()
                        val count=input.read(buffer);if(count<0) break
                        if(out.size()+count>limit) throw TryOnFailure("자체 AI 응답이 너무 커요.")
                        out.write(buffer,0,count)
                    }
                    out.toByteArray()
                }
            } finally { conn.disconnect() }
        }

    fun validatePng(bytes: ByteArray) {
        val signature=byteArrayOf(-119,80,78,71,13,10,26,10)
        if (bytes.size !in 8..8*1024*1024 || !bytes.take(8).toByteArray().contentEquals(signature))
            throw TryOnFailure("유효한 자체 AI PNG 결과가 없어요.")
        validateDecodedImage(bytes,setOf("image/png"))
    }
    private fun validateDecodedImage(bytes: ByteArray, types: Set<String>) {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        try {BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)}
        catch (_: RuntimeException) {throw TryOnFailure("자체 AI PNG 결과를 읽지 못했어요.")}
        if (bounds.outMimeType !in types || minOf(bounds.outWidth,bounds.outHeight)<32 || bounds.outWidth.toLong()*bounds.outHeight>20_000_000)
            throw TryOnFailure("자체 AI 결과 크기가 유효하지 않아요.")
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1024)sample*=2
        val decoded=try {BitmapFactory.decodeByteArray(bytes,0,bytes.size,
            BitmapFactory.Options().apply {inSampleSize=sample})}
            catch (_: RuntimeException) {throw TryOnFailure("자체 AI PNG 결과를 끝까지 읽지 못했어요.")}
            ?: throw TryOnFailure("자체 AI PNG 결과를 끝까지 읽지 못했어요.")
        decoded.recycle()
    }
    /** Explicit document selection only. Never uploads, changes a job, or proves AI provenance. */
    suspend fun openSavedImage(context: Context, source: Uri): ByteArray = withContext(Dispatchers.IO) {
        val sandboxFile=source.scheme=="file" && runCatching {
            val path=File(requireNotNull(source.path)).canonicalPath
            listOf(context.filesDir,context.cacheDir).any { path.startsWith(it.canonicalPath+File.separator) }
        }.getOrDefault(false)
        if(source.scheme!="content" && !sandboxFile) throw TryOnFailure("직접 선택한 저장 이미지 문서만 열 수 있어요.")
        val bytes=context.contentResolver.openInputStream(source)?.use { input ->
            val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {
                coroutineContext.ensureActive()
                val count=input.read(buffer);if(count<0) break
                if(count==0) throw TryOnFailure("저장 이미지 읽기가 중단됐어요. 파일을 다시 선택해주세요.")
                if(out.size()+count>8*1024*1024) throw TryOnFailure("저장 이미지가 너무 커요.")
                out.write(buffer,0,count)
            }
            out.toByteArray()
        } ?: throw TryOnFailure("저장 이미지를 열지 못했어요.")
        if(bytes.size<8) throw TryOnFailure("유효한 저장 이미지가 아니에요.")
        if(bytes[0]==(-119).toByte()) validatePng(bytes)
        else {
            if(bytes[0]!=(-1).toByte() || bytes[1]!=(-40).toByte() ||
                bytes[bytes.size-2]!=(-1).toByte() || bytes.last()!=(-39).toByte())
                throw TryOnFailure("PNG/JPG 저장 이미지만 열 수 있어요.")
            validateDecodedImage(bytes,setOf("image/jpeg"))
        }
        bytes
    }
    suspend fun save(context: Context,bytes: ByteArray,target: Uri,png: Boolean) = withContext(Dispatchers.IO) {
        validatePng(bytes)
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: throw TryOnFailure("결과를 읽지 못했어요.")
        try {
            context.contentResolver.openOutputStream(target)?.use { out ->
                coroutineContext.ensureActive()
                if(!bitmap.compress(if(png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,95,out))
                    throw TryOnFailure("이미지를 저장하지 못했어요.")
            } ?: throw TryOnFailure("저장 위치를 열지 못했어요.")
        } finally { bitmap.recycle() }
    }
    fun friendly(reason: String?): String = when(reason) {
        "disabled" -> "자체 AI 서버의 모델 실행이 꺼져 있어요."
        "research_license_required" -> "운영자가 Human Parser의 연구·평가용 라이선스를 확인해야 해요."
        "parser_integrity_unavailable" -> "운영자가 Human Parser·SegFormer 파일 무결성과 라이선스 검토 기록을 확인해야 해요. 사진을 다시 선택해도 해결되지 않아요."
        "cpu_not_approved" -> "자체 AI 서버의 CPU 실행이 승인되지 않았어요. 운영자가 허가된 실행 환경을 준비해야 해요."
        "cuda_unavailable","vram_below_model_guidance" -> "자체 AI 서버에 사용 가능한 CUDA GPU 또는 충분한 VRAM이 없어요."
        "weights_missing","weights_or_runtime_unavailable","dependencies_missing" -> "자체 AI 서버의 모델 가중치·파서 캐시·실행 환경이 준비되지 않았어요."
        "journal_unavailable","authentication_not_configured" -> "자체 AI 서버의 안전한 인증·작업 기록 설정이 필요해요."
        "timeout" -> "자체 AI 생성 시간이 초과되어 작업을 중단했어요. 새 생성에는 다시 동의해야 해요."
        "cuda_out_of_memory" -> "자체 AI 서버의 GPU 메모리가 부족해 생성하지 못했어요. 운영자가 GPU 사용량을 확인해야 해요."
        "model_load_failed" -> "자체 AI 모델을 불러오지 못했어요. 운영자가 모델 파일과 실행 환경을 확인해야 해요. 자동 재생성하지 않아요."
        "model_inference_failed" -> "모델을 불러온 뒤 착장 생성 중 오류가 발생했어요. 새 결과는 없으며 자동 재생성하지 않아요."
        "cancelled" -> "자체 AI 작업을 취소했어요."
        "result_expired","expired" -> "자체 AI 결과의 보관 시간이 만료됐어요. 자동으로 재생성하지 않아요."
        "server_restarted","interrupted" -> "자체 AI 서버가 재시작되어 결과를 복구할 수 없어요. 자동 재생성하지 않았어요."
        else -> "자체 AI 생성에 실패했어요. 서버 상태와 원본 사진을 확인해주세요."
    }
}
