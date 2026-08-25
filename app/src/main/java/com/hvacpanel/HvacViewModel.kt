package com.hvacpanel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hvacpanel.control.AcController
import com.hvacpanel.data.Prefs
import com.hvacpanel.transport.Discovered
import com.hvacpanel.update.CheckResult
import com.hvacpanel.update.Update
import com.hvacpanel.update.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Where the in-app update flow has got to. */
sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data object NoReleases : UpdateUi
    data class Ready(val update: Update) : UpdateUi
    data class Downloading(val update: Update, val progress: Float) : UpdateUi
    data class Downloaded(val update: Update, val file: File) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

/** What the add-unit screen is doing. */
data class ScanState(
    val running: Boolean = false,
    val found: List<Discovered> = emptyList(),
    val finishedOnce: Boolean = false,
)

class HvacViewModel(app: Application) : AndroidViewModel(app) {

    val controller = AcController(app, viewModelScope)

    val units = controller.units
    val busy = controller.busy
    val notices = controller.notices

    private val _scan = MutableStateFlow(ScanState())
    val scan = _scan.asStateFlow()

    private val prefs = Prefs(app)
    val updater = UpdateChecker(app)

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val update = _update.asStateFlow()

    private var polling: Job? = null

    /** Called from the activity's onStart; nothing is polled in the background. */
    fun startPolling() {
        if (polling?.isActive == true) return
        polling = controller.startPolling()
    }

    fun stopPolling() {
        polling?.cancel()
        polling = null
    }

    fun scanLan() {
        if (_scan.value.running) return
        _scan.value = _scan.value.copy(running = true)
        viewModelScope.launch {
            val found = controller.scanLan()
            val known = controller.knownMacs()
            _scan.value = ScanState(
                running = false,
                found = found.filterNot { it.mac in known },
                finishedOnce = true,
            )
        }
    }

    fun bind(found: Discovered, room: String) {
        viewModelScope.launch {
            if (controller.bindAndAdd(found, room)) {
                _scan.value = _scan.value.copy(found = _scan.value.found - found)
            }
        }
    }

    // --------------------------------------------------------------- updates

    /** Explicit check, from the settings screen. Reports every outcome. */
    fun checkForUpdate() {
        if (_update.value is UpdateUi.Checking) return
        _update.value = UpdateUi.Checking
        viewModelScope.launch {
            prefs.lastUpdateCheckMs = System.currentTimeMillis()
            _update.value = when (val result = updater.check()) {
                is CheckResult.Available -> UpdateUi.Ready(result.update)
                CheckResult.UpToDate -> UpdateUi.UpToDate
                CheckResult.NoReleases -> UpdateUi.NoReleases
                is CheckResult.Failed -> UpdateUi.Failed(result.message)
            }
        }
    }

    /**
     * Once-a-day background check. Stays silent unless there is something to
     * install — a check that failed is not worth interrupting anyone for.
     */
    fun autoCheckForUpdate() {
        if (!prefs.autoCheckUpdates) return
        if (_update.value !is UpdateUi.Idle) return
        val age = System.currentTimeMillis() - prefs.lastUpdateCheckMs
        if (age < 24 * 60 * 60 * 1000L) return
        viewModelScope.launch {
            prefs.lastUpdateCheckMs = System.currentTimeMillis()
            val result = updater.check()
            if (result is CheckResult.Available) {
                _update.value = UpdateUi.Ready(result.update)
                controller.notify("有新版本 ${result.update.versionName}，去设置里更新")
            }
        }
    }

    var autoCheckEnabled: Boolean
        get() = prefs.autoCheckUpdates
        set(value) {
            prefs.autoCheckUpdates = value
        }

    fun downloadUpdate() {
        val ready = _update.value
        val target = when (ready) {
            is UpdateUi.Ready -> ready.update
            is UpdateUi.Failed -> return
            else -> return
        }
        _update.value = UpdateUi.Downloading(target, 0f)
        viewModelScope.launch {
            try {
                val file = updater.download(target) { progress ->
                    _update.value = UpdateUi.Downloading(target, progress)
                }
                _update.value = UpdateUi.Downloaded(target, file)
            } catch (e: CancellationException) {
                _update.value = UpdateUi.Ready(target)
                throw e
            } catch (e: Exception) {
                _update.value = UpdateUi.Failed(e.message ?: "下载失败")
            }
        }
    }

    fun installDownloaded() {
        val done = _update.value as? UpdateUi.Downloaded ?: return
        try {
            updater.install(done.file)
        } catch (e: Exception) {
            _update.value = UpdateUi.Failed("装不上：${e.message}")
        }
    }

    fun dismissUpdate() {
        _update.value = UpdateUi.Idle
    }

    override fun onCleared() {
        stopPolling()
        controller.persistNow()
        super.onCleared()
    }
}
