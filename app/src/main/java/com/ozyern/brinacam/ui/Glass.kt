package com.ozyern.brinacam.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/**
 * The backdrop frosted surfaces blur: the full-screen layer holding the
 * viewfinder (see CameraScreen).
 */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Frosted surface, the way OnePlus uses it on the quick-settings card and zoom
 * dial: a blur of what's behind plus a dark tint, no refraction or rim
 * highlight. Uses Kyant0/AndroidLiquidGlass; blur needs Android 12+, older
 * versions get the tint alone.
 */
@Composable
fun Modifier.frosted(shape: Shape, tint: Color, blurRadius: Dp = 24.dp): Modifier {
    val backdrop = LocalGlassBackdrop.current ?: return this.background(tint.copy(alpha = 1f), shape)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { blur(blurRadius.toPx()) },
        highlight = null,
        shadow = null,
        onDrawSurface = { drawRect(tint) },
    )
}
