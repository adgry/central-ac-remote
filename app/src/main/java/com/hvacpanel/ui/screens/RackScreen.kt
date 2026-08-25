package com.hvacpanel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hvacpanel.HvacViewModel
import com.hvacpanel.model.AcUnit
import com.hvacpanel.model.Mode
import com.hvacpanel.model.Reachability
import com.hvacpanel.model.TEMP_MAX
import com.hvacpanel.model.TEMP_MIN
import com.hvacpanel.ui.components.PanelKey
import com.hvacpanel.ui.components.PanelSurface
import com.hvacpanel.ui.components.SilkLabel
import com.hvacpanel.ui.components.StatusDot
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

/**
 * The rack: every indoor unit as its own panel, grouped the way the flat is.
 * One glance should answer "what is running and at what temperature", which is
 * why the setpoint is on the glass and not behind a tap.
 */
@Composable
fun RackScreen(
    vm: HvacViewModel,
    onOpenUnit: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    val units by vm.units.collectAsState()
    val busy by vm.busy.collectAsState()
    val running = units.count { it.state.power }

    PanelScreen(
        title = "中央空调",
        eyebrow = "室内机 ${units.size} 台",
        trailing = {
            PanelKey(onClick = onSettings, contentPadding = 13.dp) {
                Text("设置", style = PanelType.silkSmall, color = Ink.Silk)
            }
        },
    ) {
        Box(Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    MasterBar(
                        total = units.size,
                        running = running,
                        onAllOn = { vm.controller.setPowerAll(true) },
                        onAllOff = { vm.controller.setPowerAll(false) },
                        onNudge = { delta ->
                            val ids = units.filter { it.state.power }.map { it.id }
                            vm.controller.updateMany(ids) {
                                it.copy(targetTemp = (it.targetTemp + delta).coerceIn(TEMP_MIN, TEMP_MAX))
                            }
                        },
                    )
                }

                if (units.isEmpty()) {
                    item { EmptyRack(onAdd = onAdd) }
                }

                for ((room, group) in units.groupBy { it.room.ifBlank { "未分区" } }) {
                    item(key = "head-$room") {
                        Spacer(Modifier.height(8.dp))
                        SectionHeading(room, trailing = "${group.count { it.state.power }} 运行")
                        Spacer(Modifier.height(2.dp))
                    }
                    items(group, key = { it.id }) { unit ->
                        UnitPanel(
                            unit = unit,
                            sending = unit.id in busy,
                            onOpen = { onOpenUnit(unit.id) },
                            onTogglePower = {
                                vm.controller.update(unit.id) { s -> s.copy(power = !s.power) }
                            },
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(14.dp))
                    PanelKey(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 16.dp,
                    ) {
                        Text("添加室内机", style = PanelType.silk, color = Ink.Silk)
                    }
                }
            }
        }
        NoticeLine(vm.notices)
    }
}

/** 总控 — the breaker at the top of the rack. */
@Composable
private fun MasterBar(
    total: Int,
    running: Int,
    onAllOn: () -> Unit,
    onAllOff: () -> Unit,
    onNudge: (Int) -> Unit,
) {
    PanelSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                SilkLabel("总控", color = Ink.SilkDim)
                Spacer(Modifier.height(5.dp))
                Text(
                    text = if (running == 0) "全部停止" else "$running / $total 运行",
                    style = PanelType.nameSmall,
                    color = if (running == 0) Ink.SilkDim else Ink.Live,
                )
            }
            PanelKey(
                onClick = { onNudge(-1) },
                enabled = running > 0,
                contentPadding = 14.dp,
                label = "所有运行中的室内机降低一度",
            ) {
                Text("−", style = PanelType.nameSmall, color = if (running > 0) Ink.Silk else Ink.SilkFaint)
            }
            Spacer(Modifier.width(6.dp))
            PanelKey(
                onClick = { onNudge(1) },
                enabled = running > 0,
                contentPadding = 14.dp,
                label = "所有运行中的室内机升高一度",
            ) {
                Text("+", style = PanelType.nameSmall, color = if (running > 0) Ink.Silk else Ink.SilkFaint)
            }
            Spacer(Modifier.width(10.dp))
            PanelKey(onClick = onAllOn, accent = Ink.Live, contentPadding = 13.dp) {
                Text("全开", style = PanelType.silkSmall, color = Ink.Silk)
            }
            Spacer(Modifier.width(6.dp))
            PanelKey(onClick = onAllOff, contentPadding = 13.dp) {
                Text("全关", style = PanelType.silkSmall, color = Ink.SilkDim)
            }
        }
    }
}

