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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.networktools.opencode.net.IpScanEngine
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.RunStopRow
import com.networktools.opencode.ui.components.StatusChip
import com.networktools.opencode.vm.IpScanViewModel

@Composable
fun IpScanScreen(vm: IpScanViewModel = viewModel()) {
    var cidr by rememberSaveable { mutableStateOf("") }
    var concurrency by rememberSaveable { mutableStateOf("50") }

    val running by vm.running.collectAsState()
    val hosts by vm.hosts.collectAsState()
    val progress by vm.progress.collectAsState()
    val total by vm.total.collectAsState()
    val error by vm.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(hosts.size) {
        if (hosts.isNotEmpty()) listState.animateScrollToItem(hosts.size - 1)
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
                    label = { Text("IP 网段 (CIDR)") },
                    placeholder = { Text("例如 192.168.0.0/24") },
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
        if (running || hosts.isNotEmpty()) {
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
                        StatusChip("存活 ${hosts.size}", androidx.compose.ui.graphics.Color(0xFF2E7D32))
                    }
                }
            }
        }
        item { ErrorBanner(error) }
        items(hosts) { host ->
            Card(modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            host.ip,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${"%.1f".format(host.timeMs)} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        if (hosts.isNotEmpty() && !running) {
            item {
                val clipboard = LocalClipboardManager.current
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(hosts.joinToString("\n") { it.ip })) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.padding(end = 6.dp))
                    Text("复制全部存活 IP")
                }
            }
        }
    }
}