package com.hvacpanel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hvacpanel.HvacViewModel
import com.hvacpanel.control.OffTimer
import com.hvacpanel.model.AcState
import com.hvacpanel.model.AcUnit
import com.hvacpanel.model.FanSpeed
import com.hvacpanel.model.Link
import com.hvacpanel.model.Mode
import com.hvacpanel.model.Reachability
import com.hvacpanel.model.TEMP_MAX
import com.hvacpanel.model.TEMP_MIN
import com.hvacpanel.ui.components.PanelField
import com.hvacpanel.ui.components.PanelKey
import com.hvacpanel.ui.components.PanelSurface
import com.hvacpanel.ui.components.RepeatKey
import com.hvacpanel.ui.components.SilkLabel
import com.hvacpanel.ui.lcd.FanBars
import com.hvacpanel.ui.lcd.LcdField
import com.hvacpanel.ui.lcd.LcdSetpoint
import com.hvacpanel.ui.lcd.LcdSmallNumber
import com.hvacpanel.ui.lcd.LcdTellTale
import com.hvacpanel.ui.lcd.ModeMark
import com.hvacpanel.ui.lcd.PowerMark
import com.hvacpanel.ui.lcd.SwingMark
import com.hvacpanel.ui.theme.Ink
import com.hvacpanel.ui.theme.PanelType
import kotlinx.coroutines.delay

