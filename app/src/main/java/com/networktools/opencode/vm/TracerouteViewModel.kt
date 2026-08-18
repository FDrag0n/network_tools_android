package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktools.opencode.net.TracerouteEngine
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TracerouteViewModel : ViewModel() {

    private val _hops = MutableStateFlow<List<TracerouteEngine.Hop>>(emptyList())
    val hops: StateFlow<List<TracerouteEngine.Hop>> = _hops.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null

    fun start(
        host: String,
        maxHops: Int,
        probesPerHop: Int,
        waitSec: Int,
        numeric: Boolean,
        ipVersion: Int
    ) {
        if (_running.value) return
        _hops.value = emptyList()
        _error.value = null
        _running.value = true
        job = viewModelScope.launch {
            try {
                TracerouteEngine.trace(host, maxHops, probesPerHop, waitSec, numeric, ipVersion) { hop ->
                    _hops.update { it + hop }
                }
            } catch (e: Exception) {
                Errors.formatAndLog("Traceroute", e)
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