package com.networktools.opencode.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktools.opencode.net.PublicIpResolver
import com.networktools.opencode.util.Errors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PublicIpViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val ipv4: String? = null,
        val ipv6: String? = null,
        val ipv4Geo: PublicIpResolver.GeoInfo? = null,
        val ipv6Geo: PublicIpResolver.GeoInfo? = null,
        val ipv4Error: String? = null,
        val ipv6Error: String? = null,
        val hasLoaded: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, ipv4Error = null, ipv6Error = null)
        job = viewModelScope.launch {
            try {
                val result = PublicIpResolver.fetchAll()
                _state.value = UiState(
                    loading = false,
                    ipv4 = result.ipv4.ip,
                    ipv6 = result.ipv6.ip,
                    ipv4Geo = result.ipv4.geo,
                    ipv6Geo = result.ipv6.geo,
                    ipv4Error = result.ipv4.error,
                    ipv6Error = result.ipv6.error,
                    hasLoaded = true
                )
            } catch (e: Exception) {
                val msg = Errors.formatAndLog("PublicIP", e)
                _state.value = _state.value.copy(loading = false, ipv4Error = msg, hasLoaded = true)
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = _state.value.copy(loading = false)
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
