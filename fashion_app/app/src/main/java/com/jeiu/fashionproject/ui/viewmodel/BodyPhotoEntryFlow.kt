package com.jeiu.fashionproject.ui.viewmodel

enum class BodyPhotoSource { CAMERA, GALLERY }
enum class BodyPhotoEntryStep { HEIGHT, CONSENT, LAUNCH }
data class BodyPhotoEntryRequest(val source: BodyPhotoSource, val step: BodyPhotoEntryStep)

/** Keeps the chosen action while height/consent is requested; never substitutes a default height. */
class BodyPhotoEntryFlow {
    private var pending: BodyPhotoSource? = null
    private var externalOpen = false

    fun request(source: BodyPhotoSource, height: String, agreed: Boolean): BodyPhotoEntryRequest? {
        if (pending != null || externalOpen) return null
        pending = source
        return advance(height, agreed)
    }

    fun advance(height: String, agreed: Boolean): BodyPhotoEntryRequest? {
        val source = pending ?: return null
        val step = when {
            BodyMeasurementCalculator.parseHeight(height) == null -> BodyPhotoEntryStep.HEIGHT
            !agreed -> BodyPhotoEntryStep.CONSENT
            else -> BodyPhotoEntryStep.LAUNCH
        }
        if (step == BodyPhotoEntryStep.LAUNCH) { pending = null; externalOpen = true }
        return BodyPhotoEntryRequest(source, step)
    }

    fun cancel() { pending = null }
    fun returnedFromExternal() { externalOpen = false }
}
