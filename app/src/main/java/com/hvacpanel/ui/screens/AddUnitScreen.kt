package com.hvacpanel.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hvacpanel.HvacViewModel
import com.hvacpanel.data.Prefs
import com.hvacpanel.ui.components.PanelField
import com.hvacpanel.ui.components.PanelKey
import com.hvacpanel.ui.components.PanelSurface
import com.hvacpanel.ui.components.SilkLabel
import com.hvacpanel.ui.theme.Ink
import com.hvacpanel.ui.theme.PanelType
import kotlinx.coroutines.launch

/**
 * Adding a unit means choosing how the app will reach it. The three ways are
 * genuinely different, so each gets its own block with its own action rather
 * than one form with a hidden dropdown.
 */
@Composable
fun AddUnitScreen(vm: HvacViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scan by vm.scan.collectAsState()
    var room by remember { mutableStateOf(prefs.defaultRoom) }
    var manualName by remember { mutableStateOf("") }
    var bridge by remember { mutableStateOf(prefs.bridgeUrl) }
    var scanningBridge by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PanelScreen(title = "添加室内机", eyebrow = "选一种连接方式", onBack = onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            PanelSurface(Modifier.fillMaxWidth(), sunk = true) {
                Column(Modifier.padding(16.dp)) {
                    PanelField(room, { room = it; prefs.defaultRoom = it }, "新室内机放进哪个分区", placeholder = "卧室")
                }
            }

            Spacer(Modifier.height(20.dp))

            // -------------------------------------------------------- Infrared
            Block(
                title = "红外",
                body = "线控器上那个小黑点是红外接收窗，可以像遥控器那样对它发码。\n\n发射不靠手机：app 只算波形，通过 Wi-Fi 交给一台红外桥（ESP8266/ESP32 + 红外灯，固件在 docs/ir-bridge.ino），桥放在线控器附近对准那个小黑点。手机有没有红外口都不影响。\n\n加完之后进这台机器的「红外调试」页，可以当场验证线控器认不认。",
            ) {
                PanelField(
                    value = bridge,
                    onValueChange = { bridge = it; prefs.bridgeUrl = it },
                    label = "红外桥地址",
                    placeholder = "http://192.168.1.50",
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PanelKey(
                        onClick = {
                            if (scanningBridge) return@PanelKey
                            scanningBridge = true
                            scope.launch {
                                val found = vm.controller.findBridges().getOrDefault(emptyList())
                                if (found.isNotEmpty()) {
                                    bridge = found.first()
                                    prefs.bridgeUrl = found.first()
                                    vm.controller.notify("找到 ${found.first()}，已填好")
                                } else {
                                    vm.controller.notify(
                                        "没搜到红外桥。确认它通电了、和手机连同一个 Wi-Fi。",
                                    )
                                }
                                scanningBridge = false
                            }
                        },
                        enabled = !scanningBridge,
                        accent = Ink.Cool,
                        contentPadding = 15.dp,
                    ) {
                        Text(
                            if (scanningBridge) "搜索中…" else "搜索红外桥",
                            style = PanelType.silkSmall,
                            color = Ink.Silk,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "不知道地址就点这个",
                        style = PanelType.body,
                        color = Ink.SilkDim,
                    )
                }
                Spacer(Modifier.height(14.dp))
                PanelField(manualName, { manualName = it }, "名称", placeholder = "客厅")
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PanelKey(
                        onClick = {
                            vm.controller.addIrUnit(manualName, room, bridge)
                            manualName = ""
                            onBack()
                        },
                        contentPadding = 16.dp,
                    ) {
                        Text("添加红外室内机", style = PanelType.silk, color = Ink.Silk)
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Say what it means for the user, not just whether the part exists.
                Text(
                    if (vm.controller.phoneHasIrEmitter()) {
                        "这台手机自带红外口，地址留空就用它发。"
                    } else {
                        "这台手机没有红外口——不影响，发射由上面那台红外桥完成。"
                    },
                    style = PanelType.body,
                    color = Ink.SilkDim,
                )
            }

            // ------------------------------------------------------------ Demo
            Block(
                title = "演示机",
                body = "一台不存在的室内机，用来先把界面和分区安排好。室温会跟着设定温度慢慢变化。",
            ) {
                Row {
                    PanelKey(
                        onClick = {
                            vm.controller.addDemoUnit(manualName.ifBlank { "演示机" }, room)
                            manualName = ""
                            onBack()
                        },
                        contentPadding = 16.dp,
                    ) {
                        Text("添加演示机", style = PanelType.silk, color = Ink.Silk)
                    }
                }
            }

            // ------------------------------------------------------ Gree on LAN
            Block(
                title = "局域网搜索",
                body = "只有室内机侧装了格力 Wi-Fi 模块或智能网关才搜得到。搜得到的话这条路最好用 —— 双向，能读回真实状态和室温。搜不到就走上面的红外。",
            ) {
                PanelKey(
                    onClick = { vm.scanLan() },
                    enabled = !scan.running,
                    accent = Ink.Cool,
                    selected = scan.running,
                    contentPadding = 16.dp,
                ) {
                    Text(
                        if (scan.running) "搜索中…" else "搜索局域网",
                        style = PanelType.silk,
                        color = Ink.Silk,
                    )
                }

                if (scan.found.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    scan.found.forEach { found ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(found.name, style = PanelType.nameSmall, color = Ink.Silk)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${found.host} · ${found.model.ifBlank { "型号未知" }}",
                                    style = PanelType.data,
                                    color = Ink.SilkDim,
                                )
                            }
                            PanelKey(
                                onClick = { vm.bind(found, room) },
                                accent = Ink.Cool,
                                contentPadding = 14.dp,
                            ) {
                                Text("绑定并添加", style = PanelType.silkSmall, color = Ink.Silk)
                            }
                        }
                    }
                } else if (scan.finishedOnce && !scan.running) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "没找到新的室内机。确认手机连的是家里的 Wi-Fi（不是 5G 流量），室内机的 Wi-Fi 模块已配网。",
                        style = PanelType.body,
                        color = Ink.SilkDim,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
        NoticeLine(vm.notices)
    }
}

@Composable
private fun Block(
    title: String,
    body: String,
    content: @Composable () -> Unit,
) {
    PanelSurface(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Column(Modifier.padding(16.dp)) {
            SilkLabel(title, color = Ink.SilkDim)
            Spacer(Modifier.height(8.dp))
            Text(body, style = PanelType.body, color = Ink.SilkDim)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
