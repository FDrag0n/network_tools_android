package com.networktools.opencode.net

import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object IpCalculator {

    data class V4Result(
        val ip: String,
        val netmask: String,
        val prefix: Int,
        val wildcard: String,
        val network: String,
        val broadcast: String,
        val firstHost: String,
        val lastHost: String,
        val hostCount: Long,
        val usableHosts: Long,
        val ipBinary: String,
        val maskBinary: String,
        val networkBinary: String,
        val broadcastBinary: String
    )

    data class V6Result(
        val network: String,
        val prefix: Int,
        val end: String,
        val totalAddresses: String,
        val usable64Subnets: String,
        val addressBinary: String,
        val maskBinary: String,
        val networkBinary: String,
        val allSubnetsOfPrefixMinus8: String
    )

    fun calculateV4(ipText: String, prefixOrMask: String): V4Result {
        val ip = parseV4(ipText)
        val prefix = if (prefixOrMask.contains('.')) {
            v4MaskToPrefix(parseV4(prefixOrMask))
        } else {
            prefixOrMask.trim().toIntOrNull()
                ?: throw NetworkToolException("前缀长度无效")
        }
        if (prefix !in 0..32) throw NetworkToolException("IPv4 前缀长度须在 0~32 之间")

        val ipLong = v4ToLong(ip)
        val maskLong = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val networkLong = ipLong and maskLong
        val broadcastLong = networkLong or (maskLong.inv() and 0xFFFFFFFFL)
        val hostCount = 1L shl (32 - prefix)
        val usable = if (prefix >= 31) 0L else (hostCount - 2)

        return V4Result(
            ip = longToV4(ipLong),
            netmask = longToV4(maskLong),
            prefix = prefix,
            wildcard = longToV4(maskLong.inv() and 0xFFFFFFFFL),
            network = longToV4(networkLong),
            broadcast = longToV4(broadcastLong),
            firstHost = if (usable > 0) longToV4(networkLong + 1) else "—",
            lastHost = if (usable > 0) longToV4(broadcastLong - 1) else "—",
            hostCount = hostCount,
            usableHosts = usable,
            ipBinary = formatBinary(ipLong, 32),
            maskBinary = formatBinary(maskLong, 32),
            networkBinary = formatBinary(networkLong, 32),
            broadcastBinary = formatBinary(broadcastLong, 32)
        )
    }

    fun calculateV6(ipText: String, prefix: Int): V6Result {
        val ip = parseV6(ipText)
        if (prefix !in 0..128) throw NetworkToolException("IPv6 前缀长度须在 0~128 之间")

        val base = BigInteger(1, ip)
        val mask = maskV6(prefix)
        val network = base.and(mask)
        val rangeSize = if (prefix == 128) BigInteger.ONE else BigInteger.ONE.shiftLeft(128 - prefix)
        val end = network.add(rangeSize.subtract(BigInteger.ONE))

        val total = rangeSize.toString()
        val subnets64 = if (prefix <= 64) {
            BigInteger.ONE.shiftLeft(64 - prefix).toString() + " 个 /64 子网"
        } else {
            "无（前缀大于 /64）"
        }

        return V6Result(
            network = bigIntegerToV6(network),
            prefix = prefix,
            end = bigIntegerToV6(end),
            totalAddresses = total,
            usable64Subnets = subnets64,
            addressBinary = binaryV6(base),
            maskBinary = binaryV6(mask),
            networkBinary = binaryV6(network),
            allSubnetsOfPrefixMinus8 = if (prefix >= 8) {
                "该 /$prefix 范围内共有 ${BigInteger.ONE.shiftLeft(128 - prefix).toString()} 个地址"
            } else {
                "—"
            }
        )
    }

    private fun maskV6(prefix: Int): BigInteger {
        if (prefix == 128) return MAX_V6
        return (BigInteger.ONE.shiftLeft(prefix)).subtract(BigInteger.ONE).shiftLeft(128 - prefix)
    }

    fun parseV4(text: String): ByteArray {
        val parts = text.trim().split('.')
        if (parts.size != 4) throw NetworkToolException("IPv4 地址格式无效")
        val bytes = ByteArray(4)
        parts.forEachIndexed { i, p ->
            val v = p.toIntOrNull() ?: throw NetworkToolException("IPv4 地址格式无效")
            if (v !in 0..255) throw NetworkToolException("IPv4 地址格式无效")
            bytes[i] = v.toByte()
        }
        return bytes
    }

    fun parseV6(text: String): ByteArray {
        val address = InetAddress.getByName(text.trim())
            ?: throw NetworkToolException("IPv6 地址格式无效")
        if (address !is Inet6Address) throw NetworkToolException("输入不是 IPv6 地址")
        return address.address
    }

    private fun v4ToLong(b: ByteArray): Long {
        var v = 0L
        for (i in 0 until 4) {
            v = (v shl 8) or ((b[i].toInt() and 0xFF).toLong())
        }
        return v
    }

    private fun longToV4(v: Long): String {
        return "${(v ushr 24) and 0xFF}.${(v ushr 16) and 0xFF}.${(v ushr 8) and 0xFF}.${v and 0xFF}"
    }

    private fun v4MaskToPrefix(mask: ByteArray): Int {
        val v = v4ToLong(mask)
        if ((v.inv() and 0xFFFFFFFFL) and ((v.inv() and 0xFFFFFFFFL) + 1) != 0L) {
            throw NetworkToolException("子网掩码无效（必须为连续 1 位）")
        }
        return Integer.bitCount(v.toInt())
    }

    private fun formatBinary(v: Long, bits: Int): String {
        val s = StringBuilder()
        for (i in bits - 1 downTo 0) {
            s.append(if ((v ushr i) and 1L == 1L) '1' else '0')
            if (i % 8 == 0 && i != 0) s.append(' ')
        }
        return s.toString()
    }

    private fun binaryV6(big: BigInteger): String {
        val s = big.toString(2).padStart(128, '0')
        return s.chunked(16).joinToString(" ")
    }

    private fun bigIntegerToV6(big: BigInteger): String {
        val bytes = ByteArray(16)
        val arr = big.toByteArray()
        var j = bytes.size - 1
        var i = arr.size - 1
        while (i >= 0 && j >= 0) {
            bytes[j--] = arr[i--]
        }
        val v6 = Inet6Address.getByAddress(bytes)
        return v6.hostAddress
    }

    fun isV4(text: String): Boolean {
        val parts = text.trim().split('.')
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull()?.let { v -> v in 0..255 } == true }
    }

    fun isV6(text: String): Boolean {
        return try {
            InetAddress.getByName(text.trim()) is Inet6Address
        } catch (e: Exception) {
            false
        }
    }

    private val MAX_V6: BigInteger = (BigInteger.ONE.shiftLeft(128)).subtract(BigInteger.ONE)
}