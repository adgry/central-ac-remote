package com.hvacpanel.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hvacpanel.data.UnitStore
import com.hvacpanel.model.Link
import com.hvacpanel.transport.DemoTransport
import com.hvacpanel.transport.GreeLanTransport
import com.hvacpanel.transport.IrTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fires when a 定时关机 comes due. The app process is usually gone by then, so
 * this reads the unit list off disk, stops the unit, and writes it back.
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val unitId = intent.getStringExtra(OffTimer.EXTRA_UNIT_ID) ?: return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9_000) {
                    val store = UnitStore(appContext)
                    val units = store.load()
                    val unit = units.firstOrNull { it.id == unitId } ?: return@withTimeoutOrNull
                    val stopped = unit.state.copy(power = false, offAtEpochMs = null)
                    val transport = when (unit.link) {
                        Link.Demo -> DemoTransport()
                        is Link.GreeLan -> GreeLanTransport()
                        is Link.Infrared -> IrTransport(appContext)
                    }
                    runCatching { transport.write(unit, stopped) }
                    store.save(
                        units.map { if (it.id == unitId) it.copy(state = stopped) else it },
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
