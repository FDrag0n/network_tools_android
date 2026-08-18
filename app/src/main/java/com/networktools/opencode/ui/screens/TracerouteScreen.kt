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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.networktools.opencode.net.TracerouteEngine
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.RunStopRow
import com.networktools.opencode.util.guessIpVersion
import com.networktools.opencode.vm.TracerouteViewModel

@Composable
fun TracerouteScreen(vm: TracerouteViewModel = viewModel()) {
    var host by rememberSaveable { mutableStateOf("") }
    var maxHops by rememberSaveable { mutableStateOf("30") }
    var probes by rememberSaveable { mutableStateOf("3") }
    var waitSec by rememberSaveable { mutableStateOf("2") }
    var numeric by rememberSaveable { mutableStateOf(false) }
    var ipMode by rememberSaveable { mutableStateOf(0) }

    val running by vm.running.collectAsState()
    val hops by vm.hops.collectAsState()
    val error by vm.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(hops.size) {
        if (hops.isNotEmpty()) listState.animateScrollToItem(hops.size - 1)
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
                    placeholder = { Text("例如 8.8.8.8 或 www.example.com") },
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
                        label = { Text("每跳探测数") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = waitSec,
                        onValueChange = { v -> if (v.length <= 2 && v.all { it.isDigit() }) waitSec = v },
                        label = { Text("等待(秒)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(selected = numeric, onClick = { numeric = !numeric }, label = { Text("仅数字(不反查域名)") })
                    Spacer(Modifier.weight(1f))
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
                    val m = maxHops.toIntOrNull() ?: 30
                    val q = probes.toIntOrNull() ?: 3
                    val w = waitSec.toIntOrNull() ?: 2
                    val version = if (ipMode != 0) ipMode else guessIpVersion(host)
                    vm.start(host.trim(), m, q, w, numeric, version)
                },
                onStop = { vm.stop() }
            )
        }
        item { ErrorBanner(error) }
        items(hops) { hop -> HopRow(hop) }
        if (hops.isNotEmpty()) {
            item {
                val text = hops.joinToString("\n") {
                    "${it.seq}\t${it.hosts.joinToString(" ")}\t${it.times.joinToString(" ") { "%.3f ms".format(it) }}"
                }
                CopyResultsButton(text)
            }
        }
    }
}

@Composable
private fun HopRow(hop: TracerouteEngine.Hop) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(
                    hop.seq.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.size(12.dp))
            SelectionContainer {
                Column(modifier = Modifier.weight(1f)) {
                    if (hop.timedOut || hop.hosts.isEmpty()) {
                        Text(
                            "* 无响应",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        hop.hosts.forEach { h ->
                            Text(
                                h,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    if (hop.times.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            hop.times.joinToString("  ") { "%.2f ms".format(it) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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