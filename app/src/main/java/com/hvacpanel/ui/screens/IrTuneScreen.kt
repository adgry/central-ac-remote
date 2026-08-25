package com.hvacpanel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import com.hvacpanel.HvacViewModel
import com.hvacpanel.model.Link
import com.hvacpanel.transport.GreeIrEncoder
import com.hvacpanel.ui.components.PanelField
import com.hvacpanel.ui.components.PanelKey
import com.hvacpanel.ui.components.PanelSurface
import com.hvacpanel.ui.components.SilkLabel
import com.hvacpanel.ui.theme.Ink
import com.hvacpanel.ui.theme.PanelType
import kotlinx.coroutines.launch

/**
 * Infrared is one-way, so the only way to know whether the panel is listening is
 * to look at both sides of the conversation. This screen shows the frame the app
 * would send, lets you fire it, and — the part that actually settles arguments —
 * records a frame from the handset that came with the panel and says what the app
 * thinks that frame means. Whatever the handset shows on its own display is the
 * ground truth; anything this screen reads differently is a bug in the layout,
 * localised to a byte.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IrTuneScreen(vm: HvacViewModel, unitId: String, onBack: () -> Unit) {
    val units by vm.units.collectAsState()
    val unit = units.firstOrNull { it.id == unitId } ?: return
    val link = unit.link as? Link.Infrared ?: return
    val scope = rememberCoroutineScope()

    var bridge by remember(unitId) { mutableStateOf(link.bridgeUrl.orEmpty()) }
    var working by remember(unitId) { mutableStateOf<String?>(null) }
    var captured by remember(unitId) { mutableStateOf<IntArray?>(null) }

    val outgoing = GreeIrEncoder.frame(unit.state)
    val capturedFrame = captured?.let { GreeIrEncoder.decode(it) }

    fun run(labelText: String, block: suspend () -> Result<String>) {
        if (working != null) return
        working = labelText
        scope.launch {
            val result = block()
            vm.controller.notify(
                result.getOrElse { it.message ?: "失败了" },
            )
            working = null
        }
    }

    PanelScreen(title = "红外调试", eyebrow = unit.name, onBack = onBack) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // ------------------------------------------------------------ bridge
            Section("红外桥") {
                PanelField(
                    value = bridge,
                    onValueChange = { bridge = it },
                    label = "地址",
                    placeholder = "http://192.168.1.50",
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    PanelKey(
                        onClick = {
                            vm.controller.setBridgeUrl(unitId, bridge)
                            vm.controller.notify("地址已保存")
                        },
                        contentPadding = 15.dp,
                    ) { Text("保存地址", style = PanelType.silkSmall, color = Ink.Silk) }
                    Spacer(Modifier.width(8.dp))
                    PanelKey(
                        onClick = {
                            vm.controller.setBridgeUrl(unitId, bridge)
                            run("连接测试") {
                                vm.controller.probeBridge(unit.copy(link = Link.Infrared(bridge)))
                            }
                        },
                        enabled = working == null && bridge.isNotBlank(),
                        contentPadding = 15.dp,
                    ) { Text("连接测试", style = PanelType.silkSmall, color = Ink.Silk) }
                }
            }

            // ---------------------------------------------------------- outgoing
            Section("app 现在会发的帧") {
                FrameReadout(hex = outgoing.hex, checksumOk = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${GreeIrEncoder.waveform(unit.state).size} 个脉冲 · " +
                        "${unit.state.mode.label} ${unit.state.targetTemp}℃ " +
                        "${unit.state.fan.label}风 ${if (unit.state.power) "开机" else "关机"}",
                    style = PanelType.body,
                    color = Ink.SilkDim,
                )
                Spacer(Modifier.height(14.dp))
                PanelKey(
                    onClick = {
                        run("测试发送") {
                            vm.controller.testSendIr(unit).map { "已发出，看看线控器有没有反应" }
                        }
                    },
                    enabled = working == null && bridge.isNotBlank(),
                    accent = Ink.Cool,
                    contentPadding = 15.dp,
                ) { Text("测试发送", style = PanelType.silkSmall, color = Ink.Silk) }
            }

            // ----------------------------------------------------------- capture
            Section("录你手上的遥控器") {
                Text(
                    "先把遥控器调成一个你记得住的状态，比如「制冷 26℃ 中风」。点下面的开始录制，" +
                        "然后把遥控器对着红外桥的接收头按一下开关键。",
                    style = PanelType.body,
                    color = Ink.SilkDim,
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    PanelKey(
                        onClick = {
                            run("录制中") {
                                vm.controller.captureIr(unit).map { raw ->
                                    captured = raw
                                    "录到 ${raw.size} 个脉冲"
                                }
                            }
                        },
                        enabled = working == null && bridge.isNotBlank(),
                        accent = Ink.Warm,
                        contentPadding = 15.dp,
                    ) {
                        Text(
                            if (working == "录制中") "等待信号…" else "开始录制",
                            style = PanelType.silkSmall,
                            color = Ink.Silk,
                        )
                    }
                    if (captured != null) {
                        Spacer(Modifier.width(8.dp))
                        PanelKey(
                            onClick = {
                                val raw = captured ?: return@PanelKey
                                run("重放") {
                                    vm.controller.replayIr(unit, raw)
                                        .map { "已按原样重放，这一帧线控器一定认" }
                                }
                            },
                            enabled = working == null,
                            contentPadding = 15.dp,
                        ) { Text("重放这一帧", style = PanelType.silkSmall, color = Ink.Silk) }
                    }
                }

                val raw = captured
                if (raw != null) {
                    Spacer(Modifier.height(16.dp))
                    Verdict(
                        "脉冲数",
                        "${raw.size}",
                        if (raw.size == GreeIrEncoder.EXPECTED_PULSES) {
                            "和格力格式一致"
                        } else {
                            "格力格式应该是 ${GreeIrEncoder.EXPECTED_PULSES} 个，对不上，" +
                                "遥控器可能是另一种协议 —— 这种情况就别管码表了，用重放"
                        },
                        ok = raw.size == GreeIrEncoder.EXPECTED_PULSES,
                    )
                    if (capturedFrame != null) {
                        val checksumOk = GreeIrEncoder.checksumMatches(capturedFrame)
                        Spacer(Modifier.height(12.dp))
                        FrameReadout(hex = capturedFrame.hex, checksumOk = checksumOk)
                        Spacer(Modifier.height(12.dp))
                        Verdict(
                            "校验和",
                            if (checksumOk) "通过" else "不通过",
                            if (checksumOk) {
                                "位序和校验规则都对上了"
                            } else {
                                "位序或校验规则和 app 的假设不一样"
                            },
                            ok = checksumOk,
                        )
                        val read = GreeIrEncoder.interpret(capturedFrame)
                        Spacer(Modifier.height(12.dp))
                        Verdict(
                            "app 读成",
                            "${read.mode.label} ${read.targetTemp}℃ ${read.fan.label}风 " +
                                if (read.power) "开机" else "关机",
                            "和遥控器屏幕上显示的对一下。不一样的话，差在哪一项就说明哪个字段的位置写错了。",
                            ok = null,
                        )
                        val diff = GreeIrEncoder.differingBytes(outgoing, capturedFrame)
                        Spacer(Modifier.height(12.dp))
                        Verdict(
                            "和 app 会发的帧比",
                            if (diff.isEmpty()) "完全一样" else "第 ${diff.joinToString("、")} 字节不同",
                            if (diff.isEmpty()) {
                                "两边一致。码表没问题，发不动就是硬件对不准或距离太远。"
                            } else {
                                "把这一页截图发给我，改 GreeIrEncoder.Frame 里对应的位就行。" +
                                    "（两边状态不同的话，本来就该有差异。）"
                            },
                            ok = if (diff.isEmpty()) true else null,
                        )
                    }
                }
            }

            Section("这一页是干什么的") {
                Text(
                    "线控器上那个小黑点是红外接收窗，所以它能像被遥控器控制那样被 app 控制。" +
                        "但格力出过好几种遥控器编码，app 内置的码表不一定是你家那种。这一页就是用来" +
                        "当场判断的：录一帧真码，看校验和过不过、app 读出来的和遥控器屏幕一致不一致。\n\n" +
                        "实在对不上也有退路 —— 重放录到的原始码，这条路不依赖任何码表。",
                    style = PanelType.body,
                    color = Ink.SilkDim,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
        NoticeLine(vm.notices)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    PanelSurface(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Column(Modifier.padding(16.dp)) {
            SilkLabel(title, color = Ink.SilkDim)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Eight bytes, monospaced, the way you would read them off a logic analyser. */
@Composable
private fun FrameReadout(hex: String, checksumOk: Boolean) {
    PanelSurface(Modifier.fillMaxWidth(), sunk = true) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                hex,
                style = PanelType.data.copy(fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = if (checksumOk) Ink.Lcd else Ink.Fault,
            )
        }
    }
}

@Composable
private fun Verdict(label: String, value: String, note: String, ok: Boolean?) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SilkLabel(label, small = true, color = Ink.SilkDim)
            Spacer(Modifier.width(10.dp))
            Text(
                value,
                style = PanelType.nameSmall,
                color = when (ok) {
                    true -> Ink.Live
                    false -> Ink.Fault
                    null -> Ink.Silk
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(note, style = PanelType.body, color = Ink.SilkDim)
    }
}
