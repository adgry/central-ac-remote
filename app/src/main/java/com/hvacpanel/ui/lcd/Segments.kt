package com.hvacpanel.ui.lcd

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hvacpanel.ui.theme.GHOST_ALPHA
import com.hvacpanel.ui.theme.Ink

/*
 * Seven-segment geometry, drawn rather than typeset. A font would give us the
 * lit segments only; the thing that makes a real LCD look like an LCD is the
 * segments that are *not* lit, faintly visible through the polariser. So every
 * digit draws all seven segments in ghost ink first, then the live ones on top.
 *
 *        aaaa
 *       f    b
 *       f    b
 *        gggg
 *       e    c
 *       e    c
 *        dddd
 */

private const val A = 1 shl 0
private const val B = 1 shl 1
private const val C = 1 shl 2
private const val D = 1 shl 3
private const val E = 1 shl 4
private const val F = 1 shl 5
private const val G = 1 shl 6

private val DIGIT_MASKS = intArrayOf(
    A or B or C or D or E or F,          // 0
    B or C,                              // 1
    A or B or G or E or D,               // 2
    A or B or G or C or D,               // 3
    F or G or B or C,                    // 4
    A or F or G or C or D,               // 5
    A or F or G or E or C or D,          // 6
    A or B or C,                         // 7
    A or B or C or D or E or F or G,     // 8
    A or B or C or D or F or G,          // 9
)

/** The letter forms an LCD can make, for °C and short words like AUTO. */
private val LETTER_MASKS = mapOf(
    'C' to (A or F or E or D),
    'A' to (A or B or C or E or F or G),
    'F' to (A or F or E or G),
    'H' to (F or E or G or B or C),
    'E' to (A or F or G or E or D),
    'L' to (F or E or D),
    'o' to (G or C or D or E),
    'P' to (A or B or F or G or E),
    'r' to (E or G),
    '-' to G,
    ' ' to 0,
)

/** One tapered segment bar, horizontal. */
private fun hBar(x0: Float, x1: Float, cy: Float, k: Float): Path = Path().apply {
    moveTo(x0, cy)
    lineTo(x0 + k, cy - k)
    lineTo(x1 - k, cy - k)
    lineTo(x1, cy)
    lineTo(x1 - k, cy + k)
    lineTo(x0 + k, cy + k)
    close()
}

/** One tapered segment bar, vertical. */
private fun vBar(cx: Float, y0: Float, y1: Float, k: Float): Path = Path().apply {
    moveTo(cx, y0)
    lineTo(cx + k, y0 + k)
    lineTo(cx + k, y1 - k)
    lineTo(cx, y1)
    lineTo(cx - k, y1 - k)
    lineTo(cx - k, y0 + k)
    close()
}

/**
 * Draws one seven-segment cell.
 *
 * @param mask which segments are lit, or 0 for a blank cell
 * @param litAlpha 0..1, so a whole display can fade up like a backlight
 */
fun DrawScope.segmentCell(
    mask: Int,
    origin: Offset,
    box: Size,
    thickness: Float,
    ink: Color,
    litAlpha: Float = 1f,
    ghostAlpha: Float = GHOST_ALPHA,
) {
    val k = thickness / 2f
    val gap = thickness * 0.30f
    val x0 = origin.x + k
    val x1 = origin.x + box.width - k
    val yTop = origin.y + k
    val yMid = origin.y + box.height / 2f
    val yBot = origin.y + box.height - k

    val paths = listOf(
        A to hBar(x0 + gap, x1 - gap, yTop, k),
        G to hBar(x0 + gap, x1 - gap, yMid, k),
        D to hBar(x0 + gap, x1 - gap, yBot, k),
        F to vBar(x0, yTop + gap, yMid - gap, k),
        B to vBar(x1, yTop + gap, yMid - gap, k),
        E to vBar(x0, yMid + gap, yBot - gap, k),
        C to vBar(x1, yMid + gap, yBot - gap, k),
    )
    // Unlit first, so the lit ones sit on top of a complete glass.
    for ((bit, path) in paths) {
        if (mask and bit == 0) drawPath(path, ink.copy(alpha = ghostAlpha * litAlpha))
    }
    for ((bit, path) in paths) {
        if (mask and bit != 0) drawPath(path, ink.copy(alpha = litAlpha))
    }
}

fun DrawScope.segmentDigit(
    digit: Int?,
    origin: Offset,
    box: Size,
    thickness: Float,
    ink: Color,
    litAlpha: Float = 1f,
    ghostAlpha: Float = GHOST_ALPHA,
) = segmentCell(
    mask = digit?.let { DIGIT_MASKS[it.coerceIn(0, 9)] } ?: 0,
    origin = origin,
    box = box,
    thickness = thickness,
    ink = ink,
    litAlpha = litAlpha,
    ghostAlpha = ghostAlpha,
)

