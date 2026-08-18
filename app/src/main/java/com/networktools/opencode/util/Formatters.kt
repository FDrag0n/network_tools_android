package com.networktools.opencode.util

import java.net.InetAddress

fun String.isValidHost(): Boolean = isNotBlank()

fun InetAddress.family(): Int = if (this is java.net.Inet4Address) 4 else 6

fun guessIpVersion(host: String): Int {
    if (host.contains(':')) {
        if (host.count { it == ':' } >= 2 || host.contains("::")) return 6
    }
    val withoutPort = host.trim()
    if (withoutPort.isEmpty()) return 4
    return when {
        Regex("""^[\d.]+$""").matches(withoutPort) -> 4
        withoutPort.contains(':') -> 6
        else -> 4
    }
}

fun collectionLabel(count: Int): String = when (count) {
    0 -> "零"
    1 -> "一"
    2 -> "二"
    3 -> "三"
    else -> count.toString()
}

fun prettyPortService(port: Int): String? = SERVICE_NAMES[port]

private val SERVICE_NAMES: Map<Int, String> = mapOf(
    20 to "FTP-Data", 21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
    53 to "DNS", 67 to "DHCP", 68 to "DHCP", 69 to "TFTP", 80 to "HTTP",
    110 to "POP3", 123 to "NTP", 137 to "NetBIOS", 138 to "NetBIOS", 139 to "NetBIOS",
    143 to "IMAP", 161 to "SNMP", 162 to "SNMP-Trap", 179 to "BGP", 194 to "IRC",
    389 to "LDAP", 443 to "HTTPS", 445 to "SMB", 465 to "SMTPS", 500 to "ISAKMP",
    514 to "Syslog", 587 to "SMTP", 593 to "HTTP-RPC", 636 to "LDAPS", 873 to "rsync",
    990 to "FTPS", 993 to "IMAPS", 995 to "POP3S", 1080 to "Socks", 1194 to "OpenVPN",
    1433 to "MSSQL", 1521 to "Oracle", 1701 to "L2TP", 1723 to "PPTP", 1900 to "SSDP",
    2181 to "ZooKeeper", 2222 to "SSH", 2375 to "Docker", 2376 to "Docker-TLS",
    3000 to "HTTP-Alt", 3128 to "Squid", 3306 to "MySQL", 3389 to "RDP", 3690 to "SVN",
    4443 to "HTTPS-Alt", 4567 to "HTTP-Alt", 5000 to "HTTP-Alt", 5001 to "HTTP-Alt",
    5060 to "SIP", 5222 to "XMPP", 5228 to "GCM", 5432 to "PostgreSQL", 5672 to "AMQP",
    5900 to "VNC", 5985 to "WinRM", 6379 to "Redis", 6666 to "IRC-Alt", 6679 to "IRC",
    7001 to "WebLogic", 8000 to "HTTP-Alt", 8005 to "Tomcat", 8008 to "HTTP-Alt",
    8009 to "AJP", 8080 to "HTTP-Alt", 8081 to "HTTP-Alt", 8082 to "HTTP-Alt",
    8083 to "HTTP-Alt", 8084 to "HTTP-Alt", 8085 to "HTTP-Alt", 8086 to "HTTP-Alt",
    8087 to "HTTP-Alt", 8088 to "HTTP-Alt", 8090 to "HTTP-Alt", 8443 to "HTTPS-Alt",
    8888 to "HTTP-Alt", 9000 to "HTTP-Alt", 9090 to "HTTP-Alt", 9092 to "Kafka",
    9200 to "Elasticsearch", 9300 to "Elasticsearch", 9418 to "Git", 10000 to "HTTP-Alt",
    11211 to "Memcached", 16379 to "Redis-Alt", 27017 to "MongoDB", 32400 to "Plex",
    25565 to "Minecraft", 49152 to "RPC", 50000 to "SAP"
)