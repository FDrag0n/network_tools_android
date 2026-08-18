package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktools.opencode.net.PingEngine
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PingViewModel : ViewModel() {

    private val _packets = MutableStateFlow<List<PingEngine.Packet>>(emptyList())
    val packets: StateFlow<List<PingEngine.Packet>> = _packets.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null

    fun start(host: String, count: Int, intervalSec: Double, packetSize: Int, ipVersion: Int) {
        if (_running.value) return
        _packets.value = emptyList()
        _error.value = null
        _running.value = true
        job = viewModelScope.launch {
            try {
                PingEngine.ping(host, count, intervalSec, packetSize, ipVersion) { p ->
                    _packets.update { it + p }
                }
            } catch (e: Exception) {
                _error.value = Errors.formatAndLog("Ping", e)
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