@Composable
private fun EmptyRack(onAdd: () -> Unit) {
    PanelSurface(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("还没有室内机", style = PanelType.name, color = Ink.Silk)
            Spacer(Modifier.height(8.dp))
            Text(
                "搜索局域网上的格力室内机，或者先加一台演示机把界面走一遍。",
                style = PanelType.body,
                color = Ink.SilkDim,
            )
            Spacer(Modifier.height(16.dp))
            PanelKey(onClick = onAdd, contentPadding = 16.dp) {
                Text("去添加", style = PanelType.silk, color = Ink.Silk)
            }
        }
    }
}

/** One indoor unit. Name and power live on the housing; state lives on the glass. */
@Composable
private fun UnitPanel(
    unit: AcUnit,
    sending: Boolean,
    onOpen: () -> Unit,
    onTogglePower: () -> Unit,
) {
    val s = unit.state
    val dotColor = when {
        unit.reach == Reachability.OFFLINE -> Ink.Fault
        s.power -> Ink.Live
        else -> Ink.SilkFaint
    }
    PanelSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onOpen)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(dotColor)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = unit.name,
                    style = PanelType.name,
                    color = Ink.Silk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                if (sending) {
                    SilkLabel("发送", small = true, color = Ink.Cool)
                    Spacer(Modifier.width(10.dp))
                } else if (unit.reach == Reachability.OFFLINE) {
                    SilkLabel("未响应", small = true, color = Ink.Fault)
                    Spacer(Modifier.width(10.dp))
                }
                PanelKey(
                    onClick = onTogglePower,
                    selected = s.power,
                    accent = if (s.mode == Mode.HEAT) Ink.Warm else Ink.Cool,
                    contentPadding = 16.dp,
                    circular = true,
                    label = if (s.power) "关闭 ${unit.name}" else "开启 ${unit.name}",
                ) {
                    PowerMark(
                        glyphSize = 20.dp,
                        color = if (s.power) {
                            if (s.mode == Mode.HEAT) Ink.Warm else Ink.Cool
                        } else {
                            Ink.SilkFaint
                        },
                    )
                }
            }
            LcdField(
                backlit = s.power,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) { backlight ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ModeMark(s.mode, lit = true, glyphSize = 20.dp, backlight = backlight)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            s.mode.label,
                            style = PanelType.lcdLabel,
                            color = Ink.LcdInk.copy(alpha = backlight),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    LcdSetpoint(
                        value = if (s.showsSetpoint) s.setpoint else null,
                        cellHeight = 46.dp,
                        backlight = backlight,
                    )
                    Spacer(Modifier.width(10.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val tellTales = buildList {
                            if (s.sleep) add("睡眠")
                            if (s.eco) add("节能")
                            if (s.turbo) add("强劲")
                            if (s.childLock) add("童锁")
                        }
                        tellTales.take(2).forEach {
                            LcdTellTale(it, on = true, backlight = backlight)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FanBars(s.fan, height = 15.dp, backlight = backlight, on = s.power)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                s.fan.label,
                                style = PanelType.lcdLabel,
                                color = Ink.LcdInk.copy(alpha = backlight),
                            )
                            if (s.swingVertical) {
                                Spacer(Modifier.width(8.dp))
                                SwingMark(true, glyphSize = 16.dp, backlight = backlight)
                            }
                        }
                        if (s.roomTemp != null) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "室温",
                                    style = PanelType.lcdLabel,
                                    color = Ink.LcdInk.copy(alpha = 0.72f * backlight),
                                )
                                Spacer(Modifier.width(5.dp))
                                LcdSmallNumber(s.roomTemp, cellHeight = 19.dp, backlight = backlight)
                            }
                        }
                    }
                }
            }
        }
    }
}
