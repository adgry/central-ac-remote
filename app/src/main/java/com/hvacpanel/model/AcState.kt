package com.hvacpanel.model

/** Operating mode. Values match the Gree LAN protocol's `Mod` field. */
enum class Mode(val gree: Int, val label: String) {
    AUTO(0, "自动"),
    COOL(1, "制冷"),
    DRY(2, "除湿"),
    FAN(3, "送风"),
    HEAT(4, "制热");

    companion object {
        fun fromGree(v: Int): Mode = entries.firstOrNull { it.gree == v } ?: AUTO
    }
}

/**
 * Fan speed as the wall panel presents it: 自动 / 低 / 中 / 高.
 * `gree` is the value on the wire (0 = auto, 1..5 = low..high).
 */
enum class FanSpeed(val gree: Int, val label: String, val bars: Int) {
    AUTO(0, "自动", 0),
    LOW(1, "低", 1),
    MEDIUM(3, "中", 2),
    HIGH(5, "高", 3);

    companion object {
        fun fromGree(v: Int): FanSpeed = when (v) {
            0 -> AUTO
            1, 2 -> LOW
            3, 4 -> MEDIUM
            else -> HIGH
        }
    }
}

const val TEMP_MIN = 16
const val TEMP_MAX = 30

/**
 * Everything one indoor unit is currently doing. Immutable; the controller
 * replaces it wholesale so Compose can diff it.
 */
data class AcState(
    val power: Boolean = false,
    val mode: Mode = Mode.COOL,
    /** Setpoint in whole degrees, 16..30. */
    val targetTemp: Int = 26,
    /** Adds 0.5 °C to the setpoint. The panel shows it as a half-lit segment. */
    val halfDegree: Boolean = false,
    val fan: FanSpeed = FanSpeed.AUTO,
    /** Up/down louvre sweep. */
    val swingVertical: Boolean = false,
    /** Left/right louvre sweep. Not fitted on every indoor unit. */
    val swingHorizontal: Boolean = false,
    val sleep: Boolean = false,
    /** 节能: caps compressor output. */
    val eco: Boolean = false,
    val quiet: Boolean = false,
    /** 强劲 / turbo. */
    val turbo: Boolean = false,
    /** 干燥: keeps the fan on after stopping, to dry the coil. */
    val dryCoil: Boolean = false,
    /** 健康 / ioniser. */
    val health: Boolean = false,
    /** LCD backlight on the wall panel itself. */
    val panelLight: Boolean = true,
    /** 童锁: the wall panel refuses local input. */
    val childLock: Boolean = false,
    /** Measured room temperature, when the unit reports one. */
    val roomTemp: Int? = null,
    /** When the unit should stop itself, as epoch millis, or null. */
    val offAtEpochMs: Long? = null,
) {
    /** Minutes left on the 定时关机, or null. Counts down on its own. */
    fun offInMinutes(now: Long = System.currentTimeMillis()): Int? =
        offAtEpochMs?.let { (((it - now) + 59_999) / 60_000).toInt().coerceAtLeast(0) }

    /** Setpoint including the half degree, for display. */
    val setpoint: Double get() = targetTemp + if (halfDegree) 0.5 else 0.0

    fun withTempDelta(delta: Int): AcState =
        copy(targetTemp = (targetTemp + delta).coerceIn(TEMP_MIN, TEMP_MAX))

    /** Modes where a setpoint is meaningless; the panel blanks the digits. */
    val showsSetpoint: Boolean get() = mode != Mode.FAN

    val fanAdjustable: Boolean get() = mode != Mode.DRY
}
