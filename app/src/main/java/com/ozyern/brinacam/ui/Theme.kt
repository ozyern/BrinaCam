package com.ozyern.brinacam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object BrinaColors {
    val Accent = Color(0xFFF28C38)
    val AccentLight = Color(0xFFF7A548)
    val AccentDark = Color(0xFFEC6F24)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceRaised = Color(0xFF2B2B2B)
    val ChipSelected = Color(0x99594436)
    val Recording = Color(0xFFE5484D)
    val TextDim = Color(0xFF8C8C8C)
}

@Composable
fun BrinaCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BrinaColors.Accent,
            onPrimary = Color.Black,
            background = Color.Black,
            surface = BrinaColors.Surface,
            onSurface = Color.White,
        ),
        content = content,
    )
}
