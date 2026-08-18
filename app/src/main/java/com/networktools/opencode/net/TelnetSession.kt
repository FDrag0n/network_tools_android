package com.networktools.opencode.net

import java.net.InetSocketAddress
import java.net.Socket

class TelnetSession(
    val host: String,
    val port: Int,
    val onOutput: (String) -> Unit,
    val onState: (State) -> Unit
) {
    enum class State { CONNECTING, CONNECTED, CLOSED, ERROR }

    private var socket: Socket? = null
    private var reader: Thread? = null

    fun connect() {
        onState(State.CONNECTING)
        try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 10_000)
            socket = s
            onState(State.CONNECTED)
            reader = Thread { readLoop(s) }.apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            onOutput("连接失败: ${e.message}\n")
            onState(State.ERROR)
        }
    }

    private fun readLoop(s: Socket) {
        try {
            val input = s.getInputStream()
            val buf = StringBuilder()
            while (true) {
                val ch = input.read()
                if (ch == -1) break
                if (ch == '\n'.code) {
                    onOutput(buf.toString() + "\n")
                    buf.setLength(0)
                } else if (ch != '\r'.code) {
                    buf.append(ch.toChar())
                }
            }
            if (buf.isNotEmpty()) onOutput(buf.toString() + "\n")
            onState(State.CLOSED)
        } catch (_: Exception) {
            onState(State.CLOSED)
        }
    }

    fun send(text: String) {
        try {
            val s = socket ?: return
            s.getOutputStream().write((text + "\r\n").toByteArray(Charsets.ISO_8859_1))
            s.getOutputStream().flush()
        } catch (_: Exception) {
        }
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }
}