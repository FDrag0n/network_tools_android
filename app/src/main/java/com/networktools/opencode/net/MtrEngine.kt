package com.networktools.opencode.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MtrEngine {

    data class HopRow(
        val hop: Int,
        val host: String?,
        val sent: Int,
        val lost: Int,
        val min: Double?,
        val avg: Double?,
        val max: Double?,
        val last: Double?,
        val reached: Boolean
    )

    suspend fun run(
        host: String,
        maxHops: Int,
        probesPerHop: Int,
        intervalSec: Double,
        ipVersion: Int,
        onCycleStart: suspend (cycle: Int) -> Unit,
        onHopRow: suspend (HopRow) -> Unit,
        onCycleEnd: suspend (cycle: Int) -> Unit
    ) {
        val address = NetUtils.resolveSingle(host, ipVersion)
        val binary = NetUtils.pingBinary(address, ipVersion)
        val target = NetUtils.targetLiteral(address, host, ipVersion)
        val probes = probesPerHop.coerceAtLeast(1)
        var cycle = 0

        while (currentCoroutineContext().isActive) {
            cycle++
            onCycleStart(cycle)
            for (hop in 1..maxHops) {
                if (!currentCoroutineContext().isActive) break
                val row = probeHop(binary, target, hop, probes)
                onHopRow(row)
                if (row.reached) break
            }
            onCycleEnd(cycle)
            delay((intervalSec * 1000).toLong())
        }
    }

    private suspend fun probeHop(binary: String, target: String, hop: Int, probes: Int): HopRow {
        val times = mutableListOf<Double>()
        var host: String? = null
        var reached = false
        var sent = 0
        var lost = 0

        withContext(Dispatchers.IO) {
            for (i in 1..probes) {
                sent++
                var hopHost: String? = null
                var gotReply = false
                val command = listOf(binary, "-c", "1", "-t", hop.toString(), "-w", "3", target)
                CommandRunner.run(command) { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@run
                    if (trimmed.contains("bytes from") && trimmed.contains("time=") &&
                        !trimmed.contains("Time to live") && !trimmed.contains("ttl exceeded")
                    ) {
                        gotReply = true
                        val t = Regex("""time=([\d.]+)""").find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull()
                        if (t != null) times += t
                    } else {
                        val m = Regex("""(?i)from ([0-9a-fA-F:.]+)""").find(trimmed)
                        if (m != null) hopHost = m.groupValues[1]
                    }
                }
                if (gotReply) {
                    reached = true
                    host = host ?: hopHost
                } else {
                    lost++
                    host = host ?: hopHost
                }
            }
        }
        return HopRow(
            hop = hop,
            host = host,
            sent = sent,
            lost = lost,
            min = times.minOrNull(),
            avg = if (times.isEmpty()) null else times.sum() / times.size,
            max = times.maxOrNull(),
            last = times.lastOrNull(),
            reached = reached
        )
    }
}