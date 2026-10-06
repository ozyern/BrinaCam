package com.ozyern.brinacam.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * The backdrop every glass control refracts: the full-screen layer holding the
 * viewfinder (see CameraScreen).
 */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Liquid-glass surface (Kyant0/AndroidLiquidGlass): blur, vibrancy and lens
 * refraction of what's behind, plus a translucent tint. Blur needs Android 12+
 * and refraction Android 13+; older versions fall back to the tint alone.
 */
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color = Color(0x33FFFFFF),
    blurRadius: Dp = 10.dp,
    refractionHeight: Dp = 10.dp,
    refractionAmount: Dp = 18.dp,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(blurRadius.toPx())
        lens(refractionHeight.toPx(), refractionAmount.toPx())
    },
    onDrawSurface = { drawRect(tint) },
)

fun Modifier.glassCircle(backdrop: Backdrop, tint: Color = Color(0x40262626)): Modifier =
    glass(backdrop, CircleShape, tint)

fun Modifier.glassPill(backdrop: Backdrop, radius: Dp = 26.dp, tint: Color = Color(0x40262626)): Modifier =
    glass(backdrop, RoundedCornerShape(radius), tint)
