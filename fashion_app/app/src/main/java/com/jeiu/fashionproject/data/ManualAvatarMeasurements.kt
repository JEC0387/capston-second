package com.jeiu.fashionproject.data

/** Only a freshly typed, explicitly measured value may be offered back to the original record. */
object ManualAvatarMeasurements {
    fun proposed(previous: ManualBodyMeasurements?, height: Float, part: AvatarPart, cm: Float,
        timestamp: Long): ManualBodyMeasurements? {
        if (!height.isFinite() || !cm.isFinite() || part == AvatarPart.THIGH) return null
        // A different avatar must not change the height/context of a user's existing measurements.
        if (previous != null && previous.heightCm != height) return null
        val base = previous ?: ManualBodyMeasurements(height, null, null, null, null, null, null, timestamp)
        val next = when (part) {
            AvatarPart.SHOULDER -> base.copy(shoulderWidthCm = cm)
            AvatarPart.CHEST -> base.copy(chestCircumferenceCm = cm)
            AvatarPart.WAIST -> base.copy(waistCircumferenceCm = cm)
            AvatarPart.HIP -> base.copy(hipCircumferenceCm = cm)
            AvatarPart.LEG -> base.copy(legLengthCm = cm)
            AvatarPart.TORSO -> base.copy(torsoLengthCm = cm)
            AvatarPart.THIGH -> return null
        }.copy(enteredAt = timestamp)
        return next.takeIf { it.isValid() }
    }
}
