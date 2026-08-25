package com.hvacpanel.model

/** How the app reaches one indoor unit. */
sealed interface Link {
    /** A simulated unit. Fully functional, no hardware. */
    data object Demo : Link

    /**
     * Gree's LAN protocol: UDP to port 7000, AES-encrypted JSON.
     * [key] is the per-device key handed out at bind time; null means unbound.
     */
    data class GreeLan(val host: String, val mac: String, val key: String? = null) : Link

    /**
     * The app builds the 38 kHz waveform; something else radiates it —
     * either the phone's own blaster or a small Wi-Fi bridge at [bridgeUrl].
     */
    data class Infrared(val bridgeUrl: String? = null) : Link
}

enum class Reachability { UNKNOWN, ONLINE, OFFLINE }

/** One indoor unit as the app knows it. */
data class AcUnit(
    val id: String,
    val name: String,
    val room: String,
    val link: Link,
    val state: AcState = AcState(),
    val reach: Reachability = Reachability.UNKNOWN,
    /** Set when the last exchange failed, cleared on success. */
    val lastError: String? = null,
    val lastSeenEpochMs: Long = 0L,
) {
    val isOneWay: Boolean get() = link is Link.Infrared
}
