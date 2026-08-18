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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.networktools.opencode.net.PingEngine
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.RunStopRow
import com.networktools.opencode.util.guessIpVersion
import com.networktools.opencode.vm.PingViewModel

@Composable
fun PingScreen(vm: PingViewModel = viewModel()) {
    var host by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableStateOf("4") }
    var interval by rememberSaveable { mutableStateOf("1.0") }
    var size by rememberSaveable { mutableStateOf("56") }
    var ipMode by rememberSaveable { mutableStateOf(0) }

    val running by vm.running.collectAsState()
    val packets by vm.packets.collectAsState()
    val error by vm.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(packets.size) {
        if (packets.isNotEmpty()) {
            listState.animateScrollToItem(packets.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("主机或 IP 地址") },
                    placeholder = { Text("例如 8.8.8.8、2606:4700:4700::1111 或 www.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = count,
                        onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) count = v },
                        label = { Text("次数") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() || it == '.' }) interval = v },
                        label = { Text("间隔(秒)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = size,
                        onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() }) size = v },
                        label = { Text("包大小") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        FilterChip(selected = ipMode == 0, onClick = { ipMode = 0 }, label = { Text("自动") })
                        FilterChip(selected = ipMode == 4, onClick = { ipMode = 4 }, label = { Text("IPv4") })
                        FilterChip(selected = ipMode == 6, onClick = { ipMode = 6 }, label = { Text("IPv6") })
                    }
                }
            }
        }
        item {
            RunStopRow(
                running = running,
                enabled = host.isNotBlank(),
                onStart = {
                    val c = count.toIntOrNull() ?: 4
                    val iv = interval.toDoubleOrNull() ?: 1.0
                    val s = size.toIntOrNull() ?: 56
                    val version = if (ipMode != 0) ipMode else guessIpVersion(host)
                    vm.start(host.trim(), c, iv, s, version)
                },
                onStop = { vm.stop() }
            )
        }
        item { ErrorBanner(error) }
        items(packets) { packet ->
            if (packet.kind != PingEngine.Kind.DONE) {
                PingPacketRow(packet)
            }
        }
        if (packets.any { it.kind != PingEngine.Kind.DONE }) {
            item {
                val text = packets
                    .filter { it.kind != PingEngine.Kind.DONE }
                    .joinToString("\n") { it.raw }
                CopyResultsButton(text)
            }
        }
    }
}

@Composable
private fun PingPacketRow(packet: PingEngine.Packet) {
    val (bg, fg) = when (packet.kind) {
        PingEngine.Kind.REPLY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PingEngine.Kind.TIMEOUT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        PingEngine.Kind.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        PingEngine.Kind.SUMMARY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                when (packet.kind) {
                    PingEngine.Kind.REPLY -> {
                        Text(
                            "回复 #${packet.seq}  延迟 ${packet.timeMs?.let { "%.3f ms".format(it) } ?: "—"}  TTL ${packet.ttl ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = fg,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "来自 ${packet.host ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = fg.copy(alpha = 0.8f)
                        )
                    }
                    PingEngine.Kind.TIMEOUT -> {
                        Text("请求超时 #${packet.seq}", style = MaterialTheme.typography.bodyMedium, color = fg)
                    }
                    else -> {
                        Text(
                            packet.raw,
                            style = MaterialTheme.typography.bodySmall,
                            color = fg,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
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