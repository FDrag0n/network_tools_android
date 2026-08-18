package com.networktools.opencode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.networktools.opencode.net.PortScanner
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.RunStopRow
import com.networktools.opencode.ui.components.StatusChip
import com.networktools.opencode.util.guessIpVersion
import com.networktools.opencode.util.prettyPortService
import com.networktools.opencode.vm.PortScanViewModel

@Composable
fun PortScanScreen(vm: PortScanViewModel = viewModel()) {
    var host by rememberSaveable { mutableStateOf("") }
    var startPort by rememberSaveable { mutableStateOf("1") }
    var endPort by rememberSaveable { mutableStateOf("1024") }
    var timeout by rememberSaveable { mutableStateOf("1000") }
    var concurrency by rememberSaveable { mutableStateOf("50") }
    var ipMode by rememberSaveable { mutableStateOf(0) }

    val running by vm.running.collectAsState()
    val results by vm.results.collectAsState()
    val progress by vm.progress.collectAsState()
    val total by vm.total.collectAsState()
    val error by vm.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(results.size) {
        if (results.isNotEmpty()) listState.animateScrollToItem(results.size - 1)
    }

    val openCount = results.count { it.state == PortScanner.State.OPEN }
    val closedCount = results.count { it.state == PortScanner.State.CLOSED }
    val filteredCount = results.count { it.state == PortScanner.State.FILTERED }

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
                    label = { Text("目标主机或 IP") },
                    placeholder = { Text("例如 192.168.1.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startPort,
                        onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() }) startPort = v },
                        label = { Text("起始端口") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endPort,
                        onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() }) endPort = v },
                        label = { Text("结束端口") },
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
                        value = concurrency,
                        onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) concurrency = v },
                        label = { Text("并发数") },
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
                    val s = startPort.toIntOrNull() ?: 1
                    val e = endPort.toIntOrNull() ?: 1024
                    val t = timeout.toIntOrNull() ?: 1000
                    val c = concurrency.toIntOrNull() ?: 50
                    val version = if (ipMode != 0) ipMode else guessIpVersion(host)
                    vm.start(host.trim(), s.coerceAtLeast(1), e.coerceAtLeast(1), t, c, version)
                },
                onStop = { vm.stop() }
            )
        }
        if (running || results.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) progress.toFloat() / total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "已扫描 $progress / ${if (total > 0) total else "?"} 个端口",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip("开放 $openCount", Color(0xFF2E7D32))
                        StatusChip("关闭 $closedCount", MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusChip("过滤 $filteredCount", MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
        item { ErrorBanner(error) }
        items(results) { result -> PortResultRow(result) }
        if (results.isNotEmpty()) {
            item {
                val text = results.joinToString("\n") {
                    "${it.port}\t${it.state.name}\t${it.service ?: "—"}\t${it.timeMs}ms"
                }
                CopyResultsButton(text)
            }
        }
    }
}

@Composable
private fun PortResultRow(result: PortScanner.Result) {
    val stateColor = when (result.state) {
        PortScanner.State.OPEN -> Color(0xFF2E7D32)
        PortScanner.State.CLOSED -> MaterialTheme.colorScheme.onSurfaceVariant
        PortScanner.State.FILTERED -> MaterialTheme.colorScheme.tertiary
    }
    SelectionContainer {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            StatusChip(result.state.name, stateColor)
            Spacer(Modifier.size(10.dp))
            Text(
                result.port.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.size(10.dp))
            val service = result.service ?: prettyPortService(result.port)
            Text(
                service ?: "未知服务",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${result.timeMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CopyResultsButton(text: String) {
    val clipboard = LocalClipboardManager.current
    OutlinedButton(
        onClick = { clipboard.setText(AnnotatedString(text)) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("复制全部结果")
    }
}