/**
 * One unit, everything it can do. The keys are grouped and labelled the way the
 * wall panel labels them — 模式, 风速, 摆风, 功能, 定时 — so anyone who has used
 * the panel already knows this screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitScreen(
    vm: HvacViewModel,
    unitId: String,
    onBack: () -> Unit,
    onTuneIr: (String) -> Unit = {},
) {
    val units by vm.units.collectAsState()
    val busy by vm.busy.collectAsState()
    val unit = units.firstOrNull { it.id == unitId } ?: return
    val s = unit.state
    val caps = vm.controller.capsFor(unit)
    var editing by remember { mutableStateOf(false) }

    // The 定时 label counts down, so it needs its own slow clock.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.offAtEpochMs) {
        while (s.offAtEpochMs != null) {
            nowMs = System.currentTimeMillis()
            delay(20_000)
        }
    }
    val offIn = s.offAtEpochMs?.let { s.offInMinutes(nowMs) }

    // The glass runs its own self-test when the panel opens, same as a real one.
    var selfTest by remember { mutableStateOf(true) }
    LaunchedEffect(unitId) {
        selfTest = true
        delay(190)
        selfTest = false
        vm.controller.refresh(unitId)
    }

    fun edit(transform: (AcState) -> AcState) = vm.controller.update(unitId, transform)

    PanelScreen(
        title = unit.name,
        eyebrow = unit.room.ifBlank { "未分区" },
        onBack = onBack,
        trailing = {
            PanelKey(onClick = { editing = !editing }, selected = editing, contentPadding = 14.dp) {
                Text("改名", style = PanelType.silkSmall, color = Ink.Silk)
            }
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            if (editing) {
                RenamePanel(
                    unit = unit,
                    onSave = { name, room ->
                        vm.controller.rename(unitId, name, room)
                        editing = false
                    },
                    onDelete = {
                        vm.controller.remove(unitId)
                        onBack()
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            // ------------------------------------------------------- the glass
            PanelSurface(Modifier.fillMaxWidth()) {
                LcdField(
                    backlit = s.power,
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                ) { backlight ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        // Status across the top, as on the panel.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ModeMark(s.mode, lit = true, glyphSize = 22.dp, backlight = backlight)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                s.mode.label,
                                style = PanelType.lcdLabel,
                                color = Ink.LcdInk.copy(alpha = backlight),
                            )
                            Spacer(Modifier.weight(1f))
                            FanBars(s.fan, height = 16.dp, backlight = backlight, on = s.power)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                s.fan.label,
                                style = PanelType.lcdLabel,
                                color = Ink.LcdInk.copy(alpha = backlight),
                            )
                            Spacer(Modifier.width(12.dp))
                            SwingMark(s.swingVertical, glyphSize = 17.dp, backlight = backlight)
                        }

                        Spacer(Modifier.height(2.dp))

                        // Nothing but the reading: keys live on the housing.
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            LcdSetpoint(
                                value = when {
                                    selfTest -> 88.0
                                    !s.showsSetpoint -> null
                                    else -> s.setpoint
                                },
                                cellHeight = 104.dp,
                                backlight = backlight,
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (caps.roomTemp && s.roomTemp != null) {
                                Text(
                                    "室温",
                                    style = PanelType.lcdLabel,
                                    color = Ink.LcdInk.copy(alpha = 0.75f * backlight),
                                )
                                Spacer(Modifier.width(5.dp))
                                LcdSmallNumber(s.roomTemp, cellHeight = 18.dp, backlight = backlight)
                            } else if (!s.showsSetpoint) {
                                Text(
                                    "送风不控温",
                                    style = PanelType.lcdLabel,
                                    color = Ink.LcdInk.copy(alpha = 0.75f * backlight),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (offIn != null) {
                                LcdTellTale("定时", on = true, backlight = backlight)
                                Spacer(Modifier.width(4.dp))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Tell-tales: every one etched, the active ones filled.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            LcdTellTale("睡眠", s.sleep, backlight = backlight)
                            if (caps.eco) LcdTellTale("节能", s.eco, backlight = backlight)
                            if (caps.quiet) LcdTellTale("静音", s.quiet, backlight = backlight)
                            if (caps.turbo) LcdTellTale("强劲", s.turbo, backlight = backlight)
                            if (caps.dryCoil) LcdTellTale("干燥", s.dryCoil, backlight = backlight)
                            if (caps.health) LcdTellTale("健康", s.health, backlight = backlight)
                            if (caps.childLock) LcdTellTale("童锁", s.childLock, backlight = backlight)
                        }
                    }
                }

                // ⊖ 开关 ⊕ — one row of keys on the housing, as on the panel.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RepeatKey(
                        onFire = { edit { it.withTempDelta(-1) } },
                        enabled = s.power && s.showsSetpoint && s.targetTemp > TEMP_MIN,
                        circular = true,
                        label = "降低一度",
                        contentPadding = 20.dp,
                        verticalPadding = 20.dp,
                    ) {
                        Text(
                            "−",
                            style = PanelType.name.copy(fontSize = 26.sp),
                            color = if (s.power && s.showsSetpoint) Ink.Silk else Ink.SilkFaint,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    PanelKey(
                        onClick = { edit { it.copy(power = !it.power) } },
                        modifier = Modifier.weight(1f),
                        selected = s.power,
                        accent = if (s.mode == Mode.HEAT) Ink.Warm else Ink.Cool,
                        contentPadding = 12.dp,
                    ) {
                        PowerMark(
                            glyphSize = 19.dp,
                            color = if (s.power) {
                                if (s.mode == Mode.HEAT) Ink.Warm else Ink.Cool
                            } else {
                                Ink.SilkDim
                            },
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            if (s.power) "关机" else "开机",
                            style = PanelType.silk,
                            color = if (s.power) Ink.Silk else Ink.SilkDim,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    RepeatKey(
                        onFire = { edit { it.withTempDelta(1) } },
                        enabled = s.power && s.showsSetpoint && s.targetTemp < TEMP_MAX,
                        circular = true,
                        label = "升高一度",
                        contentPadding = 20.dp,
                        verticalPadding = 20.dp,
                    ) {
                        Text(
                            "+",
                            style = PanelType.name.copy(fontSize = 26.sp),
                            color = if (s.power && s.showsSetpoint) Ink.Silk else Ink.SilkFaint,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ------------------------------------------------------------ 模式
            KeyGroup("模式") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Mode.entries.forEach { mode ->
                        PanelKey(
                            onClick = { edit { it.copy(mode = mode) } },
                            selected = s.mode == mode,
                            accent = if (mode == Mode.HEAT) Ink.Warm else Ink.Cool,
                            contentPadding = 13.dp,
                        ) {
                            ModeMark(
                                mode = mode,
                                lit = true,
                                glyphSize = 16.dp,
                                ink = if (s.mode == mode) {
                                    if (mode == Mode.HEAT) Ink.Warm else Ink.Cool
                                } else {
                                    Ink.SilkDim
                                },
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                mode.label,
                                style = PanelType.silkSmall,
                                color = if (s.mode == mode) Ink.Silk else Ink.SilkDim,
                            )
                        }
                    }
                }
            }

            // ------------------------------------------------------------ 风速
            KeyGroup("风速", note = if (!s.fanAdjustable) "除湿时风速由室内机自己定" else null) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    FanSpeed.entries.forEach { fan ->
                        PanelKey(
                            onClick = { edit { it.copy(fan = fan) } },
                            enabled = s.fanAdjustable,
                            selected = s.fan == fan && s.fanAdjustable,
                            contentPadding = 8.dp,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                fan.label,
                                style = PanelType.silkSmall,
                                color = if (s.fan == fan && s.fanAdjustable) Ink.Silk else Ink.SilkDim,
                            )
                        }
                    }
                }
            }

            // ------------------------------------------------------------ 摆风
            KeyGroup("摆风") {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    ToggleKey(
                        label = "上下",
                        on = s.swingVertical,
                        modifier = Modifier.weight(1f),
                    ) { edit { it.copy(swingVertical = !it.swingVertical) } }
                    if (caps.swingHorizontal) {
                        ToggleKey(
                            label = "左右",
                            on = s.swingHorizontal,
                            modifier = Modifier.weight(1f),
                        ) { edit { it.copy(swingHorizontal = !it.swingHorizontal) } }
                    }
                }
            }

            // ------------------------------------------------------------ 功能
            KeyGroup("功能") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (caps.halfDegree) {
                        ToggleKey("半度", s.halfDegree, enabled = s.showsSetpoint) {
                            edit { it.copy(halfDegree = !it.halfDegree) }
                        }
                    }
                    ToggleKey("睡眠", s.sleep) { edit { it.copy(sleep = !it.sleep) } }
                    if (caps.eco) ToggleKey("节能", s.eco) { edit { it.copy(eco = !it.eco) } }
                    if (caps.quiet) ToggleKey("静音", s.quiet) { edit { it.copy(quiet = !it.quiet, turbo = false) } }
                    if (caps.turbo) ToggleKey("强劲", s.turbo) { edit { it.copy(turbo = !it.turbo, quiet = false) } }
                    if (caps.dryCoil) ToggleKey("干燥", s.dryCoil) { edit { it.copy(dryCoil = !it.dryCoil) } }
                    if (caps.health) ToggleKey("健康", s.health) { edit { it.copy(health = !it.health) } }
                    if (caps.panelLight) ToggleKey("面板灯", s.panelLight) { edit { it.copy(panelLight = !it.panelLight) } }
                    if (caps.childLock) ToggleKey("童锁", s.childLock) { edit { it.copy(childLock = !it.childLock) } }
                }
            }

            // ------------------------------------------------------------ 定时
            KeyGroup(
                "定时关机",
                note = offIn?.let { "还剩 ${OffTimer.humanise(it)}" },
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    OffTimer.PRESETS_MINUTES.forEach { minutes ->
                        PanelKey(
                            onClick = { vm.controller.setOffTimer(unitId, minutes) },
                            contentPadding = 13.dp,
                        ) {
                            Text(
                                OffTimer.humanise(minutes),
                                style = PanelType.keyLabel,
                                color = Ink.SilkDim,
                            )
                        }
                    }
                    if (offIn != null) {
                        PanelKey(
                            onClick = { vm.controller.setOffTimer(unitId, null) },
                            selected = true,
                            accent = Ink.Fault,
                            contentPadding = 13.dp,
                        ) {
                            Text("取消定时", style = PanelType.keyLabel, color = Ink.Silk)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            LinkFooter(unit, sending = unitId in busy, onTuneIr = { onTuneIr(unitId) })
            Spacer(Modifier.height(28.dp))
        }
        NoticeLine(vm.notices)
    }
}

/** A labelled group of keys, silkscreen heading above. */
@Composable
private fun KeyGroup(
    title: String,
    note: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SilkLabel(title, color = Ink.SilkDim)
            if (note != null) {
                Spacer(Modifier.width(10.dp))
                Text(note, style = PanelType.body, color = Ink.SilkDim)
            }
        }
        Spacer(Modifier.height(9.dp))
        content()
    }
}

