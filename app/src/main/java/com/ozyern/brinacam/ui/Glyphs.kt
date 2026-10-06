package com.ozyern.brinacam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.ozyern.brinacam.camera.FlashSetting
import kotlin.math.cos
import kotlin.math.sin

/*
 * Thin-line icons in the OnePlus camera style. Each is drawn on a 24x24 grid
 * with 1.5-unit strokes and scaled to the modifier's size.
 */

private const val GRID = 24f
private const val LINE = 1.5f

private fun DrawScope.onGrid(block: DrawScope.() -> Unit) {
    scale(size.width / GRID, size.height / GRID, pivot = Offset.Zero) { block() }
}

private fun DrawScope.line(color: Color, a: Offset, b: Offset, width: Float = LINE) =
    drawLine(color, a, b, strokeWidth = width, cap = StrokeCap.Round)

private val stroke = Stroke(width = LINE, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun boltPath() = Path().apply {
    moveTo(13.2f, 5.2f)
    lineTo(8.2f, 12.8f)
    lineTo(11.6f, 12.8f)
    lineTo(10.8f, 18.8f)
    lineTo(15.8f, 11.2f)
    lineTo(12.4f, 11.2f)
    close()
}

@Composable
fun FlashGlyph(setting: FlashSetting, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val color = if (setting == FlashSetting.ON) BrinaColors.Accent else Color.White
    Canvas(modifier) {
        onGrid {
            drawCircle(color, 9.6f, Offset(12f, 12f), style = stroke)
            drawPath(boltPath(), color, style = if (setting == FlashSetting.ON) Fill else stroke)
            when (setting) {
                FlashSetting.OFF -> {
                    line(Color.Black, Offset(5.6f, 5.6f), Offset(18.4f, 18.4f), 3.2f)
                    line(color, Offset(5.6f, 5.6f), Offset(18.4f, 18.4f))
                }
                FlashSetting.AUTO -> {
                    val text = measurer.measure(
                        "A",
                        TextStyle(fontFamily = BrinaFont, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, color = color),
                    )
                    val scaleBack = GRID / size.width
                    scale(scaleBack, scaleBack, pivot = Offset(15.6f, 15.2f)) {
                        drawText(text, topLeft = Offset(15.6f, 15.2f))
                    }
                }
                FlashSetting.ON -> Unit
            }
        }
    }
}

/** Stopwatch; slashed when off, showing the delay when on. */
@Composable
fun TimerGlyph(seconds: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val color = if (seconds > 0) BrinaColors.Accent else Color.White
    Canvas(modifier) {
        onGrid {
            drawCircle(color, 7.8f, Offset(12f, 13.4f), style = stroke)
            line(color, Offset(9.8f, 3.6f), Offset(14.2f, 3.6f))
            line(color, Offset(12f, 3.6f), Offset(12f, 5.6f))
            line(color, Offset(18.2f, 6.6f), Offset(19.2f, 7.6f))
            if (seconds > 0) {
                val text = measurer.measure(
                    seconds.toString(),
                    TextStyle(fontFamily = BrinaFont, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = color),
                )
                val scaleBack = GRID / size.width
                val w = text.size.width * scaleBack
                val h = text.size.height * scaleBack
                scale(scaleBack, scaleBack, pivot = Offset(12f - w / 2f, 13.4f - h / 2f)) {
                    drawText(text, topLeft = Offset(12f - w / 2f, 13.4f - h / 2f))
                }
            } else {
                line(color, Offset(12f, 13.4f), Offset(12f, 9.6f))
                line(Color.Black, Offset(4.6f, 5.4f), Offset(19.4f, 20.2f), 3.2f)
                line(color, Offset(4.6f, 5.4f), Offset(19.4f, 20.2f))
            }
        }
    }
}

/** Rounded focus frame with a centre dot. */
@Composable
fun FocusFrameGlyph(locked: Boolean, modifier: Modifier = Modifier) {
    val color = if (locked) BrinaColors.Accent else Color.White
    Canvas(modifier) {
        onGrid {
            val corners = listOf(
                Triple(Offset(4f, 9f), Offset(4f, 4f), Offset(9f, 4f)),
                Triple(Offset(15f, 4f), Offset(20f, 4f), Offset(20f, 9f)),
                Triple(Offset(20f, 15f), Offset(20f, 20f), Offset(15f, 20f)),
                Triple(Offset(9f, 20f), Offset(4f, 20f), Offset(4f, 15f)),
            )
            corners.forEach { (start, corner, end) ->
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    val inX = corner.x + (start.x - corner.x) * 0.45f
                    val inY = corner.y + (start.y - corner.y) * 0.45f
                    lineTo(inX, inY)
                    quadraticTo(corner.x, corner.y, corner.x + (end.x - corner.x) * 0.45f, corner.y + (end.y - corner.y) * 0.45f)
                    lineTo(end.x, end.y)
                }
                drawPath(path, color, style = stroke)
            }
            drawCircle(color, 2.4f, Offset(12f, 12f))
        }
    }
}

