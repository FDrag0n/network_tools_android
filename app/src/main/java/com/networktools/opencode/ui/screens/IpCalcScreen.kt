package com.networktools.opencode.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.LabelValueRow
import com.networktools.opencode.vm.CalcResult
import com.networktools.opencode.vm.IpCalcViewModel

@Composable
fun IpCalcScreen(vm: IpCalcViewModel = viewModel()) {
    var mode by rememberSaveable { mutableIntStateOf(4) }
    var ip by rememberSaveable { mutableStateOf("") }
    var prefix by rememberSaveable { mutableStateOf("24") }

    val result by vm.result.collectAsState()
    val error by vm.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = mode == 4, onClick = { mode = 4 }, label = { Text("IPv4") })
            FilterChip(selected = mode == 6, onClick = { mode = 6 }, label = { Text("IPv6") })
        }
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text(if (mode == 4) "IPv4 地址" else "IPv6 地址") },
            placeholder = { Text(if (mode == 4) "例如 192.168.1.15" else "例如 2001:db8::1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = prefix,
            onValueChange = {
                if (it.length <= 3 && (it.all { c -> c.isDigit() || c == '/' })) {
                    prefix = it.replace("/", "")
                }
            },
            label = { Text(if (mode == 4) "前缀长度或子网掩码" else "前缀长度") },
            placeholder = { Text(if (mode == 4) "例如 24 或 255.255.255.0" else "例如 64") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        androidx.compose.material3.Button(
            onClick = {
                if (mode == 4) vm.calculateV4(ip.trim(), prefix.trim())
                else vm.calculateV6(ip.trim(), prefix.toIntOrNull() ?: 64)
            },
            enabled = ip.isNotBlank() && prefix.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("计算")
        }
        ErrorBanner(error)
        val rows = when (val r = result) {
            is CalcResult.V4 -> r.rows
            is CalcResult.V6 -> r.rows
            null -> emptyList()
        }
        rows.forEach { row -> LabelValueRow(row.label, row.value) }
        if (rows.isNotEmpty()) {
            val text = rows.joinToString("\n") { "${it.label}: ${it.value}" }
            CopyResultsButton(text)
        }
        Spacer(Modifier.height(24.dp))
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