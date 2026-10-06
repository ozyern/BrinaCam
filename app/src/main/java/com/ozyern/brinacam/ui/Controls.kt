package com.ozyern.brinacam.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer10
import androidx.compose.material.icons.rounded.Timer3
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.brinacam.camera.CaptureMode
import com.ozyern.brinacam.camera.FlashSetting
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RoundButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 40,
    color: Color = BrinaColors.Surface,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ---- Top bar ------------------------------------------------------------------------------
// Measured from the OnePlus 13 camera (dp): pill 133.7 x 36.3 at x=16, round buttons 36.3
// with an 11.5 gap and 16 right margin, all centred 45.7 above the viewfinder.

val ControlHeight = 36.dp

@Composable
fun TopBar(
    flash: FlashSetting,
    timerSeconds: Int,
    exposureValue: Float,
    exposureEnabled: Boolean,
    focusLocked: Boolean,
    quickMenuOpen: Boolean,
    rotation: Float,
    onFlash: () -> Unit,
    onTimer: () -> Unit,
    onExposure: () -> Unit,
    onFocusLock: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(ControlHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(ControlHeight)
                .clip(RoundedCornerShape(18.dp))
                .background(BrinaColors.Surface)
                .padding(start = 6.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton("Flash", onFlash) {
                FlashGlyph(flash, Modifier.size(20.dp).rotate(rotation))
            }
            PillButton("Timer", onTimer) {
                TimerGlyph(timerSeconds, Modifier.size(20.dp).rotate(rotation))
            }
            Row(
                Modifier
                    .height(ControlHeight)
                    .clickable(enabled = exposureEnabled, onClick = onExposure)
                    .semantics { contentDescription = "Exposure" }
                    .padding(start = 6.dp)
                    .rotate(rotation),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "EV",
                    color = Color(0xFF9A9A9A),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Top).padding(top = 9.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    formatEv(exposureValue),
                    color = if (exposureValue != 0f) BrinaColors.Accent else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        RoundButton("Focus lock", onFocusLock, size = 36) {
            FocusFrameGlyph(focusLocked, Modifier.size(19.dp).rotate(rotation))
        }
        Spacer(Modifier.width(11.5.dp))
        val moreBackground by animateColorAsState(
            if (quickMenuOpen) Color.White else BrinaColors.Surface,
            tween(200),
            label = "moreBg",
        )
        val moreDots by animateColorAsState(
            if (quickMenuOpen) Color.Black else Color.White,
            tween(200),
            label = "moreDots",
        )
        RoundButton("More controls", onMore, size = 36, color = moreBackground) {
            SixDots(Modifier.size(width = 14.dp, height = 9.dp), color = moreDots)
        }
    }
}

@Composable
private fun PillButton(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** OnePlus "more" glyph: two rows of three dots. */
@Composable
fun SixDots(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val stepX = size.width / 3f
        val stepY = size.height / 2f
        val radius = minOf(stepX, stepY) * 0.38f
        for (row in 0..1) for (col in 0..2) {
            drawCircle(color, radius, Offset(stepX * (col + 0.5f), stepY * (row + 0.5f)))
        }
    }
}

fun formatEv(value: Float): String = when {
    value > 0.05f -> String.format(Locale.US, "+%.1f", value)
    value < -0.05f -> String.format(Locale.US, "%.1f", value)
    else -> "0.0"
}

// ---- Bottom row ---------------------------------------------------------------------------
// Thumbnail and switch are 45.5 dp, centred at 1/6 and 5/6 of the width; the shutter is a
// 61 dp orange disc in a 75 dp dark ring, all centred on one line.

val ShutterRowHeight = 100.dp

@Composable
fun ShutterRow(
    thumbnail: Bitmap?,
    mode: CaptureMode,
    isRecording: Boolean,
    rotation: Float,
    onGallery: () -> Unit,
    onShutter: () -> Unit,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(ShutterRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(45.5.dp)
                    .rotate(rotation)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrinaColors.Surface)
                    .clickable(onClick = onGallery)
                    .semantics { contentDescription = "Gallery" },
            ) {
                if (thumbnail != null) {
                    Image(
                        thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(45.5.dp),
                    )
                }
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ShutterButton(mode = mode, isRecording = isRecording, onClick = onShutter)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            var turns by remember { mutableFloatStateOf(0f) }
            val spin by animateFloatAsState(turns * 180f, spring(stiffness = Spring.StiffnessLow), label = "switch")
            RoundButton(
                "Switch camera",
                onClick = {
                    turns += 1f
                    onSwitch()
                },
                size = 45,
            ) {
                SyncGlyph(Modifier.size(24.dp).rotate(rotation + spin))
            }
        }
    }
}

