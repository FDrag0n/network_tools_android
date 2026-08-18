package com.networktools.opencode.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

object PortScanner {

    enum class State { OPEN, CLOSED, FILTERED }

    data class Result(
        val port: Int,
        val state: State,
        val service: String?,
        val timeMs: Long
    )

    suspend fun scan(
        host: String,
        startPort: Int,
        endPort: Int,
        timeoutMs: Int,
        concurrency: Int,
        ipVersion: Int,
        onResult: suspend (Result) -> Unit,
        onProgress: suspend (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        if (startPort > endPort) throw IllegalArgumentException("起始端口大于结束端口")

        val semaphore = Semaphore(concurrency.coerceAtLeast(1))
        val scanned = AtomicInteger(0)
        val total = (endPort - startPort + 1).coerceAtLeast(0)

        val addresses = withContext(Dispatchers.IO) { resolveAddresses(host, ipVersion) }
        if (addresses.isEmpty()) throw UnknownHostException("无法解析主机 $host")

        withContext(Dispatchers.IO) {
            (startPort..endPort)
                .map { port ->
                    async {
                        semaphore.withPermit {
                            val result = probe(addresses, port, timeoutMs)
                            val scannedCount = scanned.incrementAndGet()
                            onResult(result)
                            onProgress(scannedCount, total)
                            result
                        }
                    }
                }
                .awaitAll()
        }
    }

    private fun probe(addresses: List<InetAddress>, port: Int, timeoutMs: Int): Result {
        val start = System.currentTimeMillis()
        for (addr in addresses) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(addr, port), timeoutMs)
                val elapsed = System.currentTimeMillis() - start
                return Result(port, State.OPEN, null, elapsed)
            } catch (e: SocketTimeoutException) {
                val elapsed = System.currentTimeMillis() - start
                return Result(port, State.FILTERED, null, elapsed)
            } catch (e: ConnectException) {
                val elapsed = System.currentTimeMillis() - start
                return Result(port, State.CLOSED, null, elapsed)
            } catch (e: SocketException) {
                val elapsed = System.currentTimeMillis() - start
                return Result(port, State.CLOSED, null, elapsed)
            } catch (e: NoRouteToHostException) {
                val elapsed = System.currentTimeMillis() - start
                return Result(port, State.FILTERED, null, elapsed)
            } catch (e: PortUnreachableException) {
                val elapsed = System.currentTimeMillis() - start
                return Result(port, State.CLOSED, null, elapsed)
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - start
                return Result(port, State.CLOSED, null, elapsed)
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }
        return Result(port, State.CLOSED, null, System.currentTimeMillis() - start)
    }

    fun resolveAddresses(host: String, ipVersion: Int): List<InetAddress> {
        val all = InetAddress.getAllByName(host).toList()
        return all.filter { a ->
            when (ipVersion) {
                6 -> a is java.net.Inet6Address
                4 -> a is java.net.Inet4Address
                else -> true
            }
        }
    }
}