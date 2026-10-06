package com.ozyern.brinacam.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HdrAuto
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.brinacam.camera.ColorEffect
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class QuickItem(
    val label: String,
    val active: Boolean,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val icon: @Composable (Color) -> Unit,
)

/**
 * The 2x3-dot menu: a frosted card of round toggles, OnePlus style. Measured
 * from the OnePlus 13 (dp): 384 wide, 89 dp columns, 61 dp circles, 110.6 dp
 * row pitch, 28 dp corners.
 */
@Composable
fun QuickMenu(items: List<QuickItem>, rotation: Float, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 13.7.dp)
            .frosted(RoundedCornerShape(28.dp), Color(0xD92A2A2A), blurRadius = 30.dp)
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(28.dp))
            .padding(start = 13.8.dp, end = 13.8.dp, top = 27.5.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items.chunked(4).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { colIndex, item ->
                    // Items ripple in one after another as the card opens.
                    val appear = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        delay((rowIndex * 4 + colIndex) * 22L)
                        appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 500f))
                    }
                    val iconColor = if (item.active) Color(0xFF2A1404) else Color.White
                    Column(
                        Modifier
                            .weight(1f)
                            .graphicsLayer {
                                alpha = appear.value.coerceIn(0f, 1f) * (if (item.enabled) 1f else 0.4f)
                                val scale = 0.7f + 0.3f * appear.value
                                scaleX = scale
                                scaleY = scale
                                translationY = (1f - appear.value) * 14.dp.toPx()
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = item.enabled, onClick = item.onClick)
                            .semantics { contentDescription = item.label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(61.dp)
                                .clip(CircleShape)
                                .background(
                                    if (item.active) {
                                        Brush.verticalGradient(listOf(Color(0xFFFF7A0A), Color(0xFFFF9A3C)))
                                    } else {
                                        Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x24FFFFFF)))
                                    }
                                )
                                .border(1.dp, if (item.active) Color.Transparent else Color(0x1FFFFFFF), CircleShape)
                                .rotate(rotation),
                            contentAlignment = Alignment.Center,
                        ) {
                            item.icon(iconColor)
                        }
                        Spacer(Modifier.height(11.dp))
                        Text(
                            item.label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HdrText(color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("HDR", color = color, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 15.sp)
        Text("AUTO", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 10.sp)
    }
}

fun quickMenuItems(
    aspectLabel: String,
    gridOn: Boolean,
    timerSeconds: Int,
    hdrOn: Boolean,
    hdrAvailable: Boolean,
    mirrorOn: Boolean,
    soundOn: Boolean,
    filtersAvailable: Boolean,
    onAspect: () -> Unit,
    onGrid: () -> Unit,
    onTimer: () -> Unit,
    onHdr: () -> Unit,
    onMirror: () -> Unit,
    onSound: () -> Unit,
    onFilters: () -> Unit,
    onAbout: () -> Unit,
): List<QuickItem> = listOf(
    QuickItem("Aspect ratio $aspectLabel", active = false, onClick = onAspect) { AspectGlyph(Modifier.size(26.dp)) },
    QuickItem("Grid", active = gridOn, onClick = onGrid) { GridGlyph(Modifier.size(26.dp)) },
    QuickItem(if (timerSeconds > 0) "Timer ${timerSeconds}s" else "Timer", active = timerSeconds > 0, onClick = onTimer) {
        TimerGlyph(0, Modifier.size(26.dp))
    },
    QuickItem("Filters", active = false, enabled = filtersAvailable, onClick = onFilters) { color ->
        FiltersGlyph(Modifier.size(26.dp), color)
    },
    QuickItem("Auto HDR", active = hdrOn && hdrAvailable, enabled = hdrAvailable, onClick = onHdr) { color -> HdrText(color) },
    QuickItem("Mirror", active = mirrorOn, onClick = onMirror) { MirrorGlyph(Modifier.size(26.dp)) },
    QuickItem("Sound", active = soundOn, onClick = onSound) { SoundGlyph(soundOn, Modifier.size(26.dp)) },
    QuickItem("Settings", active = false, onClick = onAbout) { GearGlyph(Modifier.size(26.dp)) },
)

/** Exposure slider shown under the top bar when EV is tapped. */
@Composable
fun ExposurePanel(
    index: Int,
    min: Int,
    max: Int,
    value: Float,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .frosted(RoundedCornerShape(24.dp), Color(0xB31C1C1C))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = BrinaColors.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Slider(
            value = index.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = BrinaColors.Accent,
                activeTrackColor = BrinaColors.Accent,
                inactiveTrackColor = Color(0x66FFFFFF),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(formatEv(value), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Camera colour effects reported by the device. */
@Composable
fun EffectsRow(effects: List<ColorEffect>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(effects, key = { it.mode }) { effect ->
            val isSelected = effect.mode == selected
            Box(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) BrinaColors.Surface else Color(0x80000000))
                    .clickable { onSelect(effect.mode) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    effect.label,
                    color = if (isSelected) BrinaColors.Accent else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Focus brackets with the exposure sun beside them; the sun follows EV drags. */
@Composable
fun FocusRing(position: Offset, key: Int, exposureFraction: Float) {
    val density = LocalDensity.current
    val sizePx = with(density) { 76.dp.toPx() }
    val appear = remember(key) { Animatable(1.3f) }
    LaunchedEffect(key) { appear.animateTo(1f, tween(180)) }

    Box(
        Modifier.offset {
            IntOffset((position.x - sizePx / 2f).roundToInt(), (position.y - sizePx / 2f).roundToInt())
        },
    ) {
        Canvas(Modifier.size(76.dp)) {
            val scale = appear.value
            val half = size.width / 2f * scale
            val c = Offset(size.width / 2f, size.height / 2f)
            val arm = size.width * 0.2f
            val stroke = 1.6f.dp.toPx()
            val color = Color.White
            listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                val corner = Offset(c.x + sx * half, c.y + sy * half)
                drawLine(color, corner, Offset(corner.x - sx * arm, corner.y), stroke, StrokeCap.Round)
                drawLine(color, corner, Offset(corner.x, corner.y - sy * arm), stroke, StrokeCap.Round)
            }
        }
        // Sun: moves up for brighter, down for darker.
        val travel = with(density) { 30.dp.toPx() }
        Icon(
            Icons.Rounded.WbSunny,
            contentDescription = null,
            tint = if (exposureFraction != 0f) BrinaColors.Accent else Color.White,
            modifier = Modifier
                .offset { IntOffset((sizePx + 8.dp.toPx()).roundToInt(), (sizePx / 2f - 9.dp.toPx() - exposureFraction * travel).roundToInt()) }
                .size(18.dp),
        )
    }
}
