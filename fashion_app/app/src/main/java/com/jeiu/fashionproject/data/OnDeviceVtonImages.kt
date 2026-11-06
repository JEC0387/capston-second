package com.jeiu.fashionproject.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/** Local, bounded image preparation only; this is not a model or an inference substitute.
 * Canonical PNGs keep the displayed EXIF orientation and aspect ratio, and drop metadata.
 * Tensor layouts, semantic masks, and model-specific normalization belong to a validated adapter.
 * A selected content provider may itself be cloud-backed; no HTTP loader or URI fallback is used here.
 */
object OnDeviceVtonImages {
    const val MAX_ENCODED_BYTES = 8 * 1024 * 1024
    private const val MAX_PIXELS = 20_000_000L
    private val roles = DeviceGarmentRole.entries.toList()
    private val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)

    /** Shared by controller and export: a corrupt PNG cannot first be displayed as success. */
    fun validateResultPng(bytes: ByteArray) {
        LocalVtonRepository.validatePng(bytes)
        validatePngStructure(bytes)
    }

    suspend fun prepare(
        context: Context,
        jobId: String,
        selection: DeviceVtonSelection,
        model: DeviceModelSpec
    ): DevicePreparedJob {
        val frozenSelection = selection.copy()
        val frozenModel = model.snapshot()
        validateSelection(jobId, frozenSelection, frozenModel)
        return withContext(Dispatchers.IO) {
            try {
                val person = freeze(context, frozenSelection.person, frozenModel.maxSide)
                val garments = roles.associateWith { role ->
                    currentCoroutineContext().ensureActive()
                    freeze(context, frozenSelection.garments.single { it.role == role }.source,
                        frozenModel.maxSide)
                }
                currentCoroutineContext().ensureActive()
                DevicePreparedJob(jobId, frozenSelection, frozenModel, person, garments,
                    receiptDigest(jobId, frozenSelection, frozenModel, person, garments))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: DeviceVtonFailure) {
                throw failure
            } catch (_: OutOfMemoryError) {
                throw DeviceVtonFailure("이미지를 준비할 메모리가 부족해요. 더 작은 사진으로 다시 선택해주세요.")
            } catch (_: Exception) {
                throw DeviceVtonFailure("선택한 이미지를 읽을 수 없어요. 기기에서 다시 선택해주세요.")
            }
        }
    }

    /** Saves the exact verified result PNG, without a lossy or metadata-changing re-encode.
     * SAF writes are not atomic. On error/cancellation the caller must not report success;
     * the chosen destination may contain a partial file and is not deleted automatically.
     */
    suspend fun saveExact(context: Context, png: ByteArray, target: Uri) {
        if (png.size !in 8..MAX_ENCODED_BYTES) throw DeviceVtonFailure("저장할 PNG의 크기가 올바르지 않아요.")
        val frozen = try { png.copyOf() } catch (_: OutOfMemoryError) {
            throw DeviceVtonFailure("저장할 메모리가 부족해요. 파일을 쓰기 전에 중단했어요.")
        }
        withContext(Dispatchers.IO) {
            try {
                currentCoroutineContext().ensureActive()
                validateResultPng(frozen)
                val stream = when (target.scheme?.lowercase()) {
                    "content" -> {
                        if (target.authority.isNullOrBlank() || target.fragment != null)
                            throw DeviceVtonFailure("파일 선택기에서 저장 위치를 다시 선택해주세요.")
                        context.contentResolver.openOutputStream(target, "wt")
                    }
                    "file" -> {
                        if (!target.authority.isNullOrEmpty() || target.query != null || target.fragment != null)
                            throw DeviceVtonFailure("허용되지 않은 저장 위치예요.")
                        val file = File(target.path ?: throw DeviceVtonFailure("저장 위치가 올바르지 않아요.")).canonicalFile
                        val allowed = listOf(context.filesDir, context.cacheDir).any {
                            file.path.startsWith(it.canonicalFile.path + File.separator)
                        }
                        if (!allowed) throw DeviceVtonFailure("허용되지 않은 저장 위치예요.")
                        file.outputStream()
                    }
                    else -> throw DeviceVtonFailure("파일 선택기에서 저장 위치를 다시 선택해주세요.")
                } ?: throw DeviceVtonFailure("저장 위치를 열 수 없어요.")
                stream.use {
                    var offset = 0
                    while (offset < frozen.size) {
                        currentCoroutineContext().ensureActive()
                        val count = minOf(16 * 1024, frozen.size - offset)
                        it.write(frozen, offset, count)
                        offset += count
                    }
                    it.flush()
                }
                currentCoroutineContext().ensureActive()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: DeviceVtonFailure) {
                throw failure
            } catch (_: OutOfMemoryError) {
                throw DeviceVtonFailure("저장할 메모리가 부족해요. 선택한 파일이 완성되지 않았을 수 있어요.")
            } catch (_: Exception) {
                throw DeviceVtonFailure("PNG 저장을 완료하지 못했어요. 선택한 파일이 일부만 저장됐을 수 있어요.")
            }
        }
    }

    internal fun validateSelection(jobId: String, selection: DeviceVtonSelection, model: DeviceModelSpec) {
        if (jobId.isBlank() || jobId.length > 128 || selection.revision < 0)
            throw DeviceVtonFailure("생성 요청 정보가 올바르지 않아요.")
        if (model.id.isBlank() || model.revision.isBlank() || model.id.length > 128 ||
            model.revision.length > 128 || !model.sha256.matches(Regex("[a-fA-F0-9]{64}")) ||
            model.maxSide !in 32..2048 || !model.supportedRoles.containsAll(roles))
            throw DeviceVtonFailure("상의·하의·신발을 지원하는 검증된 모델 설정이 필요해요.")
        val garments = selection.garments
        if (garments.size != roles.size || garments.map { it.role }.toSet() != roles.toSet() ||
            garments.map { it.id }.toSet().size != roles.size ||
            garments.any { it.id.isBlank() || it.id.length > 512 || it.placementKey.length > 2048 })
            throw DeviceVtonFailure("서로 다른 상의·하의·신발을 한 벌씩 선택해주세요.")
        // Reject every URI before opening any of them; a network source cannot be partially accepted.
        localUri(selection.person)
        garments.forEach { localUri(it.source) }
    }

    private fun localUri(source: String): Uri {
        if (source.isBlank() || source.length > 8192) throw DeviceVtonFailure("선택한 이미지 주소가 올바르지 않아요.")
        val uri = Uri.parse(source)
        val allowed = when (uri.scheme?.lowercase()) {
            "content", "android.resource" -> !uri.authority.isNullOrBlank() && uri.fragment == null
            "file" -> uri.authority.isNullOrEmpty() && !uri.path.isNullOrBlank() &&
                File(uri.path!!).isAbsolute && uri.query == null && uri.fragment == null
            else -> false
        }
        if (!allowed) throw DeviceVtonFailure("기기에서 직접 선택한 로컬 이미지만 사용할 수 있어요.")
        return uri
    }

    private suspend fun freeze(context: Context, source: String, maxSide: Int): DeviceImage {
        currentCoroutineContext().ensureActive()
        val encoded = context.contentResolver.openInputStream(localUri(source))?.use { readBounded(it) }
            ?: throw DeviceVtonFailure("선택한 이미지를 열 수 없어요.")
        currentCoroutineContext().ensureActive()
        return canonicalImage(encoded, maxSide)
    }

    private suspend fun readBounded(input: InputStream): ByteArray {
        val output = BoundedBytes(MAX_ENCODED_BYTES)
        val buffer = ByteArray(16 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) throw DeviceVtonFailure("이미지 읽기가 중단됐어요. 다시 선택해주세요.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private suspend fun canonicalImage(encoded: ByteArray, maxSide: Int): DeviceImage {
        val isPng = encoded.size >= 8 && pngSignature.indices.all { encoded[it] == pngSignature[it] }
        val isJpeg = encoded.size >= 4 && encoded[0] == 0xff.toByte() && encoded[1] == 0xd8.toByte() &&
            encoded[encoded.lastIndex - 1] == 0xff.toByte() && encoded.last() == 0xd9.toByte()
        if (!isPng && !isJpeg) throw DeviceVtonFailure("정상적인 PNG 또는 JPEG 이미지를 선택해주세요.")
        if (isPng) validatePngStructure(encoded)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        if (bounds.outWidth < 32 || bounds.outHeight < 32 ||
            bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS ||
            bounds.outMimeType != if (isPng) "image/png" else "image/jpeg")
            throw DeviceVtonFailure("이미지 크기 또는 형식이 지원 범위를 벗어났어요.")
        val orientation = ByteArrayInputStream(encoded).use { ExifInterface(it).rotationOrientation() }
        var sample = 1
        while ((maxOf(bounds.outWidth, bounds.outHeight) + sample - 1) / sample > maxSide) sample *= 2
        currentCoroutineContext().ensureActive()
        var bitmap: Bitmap? = BitmapFactory.decodeByteArray(encoded, 0, encoded.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                if (Build.VERSION.SDK_INT >= 26) inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            }) ?: throw DeviceVtonFailure("이미지의 실제 픽셀을 읽을 수 없어요.")
        try {
            val decoded = requireNotNull(bitmap)
            if (decoded.width <= 0 || decoded.height <= 0 || maxOf(decoded.width, decoded.height) > maxSide)
                throw DeviceVtonFailure("이미지 디코딩 크기가 안전 범위를 벗어났어요.")
            val matrix = orientationMatrix(orientation)
            if (!matrix.isIdentity) {
                val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (oriented !== decoded) decoded.recycle()
                bitmap = oriented
            }
            currentCoroutineContext().ensureActive()
            val output = BoundedBytes(MAX_ENCODED_BYTES)
            val normalized = requireNotNull(bitmap)
            if (!normalized.compress(Bitmap.CompressFormat.PNG, 100, output))
                throw DeviceVtonFailure("이미지를 PNG로 준비하지 못했어요.")
            currentCoroutineContext().ensureActive()
            return DeviceImage(output.toByteArray(), normalized.width, normalized.height)
        } finally {
            bitmap?.recycle()
        }
    }

    private fun ExifInterface.rotationOrientation() = getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL).let {
        if (it !in 1..8) ExifInterface.ORIENTATION_NORMAL else it
    }

    private fun orientationMatrix(orientation: Int) = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }

    private fun validatePngStructure(bytes: ByteArray) {
        if (bytes.size < 33 || !pngSignature.indices.all { bytes[it] == pngSignature[it] })
            throw DeviceVtonFailure("PNG 파일이 손상됐어요.")
        fun unsignedInt(offset: Int): Long = (0..3).fold(0L) { value, index ->
            (value shl 8) or (bytes[offset + index].toLong() and 255L)
        }
        var offset = 8
        var sawImageData = false
        while (offset <= bytes.size - 12) {
            val length = unsignedInt(offset)
            if (length > bytes.size.toLong() - offset - 12) throw DeviceVtonFailure("PNG 파일이 잘렸어요.")
            val count = length.toInt()
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            if (offset == 8 && (type != "IHDR" || count != 13)) throw DeviceVtonFailure("PNG 헤더가 올바르지 않아요.")
            val checksum = CRC32().apply { update(bytes, offset + 4, count + 4) }.value
            if (checksum != unsignedInt(offset + 8 + count)) throw DeviceVtonFailure("PNG 파일이 손상됐어요.")
            if (type == "IDAT") sawImageData = true
            if (type == "acTL") throw DeviceVtonFailure("움직이는 이미지 대신 정지 이미지를 선택해주세요.")
            offset += count + 12
            if (type == "IEND") {
                if (count != 0 || offset != bytes.size || !sawImageData) throw DeviceVtonFailure("PNG 파일이 올바르지 않아요.")
                return
            }
        }
        throw DeviceVtonFailure("PNG 파일이 완성되지 않았어요.")
    }

    private fun receiptDigest(jobId: String, selection: DeviceVtonSelection, model: DeviceModelSpec,
        person: DeviceImage, garments: Map<DeviceGarmentRole, DeviceImage>): String {
        // Fixed order + length prefixes avoid ambiguous concatenation and list-order dependence.
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun field(value: String) {
                val encoded = value.toByteArray(Charsets.UTF_8)
                out.writeInt(encoded.size)
                out.write(encoded)
            }
            fun image(value: DeviceImage) {
                field(value.sha256); out.writeInt(value.width); out.writeInt(value.height)
            }
            field("fashionai-device-input-v1"); field(jobId)
            out.writeLong(selection.revision)
            field(model.id); field(model.revision); field(model.sha256.lowercase()); out.writeInt(model.maxSide)
            field(selection.person); image(person)
            roles.forEach { role ->
                val input = selection.garments.single { it.role == role }
                field(role.name); field(input.id); field(input.source); field(input.placementKey)
                image(garments.getValue(role))
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private class BoundedBytes(private val maximum: Int) : ByteArrayOutputStream() {
        override fun write(value: Int) {
            if (count >= maximum) throw DeviceVtonFailure("이미지는 한 장당 8MB 이하여야 해요.")
            super.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length > maximum - count) throw DeviceVtonFailure("이미지는 한 장당 8MB 이하여야 해요.")
            super.write(bytes, offset, length)
        }
    }
}
