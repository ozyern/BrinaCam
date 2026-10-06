package com.ozyern.brinacam.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
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

/** Glass when a backdrop is available, otherwise a flat fill. */
@Composable
fun Modifier.glassOrFill(shape: Shape, tint: Color): Modifier {
    val backdrop = LocalGlassBackdrop.current
    return if (backdrop != null) {
        this.glass(backdrop, shape, tint)
    } else {
        this.background(tint.copy(alpha = 1f), shape)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    rotation: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 48,
    tint: Color = Color.White,
    surface: Color = Color(0xCC262626),
) {
    Box(
        modifier
            .size(size.dp)
            .glassOrFill(CircleShape, surface)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp).rotate(rotation))
    }
}

// ---- Top bar ------------------------------------------------------------------------------

@Composable
fun TopBar(
    flash: FlashSetting,
    timerSeconds: Int,
    exposureValue: Float,
    exposureEnabled: Boolean,
    gridOn: Boolean,
    rotation: Float,
    onFlash: () -> Unit,
    onTimer: () -> Unit,
    onExposure: () -> Unit,
    onGrid: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(48.dp)
                .glassOrFill(RoundedCornerShape(24.dp), Color(0xCC262626))
                .padding(horizontal = 6.dp),
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
                    .padding(start = 6.dp, end = 12.dp)
                    .rotate(rotation),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "EV",
                    color = BrinaColors.TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Top).padding(top = 8.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    formatEv(exposureValue),
                    color = if (exposureValue != 0f) BrinaColors.Accent else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        GlassIconButton(
            icon = if (gridOn) Icons.Rounded.GridOn else Icons.Rounded.GridOff,
            description = "Grid",
            rotation = rotation,
            onClick = onGrid,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(48.dp)
                .glassOrFill(CircleShape, Color(0xCC262626))
                .clip(CircleShape)
                .clickable(onClick = onMore)
                .semantics { contentDescription = "More controls" },
            contentAlignment = Alignment.Center,
        ) {
            NineDots(Modifier.size(18.dp))
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
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp).rotate(rotation))
    }
}

@Composable
fun NineDots(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val step = size.width / 3f
        val radius = step * 0.28f
        for (row in 0..2) for (col in 0..2) {
            drawCircle(color, radius, Offset(step * (col + 0.5f), step * (row + 0.5f)))
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
            .height(112.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(52.dp)
                    .glassOrFill(RoundedCornerShape(14.dp), Color(0xCC262626))
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onGallery)
                    .semantics { contentDescription = "Gallery" }
                    .rotate(rotation),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(
                        thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(11.dp)),
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
            GlassIconButton(
                icon = Icons.Rounded.Cameraswitch,
                description = "Switch camera",
                rotation = rotation + spin,
                size = 52,
                onClick = {
                    turns += 1f
                    onSwitch()
                },
            )
        }
    }
}

@Composable
fun ShutterButton(mode: CaptureMode, isRecording: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.88f else 1f, label = "press")
    val isVideo = mode == CaptureMode.VIDEO
    val discColor = if (isVideo) BrinaColors.Recording else BrinaColors.Accent

    Box(
        Modifier
            .size(104.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = "Shutter" },
        contentAlignment = Alignment.Center,
    ) {
        // Soft glow behind the button.
        Canvas(Modifier.size(104.dp)) {
            drawCircle(
                Brush.radialGradient(
                    listOf(discColor.copy(alpha = 0.32f), Color.Transparent),
                    radius = size.minDimension / 2f,
                ),
            )
        }
        // Liquid-glass ring.
        Box(
            Modifier
                .size(82.dp)
                .glassOrFill(CircleShape, Color(0x55303030)),
        )
        if (isRecording) {
            Box(
                Modifier
                    .size(30.dp)
                    .scale(press)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrinaColors.Recording),
            )
        } else {
            Box(
                Modifier
                    .size(66.dp)
                    .scale(press)
                    .clip(CircleShape)
                    .background(
                        if (isVideo) {
                            Brush.verticalGradient(listOf(Color(0xFFF0656A), BrinaColors.Recording))
                        } else {
                            Brush.verticalGradient(listOf(BrinaColors.AccentLight, BrinaColors.AccentDark))
                        }
                    ),
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
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "modes",
    )
    val threshold = with(density) { 40.dp.toPx() }

    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
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
                        0f to Color.Transparent,
                        0.16f to Color.Black,
                        0.84f to Color.Black,
                        1f to Color.Transparent,
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
                Row(
                    Modifier
                        .onGloballyPositioned {
                            centers[mode] = it.positionInParent().x + it.size.width / 2f
                        }
                        .padding(horizontal = 4.dp)
                        .height(44.dp)
                        .then(
                            if (isSelected) {
                                Modifier.glassOrFill(RoundedCornerShape(22.dp), Color(0xE61C1C1C))
                            } else {
                                Modifier
                            }
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(enabled = enabled) {
                            if (isSelected && mode == CaptureMode.PHOTO) onPhotoOptions() else onSelect(mode)
                        }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        mode.label,
                        color = if (isSelected) BrinaColors.Accent else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp,
                    )
                    if (isSelected && mode == CaptureMode.PHOTO) {
                        Icon(
                            Icons.Rounded.ArrowDropUp,
                            contentDescription = null,
                            tint = BrinaColors.Accent,
                            modifier = Modifier.size(22.dp),
                        )
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
            .glassOrFill(RoundedCornerShape(16.dp), Color(0x80000000))
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
        val color = Color.White.copy(alpha = 0.35f)
        val stroke = 1.dp.toPx()
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
