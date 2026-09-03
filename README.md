# Network Tools Android（网络工具箱）

一款 Android 网络诊断工具箱，基于 Jetpack Compose + Material 3 构建，支持 IPv4 / IPv6。

## 功能

### 连通性测试
- **Ping 检测**：ICMP 连通性与延迟测试
- **TCPing**：TCP 端口连通性与延迟
- **MTR**：持续逐跳链路质量分析
- **路由追踪**：逐跳分析数据包路径
- **Telnet**：文本交互式终端连接（支持 banner 抓取）

### 扫描与发现
- **端口扫描**：TCP 端口开放状态检测
- **IP 段扫描**：CIDR 网段 ICMP 主机发现（含 TTL / 延迟解析）
- **局域网扫描**：自动探测本机网段，ICMP 发现设备，结合 ARP 获取 MAC 与厂商、TTL 推断系统、反向 DNS 解析主机名，并扫描常见端口与抓取服务指纹

### DNS 与 Web
- **DNS 解析**：查询 A / AAAA / MX 等记录
- **HTTP 工具**：GET / POST / PUT 等请求调试
- **公网 IP**：查看 IPv4 / IPv6 公网出口地址及归属地（国家 / 城市 / 经纬度 / ASN / ISP），基于 `https://ip.sb/api/`（`api-ipv4.ip.sb/ip`、`api-ipv6.ip.sb/ip`、`api.ip.sb/geoip/{ip}`）

### 带宽与计算
- **iperf3**：基于 iperf3 协议的 TCP 吞吐量测试（客户端，支持上传 / 下载）
- **IP 计算器**：IPv4 / IPv6 子网地址计算

## 版本

- **1.1 (versionCode 2)**：新增公网 IP（IPv4/IPv6 + GeoIP）
- **1.0 (versionCode 1)**：首版，含 Ping/TCPing/MTR/路由追踪/端口与网段扫描/局域网扫描/DNS/HTTP/Telnet/iperf3/IP 计算器

## 构建

```bash
./gradlew :app:assembleDebug   # 调试包
./gradlew :app:assembleRelease  # 正式包（需 keystore.jks，已提交固定签名，升级签名一致）
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk` / `app/build/outputs/apk/release/app-release.apk`

GitHub 发布 Release 时自动构建并上传签名 APK（`.github/workflows/release-apk.yml`）。

## 技术要点

- 纯 Kotlin + Jetpack Compose，无第三方网络库依赖
- 系统命令（ping / traceroute 等）通过 `ProcessBuilder` 调用
- iperf3 客户端按真实控制协议实现（Cookie 握手 + 二进制 JSON 帧）
- 局域网扫描的 ARP 读取采用多源 best-effort（`/proc/net/arp`、`ip neigh`、本机接口 MAC）