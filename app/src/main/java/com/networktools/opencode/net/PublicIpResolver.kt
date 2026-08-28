package com.networktools.opencode.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 参考 https://ip.sb/api/ 实现的公网 IP 查询。
 * - IPv4: https://api-ipv4.ip.sb/ip  (plain text)
 * - IPv6: https://api-ipv6.ip.sb/ip  (plain text)
 * - Geo  : https://api.ip.sb/geoip/{ip}  (JSON)
 * 每个请求均需自定义 User-Agent，否则会被边缘节点拦截。
 */
object PublicIpResolver {

    private const val UA = "NetworkTools/1.1 (Android)"
    private const val TIMEOUT_MS = 10000

    private const val IPV4_URL = "https://api-ipv4.ip.sb/ip"
    private const val IPV6_URL = "https://api-ipv6.ip.sb/ip"
    private const val GEOIP_BASE = "https://api.ip.sb/geoip"

    data class GeoInfo(
        val ip: String,
        val countryCode: String?,
        val country: String?,
        val regionCode: String?,
        val region: String?,
        val city: String?,
        val postalCode: String?,
        val continentCode: String?,
        val latitude: Double?,
        val longitude: Double?,
        val timezone: String?,
        val offset: Int?,
        val asn: Long?,
        val asnOrganization: String?,
        val isp: String?,
        val organization: String?
    )

    data class IpResult(
        val ip: String?,
        val geo: GeoInfo?,
        val error: String?
    )

    data class CombinedResult(
        val ipv4: IpResult,
        val ipv6: IpResult
    )

    suspend fun fetchAll(): CombinedResult = withContext(Dispatchers.IO) {
        val v4 = async { fetchOne(IPV4_URL) }
        val v6 = async { fetchOne(IPV6_URL) }
        CombinedResult(v4.await(), v6.await())
    }

    private suspend fun fetchOne(ipUrl: String): IpResult = withContext(Dispatchers.IO) {
        try {
            val ip = httpGetText(ipUrl)?.trim()?.takeIf { it.isNotEmpty() }
            if (ip == null) {
                return@withContext IpResult(null, null, "未获取到 IP（空响应）")
            }
            val geo = try {
                fetchGeo(ip)
            } catch (e: Exception) {
                null
            }
            IpResult(ip = ip, geo = geo, error = null)
        } catch (e: Exception) {
            IpResult(null, null, e.message ?: e::class.simpleName ?: "未知错误")
        }
    }

    suspend fun fetchGeo(ip: String): GeoInfo? = withContext(Dispatchers.IO) {
        val jsonText = httpGetText("$GEOIP_BASE/$ip") ?: return@withContext null
        parseGeo(jsonText)
    }

    fun parseGeo(jsonText: String): GeoInfo? {
        return try {
            val o = JSONObject(jsonText)
            if (!o.has("ip")) return null
            GeoInfo(
                ip = o.optString("ip", ""),
                countryCode = o.optStringOrNull("country_code"),
                country = o.optStringOrNull("country"),
                regionCode = o.optStringOrNull("region_code"),
                region = o.optStringOrNull("region"),
                city = o.optStringOrNull("city"),
                postalCode = o.optStringOrNull("postal_code"),
                continentCode = o.optStringOrNull("continent_code"),
                latitude = o.optDoubleOrNull("latitude"),
                longitude = o.optDoubleOrNull("longitude"),
                timezone = o.optStringOrNull("timezone"),
                offset = o.optIntOrNull("offset"),
                asn = o.optLongOrNull("asn"),
                asnOrganization = o.optStringOrNull("asn_organization"),
                isp = o.optStringOrNull("isp"),
                organization = o.optStringOrNull("organization")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGetText(urlText: String): String? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlText)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream ?: return null
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) {
                // 400 表示无效 IP 等，尝试解析错误信息
                if (body.contains("error", ignoreCase = true)) {
                    throw RuntimeException(body.trim().take(300))
                }
                throw RuntimeException("HTTP $code: ${body.trim().take(200)}")
            }
            return body
        } finally {
            conn?.disconnect()
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) try { getDouble(key) } catch (_: Exception) { null } else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) try { getInt(key) } catch (_: Exception) { null } else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) try { getLong(key) } catch (_: Exception) { null } else null
}
