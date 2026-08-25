package com.hvacpanel.transport

import com.hvacpanel.model.AcState
import com.hvacpanel.model.AcUnit

class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** One indoor unit found on the network. */
data class Discovered(
    val name: String,
    val host: String,
    val mac: String,
    val model: String,
)

/**
 * A way of talking to one indoor unit. Implementations are stateless with
 * respect to the unit; everything they need is on the [AcUnit].
 */
interface Transport {
    /** False for one-way links (infrared), where the unit cannot answer. */
    val canRead: Boolean

    /** Current state of the unit. Throws [TransportException] if unreachable. */
    suspend fun read(unit: AcUnit): AcState

    /**
     * Ask the unit to become [desired]. Returns the state the unit confirms,
     * which for a one-way link is just [desired] echoed back.
     */
    suspend fun write(unit: AcUnit, desired: AcState): AcState

    /** Look for units. Returns empty for transports that cannot search. */
    suspend fun discover(timeoutMs: Long = 3_000): List<Discovered> = emptyList()
}

/**
 * What a link can actually carry. The control screen hides functions the unit
 * has no way of receiving, rather than offering a switch that springs back.
 */
data class Caps(
    val roomTemp: Boolean = true,
    val halfDegree: Boolean = true,
    val swingHorizontal: Boolean = true,
    val sleep: Boolean = true,
    val eco: Boolean = true,
    val quiet: Boolean = true,
    val turbo: Boolean = true,
    val dryCoil: Boolean = true,
    val health: Boolean = true,
    val panelLight: Boolean = true,
    val childLock: Boolean = true,
)

/** Everything a transport can carry, unless it says otherwise. */
val FULL_CAPS = Caps()
