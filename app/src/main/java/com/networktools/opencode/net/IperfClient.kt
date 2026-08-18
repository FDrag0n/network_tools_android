package com.networktools.opencode.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

object IperfClient {

    data class Result(
        val bytes: Long,
        val seconds: Double,
        val mbps: Double,
        val serverResult: String? = null
    )

    private const val ST_PARAM_EXCHANGE = 9
    private const val ST_CREATE_STREAMS = 10
    private const val ST_TEST_START = 1
    private const val ST_TEST_RUNNING = 2
    private const val ST_TEST_END = 4
    private const val ST_EXCHANGE_RESULTS = 13
    private const val ST_DISPLAY_RESULTS = 14
    private const val ST_IPERF_DONE = 16
    private const val ST_SERVER_ERROR = 0xFE.toByte()
    private const val ST_ACCESS_DENIED = 0xFF.toByte()

    suspend fun test(
        host: String,
        port: Int,
        seconds: Int,
        reverse: Boolean,
        bufferSize: Int,
        onProgress: suspend (mbps: Double, elapsed: Double) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        val time = seconds.coerceAtLeast(1)
        val buffer = ByteArray(bufferSize.coerceIn(1024, 4 * 1024 * 1024))
        val cookie = makeCookie()

        val control = Socket()
        control.connect(InetSocketAddress(host, port), 10_000)
        control.tcpNoDelay = true
        control.soTimeout = 30_000
        val cOut = control.getOutputStream()
        val cIn = control.getInputStream()

        try {
            cOut.write(cookie.toByteArray(Charsets.US_ASCII))
            cOut.flush()

            readState(cIn, ST_PARAM_EXCHANGE)

            val params = JSONObject().apply {
                put("tcp", true)
                put("omit", 0)
                put("time", time)
                put("num", 0)
                put("blockcount", 0)
                put("parallel", 1)
                if (reverse) put("reverse", true)
                put("len", buffer.size)
                put("client_version", "3.21")
            }
            writeJson(cOut, params)

            readState(cIn, ST_CREATE_STREAMS)

            val data = Socket()
            try {
                data.connect(InetSocketAddress(host, port), 10_000)
                data.tcpNoDelay = true
                data.soTimeout = 2_000
                val dOut = data.getOutputStream()
                dOut.write(cookie.toByteArray(Charsets.US_ASCII))
                dOut.flush()

                readState(cIn, ST_TEST_START)
                readState(cIn, ST_TEST_RUNNING)

                val start = System.nanoTime()
                var bytes: Long = 0
                var elapsedLast = 0.0

                if (reverse) {
                    val input = data.getInputStream()
                    val chunk = ByteArray(64 * 1024)
                    while (currentCoroutineContext().isActive) {
                        val elapsed = (System.nanoTime() - start) / 1e9
                        if (elapsed >= time) break
                        val n = try {
                            input.read(chunk)
                        } catch (_: SocketTimeoutException) {
                            continue
                        }
                        if (n == -1) break
                        bytes += n
                        if (elapsed - elapsedLast >= 0.5) {
                            elapsedLast = elapsed
                            onProgress(bytes * 8.0 / elapsed / 1e6, elapsed)
                        }
                    }
                } else {
                    while (currentCoroutineContext().isActive) {
                        val elapsed = (System.nanoTime() - start) / 1e9
                        if (elapsed >= time) break
                        dOut.write(buffer)
                        bytes += buffer.size
                        if (elapsed - elapsedLast >= 0.5) {
                            elapsedLast = elapsed
                            onProgress(bytes * 8.0 / elapsed / 1e6, elapsed)
                        }
                    }
                }
                data.close()

                val elapsed = (System.nanoTime() - start) / 1e9
                val mbps = if (elapsed > 0) bytes * 8.0 / elapsed / 1e6 else 0.0

                cOut.write(ST_TEST_END)
                cOut.flush()

                readState(cIn, ST_EXCHANGE_RESULTS)

                val results = JSONObject().apply {
                    put("cpu_util_total", 0)
                    put("cpu_util_user", 0)
                    put("cpu_util_system", 0)
                    put("sender_has_retransmits", 0)
                    put("streams", org.json.JSONArray().put(
                        JSONObject().apply {
                            put("id", 1)
                            put("bytes", bytes)
                            put("retransmits", 0)
                            put("jitter", 0)
                            put("errors", 0)
                            put("packets", 0)
                        }
                    ))
                }
                writeJson(cOut, results)

                val serverResults = readJson(cIn)
                val serverResult = serverResults?.toString()

                readState(cIn, ST_DISPLAY_RESULTS)

                cOut.write(ST_IPERF_DONE)
                cOut.flush()

                Result(bytes, elapsed, mbps, serverResult)
            } finally {
                try {
                    data.close()
                } catch (_: Exception) {
                }
            }
        } finally {
            try {
                control.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun makeCookie(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder(37)
        repeat(36) { sb.append(chars[kotlin.random.Random.nextInt(chars.length)]) }
        sb.append('\u0000')
        return sb.toString()
    }

    private fun readState(input: InputStream, expected: Int) {
        val b = input.read()
        if (b == -1) throw NetworkToolException("iperf3 控制连接被服务器提前关闭")
        if (b.toByte() == ST_SERVER_ERROR) throw NetworkToolException("iperf3 服务器返回错误（SERVER_ERROR）")
        if (b.toByte() == ST_ACCESS_DENIED) throw NetworkToolException("iperf3 服务器拒绝连接（ACCESS_DENIED）")
        if (b != expected) throw NetworkToolException("iperf3 协议状态不符：期望 $expected，实际 $b")
    }

    private fun writeJson(output: OutputStream, json: JSONObject) {
        val raw = json.toString().toByteArray(Charsets.US_ASCII)
        output.write(raw.size ushr 24 and 0xFF)
        output.write(raw.size ushr 16 and 0xFF)
        output.write(raw.size ushr 8 and 0xFF)
        output.write(raw.size and 0xFF)
        output.write(raw)
        output.flush()
    }

    private fun readJson(input: InputStream): JSONObject? {
        val len = readLength(input) ?: return null
        if (len <= 0) return null
        val raw = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = input.read(raw, off, len - off)
            if (n == -1) throw NetworkToolException("iperf3 读取服务器 JSON 时连接关闭")
            off += n
        }
        return try {
            JSONObject(String(raw, Charsets.US_ASCII))
        } catch (_: Exception) {
            null
        }
    }

    private fun readLength(input: InputStream): Int? {
        val b0 = input.read()
        if (b0 == -1) return null
        val b1 = input.read()
        if (b1 == -1) throw NetworkToolException("iperf3 读取长度时连接关闭")
        val b2 = input.read()
        if (b2 == -1) throw NetworkToolException("iperf3 读取长度时连接关闭")
        val b3 = input.read()
        if (b3 == -1) throw NetworkToolException("iperf3 读取长度时连接关闭")
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }
}