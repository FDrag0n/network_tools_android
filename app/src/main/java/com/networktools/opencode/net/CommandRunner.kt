package com.networktools.opencode.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.IOException

object CommandRunner {

    suspend fun run(command: List<String>, onLine: suspend (String) -> Unit): Int =
        withContext(Dispatchers.IO) { execute(command, onLine) }

    private suspend fun execute(command: List<String>, onLine: suspend (String) -> Unit): Int {
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (e: IOException) {
            throw NetworkToolException("无法执行系统命令 ${command.firstOrNull() ?: ""}: ${e.message}")
        }
        try {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (!currentCoroutineContext().isActive) break
                    onLine(line)
                }
            }
            return process.waitFor()
        } finally {
            process.destroyForcibly()
        }
    }
}

class NetworkToolException(message: String) : Exception(message)