package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktools.opencode.net.PortScanner
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PortScanViewModel : ViewModel() {

    private val _results = MutableStateFlow<List<PortScanner.Result>>(emptyList())
    val results: StateFlow<List<PortScanner.Result>> = _results.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _total = MutableStateFlow(0)
    val total: StateFlow<Int> = _total.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null

    fun start(host: String, startPort: Int, endPort: Int, timeoutMs: Int, concurrency: Int, ipVersion: Int) {
        if (_running.value) return
        _results.value = emptyList()
        _error.value = null
        _progress.value = 0
        _total.value = (endPort - startPort + 1).coerceAtLeast(0)
        _running.value = true
        job = viewModelScope.launch {
            try {
                PortScanner.scan(
                    host = host,
                    startPort = startPort,
                    endPort = endPort,
                    timeoutMs = timeoutMs,
                    concurrency = concurrency,
                    ipVersion = ipVersion,
                    onResult = { r -> _results.update { it + r } },
                    onProgress = { scanned, total -> _progress.value = scanned; _total.value = total }
                )
            } catch (e: Exception) {
                _error.value = Errors.formatAndLog("PortScan", e)
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