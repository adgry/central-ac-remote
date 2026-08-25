package com.hvacpanel.transport

import android.content.Context
import android.hardware.ConsumerIrManager
import com.hvacpanel.model.AcState
import com.hvacpanel.model.AcUnit
import com.hvacpanel.model.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

/**
 * The wall panel in the photo has an infrared window, so it can be driven the
 * same way a handset drives it. The app only builds the waveform; radiating it
 * needs an emitter, in order of preference:
 *
 *  1. a small Wi-Fi bridge (ESP8266/ESP32 + IR LED) pointed at the panel —
 *     see `docs/ir-bridge.ino`; this is what actually works day to day,
 *  2. the phone's own blaster, on the few phones that still have one.
 *
 * Infrared is write-only: nothing comes back, so the app shows the state it
 * last sent rather than the state of the unit.
 */
class IrTransport(private val context: Context) : Transport {

    override val canRead = false

    val caps = Caps(
        roomTemp = false,
        halfDegree = false,
        swingHorizontal = false,
        eco = false,
        quiet = false,
        childLock = false,
    )

    override suspend fun read(unit: AcUnit): AcState =
        throw TransportException("红外是单向的，读不到室内机状态")

    override suspend fun write(unit: AcUnit, desired: AcState): AcState {
        val link = unit.link as? Link.Infrared ?: throw TransportException("链路不是红外")
        val pattern = GreeIrEncoder.waveform(desired)
        val bridge = link.bridgeUrl?.let(::normalizeUrl)
        if (!bridge.isNullOrEmpty()) {
            sendViaBridge(bridge, pattern, repeat = 2)
        } else {
            sendViaPhone(pattern)
        }
        return desired
    }

    // ----------------------------------------------------------- addresses

    companion object {
        /** What the bridge sketch calls itself in its status JSON. */
        const val BRIDGE_ID = "hvacpanel-ir-bridge"

        /**
         * Accepts what people actually type. A bare address gets http://, and a
         * pasted endpoint path is trimmed back to the base — otherwise the
         * connection test would GET /send and be told 405 by our own bridge.
         */
        fun normalizeUrl(input: String): String {
            var url = input.trim()
            if (url.isEmpty()) return ""
            if (!url.contains("://")) url = "http://$url"
            url = url.trimEnd('/')
            for (suffix in listOf("/send", "/capture")) {
                if (url.endsWith(suffix)) url = url.dropLast(suffix.length).trimEnd('/')
            }
            return url
        }

        /**
         * Whether a status body came from our bridge. Pulled out of the network
         * sweep so the part that can be got wrong — deciding that some random
         * device on port 80 is the bridge — is testable without hardware.
         */
        fun isBridgeStatus(body: String): Boolean =
            runCatching { JSONObject(body).optString("device") == BRIDGE_ID }.getOrDefault(false)
    }

    /**
     * Sweeps every /24 this phone is on, looking for the bridge. Saves the owner
     * from digging the address out of a router admin page: the bridge names
     * itself in its status JSON, so a hit is unambiguous.
     */
    suspend fun discoverBridges(perHostTimeoutMs: Int = 500): List<String> = coroutineScope {
        val candidates = localSubnetHosts()
        if (candidates.isEmpty()) return@coroutineScope emptyList()
        @OptIn(ExperimentalCoroutinesApi::class)
        val net = Dispatchers.IO.limitedParallelism(40)
        candidates.map { host ->
            async(net) { if (looksLikeBridge(host, perHostTimeoutMs)) "http://$host" else null }
        }.awaitAll().filterNotNull()
    }

