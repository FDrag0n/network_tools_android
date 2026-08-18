package com.networktools.opencode.net

import java.net.InetAddress

object TracerouteEngine {

    data class Hop(
        val seq: Int,
        val hosts: List<String>,
        val times: List<Double>,
        val raw: String,
        val timedOut: Boolean
    )

    private class SystemTracerouteUnavailable(message: String) : Exception(message)

    suspend fun trace(
        host: String,
        maxHops: Int,
        probesPerHop: Int,
        waitSec: Int,
        numeric: Boolean,
        ipVersion: Int,
        onHop: suspend (Hop) -> Unit
    ) {
        val address = NetUtils.resolveSingle(host, ipVersion)
        val binary = NetUtils.traceBinary(address, ipVersion)
        val target = NetUtils.targetLiteral(address, host, ipVersion)

        try {
            runSystemTrace(binary, address, target, maxHops, probesPerHop, waitSec, numeric, onHop)
            return
        } catch (e: SystemTracerouteUnavailable) {
            // 系统 traceroute 不可用，回退到基于 TTL 的 ping 方法
        } catch (e: NetworkToolException) {
            if (e.message?.contains("退出码") == true) {
                // traceroute 命令本身失败，回退
            } else {
                throw e
            }
        }
        runTtlPingTrace(address, target, ipVersion, maxHops, onHop)
    }

    private suspend fun runSystemTrace(
        binary: String,
        address: InetAddress?,
        target: String,
        maxHops: Int,
        probesPerHop: Int,
        waitSec: Int,
        numeric: Boolean,
        onHop: suspend (Hop) -> Unit
    ) {
        val command = mutableListOf(
            binary,
            "-m", maxHops.toString(),
            "-q", probesPerHop.toString(),
            "-w", waitSec.toString()
        )
        if (numeric) command += "-n"
        command += target

        var permissionError = false
        val exit: Int = try {
            CommandRunner.run(command) { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@run
                if (trimmed.contains("Operation not permitted") ||
                    trimmed.contains("Permission denied") ||
                    trimmed.contains("not found") ||
                    trimmed.contains("No such file")
                ) {
                    permissionError = true
                    return@run
                }
                parse(trimmed)?.let { onHop(it) }
            }
        } catch (e: NetworkToolException) {
            if (e.message?.contains("无法执行系统命令") == true) {
                throw SystemTracerouteUnavailable(e.message ?: "")
            }
            throw e
        }
        if (permissionError) {
            throw SystemTracerouteUnavailable("系统 traceroute 需要更高权限")
        }
        if (exit != 0) {
            throw NetworkToolException("命令执行失败（退出码 $exit），请检查输入或设备网络工具是否可用")
        }
    }

    private suspend fun runTtlPingTrace(
        address: InetAddress?,
        target: String,
        ipVersion: Int,
        maxHops: Int,
        onHop: suspend (Hop) -> Unit
    ) {
        val binary = NetUtils.pingBinary(address, ipVersion)
        for (hop in 1..maxHops) {
            var hopHost: String? = null
            var reached = false
            val command = listOf(binary, "-c", "1", "-t", hop.toString(), "-w", "5", target)
            CommandRunner.run(command) { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@run
                if (trimmed.contains("bytes from") && trimmed.contains("time=") &&
                    !trimmed.contains("Time to live") && !trimmed.contains("ttl exceeded")
                ) {
                    reached = true
                } else {
                    val m = Regex("""(?i)from ([0-9a-fA-F:.]+)""").find(trimmed)
                    if (m != null) hopHost = m.groupValues[1]
                }
            }
            val fallbackHost = address?.hostAddress ?: target
            val displayHost = hopHost ?: if (reached) fallbackHost else null
            onHop(Hop(hop, listOfNotNull(displayHost), emptyList(), "", displayHost == null))
            if (reached) break
        }
    }

    private fun parse(line: String): Hop? {
        val match = Regex("""^\s*(\d+)\s+(.*)$""").find(line.trimEnd()) ?: return null
        val seq = match.groupValues[1].toIntOrNull() ?: return null
        val rest = match.groupValues[2]
        val hosts = Regex("""\(([0-9a-fA-F:.]+)\)""").findAll(rest)
            .map { it.groupValues[1] }
            .toList()
            .ifEmpty {
                Regex("""([\d.]+|[0-9a-fA-F:]+)""").findAll(rest)
                    .map { it.groupValues[1] }
                    .filter {
                        (it.contains('.') && it.count { c -> c == '.' } == 3) || it.contains(':')
                    }
                    .toList()
            }
            .distinct()
        val times = Regex("""([\d.]+)\s*ms""").findAll(rest)
            .map { it.groupValues[1].toDouble() }
            .toList()
        val timedOut = rest.trim() == "*" || (rest.contains("*") && times.isEmpty())
        return Hop(seq, hosts, times, line.trimEnd(), timedOut)
    }
}