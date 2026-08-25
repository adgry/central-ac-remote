package com.hvacpanel.control

import android.content.Context
import com.hvacpanel.data.UnitStore
import com.hvacpanel.model.AcState
import com.hvacpanel.model.AcUnit
import com.hvacpanel.model.Link
import com.hvacpanel.model.Reachability
import com.hvacpanel.transport.Caps
import com.hvacpanel.transport.DemoTransport
import com.hvacpanel.transport.Discovered
import com.hvacpanel.transport.FULL_CAPS
import com.hvacpanel.transport.GreeLanTransport
import com.hvacpanel.transport.IrTransport
import com.hvacpanel.transport.Transport
import com.hvacpanel.transport.TransportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Holds the unit list and is the only thing that talks to transports.
 *
 * Every change is applied locally first so the panel reacts on the same frame
 * as the tap, then pushed after a short pause — holding ⊕ to run the setpoint
 * from 26 to 22 sends one command, not four. Units that can answer are polled,
 * and a poll never overwrites a change that has not been pushed yet.
 */
class AcController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val WRITE_DEBOUNCE_MS = 340L
        const val POLL_INTERVAL_MS = 8_000L
        const val SAVE_DEBOUNCE_MS = 700L
    }

    private val store = UnitStore(context)
    private val demo = DemoTransport()
    private val gree = GreeLanTransport()
    private val infrared by lazy { IrTransport(context) }

    private val _units = MutableStateFlow(store.load())
    val units = _units.asStateFlow()

    /** Units with a command in flight, for the "sending" tick on a panel. */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy = _busy.asStateFlow()

    /** One-line messages for the status line. */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices = _notices.asSharedFlow()

    /** Debounce timers, one per unit. Only the wait is ever cancelled. */
    private val debounce = HashMap<String, Job>()
    /** Units with a write actually on the wire. */
    private val inFlight = HashSet<String>()
    /** Units that changed again while their write was in flight. */
    private val restale = HashSet<String>()
    private var saveJob: Job? = null

    // ------------------------------------------------------------- transports

    fun transportFor(unit: AcUnit): Transport = when (unit.link) {
        Link.Demo -> demo
        is Link.GreeLan -> gree
        is Link.Infrared -> infrared
    }

    fun capsFor(unit: AcUnit): Caps = when (unit.link) {
        Link.Demo -> FULL_CAPS
        is Link.GreeLan -> gree.caps
        is Link.Infrared -> infrared.caps
    }

    fun phoneHasIrEmitter(): Boolean = infrared.phoneHasEmitter()

    // ----------------------------------------------------------------- edits

    /** Change one unit. Applied immediately, pushed shortly after. */
    fun update(id: String, transform: (AcState) -> AcState) {
        var changed = false
        _units.update { list ->
            list.map { u ->
                if (u.id != id) u else {
                    val next = transform(u.state)
                    if (next == u.state) u else { changed = true; u.copy(state = next) }
                }
            }
        }
        if (changed) {
            persistSoon()
            schedulePush(id)
        }
    }

    /** Change several units at once — one debounced push each. */
    fun updateMany(ids: Collection<String>, transform: (AcState) -> AcState) {
        ids.forEach { update(it, transform) }
    }

    fun setPowerAll(on: Boolean) {
        val ids = _units.value.map { it.id }
        updateMany(ids) { it.copy(power = on) }
        _notices.tryEmit(if (on) "已开启 ${ids.size} 台" else "已关闭 ${ids.size} 台")
    }

    fun rename(id: String, name: String, room: String) {
        _units.update { list ->
            list.map { if (it.id == id) it.copy(name = name.trim(), room = room.trim()) else it }
        }
        persistSoon()
    }

    fun remove(id: String) {
        debounce.remove(id)?.cancel()
        restale -= id
        _units.update { list -> list.filterNot { it.id == id } }
        persistSoon()
    }

    fun addDemoUnit(name: String, room: String) {
        add(
            AcUnit(
                id = "demo-${UUID.randomUUID().toString().take(6)}",
                name = name.ifBlank { "室内机" },
                room = room,
                link = Link.Demo,
                state = AcState(roomTemp = 27),
                reach = Reachability.ONLINE,
            ),
        )
    }

    fun addIrUnit(name: String, room: String, bridgeUrl: String?) {
        add(
            AcUnit(
                id = "ir-${UUID.randomUUID().toString().take(6)}",
                name = name.ifBlank { "室内机" },
                room = room,
                link = Link.Infrared(
                    IrTransport.normalizeUrl(bridgeUrl.orEmpty()).ifBlank { null },
                ),
                reach = Reachability.UNKNOWN,
            ),
        )
    }

    private fun add(unit: AcUnit) {
        if (_units.value.any { it.id == unit.id }) {
            _notices.tryEmit("${unit.name} 已经在列表里了")
            return
        }
        _units.update { it + unit }
        persistSoon()
    }

    // ------------------------------------------------------------- discovery

    suspend fun scanLan(): List<Discovered> = try {
        gree.discover(3_000)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _notices.tryEmit("搜索失败：${noticeOf(e)}")
        emptyList()
    }

    /** Known macs, so the add screen can grey out units already in the list. */
    fun knownMacs(): Set<String> =
        _units.value.mapNotNull { (it.link as? Link.GreeLan)?.mac }.toSet()

    /**
     * Bind a discovered unit and add it. Binding is what turns the shared
     * factory key into a key only this phone holds.
     */
    suspend fun bindAndAdd(found: Discovered, room: String): Boolean = try {
        val key = gree.bind(found.host, found.mac)
        add(
            AcUnit(
                id = "gree-${found.mac}",
                name = found.name,
                room = room,
                link = Link.GreeLan(found.host, found.mac, key),
                reach = Reachability.ONLINE,
                lastSeenEpochMs = System.currentTimeMillis(),
            ),
        )
        refresh("gree-${found.mac}")
        _notices.tryEmit("已添加 ${found.name}")
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _notices.tryEmit("绑定 ${found.name} 失败：${noticeOf(e)}")
        false
    }

    // ------------------------------------------------------------- infrared

    /** Change which bridge a unit sends through. */
    fun setBridgeUrl(id: String, url: String) {
        val clean = IrTransport.normalizeUrl(url)
        patch(id) { u ->
            val link = u.link as? Link.Infrared ?: return@patch u
            u.copy(link = link.copy(bridgeUrl = clean.ifBlank { null }))
        }
    }

    /** Sweep the local network for infrared bridges. */
    suspend fun findBridges(): Result<List<String>> = runCatching {
        infrared.discoverBridges()
    }

    private fun bridgeOf(unit: AcUnit): String =
        (unit.link as? Link.Infrared)?.bridgeUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw TransportException("先填红外桥地址")

    suspend fun probeBridge(unit: AcUnit): Result<String> = runCatching {
        infrared.probeBridge(bridgeOf(unit))
    }

    /** Send the unit's current settings once, to see whether the panel reacts. */
    suspend fun testSendIr(unit: AcUnit): Result<Unit> = runCatching {
        infrared.write(unit, unit.state)
        Unit
    }

    /** Record one frame from the handset that came with the panel. */
    suspend fun captureIr(unit: AcUnit): Result<IntArray> = runCatching {
        infrared.capture(bridgeOf(unit))
    }

    /** Replay a recorded frame verbatim. */
    suspend fun replayIr(unit: AcUnit, raw: IntArray): Result<Unit> = runCatching {
        infrared.sendRaw(bridgeOf(unit), raw, repeat = 2)
        Unit
    }

    // ----------------------------------------------------------------- pushes

    private fun schedulePush(id: String) {
        debounce.remove(id)?.cancel()
        debounce[id] = scope.launch {
            delay(WRITE_DEBOUNCE_MS)
            debounce.remove(id)
            drain(id)
        }
    }

    /**
     * Pushes until the unit is up to date. A write already on the wire is never
     * cancelled — a half-sent UDP datagram or infrared frame cannot be recalled,
     * so instead the unit is marked stale and pushed again once the write lands.
     */
    private suspend fun drain(id: String) {
        if (id in inFlight) {
            restale += id
            return
        }
        inFlight += id
        try {
            do {
                restale -= id
                push(id)
            } while (id in restale)
        } finally {
            inFlight -= id
        }
    }

    private suspend fun push(id: String) {
        val unit = _units.value.firstOrNull { it.id == id } ?: return
        _busy.update { it + id }
        try {
            val confirmed = transportFor(unit).write(unit, unit.state)
            patch(id) { u ->
                u.copy(
                    state = u.state.copy(roomTemp = confirmed.roomTemp ?: u.state.roomTemp),
                    reach = Reachability.ONLINE,
                    lastError = null,
                    lastSeenEpochMs = System.currentTimeMillis(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            patch(id) { it.copy(reach = Reachability.OFFLINE, lastError = detailOf(e)) }
            _notices.tryEmit("${unit.name}：${noticeOf(e)}")
        } finally {
            _busy.update { it - id }
        }
    }

    /** What the status line says. Transports phrase their own failures. */
    private fun noticeOf(e: Exception): String =
        (e as? TransportException)?.message ?: "连不上，检查一下网络"

    /** What the unit's footer shows — the technical detail, kept out of the notice. */
    private fun detailOf(e: Exception): String =
        (e as? TransportException)?.message ?: "${e::class.simpleName}: ${e.message}"

    /** Read one unit now. */
    fun refresh(id: String) {
        scope.launch { readOne(id) }
    }

    fun refreshAll() {
        scope.launch {
            _units.value.filter { transportFor(it).canRead }.forEach { readOne(it.id) }
        }
    }

    private suspend fun readOne(id: String) {
        val unit = _units.value.firstOrNull { it.id == id } ?: return
        val transport = transportFor(unit)
        if (!transport.canRead) return
        // A local change still queued or in flight outranks whatever the unit says.
        if (id in debounce || id in inFlight) return
        try {
            val fresh = transport.read(unit)
            if (id in debounce || id in inFlight) return
            patch(id) { u ->
                u.copy(
                    state = fresh.copy(offAtEpochMs = u.state.offAtEpochMs),
                    reach = Reachability.ONLINE,
                    lastError = null,
                    lastSeenEpochMs = System.currentTimeMillis(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            patch(id) { it.copy(reach = Reachability.OFFLINE, lastError = detailOf(e)) }
        }
    }

    /** Background refresh while a screen is showing. */
    fun startPolling(): Job = scope.launch {
        while (true) {
            _units.value.filter { transportFor(it).canRead }.forEach { readOne(it.id) }
            delay(POLL_INTERVAL_MS)
        }
    }

    // ----------------------------------------------------------------- timers

    fun setOffTimer(id: String, minutes: Int?) {
        val unit = _units.value.firstOrNull { it.id == id } ?: return
        val deadline = minutes?.let { System.currentTimeMillis() + it * 60_000L }
        patch(id) { it.copy(state = it.state.copy(offAtEpochMs = deadline)) }
        if (minutes == null) {
            OffTimer.cancel(context, id)
            _notices.tryEmit("${unit.name} 定时已取消")
        } else {
            OffTimer.schedule(context, id, minutes)
            _notices.tryEmit("${unit.name} 将在 ${OffTimer.humanise(minutes)}后关机")
        }
        persistSoon()
    }

    /** Post a line to the status line from outside the controller. */
    fun notify(message: String) {
        _notices.tryEmit(message)
    }

    // --------------------------------------------------------------- plumbing

    private fun patch(id: String, transform: (AcUnit) -> AcUnit) {
        _units.update { list -> list.map { if (it.id == id) transform(it) else it } }
        persistSoon()
    }

    private fun persistSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            store.save(_units.value)
        }
    }

    fun persistNow() = store.save(_units.value)
}
