package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktools.opencode.net.MtrEngine
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MtrViewModel : ViewModel() {

    private val _rows = MutableStateFlow<List<MtrEngine.HopRow>>(emptyList())
    val rows: StateFlow<List<MtrEngine.HopRow>> = _rows.asStateFlow()

    private val _cycle = MutableStateFlow(0)
    val cycle: StateFlow<Int> = _cycle.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null

    fun start(host: String, maxHops: Int, probes: Int, intervalSec: Double, ipVersion: Int) {
        if (_running.value) return
        _rows.value = emptyList()
        _cycle.value = 0
        _error.value = null
        _running.value = true
        job = viewModelScope.launch {
            try {
                MtrEngine.run(
                    host = host, maxHops = maxHops, probesPerHop = probes,
                    intervalSec = intervalSec, ipVersion = ipVersion,
                    onCycleStart = { c -> _cycle.value = c },
                    onHopRow = { r -> _rows.update { rows ->
                        val index = rows.indexOfFirst { it.hop == r.hop }
                        if (index >= 0) rows.toMutableList().apply { this[index] = r } else rows + r
                    } },
                    onCycleEnd = { }
                )
            } catch (e: Exception) {
                _error.value = Errors.formatAndLog("MTR", e)
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