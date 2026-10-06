package com.ozyern.brinacam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ozyern.brinacam.R

object BrinaColors {
    val Accent = Color(0xFFF28C38)
    val AccentLight = Color(0xFFF7A548)
    val AccentDark = Color(0xFFEC6F24)
    val Surface = Color(0xFF1C1C1C)
    val SurfaceRaised = Color(0xFF2B2B2B)
    val ChipSelected = Color(0x66000000)
    val Recording = Color(0xFFE5484D)
    val TextDim = Color(0xFF8C8C8C)
}

/**
 * Manrope (SIL OFL, see licenses/) is the closest open font to OnePlus Sans:
 * wide, rounded geometric shapes and similar figures. One variable font file
 * provides every weight.
 */
@OptIn(ExperimentalTextApi::class)
val BrinaFont = FontFamily(
    listOf(400, 500, 600, 700, 800).map { weight ->
        Font(
            R.font.manrope,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    }
)

private fun TextStyle.branded() = copy(fontFamily = BrinaFont)

private val BrinaTypography = Typography().run {
    copy(
        displayLarge = displayLarge.branded(),
        displayMedium = displayMedium.branded(),
        displaySmall = displaySmall.branded(),
        headlineLarge = headlineLarge.branded(),
        headlineMedium = headlineMedium.branded(),
        headlineSmall = headlineSmall.branded(),
        titleLarge = titleLarge.branded(),
        titleMedium = titleMedium.branded(),
        titleSmall = titleSmall.branded(),
        bodyLarge = bodyLarge.branded(),
        bodyMedium = bodyMedium.branded(),
        bodySmall = bodySmall.branded(),
        labelLarge = labelLarge.branded(),
        labelMedium = labelMedium.branded(),
        labelSmall = labelSmall.branded(),
    )
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
        typography = BrinaTypography,
        content = content,
    )
}
