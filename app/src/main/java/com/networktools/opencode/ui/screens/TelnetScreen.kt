package com.networktools.opencode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.networktools.opencode.net.TelnetSession
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.StatusChip
import com.networktools.opencode.vm.TelnetViewModel

@Composable
fun TelnetScreen(vm: TelnetViewModel = viewModel()) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("23") }
    var input by rememberSaveable { mutableStateOf("") }

    val lines by vm.lines.collectAsState()
    val state by vm.state.collectAsState()
    val error by vm.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("主机") },
                singleLine = true,
                enabled = state != TelnetSession.State.CONNECTING && state != TelnetSession.State.CONNECTED,
                modifier = Modifier.weight(2f)
            )
            OutlinedTextField(
                value = port,
                onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() }) port = v },
                label = { Text("端口") },
                singleLine = true,
                enabled = state != TelnetSession.State.CONNECTING && state != TelnetSession.State.CONNECTED,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state == TelnetSession.State.CONNECTED || state == TelnetSession.State.CONNECTING) {
                Button(
                    onClick = { vm.disconnect() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Stop, null, modifier = Modifier.padding(end = 6.dp))
                    Text("断开")
                }
            } else {
                OutlinedButton(
                    onClick = { vm.connect(host.trim(), port.toIntOrNull() ?: 23) },
                    enabled = host.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("连接")
                }
            }
            StatusChip(
                when (state) {
                    TelnetSession.State.CONNECTED -> "已连接"
                    TelnetSession.State.CONNECTING -> "连接中"
                    TelnetSession.State.CLOSED -> "已断开"
                    TelnetSession.State.ERROR -> "错误"
                    null -> "未连接"
                },
                when (state) {
                    TelnetSession.State.CONNECTED -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
                    TelnetSession.State.CONNECTING -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        ErrorBanner(error)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            item {
                SelectionContainer {
                    Column {
                        lines.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("输入命令") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    vm.send(input)
                    input = ""
                },
                enabled = state == TelnetSession.State.CONNECTED && input.isNotBlank()
            ) {
                Text("发送")
            }
        }
    }
}