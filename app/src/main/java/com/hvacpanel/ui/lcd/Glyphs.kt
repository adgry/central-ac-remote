package com.hvacpanel.ui.lcd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hvacpanel.model.FanSpeed
import com.hvacpanel.model.Mode
import com.hvacpanel.ui.theme.GHOST_ALPHA
import com.hvacpanel.ui.theme.Ink
import com.hvacpanel.ui.theme.PanelType
import kotlin.math.cos
import kotlin.math.sin

/*
 * Pictograms that live on the glass. Each one is drawn twice over the app's
 * life: solid when its function is on, at ghost density when it is off but
 * still etched on the panel. Nothing here is an icon font.
 */

private fun DrawScope.snowflake(c: Offset, r: Float, w: Float, color: Color) {
    for (i in 0 until 3) {
        val a = Math.toRadians((i * 60).toDouble())
        val dx = (cos(a) * r).toFloat()
        val dy = (sin(a) * r).toFloat()
        drawLine(color, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), w)
    }
    // Barbs, two per arm tip.
    for (i in 0 until 6) {
        val a = Math.toRadians((i * 60).toDouble())
        val tip = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
        val inner = Offset(c.x + (cos(a) * r * 0.52f).toFloat(), c.y + (sin(a) * r * 0.52f).toFloat())
        for (side in intArrayOf(-1, 1)) {
            val ba = a + side * Math.toRadians(52.0)
            val end = Offset(
                inner.x + (cos(ba) * r * 0.34f).toFloat(),
                inner.y + (sin(ba) * r * 0.34f).toFloat(),
            )
            drawLine(color, inner, end, w * 0.85f)
        }
        // keep the tip crisp
        drawLine(color, inner, tip, w)
    }
}

private fun DrawScope.sun(c: Offset, r: Float, w: Float, color: Color) {
    drawCircle(color, r * 0.44f, c, style = Stroke(width = w))
    for (i in 0 until 8) {
        val a = Math.toRadians((i * 45).toDouble())
        drawLine(
            color,
            Offset(c.x + (cos(a) * r * 0.66f).toFloat(), c.y + (sin(a) * r * 0.66f).toFloat()),
            Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()),
            w,
        )
    }
}

private fun DrawScope.droplet(c: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        cubicTo(c.x + r * 0.78f, c.y - r * 0.12f, c.x + r * 0.64f, c.y + r * 0.86f, c.x, c.y + r * 0.86f)
        cubicTo(c.x - r * 0.64f, c.y + r * 0.86f, c.x - r * 0.78f, c.y - r * 0.12f, c.x, c.y - r)
        close()
    }
    drawPath(path, color)
}

/** Three drifting lines: air moving, with no cooling or heating behind it. */
private fun DrawScope.airflow(c: Offset, r: Float, w: Float, color: Color) {
    val widths = floatArrayOf(1f, 0.76f, 0.92f)
    for (i in 0 until 3) {
        val y = c.y + (i - 1) * r * 0.62f
        val half = r * widths[i]
        val path = Path().apply {
            moveTo(c.x - half, y)
            quadraticTo(c.x - half * 0.2f, y - r * 0.34f, c.x + half * 0.24f, y)
            quadraticTo(c.x + half * 0.66f, y + r * 0.30f, c.x + half, y - r * 0.06f)
        }
        drawPath(path, color, style = Stroke(width = w))
    }
}

