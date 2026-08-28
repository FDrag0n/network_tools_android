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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.networktools.opencode.net.PublicIpResolver
import com.networktools.opencode.ui.components.ErrorBanner
import com.networktools.opencode.ui.components.LabelValueRow
import com.networktools.opencode.vm.PublicIpViewModel

@Composable
fun PublicIpScreen(vm: PublicIpViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        if (!state.hasLoaded && !state.loading) vm.refresh()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "公网出口 IP",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(
                    onClick = { if (state.loading) vm.stop() else vm.refresh() },
                    enabled = true
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.loading) "停止" else "刷新")
                }
            }
        }

        Text(
            "分别通过 api-ipv4.ip.sb 与 api-ipv6.ip.sb 获取，归属地来自 api.ip.sb/geoip/{ip}。需自定义 User-Agent。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        IpCard(
            title = "IPv4",
            ip = state.ipv4,
            geo = state.ipv4Geo,
            error = state.ipv4Error,
            loading = state.loading && !state.hasLoaded
        )

        IpCard(
            title = "IPv6",
            ip = state.ipv6,
            geo = state.ipv6Geo,
            error = state.ipv6Error,
            loading = state.loading && !state.hasLoaded
        )

        if (state.ipv4 != null || state.ipv6 != null) {
            val allText = buildString {
                state.ipv4?.let { append("IPv4: $it\n") }
                state.ipv6?.let { append("IPv6: $it\n") }
                state.ipv4Geo?.let { append("\n[IPv4 Geo]\n${geoToText(it)}\n") }
                state.ipv6Geo?.let { append("\n[IPv6 Geo]\n${geoToText(it)}\n") }
            }
            val clipboard = LocalClipboardManager.current
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(allText.trim())) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("复制全部")
            }
        }
    }
}

@Composable
private fun IpCard(
    title: String,
    ip: String?,
    geo: PublicIpResolver.GeoInfo?,
    error: String?,
    loading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (ip != null) {
                    val clipboard = LocalClipboardManager.current
                    IconButton(onClick = { clipboard.setText(AnnotatedString(ip)) }) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "复制",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            when {
                loading && ip == null && error == null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("查询中…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error != null && ip == null -> {
                    ErrorBanner(error)
                    if (title == "IPv6" && error.isNotEmpty()) {
                        Text(
                            "提示：无 IPv6 地址通常表示当前网络不支持 IPv6。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ip != null -> {
                    SelectionContainer {
                        Text(
                            ip,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (geo != null) {
                        Spacer(Modifier.height(4.dp))
                        GeoDetails(geo)
                    } else {
                        Text(
                            "归属地信息暂不可用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    error?.let { ErrorBanner(it) }
                }
                else -> {
                    Text("暂无数据", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GeoDetails(geo: PublicIpResolver.GeoInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        geo.country?.let { LabelValueRow("国家", formatCountry(geo)) }
        geo.region?.let { LabelValueRow("地区", listOfNotNull(geo.region, geo.regionCode).joinToString(" ")) }
        geo.city?.let { LabelValueRow("城市", it) }
        geo.postalCode?.let { LabelValueRow("邮编", it) }
        if (geo.latitude != null && geo.longitude != null) {
            LabelValueRow("经纬度", "${geo.latitude}, ${geo.longitude}")
        }
        geo.timezone?.let { LabelValueRow("时区", it + (geo.offset?.let { o -> " (UTC${if (o >= 0) "+" else ""}${o / 3600})" } ?: "")) }
        geo.asn?.let { LabelValueRow("ASN", "AS$it ${geo.asnOrganization ?: ""}".trim()) }
        geo.isp?.let { LabelValueRow("ISP", it) }
        geo.organization?.let { if (it != geo.isp) LabelValueRow("组织", it) }
        geo.continentCode?.let { LabelValueRow("大洲", it) }
    }
}

private fun formatCountry(geo: PublicIpResolver.GeoInfo): String {
    return listOfNotNull(geo.country, geo.countryCode?.let { "($it)" }).joinToString(" ")
}

private fun geoToText(g: PublicIpResolver.GeoInfo): String = buildString {
    append("ip=${g.ip}\n")
    g.country?.let { append("country=$it ${g.countryCode ?: ""}\n") }
    g.region?.let { append("region=$it ${g.regionCode ?: ""}\n") }
    g.city?.let { append("city=$it\n") }
    g.latitude?.let { append("lat=$it lon=${g.longitude}\n") }
    g.timezone?.let { append("timezone=$it offset=${g.offset}\n") }
    g.asn?.let { append("asn=AS$it ${g.asnOrganization ?: ""}\n") }
    g.isp?.let { append("isp=$it\n") }
    g.organization?.let { append("org=$it\n") }
}
