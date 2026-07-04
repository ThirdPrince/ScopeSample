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

    fun runThreadDemo() {
        // 文章优化点：直接在 viewModelScope (Main线程) 启动
        // 依靠 TaskRunner 内部的 withContext 进行线程精确切换
        viewModelScope.launch {
            appendLog("--- Initial Thread Info (Filtered) ---")
            appendLog(getThreadInfoString())
            appendLog("---------------------------\n")

            val taskTimes = listOf(1000L, 2000L, 3000L, 4000L)
            
            // 这里不再使用 launch(Dispatchers.IO)
            // 这样就不会多占 DefaultDispatcher 的 Worker 线程
            launch(Dispatchers.IO) {
                taskTimes.forEach { time ->
                    launch {
                        // TaskRunner 内部会自己处理切换到 TaskRunner-Pool-x
                        val result = TaskRunner.test(time)
                        appendLog(result)
                    }
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
        // 实际上 TaskRunner 是单例，这里可以不关，或者改为由 MyApplication 管理
    }
}