/** Mode pictogram, sized to [glyphSize], lit or merely etched. */
@Composable
fun ModeMark(
    mode: Mode,
    lit: Boolean,
    modifier: Modifier = Modifier,
    glyphSize: Dp = 22.dp,
    ink: Color = Ink.LcdInk,
    backlight: Float = 1f,
) {
    val alpha = (if (lit) 1f else GHOST_ALPHA) * backlight
    Canvas(modifier = modifier.size(glyphSize)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.46f
        val w = size.minDimension * 0.085f
        val color = ink.copy(alpha = alpha)
        when (mode) {
            Mode.COOL -> snowflake(c, r, w, color)
            Mode.HEAT -> sun(c, r, w, color)
            Mode.DRY -> droplet(c, r * 0.92f, color)
            Mode.FAN -> airflow(c, r * 0.9f, w, color)
            // A stroked letter A: the segment form is illegible below ~40dp.
            Mode.AUTO -> {
                val top = Offset(c.x, c.y - r * 0.92f)
                val foot = r * 0.66f
                drawLine(color, top, Offset(c.x - foot, c.y + r * 0.86f), w, cap = StrokeCap.Round)
                drawLine(color, top, Offset(c.x + foot, c.y + r * 0.86f), w, cap = StrokeCap.Round)
                drawLine(
                    color,
                    Offset(c.x - foot * 0.52f, c.y + r * 0.18f),
                    Offset(c.x + foot * 0.52f, c.y + r * 0.18f),
                    w,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Fan speed as stacked bars, the way the wall panel shows it. Auto lights no
 * bars — the label next to it carries that.
 */
@Composable
fun FanBars(
    fan: FanSpeed,
    modifier: Modifier = Modifier,
    height: Dp = 18.dp,
    ink: Color = Ink.LcdInk,
    backlight: Float = 1f,
    on: Boolean = true,
) {
    Canvas(modifier = modifier.size(width = height * 1.15f, height = height)) {
        val bars = 3
        val gap = size.width * 0.16f
        val barW = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val h = size.height * (0.42f + 0.29f * i)
            val lit = on && fan.bars > i
            drawRect(
                color = ink.copy(alpha = (if (lit) 1f else GHOST_ALPHA) * backlight),
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
            )
        }
    }
}

/** Louvre sweep: a fixed blade above, air swinging up and down below it. */
@Composable
fun SwingMark(
    on: Boolean,
    modifier: Modifier = Modifier,
    glyphSize: Dp = 20.dp,
    ink: Color = Ink.LcdInk,
    backlight: Float = 1f,
) {
    val alpha = (if (on) 1f else GHOST_ALPHA) * backlight
    Canvas(modifier = modifier.size(glyphSize)) {
        val color = ink.copy(alpha = alpha)
        val w = size.minDimension * 0.11f
        val cx = size.width / 2f
        // The blade.
        drawLine(
            color,
            Offset(size.width * 0.14f, size.height * 0.20f),
            Offset(size.width * 0.86f, size.height * 0.20f),
            w,
            cap = StrokeCap.Round,
        )
        // The throw of the air: one shaft, a head at each end.
        val topY = size.height * 0.42f
        val botY = size.height * 0.92f
        drawLine(color, Offset(cx, topY), Offset(cx, botY), w, cap = StrokeCap.Round)
        val span = size.width * 0.19f
        val head = size.height * 0.14f
        drawLine(color, Offset(cx - span, topY + head), Offset(cx, topY), w, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + span, topY + head), Offset(cx, topY), w, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - span, botY - head), Offset(cx, botY), w, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + span, botY - head), Offset(cx, botY), w, cap = StrokeCap.Round)
    }
}

/** IEC power mark, for the key that starts and stops a unit. */
@Composable
fun PowerMark(
    modifier: Modifier = Modifier,
    glyphSize: Dp = 22.dp,
    color: Color = Ink.Silk,
) {
    Canvas(modifier = modifier.size(glyphSize)) {
        val w = size.minDimension * 0.115f
        val r = size.minDimension * 0.36f
        val c = Offset(size.width / 2f, size.height * 0.56f)
        drawArc(
            color = color,
            startAngle = -62f,
            sweepAngle = 304f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        drawLine(
            color,
            Offset(c.x, c.y - r * 1.42f),
            Offset(c.x, c.y - r * 0.18f),
            w,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

/**
 * A boxed word on the glass, like the 回气 / 节能 tell-tales along the bottom of
 * the real panel. Off means outlined in ghost ink, not absent.
 */
@Composable
fun LcdTellTale(
    text: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    ink: Color = Ink.LcdInk,
    backlight: Float = 1f,
) {
    val frameAlpha = (if (on) 0.92f else GHOST_ALPHA + 0.05f) * backlight
    val textAlpha = (if (on) 1f else GHOST_ALPHA + 0.14f) * backlight
    Box(
        modifier = modifier
            .drawBehind {
                val r = size.height * 0.18f
                drawRoundRect(
                    color = ink.copy(alpha = frameAlpha),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = size.height * 0.085f),
                )
                if (on) {
                    drawRoundRect(
                        color = ink.copy(alpha = 0.13f * backlight),
                        cornerRadius = CornerRadius(r, r),
                    )
                }
            }
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = PanelType.silkSmall.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
            color = ink.copy(alpha = textAlpha),
        )
    }
}
