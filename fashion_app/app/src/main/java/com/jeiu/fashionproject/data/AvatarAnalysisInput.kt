package com.jeiu.fashionproject.data

/** Exact tape values for analysis, with a separate, validated approximation for the renderer. */
internal object AvatarAnalysisInput {
    val coreParts = listOf(AvatarPart.CHEST, AvatarPart.WAIST, AvatarPart.HIP, AvatarPart.SHOULDER)
    val extraParts = listOf(AvatarPart.LEG, AvatarPart.TORSO, AvatarPart.THIGH)
    private val dimensions = mapOf(
        AvatarPart.SHOULDER to BodyDimension.SHOULDER_WIDTH,
        AvatarPart.CHEST to BodyDimension.CHEST_CIRCUMFERENCE,
        AvatarPart.WAIST to BodyDimension.WAIST_CIRCUMFERENCE,
        AvatarPart.HIP to BodyDimension.HIP_CIRCUMFERENCE,
        AvatarPart.LEG to BodyDimension.LEG_LENGTH,
        AvatarPart.TORSO to BodyDimension.TORSO_LENGTH
    )

    fun recorded(profile: AvatarProfile): Map<AvatarPart, Float> =
        AvatarPart.entries.mapNotNull { part ->
            val reference = profile.centimeters?.references?.get(part)
            val cm = if (reference != null) reference.cm.takeIf { reference.origin == AvatarValueOrigin.MANUAL }
            else profile.applied[dimensions[part]]?.takeIf { it.origin == MeasurementOrigin.USER_ENTERED }?.cm
            cm?.let { part to it }
        }.toMap()

    fun valid(part: AvatarPart, value: Float, height: Float): Boolean = value.isFinite() && when (part) {
        AvatarPart.SHOULDER -> value in 15f..75f && value / height in .10f.. .45f
        AvatarPart.CHEST, AvatarPart.WAIST, AvatarPart.HIP -> value in 40f..200f
        AvatarPart.LEG -> value in height * .25f..height * .65f
        AvatarPart.TORSO -> value in height * .15f..height * .40f
        AvatarPart.THIGH -> value in 20f..100f
    }

    fun lengthsFit(values: Map<AvatarPart, Float>, height: Float): Boolean {
        val leg = values[AvatarPart.LEG] ?: return true
        val torso = values[AvatarPart.TORSO] ?: return true
        return leg + torso <= height * .95f
    }

    fun build(height: Float, weight: Float, measured: Map<AvatarPart, Float>,
        previous: AvatarProfile?, timestamp: Long): AvatarProfile? {
        if (measured.any { (part, cm) -> !valid(part, cm, height) } || !lengthsFit(measured, height)) return null
        val sameBody = previous?.takeIf { it.valid() && it.heightCm == height && it.weightKg == weight }
        val base = sameBody?.let { AvatarCentimeters.fromLegacy(it, timestamp) }
            ?: AvatarCentimeters.generate(height, weight, timestamp) ?: return null
        val oldMeasured = previous?.let(::recorded).orEmpty()
        val references = base.references.filterValues { it.origin != AvatarValueOrigin.MANUAL } +
            measured.mapValues { (_, cm) -> AvatarCm(cm, AvatarValueOrigin.MANUAL) }

        val changed = AvatarPart.entries.filter { sameBody == null || oldMeasured[it] != measured[it] }
        val targets = changed.associateWith { part ->
            measured[part]?.let { cm ->
                val snapped = AvatarCentimeters.snap(cm)
                AvatarCm(snapped, if (snapped == cm) AvatarValueOrigin.MANUAL else AvatarValueOrigin.ADJUSTED)
            } ?: AvatarCm(base.initial.getValue(part), AvatarValueOrigin.AUTO)
        }
        val updated = base.copy(references = references, updatedAt = maxOf(timestamp, base.updatedAt))
        val combined = updated.copy(values = updated.values + targets)
        val model = if (combined.issue() == null) combined else {
            // Some real tape measurements cannot be represented by this mannequin.
            // Keep the exact input and only apply compatible changes to the preview.
            targets.entries.fold(updated) { current, (part, value) ->
                current.copy(values = current.values + (part to value)).takeIf { it.issue() == null } ?: current
            }
        }
        // The photo and manual-entry stores remain independent. The saved avatar is a
        // single durable record containing both exact references and visual settings.
        val source = sameBody?.takeIf { it.source == AvatarSource.PHOTO }
        return (source ?: AvatarProfile(height, weight)).copy(centimeters = model).takeIf(AvatarProfile::valid)
    }
}
