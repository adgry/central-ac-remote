package com.hvacpanel.ui.lcd

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.hvacpanel.ui.theme.Ink

/**
 * The glass. A backlit sage field inside a brushed bezel, with the backlight
 * ramping up rather than snapping on, because that is what the real one does
 * when a unit starts.
 *
 * [backlit] false is a stopped unit: the field goes flat and grey and only the
 * etched ghosts remain readable.
 */
@Composable
fun LcdField(
    backlit: Boolean,
    modifier: Modifier = Modifier,
    corner: Int = 4,
    content: @Composable BoxScope.(backlight: Float) -> Unit,
) {
    val level by animateFloatAsState(
        targetValue = if (backlit) 1f else 0.34f,
        animationSpec = tween(260),
        label = "lcdLevel",
    )
    val field by animateColorAsState(
        targetValue = if (backlit) Ink.Lcd else Ink.LcdOff,
        animationSpec = tween(300),
        label = "lcdField",
    )
    Box(
        modifier = modifier
            // Bezel: a dark seat with one lit hairline along the top edge.
            .drawBehind {
                val r = (corner + 2).dp.toPx()
                drawRoundRect(
                    color = Ink.Bezel,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
                drawLine(
                    color = Ink.BezelLit.copy(alpha = 0.55f),
                    start = Offset(r, 0.6f),
                    end = Offset(size.width - r, 0.6f),
                    strokeWidth = 1.2f,
                )
            }
            .padding(1.5.dp)
            .clip(RoundedCornerShape(corner.dp))
            // The field itself: STN glass is never evenly lit.
            .drawBehind {
                drawRect(field)
                drawRect(
                    Brush.verticalGradient(
                        0f to Ink.LcdInk.copy(alpha = 0.05f),
                        0.42f to Ink.LcdInk.copy(alpha = 0.0f),
                        1f to Ink.LcdInk.copy(alpha = 0.09f),
                    ),
                )
            },
    ) {
        content(level)
    }
}
