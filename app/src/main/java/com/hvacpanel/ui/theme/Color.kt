package com.hvacpanel.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette is taken from the hardware this app replaces: a wall-mounted wired
 * controller. Warm graphite housing, brushed bezel, sage-green STN backlight,
 * near-black segment ink, silkscreened labels. Cool/warm are the only two
 * chromatic accents and they only ever mean "cooling" and "heating".
 */
object Ink {
    /** Instrument rack behind everything. Warm, never pure black. */
    val Housing = Color(0xFF14120F)
    /** A panel face sitting on the rack. */
    val Panel = Color(0xFF1E1B17)
    /** A panel face, pressed / recessed. */
    val PanelSunk = Color(0xFF171410)

    /** Brushed metal bezel, and its top highlight hairline. */
    val Bezel = Color(0xFF413C34)
    val BezelLit = Color(0xFF6E6960)

    /** The hero material: backlit sage LCD field. */
    val Lcd = Color(0xFFA7B394)
    /** Same field with the backlight off (unit is powered down). */
    val LcdOff = Color(0xFF4E5347)
    /** Segment ink. Slight green cast, like real polarised film. */
    val LcdInk = Color(0xFF16211A)

    /** Silkscreened label ink on the housing. */
    val Silk = Color(0xFFC9C3B6)
    /** Secondary text. Holds 4.7:1 on [Panel], so body copy may use it. */
    val SilkDim = Color(0xFF8A8478)
    /** Off, disabled, not-fitted. Deliberately below body-text contrast. */
    val SilkFaint = Color(0xFF6E685F)

    /** Cooling. Heating. Nothing else uses these. */
    val Cool = Color(0xFF57A8C7)
    val Warm = Color(0xFFE0872C)
    /** A unit that stopped answering. */
    val Fault = Color(0xFFC4553F)
    /** Running indicator on the rack. */
    val Live = Color(0xFF7FA86B)
}

/** Ink density for a segment or icon that exists on the glass but is not lit. */
const val GHOST_ALPHA = 0.11f
