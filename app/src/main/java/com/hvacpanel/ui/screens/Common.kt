package com.hvacpanel.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hvacpanel.ui.components.PanelKey
import com.hvacpanel.ui.components.SilkLabel
import com.hvacpanel.ui.theme.Ink
import com.hvacpanel.ui.theme.PanelType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

/**
 * Every screen sits on the housing, under a thin header with the same seam the
 * panels have. No elevation, no shadow: this is one continuous piece of metal.
 */
@Composable
fun PanelScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    eyebrow: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Housing)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = Ink.Bezel,
                        start = Offset(0f, size.height - 0.5f),
                        end = Offset(size.width, size.height - 0.5f),
                        strokeWidth = 1f,
                    )
                }
                .padding(start = if (onBack == null) 20.dp else 8.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        ) {
            if (onBack != null) {
                PanelKey(
                    onClick = onBack,
                    contentPadding = 12.dp,
                ) { Text("返回", style = PanelType.silkSmall, color = Ink.Silk) }
                Spacer(Modifier.padding(4.dp))
            }
            Column(Modifier.weight(1f)) {
                if (eyebrow != null) {
                    SilkLabel(eyebrow, small = true, color = Ink.SilkDim)
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    text = title,
                    style = PanelType.name,
                    color = Ink.Silk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
        content()
    }
}

/**
 * The status line: one sentence about the last thing that happened, then it
 * clears itself. Errors say what to do about them, so they linger longer.
 */
@Composable
fun NoticeLine(notices: SharedFlow<String>, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notices) {
        notices.collect { text ->
            message = text
            delay(3_600)
            if (message == text) message = null
        }
    }
    AnimatedVisibility(visible = message != null, modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Ink.PanelSunk)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.orEmpty(),
                style = PanelType.body,
                color = Ink.SilkDim,
                maxLines = 2,
            )
        }
    }
}

/** Section heading, silkscreened on the housing between groups of panels. */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) = Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    SilkLabel(text, color = Ink.SilkDim)
    if (trailing != null) SilkLabel(trailing, small = true, color = Ink.SilkDim)
}
