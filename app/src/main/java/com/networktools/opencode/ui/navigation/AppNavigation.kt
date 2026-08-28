package com.networktools.opencode.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.networktools.opencode.ui.screens.BandwidthScreen
import com.networktools.opencode.ui.screens.DnsScreen
import com.networktools.opencode.ui.screens.HomeScreen
import com.networktools.opencode.ui.screens.HttpScreen
import com.networktools.opencode.ui.screens.IpCalcScreen
import com.networktools.opencode.ui.screens.IpScanScreen
import com.networktools.opencode.ui.screens.LanScanScreen
import com.networktools.opencode.ui.screens.MtrScreen
import com.networktools.opencode.ui.screens.PingScreen
import com.networktools.opencode.ui.screens.PortScanScreen
import com.networktools.opencode.ui.screens.PublicIpScreen
import com.networktools.opencode.ui.screens.TcpPingScreen
import com.networktools.opencode.ui.screens.TelnetScreen
import com.networktools.opencode.ui.screens.TracerouteScreen

enum class ToolCategory(val label: String) {
    CONNECTIVITY("连通性测试"),
    SCAN("网络扫描"),
    DNS("DNS"),
    SERVICE("服务工具"),
    CALC("计算工具")
}

enum class ToolScreen(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val category: ToolCategory
) {
    PING("ping", "Ping 检测", "ICMP 连通性与延迟测试", Icons.Outlined.GpsFixed, ToolCategory.CONNECTIVITY),
    TCPING("tcping", "TCPing", "TCP 端口连通性与延迟", Icons.Outlined.Timeline, ToolCategory.CONNECTIVITY),
    MTR("mtr", "MTR", "持续逐跳链路质量分析", Icons.Outlined.GraphicEq, ToolCategory.CONNECTIVITY),
    TRACEROUTE("traceroute", "路由追踪", "逐跳分析数据包路径", Icons.Outlined.Route, ToolCategory.CONNECTIVITY),
    PORT_SCAN("portscan", "端口扫描", "TCP 端口开放状态检测", Icons.Outlined.Radar, ToolCategory.SCAN),
    IP_SCAN("ipscan", "IP 段扫描", "CIDR 网段主机存活检测", Icons.Outlined.GridOn, ToolCategory.SCAN),
    LAN_SCAN("lanscan", "局域网扫描", "自动探测子网并扫描设备", Icons.Outlined.Hub, ToolCategory.SCAN),
    DNS("dns", "DNS 解析", "查询 A / AAAA / MX 等记录", Icons.Outlined.Dns, ToolCategory.DNS),
    HTTP("http", "HTTP 工具", "GET / POST / PUT 等请求", Icons.Outlined.Http, ToolCategory.SERVICE),
    TELNET("telnet", "Telnet", "文本交互式终端连接", Icons.Outlined.Terminal, ToolCategory.SERVICE),
    BANDWIDTH("bandwidth", "带宽测试", "iperf3 协议测速", Icons.Outlined.Speed, ToolCategory.SERVICE),
    PUBLIC_IP("publicip", "公网 IP", "查看 IPv4 / IPv6 及归属地", Icons.Outlined.Language, ToolCategory.SERVICE),
    IP_CALC("ipcalc", "IP 计算器", "IPv4 / IPv6 子网地址计算", Icons.Outlined.Calculate, ToolCategory.CALC)
}

private const val HOME_ROUTE = "home"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentScreen = ToolScreen.entries.find { it.route == currentRoute }
    val isHome = currentRoute == HOME_ROUTE

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isHome) "网络工具箱" else (currentScreen?.title ?: ""),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (!isHome) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HOME_ROUTE,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(HOME_ROUTE) {
                HomeScreen(onOpen = { route -> navController.navigate(route) })
            }
            composable(ToolScreen.PING.route) { PingScreen() }
            composable(ToolScreen.TCPING.route) { TcpPingScreen() }
            composable(ToolScreen.MTR.route) { MtrScreen() }
            composable(ToolScreen.TRACEROUTE.route) { TracerouteScreen() }
            composable(ToolScreen.PORT_SCAN.route) { PortScanScreen() }
            composable(ToolScreen.IP_SCAN.route) { IpScanScreen() }
            composable(ToolScreen.LAN_SCAN.route) { LanScanScreen() }
            composable(ToolScreen.DNS.route) { DnsScreen() }
            composable(ToolScreen.HTTP.route) { HttpScreen() }
            composable(ToolScreen.TELNET.route) { TelnetScreen() }
            composable(ToolScreen.BANDWIDTH.route) { BandwidthScreen() }
            composable(ToolScreen.PUBLIC_IP.route) { PublicIpScreen() }
            composable(ToolScreen.IP_CALC.route) { IpCalcScreen() }
        }
    }
}