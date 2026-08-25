package com.hvacpanel.transport

import com.hvacpanel.model.AcState
import com.hvacpanel.model.AcUnit
import com.hvacpanel.model.Mode
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

/**
 * A simulated indoor unit, so the whole app is usable before any hardware is
 * wired up. Room temperature drifts toward the setpoint at a believable rate
 * and creeps back toward ambient when the unit is off, which is enough to make
 * the panels behave like the real thing.
 */
class DemoTransport : Transport {

    override val canRead = true

    private data class Sim(var state: AcState, var roomTempTenths: Int, var lastTickMs: Long)

    private val sims = HashMap<String, Sim>()

    private fun sim(unit: AcUnit): Sim = sims.getOrPut(unit.id) {
        val seed = unit.state.roomTemp ?: 27
        Sim(unit.state, seed * 10, System.currentTimeMillis())
    }

    /** Ambient temperature the room falls back to with everything off. */
    private val ambientTenths = 293

    private fun tick(s: Sim) {
        val now = System.currentTimeMillis()
        val elapsed = (now - s.lastTickMs).coerceAtMost(60_000)
        if (elapsed < 400) return
        s.lastTickMs = now
        // A degree every ~40 s of app time while running; slower while idle.
        val stepTenths = (elapsed / 1600.0).toInt().coerceAtLeast(1)
        val target = if (s.state.power && s.state.mode != Mode.FAN) {
            (s.state.setpoint * 10).toInt()
        } else {
            ambientTenths
        }
        val gap = target - s.roomTempTenths
        if (gap != 0) {
            val move = minOf(abs(gap), if (s.state.power) stepTenths else (stepTenths + 1) / 2)
            s.roomTempTenths += move * gap.sign
        }
        s.state = s.state.copy(roomTemp = (s.roomTempTenths + 5) / 10)
    }

    override suspend fun read(unit: AcUnit): AcState {
        delay(90 + Random.nextLong(80))
        val s = sim(unit)
        tick(s)
        return s.state
    }

    override suspend fun write(unit: AcUnit, desired: AcState): AcState {
        delay(120 + Random.nextLong(110))
        val s = sim(unit)
        tick(s)
        s.state = desired.copy(roomTemp = s.state.roomTemp)
        return s.state
    }
}