@Composable
private fun ToggleKey(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = PanelKey(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    selected = on,
    contentPadding = 13.dp,
) {
    Text(
        label,
        style = PanelType.silkSmall,
        color = when {
            !enabled -> Ink.SilkFaint
            on -> Ink.Silk
            else -> Ink.SilkDim
        },
    )
}

@Composable
private fun RenamePanel(
    unit: AcUnit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(unit.id) { mutableStateOf(unit.name) }
    var room by remember(unit.id) { mutableStateOf(unit.room) }
    PanelSurface(Modifier.fillMaxWidth(), sunk = true) {
        Column(Modifier.padding(16.dp)) {
            PanelField(name, { name = it }, "名称", placeholder = "客厅")
            Spacer(Modifier.height(12.dp))
            PanelField(room, { room = it }, "分区", placeholder = "公共区")
            Spacer(Modifier.height(16.dp))
            Row {
                PanelKey(onClick = { onSave(name, room) }, accent = Ink.Cool, selected = true, contentPadding = 16.dp) {
                    Text("保存", style = PanelType.silk, color = Ink.Silk)
                }
                Spacer(Modifier.width(8.dp))
                PanelKey(onClick = onDelete, contentPadding = 16.dp) {
                    Text("移除这台", style = PanelType.silk, color = Ink.Fault)
                }
            }
        }
    }
}

/** What the app is talking to, and whether it answered. */
@Composable
private fun LinkFooter(unit: AcUnit, sending: Boolean, onTuneIr: () -> Unit) {
    val (kind, detail) = when (val l = unit.link) {
        Link.Demo -> "演示" to "本机模拟，不发任何指令"
        is Link.GreeLan -> "局域网" to "${l.host} · ${l.mac.takeLast(6)}${if (l.key == null) " · 未绑定" else ""}"
        is Link.Infrared -> "红外" to (l.bridgeUrl?.let { "经红外桥 $it" } ?: "用手机红外口发射")
    }
    PanelSurface(Modifier.fillMaxWidth(), sunk = true) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SilkLabel("链路", small = true, color = Ink.SilkDim)
                Spacer(Modifier.width(10.dp))
                Text(kind, style = PanelType.silkSmall, color = Ink.SilkDim)
                Spacer(Modifier.weight(1f))
                when {
                    sending -> SilkLabel("发送中", small = true, color = Ink.Cool)
                    unit.reach == Reachability.ONLINE -> SilkLabel("在线", small = true, color = Ink.Live)
                    unit.reach == Reachability.OFFLINE -> SilkLabel("未响应", small = true, color = Ink.Fault)
                    else -> SilkLabel("未知", small = true, color = Ink.SilkDim)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(detail, style = PanelType.data, color = Ink.SilkDim)
            if (unit.isOneWay) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "红外是单向的：这里显示的是最后发出去的设置，不是室内机回报的状态。",
                    style = PanelType.body,
                    color = Ink.SilkDim,
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    PanelKey(onClick = onTuneIr, contentPadding = 15.dp) {
                        Text("红外调试", style = PanelType.silkSmall, color = Ink.Silk)
                    }
                }
            }
            unit.lastError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = PanelType.body, color = Ink.Fault)
            }
        }
    }
}
