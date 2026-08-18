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
import com.networktools.opencode.net.MtrEngine
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.RunStopRow
import com.networktools.opencode.util.guessIpVersion
import com.networktools.opencode.vm.MtrViewModel

@Composable
fun MtrScreen(vm: MtrViewModel = viewModel()) {
    var host by rememberSaveable { mutableStateOf("") }
    var maxHops by rememberSaveable { mutableStateOf("30") }
    var probes by rememberSaveable { mutableStateOf("1") }
    var interval by rememberSaveable { mutableStateOf("1.0") }
    var ipMode by rememberSaveable { mutableStateOf(0) }

    val running by vm.running.collectAsState()
    val rows by vm.rows.collectAsState()
    val cycle by vm.cycle.collectAsState()
    val error by vm.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size - 1)
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
                    label = { Text("目标主机或 IP") },
                    placeholder = { Text("例如 8.8.8.8 或 www.baidu.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = maxHops,
                        onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) maxHops = v },
                        label = { Text("最大跳数") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = probes,
                        onValueChange = { v -> if (v.length <= 2 && v.all { it.isDigit() }) probes = v },
                        label = { Text("每跳探测") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() || it == '.' }) interval = v },
                        label = { Text("轮询间隔(秒)") },
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
                    vm.start(
                        host.trim(),
                        maxHops.toIntOrNull()?.coerceIn(1, 64) ?: 30,
                        probes.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        interval.toDoubleOrNull() ?: 1.0,
                        if (ipMode != 0) ipMode else guessIpVersion(host)
                    )
                },
                onStop = { vm.stop() }
            )
        }
        if (running) {
            item {
                Text(
                    "第 $cycle 轮",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { ErrorBanner(error) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("跳数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                Text("主机", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("丢失", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("avg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
            }
        }
        items(rows) { row -> MtrHopRow(row) }
        if (rows.isNotEmpty()) {
            item {
                val text = rows.joinToString("\n") { r ->
                    "${r.hop}\t${r.host ?: "?"}\t${if (r.sent > 0) "${r.lost * 100 / r.sent}%" else "100%"}\t${r.avg?.let { "%.2f".format(it) } ?: "—"}"
                }
                val clipboard = LocalClipboardManager.current
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(text)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.padding(end = 6.dp))
                    Text("复制全部结果")
                }
            }
        }
    }
}

@Composable
private fun MtrHopRow(row: MtrEngine.HopRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(row.hop.toString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(end = 8.dp))
                Text(
                    row.host ?: if (row.reached) "已到达" else "无响应",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                val loss = if (row.sent > 0) row.lost * 100 / row.sent else 100
                Text(
                    "$loss%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (loss > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Text(
                    row.avg?.let { "%.2f ms".format(it) } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}