package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktools.opencode.net.DnsResolver
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DnsViewModel : ViewModel() {

    private val _records = MutableStateFlow<List<DnsResolver.RecordRow>>(emptyList())
    val records: StateFlow<List<DnsResolver.RecordRow>> = _records.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _allMode = MutableStateFlow(false)
    val allMode: StateFlow<Boolean> = _allMode.asStateFlow()

    private var job: Job? = null

    fun start(host: String, type: Int, server: String?) {
        if (_running.value) return
        _allMode.value = false
        _records.value = emptyList()
        _error.value = null
        _running.value = true
        job = viewModelScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    DnsResolver.lookup(host, type, server)
                }
                _records.value = rows
            } catch (e: Exception) {
                _error.value = Errors.formatAndLog("DNS", e)
            } finally {
                _running.value = false
            }
        }
    }

    fun startAll(host: String, server: String?) {
        if (_running.value) return
        _allMode.value = true
        _records.value = emptyList()
        _error.value = null
        _running.value = true
        job = viewModelScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    DnsResolver.lookupAll(host, server)
                }
                _records.value = rows
            } catch (e: Exception) {
                _error.value = Errors.formatAndLog("DNS", e)
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