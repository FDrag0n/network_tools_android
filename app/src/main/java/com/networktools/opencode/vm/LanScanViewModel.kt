package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktools.opencode.net.IpScanEngine
import com.networktools.opencode.net.LanScanEngine
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LanScanViewModel : ViewModel() {

    private val _devices = MutableStateFlow<List<LanScanEngine.Device>>(emptyList())
    val devices: StateFlow<List<LanScanEngine.Device>> = _devices.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _total = MutableStateFlow(0)
    val total: StateFlow<Int> = _total.asStateFlow()

    private val _subnet = MutableStateFlow<String?>(null)
    val subnet: StateFlow<String?> = _subnet.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null

    fun detectSubnet() {
        viewModelScope.launch {
            _subnet.value = withContext(Dispatchers.IO) { IpScanEngine.detectLocalSubnet() }
        }
    }

    fun start(cidr: String, concurrency: Int) {
        if (_running.value) return
        val range = try {
            IpScanEngine.parseCidr(cidr)
        } catch (e: Exception) {
            _error.value = Errors.formatAndLog("局域网扫描", e)
            return
        }
        _devices.value = emptyList()
        _error.value = null
        _progress.value = 0
        _total.value = range.total.toInt()
        _running.value = true
        job = viewModelScope.launch {
            try {
                LanScanEngine.scan(
                    range = range,
                    concurrency = concurrency,
                    onDevice = { d -> _devices.update { it + d } },
                    onProgress = { scanned, total -> _progress.value = scanned; _total.value = total }
                )
            } catch (e: Exception) {
                _error.value = Errors.formatAndLog("局域网扫描", e)
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