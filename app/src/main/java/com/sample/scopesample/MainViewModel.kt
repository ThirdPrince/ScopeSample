package com.sample.scopesample

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel : ViewModel() {

    private val _logText = MutableStateFlow("Initializing...\n")
    val logText: StateFlow<String> = _logText.asStateFlow()

    /**
     * 演示逻辑：直接在 viewModelScope 中分发任务。
     * 由于 TaskRunner.test 内部已经通过 withContext 切换到了私有线程池，
     * 此处无需（也不应）再使用 launch(Dispatchers.IO)。
     */
    fun runThreadDemo() {
        viewModelScope.launch(Dispatchers.IO) {

            val taskTimes = listOf(1000L, 2000L, 3000L, 4000L)
            
            // ✅ 优化：去除 launch(Dispatchers.IO) 嵌套
            // 任务直接从主线程分发给 TaskRunner，避免占用 DefaultDispatcher 的 Worker 线程
            taskTimes.forEach { time ->
                launch {
                    val result = TaskRunner.test(time)
                    appendLog(result)
                }
            }

            delay(4500)
            appendLog("\n--- Threads after 4.5s (Optimized) ---")
            appendLog(getThreadInfoString())
        }
    }

    private fun appendLog(msg: String) {
        Log.d("ScopeSample", msg)
        _logText.update { current -> current + msg + "\n" }
    }

    private fun getThreadInfoString(): String {
        val allThreads = Thread.getAllStackTraces().keys
        val excludeKeywords = listOf(
            "Binder", "main", "Jit thread pool", "Signal Catcher", "HeapTaskDaemon",
            "ReferenceQueueDaemon", "FinalizerDaemon", "FinalizerWatchdogDaemon",
            "Profile Saver", "RenderThread"
        )
        val filteredThreads = allThreads.filter { thread ->
            excludeKeywords.none { keyword -> thread.name.contains(keyword, ignoreCase = true) }
        }
        val sb = StringBuilder()
        sb.append("Filtered thread count: ${filteredThreads.size} (Total: ${allThreads.size})\n")
        filteredThreads.sortedBy { it.id }.forEachIndexed { index, t ->
            sb.append("${index + 1}. [ID: ${t.id}] ${t.name} | ${t.state}\n")
        }
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
