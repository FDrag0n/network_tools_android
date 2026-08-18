package com.networktools.opencode.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

object LanScanEngine {

    data class PortInfo(
        val port: Int,
        val service: String,
        val fingerprint: String? = null
    )

    data class Device(
        val ip: String,
        val timeMs: Double,
        val ttl: Int?,
        val mac: String?,
        val vendor: String?,
        val osHint: String?,
        val hostname: String?,
        val ports: List<PortInfo>
    )

    private val commonPorts: List<Pair<Int, String>> = listOf(
        21 to "FTP",
        22 to "SSH",
        23 to "Telnet",
        25 to "SMTP",
        53 to "DNS",
        80 to "HTTP",
        110 to "POP3",
        139 to "NetBIOS",
        143 to "IMAP",
        443 to "HTTPS",
        445 to "SMB",
        993 to "IMAPS",
        995 to "POP3S",
        3306 to "MySQL",
        3389 to "RDP",
        5432 to "PostgreSQL",
        5900 to "VNC",
        6379 to "Redis",
        8080 to "HTTP-Proxy",
        5201 to "iperf3",
        5555 to "ADB",
        27017 to "MongoDB"
    )

    private val httpPorts = setOf(80, 443, 8080)

    suspend fun scan(
        range: IpScanEngine.Range,
        concurrency: Int,
        onDevice: suspend (Device) -> Unit,
        onProgress: suspend (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        val alive = java.util.Collections.synchronizedList(mutableListOf<IpScanEngine.Host>())

        IpScanEngine.scan(
            range = range,
            concurrency = concurrency,
            onHost = { h -> if (h.alive) alive.add(h) },
            onProgress = onProgress
        )

        val arpCache = readArpTable()
        val sortedAlive = alive.sortedBy { it.ip }
        val semaphore = Semaphore((concurrency / 2).coerceAtLeast(2))
        val portConcurrency = (concurrency / 2).coerceAtLeast(4)

        withContext(Dispatchers.IO) {
            sortedAlive
                .map { host ->
                    async {
                        semaphore.withPermit {
                            val device = enrich(host, arpCache, portConcurrency)
                            onDevice(device)
                            device
                        }
                    }
                }
                .awaitAll()
        }
    }

    private fun enrich(
        host: IpScanEngine.Host,
        arpCache: Map<String, String>,
        portConcurrency: Int
    ): Device {
        val mac = arpCache[host.ip] ?: ownMac(host.ip)
        val vendor = mac?.let { VendorDb.lookup(it) }
        val osHint = osHintFromTtl(host.ttl)
        val hostname = reverseDns(host.ip)
        val ports = scanPorts(host.ip, portConcurrency)
        return Device(host.ip, host.timeMs, host.ttl, mac, vendor, osHint, hostname, ports)
    }

    private fun ownMac(ip: String): String? {
        return try {
            val addr = InetAddress.getByName(ip)
            NetworkInterface.getByInetAddress(addr)?.hardwareAddress
                ?.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun readArpTable(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val lines = File("/proc/net/arp").readLines()
            for (i in 1 until lines.size) {
                val parts = lines[i].trim().split(Regex("\\s+"))
                if (parts.size >= 4 && parts[1] == "0x1") {
                    val mac = parts[3]
                    if (mac != "00:00:00:00:00:00" && mac.matches(Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}"))) {
                        result[parts[0]] = mac.uppercase()
                    }
                }
            }
        } catch (_: Exception) {
        }
        if (result.isEmpty()) {
            try {
                val process = ProcessBuilder("ip", "neigh", "show").redirectErrorStream(true).start()
                process.inputStream.bufferedReader(Charsets.UTF_8).readText().lineSequence().forEach { line ->
                    val m = Regex("""^(\S+)\s+dev\s+\S+\s+lladdr\s+([0-9A-Fa-f:]{17})""")
                        .find(line.trim())
                    if (m != null) {
                        val mac = m.groupValues[2].uppercase()
                        if (mac != "00:00:00:00:00:00") result[m.groupValues[1]] = mac
                    }
                }
                process.waitFor()
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun osHintFromTtl(ttl: Int?): String? = when {
        ttl == null -> null
        ttl == 255 -> "网络设备 / 网关"
        ttl in 129..254 -> "Unix / 网络设备"
        ttl == 128 -> "Windows"
        ttl in 65..127 -> "Unix / 网络设备"
        ttl in 1..64 -> "Linux / Android / macOS"
        else -> "未知"
    }

    private fun reverseDns(ip: String): String? {
        val future = java.util.concurrent.Executors.newSingleThreadExecutor().submit<String> {
            try {
                val address = InetAddress.getByName(ip)
                val name = address.hostName
                if (name == ip) null else name
            } catch (_: Exception) {
                null
            }
        }
        return try {
            future.get(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            future.cancel(true)
            null
        } finally {
            future.cancel(true)
        }
    }

    private fun scanPorts(ip: String, concurrency: Int): List<PortInfo> {
        val open = java.util.Collections.synchronizedList(mutableListOf<PortInfo>())
        val semaphore = java.util.concurrent.Semaphore(concurrency.coerceAtLeast(1))
        val threads = commonPorts.map { (port, service) ->
            Thread {
                semaphore.acquire()
                try {
                    probePort(ip, port, service)?.let { open.add(it) }
                } finally {
                    semaphore.release()
                }
            }.apply { start() }
        }
        threads.forEach { it.join() }
        return open.sortedBy { it.port }
    }

    private fun probePort(ip: String, port: Int, service: String): PortInfo? {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(ip, port), 800)
            val fingerprint = if (port in httpPorts) grabHttp(socket) else grabBanner(socket)
            PortInfo(port, service, fingerprint)
        } catch (_: Exception) {
            null
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun grabBanner(socket: Socket): String? {
        return try {
            socket.soTimeout = 600
            val buf = ByteArray(256)
            val n = socket.getInputStream().read(buf)
            if (n > 0) sanitize(String(buf, 0, n)) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun grabHttp(socket: Socket): String? {
        return try {
            socket.soTimeout = 800
            val out = socket.getOutputStream()
            out.write("HEAD / HTTP/1.0\r\n\r\n".toByteArray())
            out.flush()
            val buf = ByteArray(512)
            val n = socket.getInputStream().read(buf)
            if (n <= 0) return null
            val text = String(buf, 0, n)
            val server = Regex("""(?i)^Server:\s*(.+)\s*$""")
                .find(text)?.groupValues?.get(1)?.trim()
            when {
                server != null -> "HTTP Server: $server"
                text.startsWith("HTTP/") -> text.lineSequence().first().trim()
                else -> sanitize(text)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitize(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw) {
            if (ch.isISOControl()) {
                if (ch == '\n') sb.append(' ') else continue
            } else {
                sb.append(ch)
            }
            if (sb.length >= 80) break
        }
        return sb.toString().trim()
    }
}

object VendorDb {

    private val map = buildMap {
        // Apple
        put("000393", "Apple"); put("0017F2", "Apple"); put("001B63", "Apple")
        put("001CB3", "Apple"); put("002500", "Apple"); put("002608", "Apple")
        put("0026BB", "Apple"); put("0050E4", "Apple"); put("A483E7", "Apple")
        put("A82066", "Apple"); put("B8E856", "Apple"); put("F4F15A", "Apple")
        // Samsung
        put("0000F0", "Samsung"); put("001599", "Samsung"); put("001E7E", "Samsung")
        put("0025FB", "Samsung"); put("0050F2", "Samsung"); put("A45E60", "Samsung")
        put("2884B4", "Samsung"); put("64CBB1", "Samsung")
        put("8836C6", "Samsung")
        // Huawei
        put("005056", "Huawei"); put("4846FB", "Huawei"); put("5CB43C", "Huawei")
        put("78D752", "Huawei"); put("A46CB1", "Huawei"); put("D8C7C8", "Huawei")
        put("E08FEC", "Huawei"); put("F42C1B", "Huawei")
        // Xiaomi
        put("640980", "Xiaomi"); put("98FAE3", "Xiaomi"); put("A48873", "Xiaomi")
        put("ACDB48", "Xiaomi"); put("B04E26", "Xiaomi"); put("D02B20", "Xiaomi")
        put("F0B429", "Xiaomi"); put("F8CB7E", "Xiaomi")
        // TP-Link
        put("001D0F", "TP-Link"); put("40167E", "TP-Link"); put("50642B", "TP-Link")
        put("54E6FC", "TP-Link"); put("6032B1", "TP-Link"); put("687251", "TP-Link")
        put("7CD95C", "TP-Link"); put("A06391", "TP-Link"); put("B0487A", "TP-Link")
        put("C04A00", "TP-Link"); put("C83A35", "TP-Link"); put("CC32E5", "TP-Link")
        put("D807B6", "TP-Link"); put("E0A496", "TP-Link"); put("E848B8", "TP-Link")
        put("F4EC38", "TP-Link"); put("F8659B", "TP-Link")
        // Intel
        put("0013E8", "Intel"); put("001E67", "Intel"); put("0026C7", "Intel")
        put("28923A", "Intel"); put("3CA6F6", "Intel"); put("40B076", "Intel")
        put("58605F", "Intel"); put("5CF938", "Intel"); put("6805CA", "Intel")
        put("6C626E", "Intel"); put("74DFBF", "Intel"); put("78E3B5", "Intel")
        put("8C705A", "Intel"); put("A0369F", "Intel"); put("B49691", "Intel")
        put("E03E2C", "Intel")
        // Realtek
        put("00E04C", "Realtek"); put("2C56DC", "Realtek"); put("40169F", "Realtek")
        put("508A9F", "Realtek"); put("705A0F", "Realtek"); put("94DE80", "Realtek")
        put("A4BADB", "Realtek"); put("B46BFC", "Realtek")
        // Cisco
        put("00000C", "Cisco"); put("0001C9", "Cisco"); put("00025B", "Cisco")
        put("00062A", "Cisco"); put("001179", "Cisco"); put("00137F", "Cisco")
        put("0016C7", "Cisco"); put("001C57", "Cisco"); put("00245C", "Cisco")
        put("00E0B0", "D-Link"); put("F0C26C", "Cisco"); put("F8A9D0", "Cisco")
        // D-Link
        put("000D88", "D-Link"); put("0013A8", "D-Link"); put("0015E9", "D-Link")
        put("0016CF", "D-Link"); put("001B11", "D-Link"); put("001CF0", "D-Link")
        put("001E58", "D-Link"); put("002191", "D-Link"); put("002354", "D-Link")
        put("00265A", "D-Link"); put("28107B", "D-Link"); put("9CD21B", "D-Link")
        put("D83062", "D-Link"); put("F07BCB", "D-Link")
        // Netgear
        put("001F33", "Netgear"); put("00223F", "Netgear"); put("002331", "Netgear")
        put("0024B2", "Netgear"); put("00259C", "Netgear"); put("0026F2", "Netgear")
        put("284C8E", "Netgear"); put("7CD1C3", "Netgear"); put("A440A0", "Netgear")
        put("C03F0E", "Netgear"); put("D03972", "Netgear"); put("F07F06", "Netgear")
        // Asus
        put("002275", "Asus"); put("0023CB", "Asus"); put("00248C", "Asus")
        put("00260E", "Asus"); put("08606E", "Asus"); put("14CC20", "Asus")
        put("284C8F", "Asus"); put("50645D", "Asus"); put("6459F8", "Asus")
        put("8CB84A", "Asus"); put("A0B4A5", "Asus"); put("B0A7B9", "Asus")
        put("B8AEED", "Asus"); put("D42B2A", "Asus"); put("E03F49", "Asus")
        put("F832E4", "Asus")
        // Google
        put("00037A", "Google"); put("0016E6", "Google"); put("001C7F", "Google")
        put("00216C", "Google"); put("00268B", "Google"); put("00F48C", "Google")
        put("1062E5", "Google"); put("14178F", "Google")
        put("305A3A", "Google"); put("68F173", "Google")
        put("94C691", "Google"); put("AC7A4D", "Google"); put("D4F36A", "Google")
        // Amazon
        put("00A0C5", "Amazon"); put("44F459", "Amazon"); put("50DC8C", "Amazon")
        put("AC63BE", "Amazon"); put("B82A72", "Amazon"); put("FC65DE", "Amazon")
        // Microsoft / Xbox
        put("0011D8", "Microsoft"); put("0019F5", "Microsoft"); put("002248", "Microsoft")
        put("00400D", "Microsoft"); put("78A2A0", "Microsoft")
        put("C8D2C1", "Microsoft")
        // Sony
        put("00043E", "Sony"); put("002621", "Sony"); put("000D5B", "Sony")
        put("7845C4", "Sony"); put("BC96A6", "Sony")
        // Lenovo
        put("0003C9", "Lenovo"); put("00163F", "Lenovo"); put("001D60", "Lenovo")
        put("00215F", "Lenovo"); put("708BCD", "Lenovo"); put("F4C7AA", "Lenovo")
        // HP
        put("001122", "HP"); put("001635", "HP"); put("001D4B", "HP")
        put("0024D7", "HP"); put("1C8F01", "HP"); put("48D705", "HP")
        // Dell
        put("001DD8", "Dell"); put("00266A", "Dell")
        put("F8BC12", "Dell"); put("ECF4BB", "Dell")
        // Raspberry Pi
        put("B827EB", "Raspberry Pi"); put("DCA632", "Raspberry Pi"); put("E45F01", "Raspberry Pi")
        // QEMU / 虚拟化
        put("525400", "QEMU / 虚拟化"); put("0E0000", "Xerox / 虚拟化")
        // 小米 / 其余常见
        put("04CF8C", "ZTE"); put("2CE05F", "爱快 / iKuai"); put("00187B", "MikroTik")
        put("48A9D2", "MikroTik"); put("DCA6BD", "SONOFF / ITEAD")
    }

    fun lookup(mac: String): String? {
        val compact = mac.replace(":", "").uppercase().take(6)
        if (compact.length < 6) return null
        return map[compact] ?: "未知厂商"
    }
}