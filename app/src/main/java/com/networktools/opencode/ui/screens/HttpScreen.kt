package com.networktools.opencode.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.RunStopRow
import com.networktools.opencode.vm.HttpViewModel

private val HTTP_METHODS = listOf("GET", "POST", "DELETE", "PUT", "PATCH", "HEAD")

@Composable
fun HttpScreen(vm: HttpViewModel = viewModel()) {
    var url by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("GET") }
    var headersText by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var timeout by rememberSaveable { mutableStateOf("10000") }

    val running by vm.running.collectAsState()
    val result by vm.result.collectAsState()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            placeholder = { Text("例如 https://httpbin.org/get") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth()
        ) {
            HTTP_METHODS.forEach { m ->
                FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
            }
        }
        OutlinedTextField(
            value = headersText,
            onValueChange = { headersText = it },
            label = { Text("请求头（每行 key: value）") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        if (method != "GET" && method != "HEAD") {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("请求体") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = timeout,
            onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() }) timeout = v },
            label = { Text("超时(毫秒)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        RunStopRow(
            running = running,
            enabled = url.isNotBlank(),
            onStart = {
                val headers = headersText.lines().mapNotNull { line ->
                    val idx = line.indexOf(':')
                    if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
                }.toMap()
                vm.send(
                    method = method,
                    url = url.trim(),
                    headers = headers,
                    body = body.takeIf { it.isNotEmpty() },
                    timeoutMs = timeout.toIntOrNull() ?: 10000
                )
            },
            onStop = { vm.stop() }
        )
        ErrorBanner(result?.error)
        result?.let { r ->
            Card(modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                "HTTP ${r.status ?: "—"} ${r.statusMessage ?: ""}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if ((r.status ?: 0) in 200..299) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            Text(
                                "${"%.0f".format(r.timeMs)} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString("HTTP ${r.status} ${r.statusMessage}\n${r.body}"))
                            }) {
                                Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.padding(0.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(
                            r.headers.joinToString("\n") { "${it.first}: ${it.second}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            r.body,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}