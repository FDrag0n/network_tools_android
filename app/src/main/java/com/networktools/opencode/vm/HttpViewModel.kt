package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktools.opencode.net.HttpRequest
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HttpViewModel : ViewModel() {

    private val _result = MutableStateFlow<HttpRequest.Result?>(null)
    val result: StateFlow<HttpRequest.Result?> = _result.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var job: Job? = null

    fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMs: Int
    ) {
        if (_running.value) return
        _running.value = true
        job = viewModelScope.launch {
            try {
                _result.value = HttpRequest.execute(method, url, headers, body, timeoutMs)
            } catch (e: Exception) {
                _result.value = HttpRequest.Result(null, null, emptyList(), "", 0.0, Errors.formatAndLog("HTTP", e))
            } finally {
                _running.value = false
            }
        }
    }

    fun clear() {
        _result.value = null
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