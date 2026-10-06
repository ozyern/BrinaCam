package com.ozyern.brinacam.camera

import android.hardware.camera2.CameraMetadata

enum class CaptureMode(val label: String) {
    NIGHT("NIGHT"),
    VIDEO("VIDEO"),
    PHOTO("PHOTO"),
    PORTRAIT("PORTRAIT"),
    HIRES("HI-RES"),
}

enum class FlashSetting { OFF, AUTO, ON }

/** Viewfinder aspect, expressed as height / width in portrait. */
enum class AspectSetting(val label: String, val heightOverWidth: Float) {
    R4_3("4:3", 4f / 3f),
    R16_9("16:9", 16f / 9f),
    R1_1("1:1", 1f),
}

data class ColorEffect(val mode: Int, val label: String)

fun colorEffectLabel(mode: Int): String = when (mode) {
    CameraMetadata.CONTROL_EFFECT_MODE_OFF -> "Original"
    CameraMetadata.CONTROL_EFFECT_MODE_MONO -> "Mono"
    CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE -> "Negative"
    CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE -> "Solarize"
    CameraMetadata.CONTROL_EFFECT_MODE_SEPIA -> "Sepia"
    CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE -> "Posterize"
    CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD -> "Whiteboard"
    CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD -> "Blackboard"
    CameraMetadata.CONTROL_EFFECT_MODE_AQUA -> "Aqua"
    else -> "Effect $mode"
}
