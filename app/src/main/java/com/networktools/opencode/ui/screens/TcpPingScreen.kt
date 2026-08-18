package com.networktools.opencode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.networktools.opencode.net.TcpPing
import com.networktools.opencode.ui.components.CopyAllButton
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.RunStopRow
import com.networktools.opencode.util.guessIpVersion
import com.networktools.opencode.util.prettyPortService
import com.networktools.opencode.vm.TcpPingViewModel

@Composable
fun TcpPingScreen(vm: TcpPingViewModel = viewModel()) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("80") }
    var count by rememberSaveable { mutableStateOf("4") }
    var timeout by rememberSaveable { mutableStateOf("3000") }
    var interval by rememberSaveable { mutableStateOf("1.0") }
    var ipMode by rememberSaveable { mutableStateOf(0) }

    val running by vm.running.collectAsState()
    val probes by vm.probes.collectAsState()
    val summary by vm.summary.collectAsState()
    val error by vm.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(probes.size) {
        if (probes.isNotEmpty()) listState.animateScrollToItem(probes.size - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("主机或 IP 地址") },
                    placeholder = { Text("例如 www.baidu.com 或 192.168.1.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() }) port = v },
                        label = { Text("端口") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = count,
                        onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) count = v },
                        label = { Text("次数") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = timeout,
                        onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() }) timeout = v },
                        label = { Text("超时(毫秒)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() || it == '.' }) interval = v },
                        label = { Text("间隔(秒)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = ipMode == 0, onClick = { ipMode = 0 }, label = { Text("自动") })
                    FilterChip(selected = ipMode == 4, onClick = { ipMode = 4 }, label = { Text("IPv4") })
                    FilterChip(selected = ipMode == 6, onClick = { ipMode = 6 }, label = { Text("IPv6") })
                }
            }
        }
        item {
            RunStopRow(
                running = running,
                enabled = host.isNotBlank(),
                onStart = {
                    val p = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: 80
                    vm.start(
                        host.trim(), p,
                        count.toIntOrNull() ?: 4,
                        timeout.toIntOrNull() ?: 3000,
                        interval.toDoubleOrNull() ?: 1.0,
                        if (ipMode != 0) ipMode else guessIpVersion(host)
                    )
                },
                onStop = { vm.stop() }
            )
        }
        item { ErrorBanner(error) }
        items(probes) { probe -> TcpProbeRow(probe) }
        summary?.let { s ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "发送 ${s.sent}  成功 ${s.ok}  失败 ${s.lost}  丢包 ${"%.1f%%".format(s.lossPercent)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "最小 ${s.min?.let { "%.3f ms".format(it) } ?: "—"}  平均 ${s.avg?.let { "%.3f ms".format(it) } ?: "—"}  最大 ${s.max?.let { "%.3f ms".format(it) } ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
        if (probes.isNotEmpty()) {
            item {
                CopyAllButton(probes.joinToString("\n") {
                    "#${it.seq} ${if (it.ok) "成功" else "失败"} ${"%.3f".format(it.timeMs)}ms"
                })
            }
        }
    }
}

@Composable
private fun TcpProbeRow(probe: TcpPing.Probe) {
    val color = if (probe.ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    Card(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    "#${probe.seq}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    if (probe.ok) "连接成功" else "连接失败",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${"%.3f".format(probe.timeMs)} ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}