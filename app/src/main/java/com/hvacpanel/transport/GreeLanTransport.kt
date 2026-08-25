package com.hvacpanel.transport

import com.hvacpanel.model.AcState
import com.hvacpanel.model.AcUnit
import com.hvacpanel.model.FanSpeed
import com.hvacpanel.model.Link
import com.hvacpanel.model.Mode
import com.hvacpanel.model.TEMP_MAX
import com.hvacpanel.model.TEMP_MIN
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Gree's local protocol. Everything is a UDP datagram on port 7000 carrying a
 * JSON envelope whose `pack` field is AES-encrypted JSON.
 *
 *  scan    → broadcast {"t":"scan"}; every unit answers with its name and mac
 *  bind    → generic key in, per-device key out; done once per unit
 *  status  → ask for a list of columns, get a matching list of values
 *  cmd     → send opt/p pairs, unit echoes what it accepted
 *
 * The layout below is the V1 (AES-ECB) dialect, which is what indoor units and
 * gateways in the field speak. Newer firmware also offers a GCM variant; if a
 * unit answers scan but refuses to bind, that is the likely reason.
 */
class GreeLanTransport : Transport {

    override val canRead = true

    /** A LAN unit cannot lock its own wall panel over this protocol. */
    val caps = Caps(childLock = false)

    private companion object {
        const val PORT = 7000
        const val REPLY_TIMEOUT_MS = 2_200

        val COLUMNS = listOf(
            "Pow", "Mod", "SetTem", "TemRec", "WdSpd", "SwUpDn", "SwingLfRig",
            "SwhSlp", "Quiet", "Tur", "SvSt", "Blo", "Health", "Lig", "TemSen",
        )
    }

    // ---------------------------------------------------------------- discovery

