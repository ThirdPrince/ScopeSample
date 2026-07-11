package com.sample.scopesample

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

class FlowViewModel : ViewModel() {

    private val _logText = MutableStateFlow("Flow Demo Ready...\n")
    val logText: StateFlow<String> = _logText.asStateFlow()

    fun clearLogs() {
        _logText.value = "Logs cleared.\n"
    }

    private fun appendLog(msg: String) {
        Log.d("FlowDemo", msg)
        _logText.update { it + msg + "\n" }
    }

    private fun createCountingFlow(name: String) = flow {
        appendLog(">>> [$name] 上游尝试启动...")
        delay(2000) 
        appendLog(">>> [$name] 上游正式生产数据")
        var count = 0
        try {
            while (true) {
                emit(count++)
                delay(1000)
            }
        } finally {
            appendLog(">>> [$name] 上游停止生产 (finally 块被触发)")
        }
    }

    // 1. Eagerly: 立即启动，永不停止
    val eagerlyFlow: StateFlow<Int> = createCountingFlow("Eagerly").stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 0
    )

    // 2. Lazily: 第一次订阅时启动，永不停止
    val lazilyFlow: StateFlow<Int> = createCountingFlow("Lazily").stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = 0
    )

    // 3. WhileSubscribed(5000): 历史值永久保留
    val whileSubscribedKeepFlow: StateFlow<Int> = createCountingFlow("WS_Keep").stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // 4. WhileSubscribed(5000, 0): 历史值立即过期
    val whileSubscribedExpiredFlow: StateFlow<Int> = createCountingFlow("WS_Expire").stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000, 0),
        initialValue = 0
    )
}
