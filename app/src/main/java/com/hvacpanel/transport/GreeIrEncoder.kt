package com.hvacpanel.transport

import com.hvacpanel.model.AcState
import com.hvacpanel.model.FanSpeed
import com.hvacpanel.model.Mode

/**
 * Builds the 38 kHz waveform a Gree handset sends: 64 bits, least significant
 * bit first, split into a 35-bit block and a 32-bit block with a fixed "010"
 * separator between them.
 *
 * Timings are the well-established Gree ones and are safe. The **byte layout
 * below is best-effort**: Gree has shipped several handset variants (YAC / YAA /
 * YAP …) and a wired panel's receiver may expect a different one. Treat
 * [Frame] as the one place to correct things — every field is named and
 * isolated, so fixing a bit position is a one-line change. If your panel
 * ignores these frames, capture your own handset with the bridge sketch's
 * `/capture` endpoint and replay the raw timings instead.
 */
object GreeIrEncoder {

    const val CARRIER_HZ = 38_000

    private const val HDR_MARK = 9000
    private const val HDR_SPACE = 4500
    private const val BIT_MARK = 620
    private const val ONE_SPACE = 1600
    private const val ZERO_SPACE = 540
    private const val BLOCK_GAP = 19_000

    /** The eight payload bytes, before they become pulses. */
    data class Frame(val bytes: IntArray) {
        val hex: String get() = bytes.joinToString(" ") { "%02X".format(it) }
        override fun equals(other: Any?) = other is Frame && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
        override fun toString() = hex
    }

    /** How many pulses a complete frame should be. */
    const val EXPECTED_PULSES = 137

    private fun fanBits(fan: FanSpeed): Int = when (fan) {
        FanSpeed.AUTO -> 0
        FanSpeed.LOW -> 1
        FanSpeed.MEDIUM -> 2
        FanSpeed.HIGH -> 3
    }

    private fun modeBits(mode: Mode): Int = when (mode) {
        Mode.AUTO -> 0
        Mode.COOL -> 1
        Mode.DRY -> 2
        Mode.FAN -> 3
        Mode.HEAT -> 4
    }

    fun frame(state: AcState): Frame {
        val b = IntArray(8)

        // byte 0 — mode, power, fan, vertical swing, sleep
        b[0] = (modeBits(state.mode) and 0x07) or
            (if (state.power) 0x08 else 0) or
            ((fanBits(state.fan) and 0x03) shl 4) or
            (if (state.swingVertical) 0x40 else 0) or
            (if (state.sleep) 0x80 else 0)

        // byte 1 — setpoint as an offset from 16 °C, timer nibble left at zero
        b[1] = (state.targetTemp - 16).coerceIn(0, 14) and 0x0F

        // byte 2 — turbo, wall-panel backlight, ioniser, coil dry
        b[2] = (if (state.turbo) 0x10 else 0) or
            (if (state.panelLight) 0x20 else 0) or
            (if (state.health) 0x40 else 0) or
            (if (state.dryCoil) 0x80 else 0)

        // byte 3 — fixed model marker in the high nibble
        b[3] = 0x50

        // byte 4 — vertical louvre position: 0 fixed, 1 full sweep
        b[4] = if (state.swingVertical) 0x01 else 0x00

        // bytes 5, 6 — unused by this variant
        // byte 7 — checksum in the high nibble
        b[7] = (checksum(b) and 0x0F) shl 4
        return Frame(b)
    }

    private fun checksum(b: IntArray): Int =
        (
            10 + (b[0] and 0x0F) + (b[1] and 0x0F) + (b[2] and 0x0F) + (b[3] and 0x0F) +
                ((b[4] and 0xF0) shr 4) + ((b[5] and 0xF0) shr 4) + ((b[6] and 0xF0) shr 4)
            ) and 0x0F

