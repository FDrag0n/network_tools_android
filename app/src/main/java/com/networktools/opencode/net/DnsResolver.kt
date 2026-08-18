package com.networktools.opencode.net

import org.xbill.DNS.Lookup
import org.xbill.DNS.MXRecord
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.Type
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

object DnsResolver {

    data class RecordRow(val name: String, val type: String, val ttl: Long, val value: String)

    fun lookup(
        host: String,
        type: Int,
        server: String?
    ): List<RecordRow> {
        val targetName = if (type == Type.PTR) toReverseName(host) else normalize(host)
        val lookup = Lookup(targetName, type)
        val resolver = server?.takeIf { it.isNotBlank() }?.let {
            try {
                SimpleResolver(it)
            } catch (e: UnknownHostException) {
                throw NetworkToolException("DNS 服务器地址无效: $it")
            }
        } ?: SimpleResolver("8.8.8.8")
        resolver.setTimeout(5)
        lookup.setResolver(resolver)

        val records = lookup.run()
        if (records == null) {
            throw NetworkToolException("查询失败: ${lookup.errorString ?: "无结果"}")
        }
        return records.map { it.toRow() }
    }

    private val ALL_TYPES = listOf(Type.A, Type.AAAA, Type.CNAME, Type.MX, Type.TXT, Type.NS, Type.SOA, Type.SRV)

    fun lookupAll(
        host: String,
        server: String?
    ): List<RecordRow> {
        val all = mutableListOf<RecordRow>()
        var firstError: Exception? = null
        for (type in ALL_TYPES) {
            try {
                all += lookup(host, type, server)
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        if (all.isEmpty()) {
            throw NetworkToolException(firstError?.message ?: "无任何记录")
        }
        return all
    }

    fun systemResolve(host: String): Map<String, List<String>> {        val all = InetAddress.getAllByName(host).toList()
        val v4 = all.filterIsInstance<Inet4Address>()
        val v6 = all.filterIsInstance<Inet6Address>()
        return buildMap {
            if (v4.isNotEmpty()) put("IPv4", v4.map { it.hostAddress })
            if (v6.isNotEmpty()) put("IPv6", v6.map { it.hostAddress })
            if (v4.isEmpty() && v6.isEmpty()) put("结果", listOf("无法解析"))
        }
    }

    private fun Record.toRow(): RecordRow {
        val value = when (this) {
            is MXRecord -> "优先级 ${priority}  服务器 ${target}"
            is SRVRecord -> "优先级 $priority  权重 $weight  端口 $port  目标 $target"
            else -> rdataToString()
        }
        return RecordRow(name.toString(true), Type.string(type), ttl, value)
    }

    fun typeName(type: Int): String = Type.string(type)

    private fun normalize(host: String): String {
        val h = host.trim()
        if (h.startsWith("http://")) return h.removePrefix("http://")
        if (h.startsWith("https://")) return h.removePrefix("https://")
        return h
    }

    fun toReverseName(ipOrHost: String): String {
        val ip = ipOrHost.trim()
        if (ip.contains(':')) {
            val bytes = InetAddress.getByName(ip).address
            val sb = StringBuilder()
            for (i in bytes.indices.reversed()) {
                val b = bytes[i].toInt() and 0xFF
                sb.append(Integer.toHexString(b and 0x0F)).append('.').append(Integer.toHexString(b ushr 4)).append('.')
            }
            return sb.append("ip6.arpa").toString()
        }
        val parts = ip.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
            return parts.reversed().joinToString(".") + ".in-addr.arpa"
        }
        return ip
    }
}