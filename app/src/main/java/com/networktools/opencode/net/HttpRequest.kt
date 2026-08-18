package com.networktools.opencode.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object HttpRequest {

    val BODY_METHODS = listOf("POST", "PUT", "PATCH")

    data class Result(
        val status: Int?,
        val statusMessage: String?,
        val headers: List<Pair<String, String>>,
        val body: String,
        val timeMs: Double,
        val error: String?
    )

    suspend fun execute(
        method: String,
        urlText: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMs: Int
    ): Result = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlText.trim())
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestMethodCompat(method)
                if (method in BODY_METHODS) {
                    doOutput = true
                    setFixedLengthStreamingMode(body?.toByteArray()?.size ?: 0)
                }
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            if (method in BODY_METHODS && body != null) {
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 400..599) connection.errorStream else connection.inputStream
            val bytes = if (stream != null) {
                stream.use { input ->
                    val buf = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        buf.write(buffer, 0, n)
                    }
                    buf.toByteArray()
                }
            } else {
                ByteArray(0)
            }
            val headersList = connection.headerFields.flatMap { (k, vs) ->
                vs?.mapNotNull { v -> if (k != null) k to v else null } ?: emptyList()
            }
            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            Result(
                status = status,
                statusMessage = connection.responseMessage,
                headers = headersList,
                body = String(bytes, Charsets.UTF_8),
                timeMs = elapsed,
                error = null
            )
        } catch (e: Exception) {
            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            Result(null, null, emptyList(), "", elapsed, e.message)
        } finally {
            connection?.disconnect()
        }
    }

    private fun HttpURLConnection.setRequestMethodCompat(method: String) {
        try {
            requestMethod = method
        } catch (e: Exception) {
            val field = javaClass.getSuperclass()
                .getDeclaredField("method")
            field.isAccessible = true
            field.set(this, method)
        }
    }
}