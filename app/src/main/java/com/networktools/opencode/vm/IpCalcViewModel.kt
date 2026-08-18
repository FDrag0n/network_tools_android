package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import com.networktools.opencode.net.IpCalculator
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.flow.MutableStateFlow

data class LabelValue(val label: String, val value: String, val copyable: Boolean = true)

sealed class CalcResult {
    data class V4(val rows: List<LabelValue>) : CalcResult()
    data class V6(val rows: List<LabelValue>) : CalcResult()
}

class IpCalcViewModel : ViewModel() {

    val result = MutableStateFlow<CalcResult?>(null)
    val error = MutableStateFlow<String?>(null)

    fun calculateV4(ip: String, prefixOrMask: String) {
        try {
            val r = IpCalculator.calculateV4(ip, prefixOrMask)
            result.value = CalcResult.V4(
                listOf(
                    LabelValue("IP 地址", r.ip),
                    LabelValue("子网掩码", r.netmask),
                    LabelValue("前缀长度", "/${r.prefix}"),
                    LabelValue("通配符掩码", r.wildcard),
                    LabelValue("网络地址", r.network),
                    LabelValue("广播地址", r.broadcast),
                    LabelValue("主机范围", "${r.firstHost} — ${r.lastHost}"),
                    LabelValue("地址总数", r.hostCount.toString()),
                    LabelValue("可用主机数", r.usableHosts.toString()),
                    LabelValue("IP 二进制", r.ipBinary),
                    LabelValue("掩码二进制", r.maskBinary),
                    LabelValue("网络地址二进制", r.networkBinary),
                    LabelValue("广播地址二进制", r.broadcastBinary)
                )
            )
            error.value = null
        } catch (e: Exception) {
            error.value = Errors.formatAndLog("IpCalc", e)
            result.value = null
        }
    }

    fun calculateV6(ip: String, prefix: Int) {
        try {
            val r = IpCalculator.calculateV6(ip, prefix)
            result.value = CalcResult.V6(
                listOf(
                    LabelValue("IPv6 地址", ip),
                    LabelValue("前缀长度", "/$prefix"),
                    LabelValue("网络地址", r.network),
                    LabelValue("地址范围结束", r.end),
                    LabelValue("地址总数", r.totalAddresses),
                    LabelValue("子网划分", r.usable64Subnets),
                    LabelValue("地址二进制", r.addressBinary),
                    LabelValue("掩码二进制", r.maskBinary),
                    LabelValue("网络地址二进制", r.networkBinary)
                )
            )
            error.value = null
        } catch (e: Exception) {
            error.value = Errors.formatAndLog("IpCalc", e)
            result.value = null
        }
    }
}