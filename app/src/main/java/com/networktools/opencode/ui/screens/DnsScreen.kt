package com.networktools.opencode.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import com.networktools.opencode.net.DnsResolver
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.RunStopRow
import com.networktools.opencode.vm.DnsViewModel
import org.xbill.DNS.Type

private data class DnsTypeOption(val label: String, val type: Int)

private val DNS_TYPES = listOf(
    DnsTypeOption("A", Type.A),
    DnsTypeOption("AAAA", Type.AAAA),
    DnsTypeOption("CNAME", Type.CNAME),
    DnsTypeOption("MX", Type.MX),
    DnsTypeOption("TXT", Type.TXT),
    DnsTypeOption("NS", Type.NS),
    DnsTypeOption("SOA", Type.SOA),
    DnsTypeOption("PTR", Type.PTR),
    DnsTypeOption("SRV", Type.SRV)
)

private val DNS_SERVERS = listOf("8.8.8.8", "1.1.1.1", "223.5.5.5", "114.114.114.114")

@Composable
fun DnsScreen(vm: DnsViewModel = viewModel()) {
    var host by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(Type.A) }
    var server by rememberSaveable { mutableStateOf("") }

    val running by vm.running.collectAsState()
    val records by vm.records.collectAsState()
    val error by vm.error.collectAsState()
    val allMode by vm.allMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("域名 / IP") },
            placeholder = { Text("例如 example.com（PTR 查询时输入 IP）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .fillMaxWidth()
        ) {
            DNS_TYPES.forEach { opt ->
                FilterChip(
                    selected = type == opt.type,
                    onClick = { type = opt.type },
                    label = { Text(opt.label) }
                )
            }
        }
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("DNS 服务器（留空使用 8.8.8.8）") },
            placeholder = { Text("支持 IPv4 / IPv6 地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DNS_SERVERS.forEach { s ->
                FilterChip(
                    selected = server == s,
                    onClick = { server = s },
                    label = { Text(s) }
                )
            }
        }
        RunStopRow(
            running = running,
            enabled = host.isNotBlank(),
            onStart = { vm.start(host.trim(), type, server.trim().ifBlank { null }) },
            onStop = { vm.stop() }
        )
        OutlinedButton(
            onClick = { vm.startAll(host.trim(), server.trim().ifBlank { null }) },
            enabled = !running && host.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("一键所有解析")
        }
        ErrorBanner(error)
        if (records.isNotEmpty()) {
            Text(
                if (allMode) "共 ${records.size} 条记录（全部类型）"
                else "共 ${records.size} 条 ${DnsResolver.typeName(type)} 记录",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        records.forEach { record ->
            DnsRecordRow(record)
        }
        if (records.isNotEmpty()) {
            val text = records.joinToString("\n") {
                "${it.name}\t${it.type}\tTTL=${it.ttl}\t${it.value}"
            }
            CopyResultsButton(text)
        }
    }
}

@Composable
private fun DnsRecordRow(record: DnsResolver.RecordRow) {
    val clipboard = LocalClipboardManager.current
    Card(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(
                            record.type,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "TTL ${record.ttl}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = {
                        clipboard.setText(AnnotatedString(record.value))
                    }) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "复制",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    record.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    record.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
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