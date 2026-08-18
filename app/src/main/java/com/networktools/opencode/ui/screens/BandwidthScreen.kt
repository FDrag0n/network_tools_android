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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.RunStopRow
import com.networktools.opencode.vm.BandwidthViewModel

@Composable
fun BandwidthScreen(vm: BandwidthViewModel = viewModel()) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("5201") }
    var seconds by rememberSaveable { mutableStateOf("10") }
    var buffer by rememberSaveable { mutableStateOf("131072") }
    var reverse by rememberSaveable { mutableStateOf(false) }

    val running by vm.running.collectAsState()
    val result by vm.result.collectAsState()
    val progress by vm.progress.collectAsState()
    val error by vm.error.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("iperf3 服务器") },
                    placeholder = { Text("例如 10.0.2.2（宿主机）或服务器 IP") },
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
                        value = seconds,
                        onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) seconds = v },
                        label = { Text("时长(秒)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = buffer,
                        onValueChange = { v -> if (v.length <= 7 && v.all { it.isDigit() }) buffer = v },
                        label = { Text("缓冲(字节)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !reverse, onClick = { reverse = false }, label = { Text("上传") })
                    FilterChip(selected = reverse, onClick = { reverse = true }, label = { Text("下载") })
                }
            }
        }
        item {
            RunStopRow(
                running = running,
                enabled = host.isNotBlank(),
                onStart = {
                    vm.start(
                        host.trim(),
                        port.toIntOrNull() ?: 5201,
                        seconds.toIntOrNull() ?: 10,
                        reverse,
                        buffer.toIntOrNull() ?: 131072
                    )
                },
                onStop = { vm.stop() }
            )
        }
        if (running || progress.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = {
                            val last = progress.lastOrNull()
                            if (last != null) (last.first / (seconds.toIntOrNull() ?: 10)).toFloat().coerceIn(0f, 1f) else 0f
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "已测 ${progress.lastOrNull()?.first?.let { "%.1f".format(it) } ?: "0.0"} 秒",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { ErrorBanner(error) }
        items(progress) { (elapsed, mbps) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "%.1f 秒".format(elapsed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "%.2f Mbps".format(mbps),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
        result?.let { r ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${if (reverse) "下载" else "上传"}带宽  %.2f Mbps".format(r.mbps),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "传输 ${r.bytes / 1024.0 / 1024.0 / 1024.0 * 8.0} Gbit（${r.bytes} 字节） 用时 %.1f 秒".format(r.seconds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}