package com.networktools.opencode.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object TcpPing {

    data class Probe(
        val seq: Int,
        val ok: Boolean,
        val timeMs: Double,
        val error: String? = null
    )

    data class Summary(
        val sent: Int,
        val ok: Int,
        val lost: Int,
        val lossPercent: Double,
        val min: Double?,
        val avg: Double?,
        val max: Double?,
        val last: Double?
    )

    suspend fun run(
        host: String,
        port: Int,
        count: Int,
        timeoutMs: Int,
        intervalSec: Double,
        ipVersion: Int,
        onProbe: suspend (Probe) -> Unit,
        onSummary: suspend (Summary) -> Unit
    ) {
        val addresses = NetUtils.resolve(host, ipVersion)
        if (addresses.isEmpty()) throw NetworkToolException("无法解析主机 $host")
        val addr = addresses.first()
        val c = count.coerceAtLeast(1)

        withContext(Dispatchers.IO) {
            val times = mutableListOf<Double>()
            var ok = 0
            for (seq in 1..c) {
                if (!currentCoroutineContext().isActive) break
                val start = System.nanoTime()
                val result = try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(addr, port), timeoutMs)
                        true
                    }
                } catch (e: Exception) {
                    false
                }
                val rtt = (System.nanoTime() - start) / 1_000_000.0
                val probe = Probe(seq, result, rtt)
                if (result) {
                    ok++
                    times += rtt
                }
                onProbe(probe)
                if (seq < c) delay((intervalSec * 1000).toLong())
            }
            val loss = (c - ok).toDouble() / c * 100.0
            onSummary(
                Summary(
                    sent = c,
                    ok = ok,
                    lost = c - ok,
                    lossPercent = loss,
                    min = times.minOrNull(),
                    avg = if (times.isEmpty()) null else times.sum() / times.size,
                    max = times.maxOrNull(),
                    last = times.lastOrNull()
                )
            )
        }
    }
}