/** Two chasing arrows, the OnePlus switch-camera icon. */
@Composable
fun SyncGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        onGrid {
            val r = 7.4f
            val c = Offset(12f, 12f)
            listOf(200f, 20f).forEach { start ->
                drawArc(
                    color, start, 125f, false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = stroke,
                )
                val end = Math.toRadians((start + 125f).toDouble())
                val tip = Offset(c.x + r * cos(end).toFloat(), c.y + r * sin(end).toFloat())
                // Arrowhead pointing along the direction of travel.
                val dir = end + Math.PI / 2
                val back = Offset(tip.x - 3f * cos(dir).toFloat(), tip.y - 3f * sin(dir).toFloat())
                val side = Offset(-sin(dir).toFloat(), cos(dir).toFloat())
                line(color, tip, Offset(back.x + side.x * 2.4f, back.y + side.y * 2.4f))
                line(color, tip, Offset(back.x - side.x * 2.4f, back.y - side.y * 2.4f))
            }
        }
    }
}

/** Portrait rectangle for the aspect-ratio toggle. */
@Composable
fun AspectGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            drawRect(Color.White, Offset(7.5f, 4.5f), Size(9f, 15f), style = stroke)
        }
    }
}

/** 3x3 grid in a rounded square. */
@Composable
fun GridGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            drawRoundRect(
                Color.White, Offset(4f, 4f), Size(16f, 16f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f), style = stroke,
            )
            for (i in 1..2) {
                val p = 4f + 16f * i / 3f
                line(Color.White, Offset(p, 4f), Offset(p, 20f))
                line(Color.White, Offset(4f, p), Offset(20f, p))
            }
        }
    }
}

/** Mirror: two halves either side of a dashed axis. */
@Composable
fun MirrorGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val left = Path().apply { moveTo(10f, 6f); lineTo(4f, 18f); lineTo(10f, 18f); close() }
            val right = Path().apply { moveTo(14f, 6f); lineTo(20f, 18f); lineTo(14f, 18f); close() }
            drawPath(left, Color.White, style = stroke)
            drawPath(right, Color.White, style = stroke)
            var y = 3.5f
            while (y < 21f) {
                line(Color.White, Offset(12f, y), Offset(12f, y + 1.6f))
                y += 3.4f
            }
        }
    }
}

/** Speaker, with waves when on and a cross when off. */
@Composable
fun SoundGlyph(on: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val body = Path().apply {
                moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 5.5f)
                lineTo(12f, 18.5f); lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close()
            }
            drawPath(body, Color.White, style = stroke)
            if (on) {
                drawArc(Color.White, -45f, 90f, false, Offset(10f, 8f), Size(8f, 8f), style = stroke)
                drawArc(Color.White, -50f, 100f, false, Offset(9f, 5f), Size(13f, 14f), style = stroke)
            } else {
                line(Color.White, Offset(15f, 9.5f), Offset(20f, 14.5f))
                line(Color.White, Offset(20f, 9.5f), Offset(15f, 14.5f))
            }
        }
    }
}

/** Six-tooth gear. */
@Composable
fun GearGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        onGrid {
            val c = Offset(12f, 12f)
            val path = Path()
            val teeth = 6
            for (i in 0 until teeth * 4) {
                val angle = Math.toRadians((i * 360.0 / (teeth * 4)) - 90.0 + 7.5)
                val r = if ((i / 2) % 2 == 0) 9f else 7f
                val p = Offset(c.x + r * cos(angle).toFloat(), c.y + r * sin(angle).toFloat())
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, Color.White, style = stroke)
            drawCircle(Color.White, 3f, c, style = stroke)
        }
    }
}
