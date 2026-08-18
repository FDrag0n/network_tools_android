package com.networktools.opencode.net

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object NetUtils {

    fun resolve(host: String, preferVersion: Int): List<InetAddress> {
        val trimmed = host.trim()
        val all = try {
            InetAddress.getAllByName(trimmed).toList().filterNotNull()
        } catch (e: Throwable) {
            // 部分 Android 设备解析某些域名时会抛出无具体信息的异常，
            // 不在此处中断，交由外部命令直接按主机名解析
            return emptyList()
        }
        if (all.isEmpty()) return emptyList()
        val filtered = when (preferVersion) {
            6 -> all.filterIsInstance<Inet6Address>()
            4 -> all.filterIsInstance<Inet4Address>()
            else -> {
                val v4 = all.filterIsInstance<Inet4Address>()
                if (v4.isNotEmpty()) v4 else all
            }
        }
        return if (filtered.isEmpty()) all else filtered
    }

    fun resolveSingle(host: String, preferVersion: Int): InetAddress? {
        return resolve(host, preferVersion).firstOrNull()
    }

    fun pingBinary(address: InetAddress?, preferVersion: Int): String = when {
        preferVersion == 6 || address is Inet6Address -> "ping6"
        else -> "ping"
    }

    fun traceBinary(address: InetAddress?, preferVersion: Int): String = when {
        preferVersion == 6 || address is Inet6Address -> "traceroute6"
        else -> "traceroute"
    }

    fun targetLiteral(address: InetAddress?, host: String, preferVersion: Int): String = when (preferVersion) {
        6 -> if (address is Inet6Address) address.hostAddress else host.trim()
        4 -> if (address is Inet4Address) address.hostAddress else host.trim()
        else -> address?.hostAddress ?: host.trim()
    }
}