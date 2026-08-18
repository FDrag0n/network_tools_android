package com.networktools.opencode.net

object PingEngine {

    enum class Kind { INFO, REPLY, TIMEOUT, ERROR, SUMMARY, DONE }

    data class Packet(
        val seq: Int,
        val host: String?,
        val ttl: Int?,
        val timeMs: Double?,
        val raw: String,
        val kind: Kind
    )

    suspend fun ping(
        host: String,
        count: Int,
        intervalSec: Double,
        packetSize: Int,
        ipVersion: Int,
        onPacket: suspend (Packet) -> Unit
    ) {
        val address = NetUtils.resolveSingle(host, ipVersion)
        val binary = NetUtils.pingBinary(address, ipVersion)
        val target = NetUtils.targetLiteral(address, host, ipVersion)
        val c = count.coerceAtLeast(1)
        val deadline = ((c * intervalSec).toInt()) + 5

        val command = listOf(
            binary,
            "-c", c.toString(),
            "-i", intervalSec.toString(),
            "-s", packetSize.toString(),
            "-w", deadline.toString(),
            target
        )

var produced = 0
        val exit = CommandRunner.run(command) { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") ||
                trimmed.contains("wrong data byte") || trimmed.startsWith("Warning: time of day goes back")
            ) return@run
            produced++
            onPacket(parse(trimmed))
        }
        if (produced == 0 && exit != 0) {
            throw NetworkToolException("命令执行失败（退出码 $exit），请检查输入或设备网络工具是否可用")
        }
        onPacket(Packet(0, null, null, null, "", Kind.DONE))
    }

    private fun parse(line: String): Packet {
        val lower = line.lowercase()
        val kind: Kind = when {
            lower.contains("bytes from") && lower.contains("icmp_seq") && lower.contains("time=") -> Kind.REPLY
            lower.contains("request timeout") || lower.contains("no answer") -> Kind.TIMEOUT
            lower.contains("unreachable") || lower.contains("can't assign") ||
                lower.contains("self energy") || lower.contains("!h") || lower.contains("!n") -> Kind.ERROR
            lower.contains("packet loss") || lower.contains("transmitted") ||
                lower.contains("received") || lower.contains("statistics") ||
                lower.contains("round-trip") || lower.contains("rtt ") -> Kind.SUMMARY
            else -> Kind.INFO
        }
        val seq = Regex("""icmp_seq=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
        val host = Regex("""bytes from (.+?): icmp_seq=""").find(line)?.groupValues?.get(1)?.trim()
        val ttl = Regex("""ttl=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
        val time = Regex("""time=([\d.]+)""").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
        return Packet(seq ?: 0, host, ttl, time, line, kind)
    }
}