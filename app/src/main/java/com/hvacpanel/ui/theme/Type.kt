package com.hvacpanel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Two voices, both borrowed from appliance panels:
 *  - Silk: condensed, widely tracked, the way Chinese labels are silkscreened
 *    under physical keys (确认/取消, 风速, 模式).
 *  - Data: monospace, for addresses and timestamps.
 * Large numbers are not type at all; they are drawn as seven-segment geometry.
 */
private val condensedName = DeviceFontFamilyName("sans-serif-condensed")
private val Condensed = FontFamily(
    Font(condensedName, weight = FontWeight.Normal),
    Font(condensedName, weight = FontWeight.Medium),
    Font(condensedName, weight = FontWeight.Bold),
)

object PanelType {
    /** Section headers and key labels. Wide tracking is the whole point. */
    val silk = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 3.2.sp,
        lineHeight = 14.sp,
    )
    val silkSmall = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Medium,
        fontSize = 9.5.sp,
        letterSpacing = 2.4.sp,
        lineHeight = 12.sp,
    )
    /** Room names on a panel. */
    val name = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        letterSpacing = 1.6.sp,
        lineHeight = 22.sp,
    )
    val nameSmall = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 1.2.sp,
        lineHeight = 18.sp,
    )
    /** Key labels that contain digits; silkscreen tracking splits those. */
    val keyLabel = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
        lineHeight = 15.sp,
    )
    /** Ordinary reading text. */
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    /** Values printed on the LCD field next to the segments. */
    val lcdLabel = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
    )
    val data = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.5.sp,
        letterSpacing = 0.4.sp,
        lineHeight = 15.sp,
    )
}

internal val AppTypography = Typography(
    bodyMedium = PanelType.body,
    labelSmall = PanelType.silkSmall,
    titleMedium = PanelType.name,
)
