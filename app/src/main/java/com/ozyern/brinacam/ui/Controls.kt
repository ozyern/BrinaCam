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
            .height(TopBarHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BrinaColors.Surface)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillIcon(
                icon = when (flash) {
                    FlashSetting.OFF -> Icons.Rounded.FlashOff
                    FlashSetting.AUTO -> Icons.Rounded.FlashAuto
                    FlashSetting.ON -> Icons.Rounded.FlashOn
                },
                description = "Flash",
                rotation = rotation,
                tint = if (flash == FlashSetting.ON) BrinaColors.Accent else Color.White,
                onClick = onFlash,
            )
            PillIcon(
                icon = when (timerSeconds) {
                    3 -> Icons.Rounded.Timer3
                    10 -> Icons.Rounded.Timer10
                    else -> Icons.Rounded.TimerOff
                },
                description = "Timer",
                rotation = rotation,
                tint = if (timerSeconds > 0) BrinaColors.Accent else Color.White,
                onClick = onTimer,
            )
            Row(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = exposureEnabled, onClick = onExposure)
                    .semantics { contentDescription = "Exposure" }
                    .padding(start = 4.dp, end = 12.dp)
                    .rotate(rotation),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "EV",
                    color = BrinaColors.TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Top).padding(top = 7.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    formatEv(exposureValue),
                    color = if (exposureValue != 0f) BrinaColors.Accent else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        RoundButton("Focus lock", onFocusLock) {
            Icon(
                Icons.Rounded.CenterFocusWeak,
                contentDescription = null,
                tint = if (focusLocked) BrinaColors.Accent else Color.White,
                modifier = Modifier.size(22.dp).rotate(rotation),
            )
        }
        Spacer(Modifier.width(10.dp))
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
        RoundButton("More controls", onMore, color = moreBackground) {
            SixDots(Modifier.size(width = 16.dp, height = 11.dp), color = moreDots)
        }
    }
}

@Composable
private fun PillIcon(
    icon: ImageVector,
    description: String,
    rotation: Float,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp).rotate(rotation))
    }
}

/** OnePlus "more" glyph: two rows of three dots. */
@Composable
fun SixDots(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val stepX = size.width / 3f
        val stepY = size.height / 2f
        val radius = minOf(stepX, stepY) * 0.36f
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
            .height(116.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(46.dp)
                    .rotate(rotation)
                    .clip(RoundedCornerShape(9.dp))
                    .background(BrinaColors.Surface)
                    .clickable(onClick = onGallery)
                    .semantics { contentDescription = "Gallery" },
            ) {
                if (thumbnail != null) {
                    Image(
                        thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(46.dp),
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
                size = 46,
            ) {
                Icon(
                    Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp).rotate(rotation + spin),
                )
            }
        }
    }
}

/** Orange disc in a thin bronze ring with a soft glow, like the OnePlus shutter. */
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
    val top by animateColorAsState(if (isVideo) Color(0xFFF2555A) else Color(0xFFFF9B2F), label = "top")
    val bottom by animateColorAsState(if (isVideo) BrinaColors.Recording else Color(0xFFF7741A), label = "bottom")

    Box(
        Modifier
            .size(100.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = "Shutter" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(100.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                Brush.radialGradient(
                    0.55f to bottom.copy(alpha = 0.28f),
                    1f to Color.Transparent,
                    center = c,
                    radius = size.minDimension / 2f,
                ),
            )
            drawCircle(Color.Black, radius = 38.dp.toPx(), center = c)
            drawCircle(
                Brush.verticalGradient(listOf(Color(0xFF5A3A22), Color(0xFF2E2016))),
                radius = 37.dp.toPx(),
                center = c,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
        if (isRecording) {
            Box(
                Modifier
                    .size(28.dp)
                    .scale(press)
                    .clip(RoundedCornerShape(7.dp))
                    .background(BrinaColors.Recording),
            )
        } else {
            Box(
                Modifier
                    .size(62.dp)
                    .scale(press)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(top, bottom))),
            )
        }
    }
}

// ---- Mode strip ---------------------------------------------------------------------------

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
            .height(60.dp)
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
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.14f to Color.Black,
                        0.86f to Color.Black,
                        1f to Color.Black.copy(alpha = 0.25f),
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
                        .padding(horizontal = 6.dp)
                        .height(46.dp)
                        .then(
                            if (isSelected) {
                                Modifier
                                    .clip(RoundedCornerShape(23.dp))
                                    .background(Color(0xFF141414))
                                    .border(BorderStroke(1.dp, Color(0xFF2A2A2A)), RoundedCornerShape(23.dp))
                            } else {
                                Modifier.clip(RoundedCornerShape(23.dp))
                            }
                        )
                        .clickable(enabled = enabled) {
                            if (isSelected && mode == CaptureMode.PHOTO) onPhotoOptions() else onSelect(mode)
                        }
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        mode.label,
                        color = textColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isSelected && mode == CaptureMode.PHOTO) {
                        Spacer(Modifier.width(7.dp))
                        Canvas(Modifier.size(width = 9.dp, height = 6.dp)) {
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

