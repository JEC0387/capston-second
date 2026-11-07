package com.jeiu.fashionproject.data

import java.security.MessageDigest

/** Runtime boundary only. No model, external service, or 2D substitute is registered here. */
enum class DeviceGarmentRole { TOP, BOTTOM, SHOES }

data class DeviceGarmentInput(
    val id: String,
    val role: DeviceGarmentRole,
    val source: String,
    val placementKey: String = ""
) {
    override fun toString() = "DeviceGarmentInput(role=$role, source=redacted)"
}

/** Owns a defensive copy: editing the wardrobe after submission cannot edit a request. */
class DeviceVtonSelection(val person: String, val revision: Long, garments: List<DeviceGarmentInput>) {
    private val frozenGarments = garments.map { it.copy() }
    val garments: List<DeviceGarmentInput> get() = frozenGarments.toList()
    fun copy(person: String = this.person, revision: Long = this.revision,
        garments: List<DeviceGarmentInput> = this.garments) = DeviceVtonSelection(person, revision, garments)
    override fun equals(other: Any?) = other is DeviceVtonSelection && person == other.person &&
        revision == other.revision && frozenGarments == other.frozenGarments
    override fun hashCode() = 31 * (31 * person.hashCode() + revision.hashCode()) + frozenGarments.hashCode()
    override fun toString() = "DeviceVtonSelection(revision=$revision, roles=${frozenGarments.map { it.role }}, sources=redacted)"
}

class DeviceModelSpec(val id: String, val revision: String, val sha256: String,
    supportedRoles: Set<DeviceGarmentRole>, val maxSide: Int = 1024) {
    private val frozenRoles = supportedRoles.toSet()
    val supportedRoles: Set<DeviceGarmentRole> get() = frozenRoles.toSet()
    fun snapshot() = DeviceModelSpec(id, revision, sha256, frozenRoles, maxSide)
    override fun equals(other: Any?) = other is DeviceModelSpec && id == other.id && revision == other.revision &&
        sha256 == other.sha256 && frozenRoles == other.frozenRoles && maxSide == other.maxSide
    override fun hashCode() = listOf(id, revision, sha256, frozenRoles, maxSide).hashCode()
    override fun toString() = "DeviceModelSpec(id=$id, revision=$revision, roles=$frozenRoles)"
}

/** An immutable local PNG; the preparer validates decoding and normalizes orientation. */
class DeviceImage(bytes: ByteArray, val width: Int, val height: Int) {
    private val frozenBytes = bytes.copyOf()
    val bytes: ByteArray get() = frozenBytes.copyOf()
    val sha256: String = deviceSha256(frozenBytes)
    override fun toString() = "DeviceImage(${width}x$height, bytes=${frozenBytes.size})"
}

class DevicePreparedJob(val id: String, selection: DeviceVtonSelection, model: DeviceModelSpec,
    val person: DeviceImage, garments: Map<DeviceGarmentRole, DeviceImage>, val inputDigest: String) {
    val selection = selection.copy()
    val model = model.snapshot()
    private val frozenGarments = garments.toMap()
    val garments: Map<DeviceGarmentRole, DeviceImage> get() = frozenGarments.toMap()
    override fun toString() = "DevicePreparedJob(id=$id, model=${model.id}, roles=${frozenGarments.keys})"
}

sealed interface DeviceEngineAvailability {
    data class Ready(val model: DeviceModelSpec) : DeviceEngineAvailability
    data class Unavailable(val reason: String) : DeviceEngineAvailability
}

/** Receipt fields must bind the output to this exact job and every selected category.
 * A trusted native adapter must provide real inference. A receipt is not proof of visual quality. */
class DeviceEngineOutput(val jobId: String, val inputDigest: String, val modelSha256: String,
    categories: Set<DeviceGarmentRole>, png: ByteArray) {
    private val frozenCategories = categories.toSet()
    private val frozenPng = png.copyOf()
    val categories: Set<DeviceGarmentRole> get() = frozenCategories.toSet()
    val png: ByteArray get() = frozenPng.copyOf()
    override fun toString() = "DeviceEngineOutput(jobId=$jobId, roles=$frozenCategories, bytes=${frozenPng.size})"
}

interface DeviceVtonEngine {
    val availability: DeviceEngineAvailability
    /** Consume only the frozen image bytes. Selection sources are provenance, not permission
     * to reopen a URI. Do not launch detached native work: return only after it has stopped. */
    suspend fun generate(job: DevicePreparedJob): DeviceEngineOutput
    /** Native work may finish later; callers keep the single-flight lock until it actually returns. */
    fun requestCancellation(jobId: String) {}
}

/** Deliberately no model importer, downloaded executable, mock result, or remote fallback. */
object UnavailableDeviceVtonEngine : DeviceVtonEngine {
    override val availability = DeviceEngineAvailability.Unavailable(
        "이 기기에 사용할 생성형 모델·검증된 Android 런타임·사용 권한이 준비되지 않았어요. " +
            "상의·하의·신발 전체 생성은 아직 사용할 수 없고, 2D 합성을 AI 결과로 대신하지 않아요.")
    override suspend fun generate(job: DevicePreparedJob): DeviceEngineOutput =
        throw DeviceVtonFailure("온디바이스 생성형 엔진이 준비되지 않았어요.")
}

class DeviceVtonFailure(message: String) : IllegalArgumentException(message)

fun deviceSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }
