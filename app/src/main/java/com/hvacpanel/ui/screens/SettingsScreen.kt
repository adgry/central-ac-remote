package com.hvacpanel.ui.screens

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hvacpanel.HvacViewModel
import com.hvacpanel.UpdateUi
import com.hvacpanel.data.Prefs
import com.hvacpanel.model.Link
import com.hvacpanel.ui.components.PanelField
import com.hvacpanel.ui.components.PanelKey
import com.hvacpanel.ui.components.PanelSurface
import com.hvacpanel.ui.components.SilkLabel
import com.hvacpanel.ui.theme.Ink
import com.hvacpanel.ui.theme.PanelType

@Composable
fun SettingsScreen(vm: HvacViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val units by vm.units.collectAsState()
    var bridge by remember { mutableStateOf(prefs.bridgeUrl) }

    val lanCount = units.count { it.link is Link.GreeLan }
    val irCount = units.count { it.link is Link.Infrared }
    val demoCount = units.count { it.link is Link.Demo }

    PanelScreen(title = "设置", eyebrow = "连接与说明", onBack = onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            UpdatePanel(vm)

            Spacer(Modifier.height(18.dp))

            PanelSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SilkLabel("室内机", color = Ink.SilkDim)
                    Spacer(Modifier.height(10.dp))
                    Line("局域网", "$lanCount 台")
                    Line("红外", "$irCount 台")
                    Line("演示", "$demoCount 台")
                    Spacer(Modifier.height(14.dp))
                    Row {
                        PanelKey(onClick = { vm.controller.refreshAll() }, contentPadding = 16.dp) {
                            Text("立即刷新全部", style = PanelType.silk, color = Ink.Silk)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            PanelSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SilkLabel("红外桥", color = Ink.SilkDim)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "app 只负责算出 38 kHz 波形，通过 Wi-Fi 交给红外桥发射。" +
                            "新加的红外室内机默认用这个地址。",
                        style = PanelType.body,
                        color = Ink.SilkDim,
                    )
                    Spacer(Modifier.height(14.dp))
                    PanelField(
                        value = bridge,
                        onValueChange = { bridge = it; prefs.bridgeUrl = it },
                        label = "默认地址",
                        placeholder = "http://192.168.1.50",
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (vm.controller.phoneHasIrEmitter()) {
                            "这台手机自带红外口，红外室内机不填地址时会用它发。"
                        } else {
                            "这台手机没有红外口，不影响用红外——发射由红外桥完成。"
                        },
                        style = PanelType.body,
                        color = Ink.SilkDim,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            PanelSurface(Modifier.fillMaxWidth(), sunk = true) {
                Column(Modifier.padding(16.dp)) {
                    SilkLabel("三种连法的差别", color = Ink.SilkDim)
                    Spacer(Modifier.height(12.dp))
                    Note(
                        "局域网",
                        "双向。能读回室内机真实状态和室温，每 8 秒刷新一次。需要室内机侧有格力 Wi-Fi 模块或网关。",
                    )
                    Note(
                        "红外",
                        "单向。对着墙上线控器的接收窗发码，发出去就算成功，读不回状态。码表按格力手持遥控器的格式生成，你家线控器如果不认，用红外桥的 /capture 录你自己的遥控器。",
                    )
                    Note(
                        "演示",
                        "不发任何东西，纯本机模拟，用来试界面。",
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
        NoticeLine(vm.notices)
    }
}

@Composable
private fun Line(label: String, value: String) = Row(
    Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, style = PanelType.body, color = Ink.SilkDim, modifier = Modifier.weight(1f))
    Text(value, style = PanelType.silkSmall, color = Ink.Silk)
}

@Composable
private fun Note(title: String, body: String) = Column(Modifier.padding(bottom = 14.dp)) {
    Text(title, style = PanelType.nameSmall, color = Ink.Silk)
    Spacer(Modifier.height(4.dp))
    Text(body, style = PanelType.body, color = Ink.SilkDim)
}

/**
 * Version and updates. The check is one tap and says what it found either way;
 * an update never installs itself — the last step is always the system installer
 * with the user's finger on it.
 */
@Composable
private fun UpdatePanel(vm: HvacViewModel) {
    val context = LocalContext.current
    val state by vm.update.collectAsState()
    var autoCheck by remember { mutableStateOf(vm.autoCheckEnabled) }

    PanelSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SilkLabel("版本", color = Ink.SilkDim)
            Spacer(Modifier.height(10.dp))
            Line("当前", "${vm.updater.currentVersion} (${vm.updater.currentVersionCode})")
            Line("更新源", vm.updater.repo)
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelKey(
                    onClick = { vm.checkForUpdate() },
                    enabled = state !is UpdateUi.Checking && state !is UpdateUi.Downloading,
                    contentPadding = 16.dp,
                ) {
                    Text(
                        if (state is UpdateUi.Checking) "查询中…" else "检查更新",
                        style = PanelType.silk,
                        color = Ink.Silk,
                    )
                }
                Spacer(Modifier.width(10.dp))
                PanelKey(
                    onClick = {
                        autoCheck = !autoCheck
                        vm.autoCheckEnabled = autoCheck
                    },
                    selected = autoCheck,
                    contentPadding = 14.dp,
                ) {
                    Text(
                        "每天自动查",
                        style = PanelType.silkSmall,
                        color = if (autoCheck) Ink.Silk else Ink.SilkDim,
                    )
                }
            }

            when (val s = state) {
                UpdateUi.Idle, UpdateUi.Checking -> Unit

                UpdateUi.UpToDate -> Outcome("已经是最新版本", Ink.Live)

                UpdateUi.NoReleases -> Outcome(
                    "仓库里还没有发布过版本。打一个 tag 并发 release，附上 APK，这里就能看到。",
                    Ink.SilkDim,
                )

                is UpdateUi.Failed -> Outcome(s.message, Ink.Fault)

                is UpdateUi.Ready -> {
                    Spacer(Modifier.height(16.dp))
                    ReleaseNotes(s.update.versionName, s.update.notes, s.update.sizeBytes)
                    Spacer(Modifier.height(14.dp))
                    if (!vm.updater.canInstall()) {
                        Text(
                            "安卓要先允许这个 app 安装应用，才能装更新。",
                            style = PanelType.body,
                            color = Ink.SilkDim,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row {
                            PanelKey(
                                onClick = {
                                    runCatching {
                                        context.startActivity(vm.updater.installPermissionIntent())
                                    }
                                },
                                accent = Ink.Warm,
                                selected = true,
                                contentPadding = 16.dp,
                            ) {
                                Text("去开启权限", style = PanelType.silk, color = Ink.Silk)
                            }
                        }
                    } else {
                        Row {
                            PanelKey(
                                onClick = { vm.downloadUpdate() },
                                accent = Ink.Cool,
                                selected = true,
                                contentPadding = 16.dp,
                            ) {
                                Text("下载并安装", style = PanelType.silk, color = Ink.Silk)
                            }
                            Spacer(Modifier.width(8.dp))
                            PanelKey(onClick = { vm.dismissUpdate() }, contentPadding = 16.dp) {
                                Text("以后再说", style = PanelType.silk, color = Ink.SilkDim)
                            }
                        }
                    }
                }

                is UpdateUi.Downloading -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "下载 ${s.update.versionName} … ${(s.progress * 100).toInt()}%",
                        style = PanelType.body,
                        color = Ink.SilkDim,
                    )
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(s.progress)
                }

                is UpdateUi.Downloaded -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "${s.update.versionName} 已下载好。下一步是系统的安装界面。",
                        style = PanelType.body,
                        color = Ink.SilkDim,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        PanelKey(
                            onClick = { vm.installDownloaded() },
                            accent = Ink.Cool,
                            selected = true,
                            contentPadding = 16.dp,
                        ) {
                            Text("现在安装", style = PanelType.silk, color = Ink.Silk)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Outcome(text: String, color: Color) {
    Spacer(Modifier.height(12.dp))
    Text(text, style = PanelType.body, color = color)
}

@Composable
private fun ReleaseNotes(version: String, notes: String, sizeBytes: Long) {
    PanelSurface(Modifier.fillMaxWidth(), sunk = true) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(version, style = PanelType.name, color = Ink.Silk)
                Spacer(Modifier.width(10.dp))
                if (sizeBytes > 0) {
                    Text(
                        "${sizeBytes / 1024 / 1024} MB",
                        style = PanelType.data,
                        color = Ink.SilkDim,
                    )
                }
            }
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    notes.lineSequence().take(12).joinToString("\n"),
                    style = PanelType.body,
                    color = Ink.SilkDim,
                )
            }
        }
    }
}

/** A plain fill bar, drawn rather than borrowed from Material. */
@Composable
private fun ProgressBar(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .drawBehind {
                drawRect(Ink.PanelSunk)
                drawRect(
                    color = Ink.Cool,
                    size = androidx.compose.ui.geometry.Size(
                        size.width * progress.coerceIn(0f, 1f),
                        size.height,
                    ),
                )
            },
    )
}