    override suspend fun discover(timeoutMs: Long): List<Discovered> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Discovered>()
        DatagramSocket().use { sock ->
            sock.broadcast = true
            sock.soTimeout = 350
            val probe = """{"t":"scan"}""".toByteArray(Charsets.UTF_8)
            for (target in broadcastTargets()) {
                runCatching { sock.send(DatagramPacket(probe, probe.size, target, PORT)) }
            }
            val buf = ByteArray(4096)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    sock.receive(pkt)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                runCatching {
                    val envelope = JSONObject(String(pkt.data, 0, pkt.length, Charsets.UTF_8))
                    if (envelope.optString("t") != "pack") return@runCatching
                    val dev = JSONObject(GreeCipher.decrypt(envelope.getString("pack")))
                    if (dev.optString("t") != "dev") return@runCatching
                    val mac = dev.optString("mac").ifBlank { envelope.optString("cid") }
                    val host = pkt.address?.hostAddress ?: return@runCatching
                    if (mac.isBlank()) return@runCatching
                    found[mac] = Discovered(
                        name = dev.optString("name").ifBlank { "室内机 ${mac.takeLast(4)}" },
                        host = host,
                        mac = mac,
                        model = dev.optString("model").ifBlank { dev.optString("series") },
                    )
                }
            }
        }
        found.values.toList()
    }

    /** Every subnet broadcast address this phone can see, plus the global one. */
    private fun broadcastTargets(): List<InetAddress> {
        val out = ArrayList<InetAddress>()
        runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.interfaceAddresses) {
                    addr.broadcast?.let(out::add)
                }
            }
        }
        runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        return out.distinct()
    }

    // ------------------------------------------------------------------- bind

    /**
     * Exchange the generic key for this unit's own key. Call once; store the
     * result on the [Link.GreeLan] or every later message will be rejected.
     */
    suspend fun bind(host: String, mac: String): String = withContext(Dispatchers.IO) {
        val request = JSONObject().apply {
            put("cid", "app")
            put("i", 1)
            put("t", "pack")
            put("uid", 0)
            put("tcid", mac)
            put(
                "pack",
                GreeCipher.encrypt(
                    JSONObject().apply {
                        put("mac", mac)
                        put("t", "bind")
                        put("uid", 0)
                    }.toString(),
                ),
            )
        }
        val reply = exchange(host, request, GreeCipher.GENERIC_KEY)
        val key = reply.optString("key")
        if (reply.optString("t") != "bindok" || key.isBlank()) {
            throw TransportException("绑定被拒绝：${reply.optString("t").ifBlank { "无响应" }}")
        }
        key
    }

    // ------------------------------------------------------------------- read

    override suspend fun read(unit: AcUnit): AcState = withContext(Dispatchers.IO) {
        val link = unit.link as? Link.GreeLan ?: throw TransportException("链路不是格力局域网")
        val key = link.key ?: throw TransportException("这台室内机还没有绑定")
        val request = envelope(
            link.mac,
            JSONObject().apply {
                put("cols", JSONArray(COLUMNS))
                put("mac", link.mac)
                put("t", "status")
            },
            key,
        )
        val reply = exchange(link.host, request, key)
        if (reply.optString("t") != "dat") throw TransportException("读取失败：${reply.optString("t")}")
        val cols = reply.getJSONArray("cols")
        val dat = reply.getJSONArray("dat")
        val values = HashMap<String, Int>(cols.length())
        for (i in 0 until minOf(cols.length(), dat.length())) {
            values[cols.getString(i)] = dat.optInt(i, 0)
        }
        decode(values, unit.state)
    }

    private fun decode(v: Map<String, Int>, previous: AcState): AcState {
        val sensed = v["TemSen"]
        return AcState(
            power = (v["Pow"] ?: 0) == 1,
            mode = Mode.fromGree(v["Mod"] ?: previous.mode.gree),
            targetTemp = (v["SetTem"] ?: previous.targetTemp).coerceIn(TEMP_MIN, TEMP_MAX),
            halfDegree = (v["TemRec"] ?: 0) == 1,
            fan = FanSpeed.fromGree(v["WdSpd"] ?: previous.fan.gree),
            swingVertical = (v["SwUpDn"] ?: 0) != 0,
            swingHorizontal = (v["SwingLfRig"] ?: 0) != 0,
            sleep = (v["SwhSlp"] ?: 0) == 1,
            eco = (v["SvSt"] ?: 0) == 1,
            quiet = (v["Quiet"] ?: 0) != 0,
            turbo = (v["Tur"] ?: 0) == 1,
            dryCoil = (v["Blo"] ?: 0) == 1,
            health = (v["Health"] ?: 0) == 1,
            panelLight = (v["Lig"] ?: 1) == 1,
            childLock = previous.childLock,
            // Firmware reports the sensor either raw or offset by 40.
            roomTemp = sensed?.let { if (it > 40) it - 40 else it }?.takeIf { it in 0..60 },
            offAtEpochMs = previous.offAtEpochMs,
        )
    }

    // ------------------------------------------------------------------ write

    override suspend fun write(unit: AcUnit, desired: AcState): AcState = withContext(Dispatchers.IO) {
        val link = unit.link as? Link.GreeLan ?: throw TransportException("链路不是格力局域网")
        val key = link.key ?: throw TransportException("这台室内机还没有绑定")
        val opt = JSONArray()
        val p = JSONArray()
        fun set(name: String, value: Int) { opt.put(name); p.put(value) }

        set("Pow", if (desired.power) 1 else 0)
        set("Mod", desired.mode.gree)
        set("SetTem", desired.targetTemp)
        set("TemRec", if (desired.halfDegree) 1 else 0)
        set("TemUn", 0) // Celsius
        set("WdSpd", desired.fan.gree)
        set("SwUpDn", if (desired.swingVertical) 1 else 0)
        set("SwingLfRig", if (desired.swingHorizontal) 1 else 0)
        set("SwhSlp", if (desired.sleep) 1 else 0)
        set("SlpMod", if (desired.sleep) 1 else 0)
        set("SvSt", if (desired.eco) 1 else 0)
        set("Quiet", if (desired.quiet) 1 else 0)
        set("Tur", if (desired.turbo) 1 else 0)
        set("Blo", if (desired.dryCoil) 1 else 0)
        set("Health", if (desired.health) 1 else 0)
        set("Lig", if (desired.panelLight) 1 else 0)

        val request = envelope(
            link.mac,
            JSONObject().apply {
                put("opt", opt)
                put("p", p)
                put("t", "cmd")
            },
            key,
        )
        val reply = exchange(link.host, request, key)
        if (reply.optString("t") != "res") throw TransportException("下发失败：${reply.optString("t")}")
        desired
    }

    // ---------------------------------------------------------------- plumbing

    private fun envelope(mac: String, pack: JSONObject, key: String) = JSONObject().apply {
        put("cid", "app")
        put("i", 0)
        put("t", "pack")
        put("uid", 0)
        put("tcid", mac)
        put("pack", GreeCipher.encrypt(pack.toString(), key))
    }

    /** Sends one datagram and decrypts the first well-formed reply. */
    private fun exchange(host: String, request: JSONObject, replyKey: String): JSONObject {
        val payload = request.toString().toByteArray(Charsets.UTF_8)
        DatagramSocket().use { sock ->
            sock.soTimeout = REPLY_TIMEOUT_MS
            val address = try {
                InetAddress.getByName(host)
            } catch (e: Exception) {
                throw TransportException("解析不了地址 $host", e)
            }
            try {
                sock.send(DatagramPacket(payload, payload.size, address, PORT))
            } catch (e: Exception) {
                throw TransportException("发不出去：${e.message}", e)
            }
            val buf = ByteArray(4096)
            val pkt = DatagramPacket(buf, buf.size)
            try {
                sock.receive(pkt)
            } catch (_: SocketTimeoutException) {
                throw TransportException("$host 没有回应")
            }
            val envelope = try {
                JSONObject(String(pkt.data, 0, pkt.length, Charsets.UTF_8))
            } catch (e: Exception) {
                throw TransportException("回应看不懂", e)
            }
            val pack = envelope.optString("pack")
            if (pack.isBlank()) throw TransportException("回应里没有数据")
            return try {
                JSONObject(GreeCipher.decrypt(pack, replyKey))
            } catch (e: Exception) {
                throw TransportException("解不开回应，密钥可能已失效", e)
            }
        }
    }
}