@Composable
fun ShutterButton(mode: CaptureMode, isRecording: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press",
    )
    val isVideo = mode == CaptureMode.VIDEO
    val top by animateColorAsState(if (isVideo) Color(0xFFF0585C) else Color(0xFFEC983E), label = "top")
    val mid by animateColorAsState(if (isVideo) Color(0xFFE94D52) else Color(0xFFEC8C38), label = "mid")
    val bottom by animateColorAsState(if (isVideo) Color(0xFFE0434A) else Color(0xFFEE7C33), label = "bottom")

    Box(
        Modifier
            .size(ShutterRowHeight)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = "Shutter" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(ShutterRowHeight)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            // Faint warm glow.
            drawCircle(
                Brush.radialGradient(
                    0.6f to bottom.copy(alpha = 0.16f),
                    1f to Color.Transparent,
                    center = c,
                    radius = size.minDimension / 2f,
                ),
            )
            // 75 dp ring: dark warm grey with a slightly lighter rim.
            drawCircle(Color(0xFF332C29), radius = 37.5.dp.toPx(), center = c)
            drawCircle(
                Color(0xFF4C4240),
                radius = 37.dp.toPx(),
                center = c,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        if (isRecording) {
            Box(
                Modifier
                    .size(26.dp)
                    .scale(press)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BrinaColors.Recording),
            )
        } else {
            Box(
                Modifier
                    .size(61.dp)
                    .scale(press)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(top, mid, bottom))),
            )
        }
    }
}

// ---- Mode strip ---------------------------------------------------------------------------

val ModeStripHeight = 46.dp

@Composable
fun ModeStrip(
    modes: List<CaptureMode>,
    selected: CaptureMode,
    enabled: Boolean,
    onSelect: (CaptureMode) -> Unit,
    onPhotoOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val centers = remember { mutableStateMapOf<CaptureMode, Float>() }
    var width by remember { mutableIntStateOf(0) }
    val target = centers[selected]?.let { width / 2f - it } ?: 0f
    val offset by animateFloatAsState(
        target,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "modes",
    )
    val threshold = with(density) { 40.dp.toPx() }

    Box(
        modifier
            .fillMaxWidth()
            .height(ModeStripHeight)
            .onSizeChanged { width = it.width }
            .pointerInput(modes, selected, enabled) {
                var travel = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travel = 0f },
                    onDragEnd = {
                        val index = modes.indexOf(selected)
                        if (enabled && travel < -threshold) modes.getOrNull(index + 1)?.let(onSelect)
                        if (enabled && travel > threshold) modes.getOrNull(index - 1)?.let(onSelect)
                    },
                ) { change, dx ->
                    change.consume()
                    travel += dx
                }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.2f to Color.Black,
                        0.8f to Color.Black,
                        1f to Color.Black.copy(alpha = 0.35f),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .offset { IntOffset(offset.roundToInt(), 0) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            modes.forEach { mode ->
                val isSelected = mode == selected
                val textColor by animateColorAsState(
                    if (isSelected) BrinaColors.Accent else Color.White,
                    label = "modeText",
                )
                Row(
                    Modifier
                        .onGloballyPositioned {
                            centers[mode] = it.positionInParent().x + it.size.width / 2f
                        }
                        .height(34.5.dp)
                        .then(
                            if (isSelected) {
                                Modifier
                                    .clip(RoundedCornerShape(17.25.dp))
                                    .background(Color(0xFF191919))
                                    .border(BorderStroke(1.dp, Color(0xFF2B2B2B)), RoundedCornerShape(17.25.dp))
                            } else {
                                Modifier.clip(RoundedCornerShape(17.25.dp))
                            }
                        )
                        .clickable(enabled = enabled) {
                            if (isSelected && mode == CaptureMode.PHOTO) onPhotoOptions() else onSelect(mode)
                        }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        mode.label,
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    if (isSelected && mode == CaptureMode.PHOTO) {
                        Spacer(Modifier.width(8.dp))
                        Canvas(Modifier.size(width = 8.dp, height = 5.5.dp)) {
                            val path = Path().apply {
                                moveTo(size.width / 2f, 0f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(path, BrinaColors.Accent)
                        }
                    }
                }
            }
        }
    }
}

// ---- Small overlays -----------------------------------------------------------------------

@Composable
fun RecordingChip(seconds: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(BrinaColors.Recording),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = Color.White.copy(alpha = 0.4f)
        val stroke = 0.8f.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
            drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

@Composable
fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

