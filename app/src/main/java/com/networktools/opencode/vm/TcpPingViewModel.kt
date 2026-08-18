package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktools.opencode.net.TcpPing
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TcpPingViewModel : ViewModel() {

    private val _probes = MutableStateFlow<List<TcpPing.Probe>>(emptyList())
    val probes: StateFlow<List<TcpPing.Probe>> = _probes.asStateFlow()

    private val _summary = MutableStateFlow<TcpPing.Summary?>(null)
    val summary: StateFlow<TcpPing.Summary?> = _summary.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null

    fun start(host: String, port: Int, count: Int, timeoutMs: Int, intervalSec: Double, ipVersion: Int) {
        if (_running.value) return
        _probes.value = emptyList()
        _summary.value = null
        _error.value = null
        _running.value = true
        job = viewModelScope.launch {
            try {
                TcpPing.run(
                    host = host, port = port, count = count, timeoutMs = timeoutMs,
                    intervalSec = intervalSec, ipVersion = ipVersion,
                    onProbe = { p -> _probes.update { it + p } },
                    onSummary = { s -> _summary.value = s }
                )
            } catch (e: Exception) {
                _error.value = Errors.formatAndLog("TCPing", e)
            } finally {
                _running.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        _running.value = false
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}