    /** Every address on the same /24 as this phone, minus its own. */
    private fun localSubnetHosts(): List<String> {
        val hosts = ArrayList<String>(254)
        runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.interfaceAddresses) {
                    val ip = addr.address as? Inet4Address ?: continue
                    if (addr.networkPrefixLength < 24) continue // too big to sweep
                    val parts = ip.hostAddress?.split('.') ?: continue
                    if (parts.size != 4) continue
                    val prefix = parts.take(3).joinToString(".")
                    val own = parts[3].toIntOrNull() ?: continue
                    for (last in 1..254) if (last != own) hosts.add("$prefix.$last")
                }
            }
        }
        return hosts.distinct()
    }

    private fun looksLikeBridge(host: String, timeoutMs: Int): Boolean {
        val conn = runCatching {
            URL("http://$host/").openConnection() as HttpURLConnection
        }.getOrNull() ?: return false
        return try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            if (conn.responseCode !in 200..299) return false
            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            isBridgeStatus(body)
        } catch (_: Exception) {
            false
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    // -------------------------------------------------------------- diagnostics

    /** Sends an arbitrary pulse train, for replaying something we captured. */
    suspend fun sendRaw(bridgeUrl: String, pattern: IntArray, repeat: Int = 1) =
        sendViaBridge(bridgeUrl, pattern, repeat)

    /** Is the bridge there, and what does it say about itself? */
    suspend fun probeBridge(bridgeUrl: String): String = withContext(Dispatchers.IO) {
        val url = normalizeUrl(bridgeUrl)
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw TransportException("地址不对：$bridgeUrl", e)
        }
        try {
            conn.connectTimeout = 2_500
            conn.readTimeout = 3_000
            val code = conn.responseCode
            if (code !in 200..299) throw TransportException("红外桥返回 $code")
            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw TransportException("回应看不懂，确认这是红外桥的地址")
            val ip = json.optString("ip").ifBlank { "?" }
            val rssi = json.optInt("rssi", 0)
            val sent = json.optInt("frames_sent", 0)
            "在线 · $ip · 信号 $rssi dBm · 已发 $sent 帧"
        } catch (e: TransportException) {
            throw e
        } catch (e: Exception) {
            throw TransportException("连不上红外桥，确认它通电了、和手机在同一个 Wi-Fi、地址没写错", e)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Waits for the bridge to see one frame from a handset and returns its pulse
     * train. This is the way out when the built-in code table is wrong: whatever
     * the panel actually answers to can be captured and replayed verbatim.
     */
    suspend fun capture(bridgeUrl: String, timeoutMs: Int = 10_000): IntArray =
        withContext(Dispatchers.IO) {
            val url = normalizeUrl(bridgeUrl) + "/capture?timeout=" + timeoutMs
            val conn = try {
                URL(url).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                throw TransportException("地址不对：$bridgeUrl", e)
            }
            try {
                conn.connectTimeout = 2_500
                conn.readTimeout = timeoutMs + 4_000
                val code = conn.responseCode
                if (code == 408) throw TransportException("没收到红外信号，对准接收头再按一次")
                if (code !in 200..299) throw TransportException("红外桥返回 $code")
                val json = JSONObject(
                    conn.inputStream.bufferedReader().use(BufferedReader::readText),
                )
                val arr = json.optJSONArray("raw")
                    ?: throw TransportException("红外桥没给出波形")
                IntArray(arr.length()) { arr.optInt(it) }
            } catch (e: TransportException) {
                throw e
            } catch (e: Exception) {
                throw TransportException("录制失败，红外桥没回应", e)
            } finally {
                conn.disconnect()
            }
        }

    /** True when this phone has a blaster of its own. */
    fun phoneHasEmitter(): Boolean {
        val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        return ir?.hasIrEmitter() == true
    }

    private suspend fun sendViaPhone(pattern: IntArray) = withContext(Dispatchers.IO) {
        val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        if (ir == null || !ir.hasIrEmitter()) {
            throw TransportException("这台手机没有红外发射口，去设置里填红外桥地址")
        }
        try {
            ir.transmit(GreeIrEncoder.CARRIER_HZ, pattern)
        } catch (e: Exception) {
            throw TransportException("红外发射失败：${e.message}", e)
        }
    }

    private suspend fun sendViaBridge(
        baseUrl: String,
        pattern: IntArray,
        repeat: Int = 1,
    ) = withContext(Dispatchers.IO) {
        val url = normalizeUrl(baseUrl) + "/send"
        val body = JSONObject().apply {
            put("carrier", GreeIrEncoder.CARRIER_HZ)
            put("raw", JSONArray(pattern.toList()))
            put("repeat", repeat.coerceIn(1, 3))
        }.toString()
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection)
        } catch (e: Exception) {
            throw TransportException("红外桥地址不对：$baseUrl", e)
        }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 2_500
            conn.readTimeout = 3_500
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                throw TransportException("红外桥返回 $code ${detail.take(80)}")
            }
            conn.inputStream.use { it.readBytes() }
        } catch (e: TransportException) {
            throw e
        } catch (e: Exception) {
            throw TransportException("连不上红外桥，确认它通电了、和手机在同一个 Wi-Fi、地址没写错", e)
        } finally {
            conn.disconnect()
        }
    }
}