    /** Alternating mark/space durations in microseconds, ready to transmit. */
    fun waveform(state: AcState): IntArray {
        val bytes = frame(state).bytes
        val out = ArrayList<Int>(140)
        out.add(HDR_MARK); out.add(HDR_SPACE)

        // First block: 32 bits, then 3 more from the separator pattern "010".
        for (i in 0 until 4) appendByte(out, bytes[i])
        for (bit in intArrayOf(0, 1, 0)) {
            out.add(BIT_MARK)
            out.add(if (bit == 1) ONE_SPACE else ZERO_SPACE)
        }
        out[out.size - 1] = BLOCK_GAP

        // Second block: the remaining 32 bits, then a closing mark.
        for (i in 4 until 8) appendByte(out, bytes[i])
        out.add(BIT_MARK)
        return out.toIntArray()
    }

    private fun appendByte(out: MutableList<Int>, value: Int) {
        for (bit in 0 until 8) {
            out.add(BIT_MARK)
            out.add(if ((value shr bit) and 1 == 1) ONE_SPACE else ZERO_SPACE)
        }
    }

    // ------------------------------------------------------------------ reading
    //
    // The same layout, run backwards. This is what makes the guesswork above
    // testable: capture a frame from the handset that came with the panel, decode
    // it here, and the tuning screen can say which bytes disagree with what the
    // app would have sent for the same settings.

    /** Threshold between a zero space (540 µs) and a one space (1600 µs). */
    private const val ONE_THRESHOLD = 1000

    /**
     * Reads 8 bytes out of a captured pulse train. Returns null if the train is
     * too short to be one of these frames — which is itself the answer: the
     * handset speaks some other protocol.
     */
    fun decode(raw: IntArray): Frame? {
        var i = 0
        // A capture usually starts with the 9 ms header; skip it if present.
        if (raw.size >= 2 && raw[0] > 6000) i = 2
        val bytes = IntArray(8)
        for (b in 0 until 4) {
            for (bit in 0 until 8) {
                if (i + 1 >= raw.size) return null
                if (raw[i + 1] > ONE_THRESHOLD) bytes[b] = bytes[b] or (1 shl bit)
                i += 2
            }
        }
        i += 6 // the three separator bits carry no payload
        for (b in 4 until 8) {
            for (bit in 0 until 8) {
                if (i + 1 >= raw.size) return null
                if (raw[i + 1] > ONE_THRESHOLD) bytes[b] = bytes[b] or (1 shl bit)
                i += 2
            }
        }
        return Frame(bytes)
    }

    /**
     * True when the frame's checksum nibble agrees with our checksum. Strong
     * evidence that both the bit order and the checksum rule are right, even if
     * individual field positions are not.
     */
    fun checksumMatches(frame: Frame): Boolean =
        ((frame.bytes[7] and 0xF0) shr 4) == checksum(frame.bytes)

    /** What the app believes a frame means. Compare with what the handset showed. */
    fun interpret(frame: Frame): AcState {
        val b = frame.bytes
        return AcState(
            power = b[0] and 0x08 != 0,
            mode = when (b[0] and 0x07) {
                1 -> Mode.COOL
                2 -> Mode.DRY
                3 -> Mode.FAN
                4 -> Mode.HEAT
                else -> Mode.AUTO
            },
            targetTemp = 16 + (b[1] and 0x0F),
            fan = when ((b[0] shr 4) and 0x03) {
                1 -> FanSpeed.LOW
                2 -> FanSpeed.MEDIUM
                3 -> FanSpeed.HIGH
                else -> FanSpeed.AUTO
            },
            swingVertical = b[0] and 0x40 != 0,
            sleep = b[0] and 0x80 != 0,
            turbo = b[2] and 0x10 != 0,
            panelLight = b[2] and 0x20 != 0,
            health = b[2] and 0x40 != 0,
            dryCoil = b[2] and 0x80 != 0,
        )
    }

    /** Byte positions where two frames disagree, for the tuning screen. */
    fun differingBytes(a: Frame, b: Frame): List<Int> =
        (0 until 8).filter { a.bytes[it] != b.bytes[it] }
}
