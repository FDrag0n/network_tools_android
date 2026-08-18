package com.networktools.opencode.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicInteger

object IpScanEngine {

    data class Range(
        val networkLong: Long,
        val broadcastLong: Long,
        val prefix: Int,
        val total: Long
    )

    data class Host(
        val ip: String,
        val timeMs: Double,
        val alive: Boolean,
        val ttl: Int? = null
    )

    fun parseCidr(text: String): Range {
        val trimmed = text.trim()
        val slash = trimmed.lastIndexOf('/')
        if (slash <= 0) throw NetworkToolException("CIDR 格式无效，示例：192.168.1.0/24")
        val ipText = trimmed.substring(0, slash)
        val prefix = trimmed.substring(slash + 1).toIntOrNull()
            ?: throw NetworkToolException("CIDR 前缀无效")
        if (prefix !in 0..32) throw NetworkToolException("IPv4 前缀须在 0~32 之间")
        val ip = IpCalculator.parseV4(ipText)
        val ipLong = v4ToLong(ip)
        val maskLong = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val network = ipLong and maskLong
        val broadcast = network or (maskLong.inv() and 0xFFFFFFFFL)
        val total = 1L shl (32 - prefix)
        if (total > 65536) throw NetworkToolException("扫描范围过大（${total} 个地址），请使用更小的网段")
        return Range(network, broadcast, prefix, total)
    }

    suspend fun scan(
        range: Range,
        concurrency: Int,
        onHost: suspend (Host) -> Unit,
        onProgress: suspend (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        val hosts = mutableListOf<Long>()
        var start = range.networkLong
        val end = range.broadcastLong
        if (range.total == 1L) hosts.add(start) else {
            while (start <= end) {
                hosts.add(start)
                start++
            }
        }

        val semaphore = Semaphore(concurrency.coerceAtLeast(1))
        val scanned = AtomicInteger(0)
        val total = hosts.size

        withContext(Dispatchers.IO) {
            hosts.map { ipLong ->
                async {
                    semaphore.withPermit {
                        val ip = longToV4(ipLong)
                        val result = probe(ip)
                        val n = scanned.incrementAndGet()
                        onHost(result)
                        onProgress(n, total)
                        result
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun probe(ip: String): Host {
        val start = System.nanoTime()
        var gotReply = false
        var rttMs: Double? = null
        var ttl: Int? = null
        try {
            val command = listOf("ping", "-c", "1", "-w", "2", ip)
            CommandRunner.run(command) { line ->
                val trimmed = line.trim()
                if (trimmed.contains("bytes from") && trimmed.contains("icmp_seq") &&
                    !trimmed.contains("ttl exceeded") && !trimmed.contains("Time to live")
                ) {
                    gotReply = true
                    Regex("""time=([\d.]+)""").find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull()
                        ?.let { rttMs = it }
                    Regex("""ttl=(\d+)""").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
                        ?.let { ttl = it }
                }
            }
        } catch (e: Exception) {
            // 忽略单个主机的探测异常
        }
        val timeMs = rttMs ?: ((System.nanoTime() - start) / 1_000_000.0).coerceAtMost(2000.0)
        return Host(ip, timeMs, gotReply, ttl)
    }

    fun detectLocalSubnet(): String? {
        var best: Pair<String, Int>? = null
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp || ni.isLoopback || ni.isVirtual) return@forEach
                val addr = ni.inetAddresses?.toList()?.filterIsInstance<Inet4Address>()?.firstOrNull()
                    ?: return@forEach
                if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress) {
                    val prefix = ni.interfaceAddresses
                        ?.filter { it.address == addr }
                        ?.firstOrNull()
                        ?.networkPrefixLength
                    if (prefix != null) {
                        val p = prefix.toInt()
                        if (p in 1..30) {
                            val candidate = "${addr.hostAddress}/$p"
                            val current = best
                            if (current == null || p > current.second) {
                                best = candidate to p
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return best?.first
    }

    private fun v4ToLong(b: ByteArray): Long {
        var v = 0L
        for (i in 0 until 4) v = (v shl 8) or ((b[i].toInt() and 0xFF).toLong())
        return v
    }

    private fun longToV4(v: Long): String =
        "${(v ushr 24) and 0xFF}.${(v ushr 16) and 0xFF}.${(v ushr 8) and 0xFF}.${v and 0xFF}"
}