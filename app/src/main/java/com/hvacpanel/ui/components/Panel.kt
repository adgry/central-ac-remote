package com.hvacpanel.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hvacpanel.ui.theme.Ink
import com.hvacpanel.ui.theme.PanelType

/**
 * A face mounted on the housing: flat fill, hairline seam along the top edge so
 * it reads as a separate piece of material rather than a floating card.
 */
@Composable
fun PanelSurface(
    modifier: Modifier = Modifier,
    sunk: Boolean = false,
    corner: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(if (sunk) Ink.PanelSunk else Ink.Panel)
            .drawBehind {
                drawLine(
                    color = Ink.BezelLit.copy(alpha = if (sunk) 0.16f else 0.30f),
                    start = Offset(0f, 0.6f),
                    end = Offset(size.width, 0.6f),
                    strokeWidth = 1.1f,
                )
            },
        content = content,
    )
}

/** Silkscreened label: condensed, widely tracked, quiet. */
@Composable
fun SilkLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.SilkDim,
    small: Boolean = false,
) = Text(
    text = text,
    style = if (small) PanelType.silkSmall else PanelType.silk,
    color = color,
    modifier = modifier,
)

/**
 * A key you press. Scales a hair, ticks the haptic motor, and comes back —
 * standing in for the click the wall panel gives you.
 */
@Composable
fun PanelKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    accent: Color = Ink.Silk,
    corner: Dp = 5.dp,
    contentPadding: Dp = 12.dp,
    circular: Boolean = false,
    /** Spoken name, for keys whose label is a symbol rather than a word. */
    label: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.972f else 1f,
        animationSpec = tween(70),
        label = "keyPress",
    )
    val fill = when {
        !enabled -> Ink.PanelSunk
        selected -> accent.copy(alpha = 0.16f)
        pressed -> Ink.Bezel.copy(alpha = 0.85f)
        else -> Ink.PanelSunk
    }
    val shape = if (circular) CircleShape else RoundedCornerShape(corner)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .then(if (label != null) Modifier.semantics { contentDescription = label; role = Role.Button } else Modifier)
            .scale(squeeze)
            .clip(shape)
            .background(fill)
            .then(
                if (selected) Modifier.drawBehind {
                    val r = if (circular) size.minDimension / 2f else corner.toPx()
                    if (circular) {
                        drawCircle(
                            color = accent.copy(alpha = 0.75f),
                            radius = r - 0.7f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f),
                        )
                    } else {
                        drawRoundRect(
                            color = accent.copy(alpha = 0.75f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f),
                        )
                    }
                } else if (circular) Modifier.drawBehind {
                    drawCircle(
                        color = Ink.BezelLit.copy(alpha = 0.22f),
                        radius = size.minDimension / 2f - 0.6f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.1f),
                    )
                } else Modifier.drawBehind {
                    drawLine(
                        color = Ink.BezelLit.copy(alpha = 0.20f),
                        start = Offset(0f, 0.6f),
                        end = Offset(size.width, 0.6f),
                        strokeWidth = 1f,
                    )
                },
            )
            .then(
                if (enabled) Modifier.clickableKey(interaction) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                } else Modifier,
            )
            .padding(horizontal = contentPadding, vertical = 11.dp),
        content = content,
    )
}

private fun Modifier.clickableKey(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
) = this.clickable(
    interactionSource = interaction,
    indication = null,
    onClick = onClick,
)

/** A hairline divider, the seam between two pieces of housing. */
@Composable
fun Seam(modifier: Modifier = Modifier) = Box(
    modifier = modifier
        .padding(vertical = 2.dp)
        .drawBehind {
            drawLine(
                color = Ink.Bezel,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1f,
            )
        },
)

@Composable
fun KeyGap(width: Dp = 7.dp) = Spacer(Modifier.width(width))

/** Small round tell-tale beside a unit's name: running, stopped, or unreachable. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 7.dp) = Box(
    modifier = modifier
        .size(size)
        .drawBehind {
            drawCircle(color)
            drawCircle(color.copy(alpha = 0.22f), radius = this.size.minDimension * 0.92f)
        },
)

@Composable
fun CenteredSilk(text: String, color: Color = Ink.SilkDim, modifier: Modifier = Modifier) = Text(
    text = text,
    style = PanelType.silkSmall,
    color = color,
    textAlign = TextAlign.Center,
    modifier = modifier,
)

/**
 * A key that keeps firing while held — how you get from 26 °C to 20 °C without
 * six taps. First repeat waits, then it accelerates a little.
 */
@Composable
fun RepeatKey(
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    corner: Dp = 5.dp,
    contentPadding: Dp = 18.dp,
    circular: Boolean = false,
    verticalPadding: Dp = 16.dp,
    /** Spoken name, for keys whose label is a symbol rather than a word. */
    label: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(70),
        label = "repeatPress",
    )

    androidx.compose.runtime.LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        // The tap itself already fired; wait before taking over.
        kotlinx.coroutines.delay(420)
        var gap = 150L
        while (true) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onFire()
            kotlinx.coroutines.delay(gap)
            gap = (gap - 12L).coerceAtLeast(70L)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .then(if (label != null) Modifier.semantics { contentDescription = label; role = Role.Button } else Modifier)
            .scale(squeeze)
            .clip(if (circular) CircleShape else RoundedCornerShape(corner))
            .background(if (!enabled) Ink.PanelSunk else if (pressed) Ink.Bezel else Ink.PanelSunk)
            .drawBehind {
                if (circular) {
                    drawCircle(
                        color = Ink.BezelLit.copy(alpha = if (enabled) 0.26f else 0.10f),
                        radius = size.minDimension / 2f - 0.6f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.1f),
                    )
                } else {
                    drawLine(
                        color = Ink.BezelLit.copy(alpha = 0.24f),
                        start = Offset(0f, 0.6f),
                        end = Offset(size.width, 0.6f),
                        strokeWidth = 1f,
                    )
                }
            }
            .then(
                if (enabled) Modifier.clickableKey(interaction) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFire()
                } else Modifier,
            )
            .padding(horizontal = contentPadding, vertical = verticalPadding),
        content = content,
    )
}

/** A single-line field styled like the rest of the housing. */
@Composable
fun PanelField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    Column(modifier) {
        SilkLabel(label, small = true, color = Ink.SilkDim)
        Spacer(Modifier.padding(top = 5.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Ink.PanelSunk)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = PanelType.body.copy(color = Ink.Silk),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink.Cool),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = PanelType.body, color = Ink.SilkFaint)
                    }
                    inner()
                },
            )
        }
    }
}
