package com.hvacpanel.data

import android.content.Context

/** A couple of app-wide settings. Not worth a database. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("hvacpanel", Context.MODE_PRIVATE)

    /** Remembered so adding a second infrared unit does not mean retyping it. */
    var bridgeUrl: String
        get() = sp.getString("bridge_url", "") ?: ""
        set(value) = sp.edit().putString("bridge_url", value.trim()).apply()

    var defaultRoom: String
        get() = sp.getString("default_room", "") ?: ""
        set(value) = sp.edit().putString("default_room", value.trim()).apply()

    /** So the automatic update check happens once a day, not once a launch. */
    var lastUpdateCheckMs: Long
        get() = sp.getLong("last_update_check", 0L)
        set(value) = sp.edit().putLong("last_update_check", value).apply()

    var autoCheckUpdates: Boolean
        get() = sp.getBoolean("auto_check_updates", true)
        set(value) = sp.edit().putBoolean("auto_check_updates", value).apply()
}
