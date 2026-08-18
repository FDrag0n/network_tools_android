package com.networktools.opencode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.networktools.opencode.ui.components.CopyAllButton
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.RunStopRow
import com.networktools.opencode.ui.components.StatusChip
import com.networktools.opencode.vm.LanScanViewModel

@Composable
fun LanScanScreen(vm: LanScanViewModel = viewModel()) {
    var cidr by rememberSaveable { mutableStateOf("") }
    var concurrency by rememberSaveable { mutableStateOf("50") }

    val running by vm.running.collectAsState()
    val devices by vm.devices.collectAsState()
    val progress by vm.progress.collectAsState()
    val total by vm.total.collectAsState()
    val subnet by vm.subnet.collectAsState()
    val error by vm.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.detectSubnet() }

    LaunchedEffect(devices.size) {
        if (devices.isNotEmpty()) listState.animateScrollToItem(devices.size - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cidr,
                    onValueChange = { cidr = it },
                    label = { Text("局域网网段") },
                    placeholder = { Text(subnet ?: "检测中…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = concurrency,
                        onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) concurrency = v },
                        label = { Text("并发数") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = false, onClick = {
                        cidr = subnet ?: ""
                        vm.detectSubnet()
                    }, label = { Text("检测本机网段") })
                }
            }
        }
        item {
            RunStopRow(
                running = running,
                enabled = cidr.isNotBlank(),
                onStart = { vm.start(cidr.trim(), concurrency.toIntOrNull() ?: 50) },
                onStop = { vm.stop() }
            )
        }
        if (running || devices.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) progress.toFloat() / total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "已扫描 $progress / $total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusChip("发现 ${devices.size} 台", Color(0xFF2E7D32))
                    }
                }
            }
        }
        item { ErrorBanner(error) }
        items(devices) { device ->
            DeviceCard(device)
        }
        if (devices.isNotEmpty() && !running) {
            item {
                CopyAllButton(devices.joinToString("\n") { deviceText(it) })
            }
        }
    }
}

@Composable
private fun DeviceCard(device: com.networktools.opencode.net.LanScanEngine.Device) {
    Card(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        device.ip,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${"%.1f".format(device.timeMs)} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                device.hostname?.let {
                    Text(
                        "主机名: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val macLine = device.mac?.let {
                    buildString {
                        append("MAC: $it")
                        device.vendor?.let { v -> append("   厂商: $v") }
                    }
                }
                macLine?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                device.osHint?.let {
                    Text(
                        "系统: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (device.ports.isNotEmpty()) {
                    Text(
                        "开放端口:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    device.ports.forEach { p ->
                        Text(
                            buildString {
                                append("  ${p.port}  ${p.service}")
                                p.fingerprint?.let { append("  [$it]") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun deviceText(device: com.networktools.opencode.net.LanScanEngine.Device): String = buildString {
    append(device.ip)
    append("  ${"%.1f".format(device.timeMs)} ms")
    device.hostname?.let { append("\n  主机名: $it") }
    device.mac?.let { append("\n  MAC: $it") }
    device.vendor?.let { append("  厂商: $it") }
    device.osHint?.let { append("\n  系统: $it") }
    if (device.ports.isNotEmpty()) {
        append("\n  开放端口: ")
        append(device.ports.joinToString(", ") { "${it.port}(${it.service})" })
    }
}