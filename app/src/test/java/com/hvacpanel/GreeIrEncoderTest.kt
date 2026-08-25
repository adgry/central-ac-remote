package com.hvacpanel

import com.hvacpanel.model.AcState
import com.hvacpanel.model.FanSpeed
import com.hvacpanel.model.Mode
import com.hvacpanel.transport.GreeIrEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The infrared tuning screen is only trustworthy if encode and decode are
 * genuinely inverses — otherwise it would report a mismatch against the app's
 * own frames and send someone chasing a bug that is not there.
 */
class GreeIrEncoderTest {

    private val samples = listOf(
        AcState(power = true, mode = Mode.COOL, targetTemp = 26, fan = FanSpeed.MEDIUM),
        AcState(power = false, mode = Mode.HEAT, targetTemp = 30, fan = FanSpeed.HIGH),
        AcState(power = true, mode = Mode.AUTO, targetTemp = 16, fan = FanSpeed.AUTO),
        AcState(power = true, mode = Mode.DRY, targetTemp = 22, fan = FanSpeed.LOW, sleep = true),
        AcState(power = true, mode = Mode.FAN, targetTemp = 24, swingVertical = true, turbo = true),
        AcState(power = true, mode = Mode.COOL, targetTemp = 19, health = true, dryCoil = true),
    )

    @Test
    fun `a waveform decodes back to the frame it came from`() {
        for (state in samples) {
            val frame = GreeIrEncoder.frame(state)
            val decoded = GreeIrEncoder.decode(GreeIrEncoder.waveform(state))
            assertEquals("round trip failed for $state", frame, decoded)
        }
    }

    @Test
    fun `every frame we build passes our own checksum`() {
        for (state in samples) {
            assertTrue(
                "checksum failed for $state",
                GreeIrEncoder.checksumMatches(GreeIrEncoder.frame(state)),
            )
        }
    }

    @Test
    fun `the fields the screen reports survive a round trip`() {
        for (state in samples) {
            val read = GreeIrEncoder.interpret(GreeIrEncoder.frame(state))
            assertEquals("power for $state", state.power, read.power)
            assertEquals("mode for $state", state.mode, read.mode)
            assertEquals("fan for $state", state.fan, read.fan)
            assertEquals("setpoint for $state", state.targetTemp, read.targetTemp)
            assertEquals("swing for $state", state.swingVertical, read.swingVertical)
            assertEquals("sleep for $state", state.sleep, read.sleep)
            assertEquals("turbo for $state", state.turbo, read.turbo)
            assertEquals("health for $state", state.health, read.health)
            assertEquals("dry coil for $state", state.dryCoil, read.dryCoil)
        }
    }

    @Test
    fun `a waveform is the length the tuning screen expects`() {
        assertEquals(
            GreeIrEncoder.EXPECTED_PULSES,
            GreeIrEncoder.waveform(samples.first()).size,
        )
    }

    @Test
    fun `noise is rejected rather than decoded into a plausible frame`() {
        assertEquals(null, GreeIrEncoder.decode(intArrayOf(9000, 4500, 620)))
        assertEquals(null, GreeIrEncoder.decode(intArrayOf()))
    }
}