fun DrawScope.segmentChar(
    ch: Char,
    origin: Offset,
    box: Size,
    thickness: Float,
    ink: Color,
    litAlpha: Float = 1f,
) = segmentCell(
    mask = LETTER_MASKS[ch] ?: 0,
    origin = origin,
    box = box,
    thickness = thickness,
    ink = ink,
    litAlpha = litAlpha,
)

/** The dot between whole and half degrees. */
private fun DrawScope.segmentDot(center: Offset, radius: Float, ink: Color, alpha: Float) {
    drawCircle(ink.copy(alpha = alpha), radius, center)
}

/**
 * A setpoint on the glass: two digits, an optional half-degree, and °C.
 *
 * @param value the setpoint, or null to blank the digits (送风 has no setpoint)
 * @param cellHeight height of one digit; everything else scales from it
 */
@Composable
fun LcdSetpoint(
    value: Double?,
    modifier: Modifier = Modifier,
    cellHeight: Dp = 92.dp,
    ink: Color = Ink.LcdInk,
    backlight: Float = 1f,
    showUnit: Boolean = true,
    ghostAlpha: Float = GHOST_ALPHA,
) {
    // Segments cross-fade rather than slide: an LCD has no moving parts.
    val fade by animateFloatAsState(
        targetValue = backlight,
        animationSpec = tween(220),
        label = "backlight",
    )
    val digitW = cellHeight * 0.56f
    val unitW = if (showUnit) cellHeight * 0.40f else 0.dp
    val half = value?.let { it - it.toInt() >= 0.5 } == true
    val unitLit = showUnit && value != null
    val halfW = if (half) cellHeight * 0.34f else 0.dp
    val totalW = digitW * 2 + halfW + unitW + cellHeight * 0.16f

    Canvas(modifier = modifier.size(width = totalW, height = cellHeight)) {
        val h = size.height
        val cell = Size(digitW.toPx(), h)
        val thickness = h * 0.135f
        val whole = value?.toInt()
        val tens = whole?.let { if (it >= 10) it / 10 else null }
        val ones = whole?.rem(10)

        var x = 0f
        segmentDigit(tens, Offset(x, 0f), cell, thickness, ink, fade, ghostAlpha)
        x += cell.width + h * 0.06f
        segmentDigit(ones, Offset(x, 0f), cell, thickness, ink, fade, ghostAlpha)
        x += cell.width

        if (half) {
            val dotR = thickness * 0.42f
            segmentDot(Offset(x + dotR * 1.6f, h - dotR), dotR, ink, fade)
            x += h * 0.08f
            val halfCell = Size(halfW.toPx() - h * 0.04f, h * 0.62f)
            segmentDigit(5, Offset(x, h - halfCell.height), halfCell, thickness * 0.7f, ink, fade, ghostAlpha)
            x += halfCell.width
        }

        if (unitLit) {
            // Ring plus a stroked C. Segment letterforms need roughly 40dp of
            // height to stay legible; the unit mark gets a third of that, so it
            // is drawn as strokes instead.
            val cx = x + h * 0.20f
            val ringR = h * 0.052f
            drawCircle(
                color = ink.copy(alpha = fade),
                radius = ringR,
                center = Offset(cx, h * 0.20f),
                style = Stroke(width = thickness * 0.30f),
            )
            val cR = h * 0.132f
            drawArc(
                color = ink.copy(alpha = fade),
                startAngle = 42f,
                sweepAngle = 276f,
                useCenter = false,
                topLeft = Offset(cx - cR, h * 0.47f - cR),
                size = Size(cR * 2, cR * 2),
                style = Stroke(width = thickness * 0.34f, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * Small two-digit readout for the measured room temperature. No ghost layer:
 * at this size the unlit segments swallow the lit ones.
 */
@Composable
fun LcdSmallNumber(
    value: Int?,
    modifier: Modifier = Modifier,
    cellHeight: Dp = 26.dp,
    ink: Color = Ink.LcdInk,
    backlight: Float = 1f,
) {
    val digitW = cellHeight * 0.58f
    Canvas(modifier = modifier.size(width = digitW * 2 + cellHeight * 0.10f, height = cellHeight)) {
        val cell = Size(digitW.toPx(), size.height)
        val thickness = size.height * 0.135f
        val tens = value?.let { if (it >= 10) it / 10 else null }
        val ones = value?.rem(10)
        segmentDigit(tens, Offset(0f, 0f), cell, thickness, ink, backlight, ghostAlpha = 0f)
        segmentDigit(
            ones,
            Offset(cell.width + size.height * 0.10f, 0f),
            cell,
            thickness,
            ink,
            backlight,
            ghostAlpha = 0f,
        )
    }
}
