package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktools.opencode.net.IperfClient
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BandwidthViewModel : ViewModel() {

    private val _result = MutableStateFlow<IperfClient.Result?>(null)
    val result: StateFlow<IperfClient.Result?> = _result.asStateFlow()

    private val _progress = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val progress: StateFlow<List<Pair<Double, Double>>> = _progress.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null

    fun start(host: String, port: Int, seconds: Int, reverse: Boolean, bufferSize: Int) {
        if (_running.value) return
        _result.value = null
        _progress.value = emptyList()
        _error.value = null
        _running.value = true
        job = viewModelScope.launch {
            try {
                val res = IperfClient.test(
                    host = host, port = port, seconds = seconds,
                    reverse = reverse, bufferSize = bufferSize,
                    onProgress = { mbps, elapsed -> _progress.update { it + (elapsed to mbps) } }
                )
                _result.value = res
            } catch (e: Exception) {
                _error.value = Errors.formatAndLog("带宽测试", e)
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