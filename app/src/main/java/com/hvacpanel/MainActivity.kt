package com.hvacpanel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hvacpanel.ui.screens.AddUnitScreen
import com.hvacpanel.ui.screens.IrTuneScreen
import com.hvacpanel.ui.screens.RackScreen
import com.hvacpanel.ui.screens.SettingsScreen
import com.hvacpanel.ui.screens.UnitScreen
import com.hvacpanel.ui.theme.CentralAcTheme
import com.hvacpanel.ui.theme.Ink

class MainActivity : ComponentActivity() {

    private val vm: HvacViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        vm.startPolling()
        vm.autoCheckForUpdate()
    }

    override fun onStop() {
        vm.stopPolling()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CentralAcTheme {
                Box(Modifier.fillMaxSize().background(Ink.Housing)) {
                    App()
                }
            }
        }
    }
}

/** Four places to be, kept as a route string so it survives rotation. */
private const val ROUTE_RACK = "rack"
private const val ROUTE_ADD = "add"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_UNIT = "unit:"
private const val ROUTE_IRTUNE = "irtune:"

@Composable
private fun App(vm: HvacViewModel = viewModel()) {
    var route by rememberSaveable { mutableStateOf(ROUTE_RACK) }
    val units by vm.units.collectAsState()

    // A unit removed while its panel is open must not leave us on a dead screen.
    LaunchedEffect(units.size, route) {
        val id = when {
            route.startsWith(ROUTE_IRTUNE) -> route.removePrefix(ROUTE_IRTUNE)
            route.startsWith(ROUTE_UNIT) -> route.removePrefix(ROUTE_UNIT)
            else -> null
        }
        if (id != null && units.none { it.id == id }) route = ROUTE_RACK
    }

    BackHandler(enabled = route != ROUTE_RACK) { route = ROUTE_RACK }

    AnimatedContent(
        targetState = route,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(110)) },
        label = "screen",
    ) { target ->
        when {
            target == ROUTE_ADD -> AddUnitScreen(vm, onBack = { route = ROUTE_RACK })
            target == ROUTE_SETTINGS -> SettingsScreen(vm, onBack = { route = ROUTE_RACK })
            target.startsWith(ROUTE_IRTUNE) -> IrTuneScreen(
                vm = vm,
                unitId = target.removePrefix(ROUTE_IRTUNE),
                onBack = { route = ROUTE_UNIT + target.removePrefix(ROUTE_IRTUNE) },
            )
            target.startsWith(ROUTE_UNIT) -> UnitScreen(
                vm = vm,
                unitId = target.removePrefix(ROUTE_UNIT),
                onBack = { route = ROUTE_RACK },
                onTuneIr = { route = ROUTE_IRTUNE + it },
            )
            else -> RackScreen(
                vm = vm,
                onOpenUnit = { route = ROUTE_UNIT + it },
                onAdd = { route = ROUTE_ADD },
                onSettings = { route = ROUTE_SETTINGS },
            )
        }
    }
}
