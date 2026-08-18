package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktools.opencode.net.TelnetSession
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TelnetViewModel : ViewModel() {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _state = MutableStateFlow<TelnetSession.State?>(null)
    val state: StateFlow<TelnetSession.State?> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var session: TelnetSession? = null

    val connected: Boolean
        get() = _state.value == TelnetSession.State.CONNECTED

    fun connect(host: String, port: Int) {
        val current = _state.value
        if (current == TelnetSession.State.CONNECTING || current == TelnetSession.State.CONNECTED) return
        _lines.value = emptyList()
        _error.value = null
        _state.value = TelnetSession.State.CONNECTING
        session = TelnetSession(
            host = host,
            port = port,
            onOutput = { line -> _lines.update { it + line } },
            onState = { s -> _state.value = s }
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                session?.connect()
            } catch (e: Exception) {
                _error.value = Errors.formatAndLog("Telnet", e)
                _state.value = TelnetSession.State.ERROR
            }
        }
    }

    fun send(text: String) {
        session?.send(text)
        _lines.update { it + "$text\r\n" }
    }

    fun disconnect() {
        session?.close()
        session = null
        _state.value = TelnetSession.State.CLOSED
    }

    override fun onCleared() {
        session?.close()
        super.onCleared()
    }
}