package com.hvacpanel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * One committed look, like the appliance it stands in for: a car dashboard does
 * not have a light mode. The system theme is deliberately ignored so the LCD
 * material reads the same at any hour.
 */
@Composable
fun CentralAcTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme() // read and ignored, on purpose
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink.Housing,
            onBackground = Ink.Silk,
            surface = Ink.Panel,
            onSurface = Ink.Silk,
            surfaceVariant = Ink.PanelSunk,
            onSurfaceVariant = Ink.SilkDim,
            primary = Ink.Cool,
            onPrimary = Ink.Housing,
            secondary = Ink.Warm,
            error = Ink.Fault,
            outline = Ink.Bezel,
        ),
        typography = AppTypography,
        content = content,
    )
}
