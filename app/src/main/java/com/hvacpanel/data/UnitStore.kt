package com.hvacpanel.data

import android.content.Context
import com.hvacpanel.model.AcState
import com.hvacpanel.model.AcUnit
import com.hvacpanel.model.FanSpeed
import com.hvacpanel.model.Link
import com.hvacpanel.model.Mode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The unit list on disk, as plain JSON. Small enough that rewriting the whole
 * file on every change is cheaper than anything cleverer.
 */
class UnitStore(context: Context) {

    private val file = File(context.filesDir, "units.json")

    fun load(): List<AcUnit> {
        if (!file.exists()) return seed()
        return runCatching {
            val root = JSONObject(file.readText())
            val arr = root.getJSONArray("units")
            (0 until arr.length()).mapNotNull { readUnit(arr.getJSONObject(it)) }
        }.getOrElse {
            // Unreadable is not the same as absent: seeding demo units here would
            // bury a real list under fake ones and then save over it. Keep the
            // file for recovery and start empty instead.
            runCatching { file.renameTo(File(file.parentFile, "units.corrupt.json")) }
            emptyList()
        }
    }

    fun save(units: List<AcUnit>) {
        runCatching {
            val arr = JSONArray()
            units.forEach { arr.put(writeUnit(it)) }
            file.writeText(JSONObject().put("units", arr).toString())
        }
    }

    // ---------------------------------------------------------------- mapping

    private fun writeUnit(u: AcUnit) = JSONObject().apply {
        put("id", u.id)
        put("name", u.name)
        put("room", u.room)
        put("link", writeLink(u.link))
        put("state", writeState(u.state))
    }

    private fun readUnit(o: JSONObject): AcUnit? {
        val id = o.optString("id").ifBlank { return null }
        return AcUnit(
            id = id,
            name = o.optString("name", "室内机"),
            room = o.optString("room", ""),
            link = readLink(o.optJSONObject("link")),
            state = readState(o.optJSONObject("state")),
        )
    }

    private fun writeLink(link: Link) = JSONObject().apply {
        when (link) {
            Link.Demo -> put("kind", "demo")
            is Link.GreeLan -> {
                put("kind", "gree")
                put("host", link.host)
                put("mac", link.mac)
                link.key?.let { put("key", it) }
            }
            is Link.Infrared -> {
                put("kind", "ir")
                link.bridgeUrl?.let { put("bridge", it) }
            }
        }
    }

    private fun readLink(o: JSONObject?): Link = when (o?.optString("kind")) {
        "gree" -> Link.GreeLan(
            host = o.optString("host"),
            mac = o.optString("mac"),
            key = o.optString("key").ifBlank { null },
        )
        "ir" -> Link.Infrared(bridgeUrl = o.optString("bridge").ifBlank { null })
        else -> Link.Demo
    }

    private fun writeState(s: AcState) = JSONObject().apply {
        put("power", s.power)
        put("mode", s.mode.name)
        put("temp", s.targetTemp)
        put("half", s.halfDegree)
        put("fan", s.fan.name)
        put("swingV", s.swingVertical)
        put("swingH", s.swingHorizontal)
        put("sleep", s.sleep)
        put("eco", s.eco)
        put("quiet", s.quiet)
        put("turbo", s.turbo)
        put("dryCoil", s.dryCoil)
        put("health", s.health)
        put("light", s.panelLight)
        put("lock", s.childLock)
        s.roomTemp?.let { put("roomTemp", it) }
        s.offAtEpochMs?.let { put("offAt", it) }
    }

    private fun readState(o: JSONObject?): AcState {
        if (o == null) return AcState()
        return AcState(
            power = o.optBoolean("power", false),
            mode = runCatching { Mode.valueOf(o.optString("mode", "COOL")) }.getOrDefault(Mode.COOL),
            targetTemp = o.optInt("temp", 26),
            halfDegree = o.optBoolean("half", false),
            fan = runCatching { FanSpeed.valueOf(o.optString("fan", "AUTO")) }.getOrDefault(FanSpeed.AUTO),
            swingVertical = o.optBoolean("swingV", false),
            swingHorizontal = o.optBoolean("swingH", false),
            sleep = o.optBoolean("sleep", false),
            eco = o.optBoolean("eco", false),
            quiet = o.optBoolean("quiet", false),
            turbo = o.optBoolean("turbo", false),
            dryCoil = o.optBoolean("dryCoil", false),
            health = o.optBoolean("health", false),
            panelLight = o.optBoolean("light", true),
            childLock = o.optBoolean("lock", false),
            roomTemp = if (o.has("roomTemp")) o.optInt("roomTemp") else null,
            // A deadline that has already passed is stale; the alarm fired without us.
            offAtEpochMs = o.optLong("offAt", 0L).takeIf { it > System.currentTimeMillis() },
        )
    }

    /**
     * First run: five simulated indoor units, so the app has something to
     * control before any hardware is bound.
     */
    private fun seed(): List<AcUnit> = listOf(
        Triple("客厅", Mode.COOL, 26) to true,
        Triple("主卧", Mode.COOL, 25) to false,
        Triple("次卧", Mode.COOL, 26) to false,
        Triple("书房", Mode.HEAT, 22) to false,
        Triple("餐厅", Mode.FAN, 26) to false,
    ).mapIndexed { i, (spec, on) ->
        val (room, mode, temp) = spec
        AcUnit(
            id = "demo-$i",
            name = room,
            room = if (i <= 0) "公共区" else if (i <= 2) "卧室" else "其他",
            link = Link.Demo,
            state = AcState(
                power = on,
                mode = mode,
                targetTemp = temp,
                fan = if (on) FanSpeed.MEDIUM else FanSpeed.AUTO,
                swingVertical = on,
                roomTemp = 27 + (i % 3),
            ),
        )
    }
}
