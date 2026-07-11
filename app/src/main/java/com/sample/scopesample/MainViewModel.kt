package com.sample.scopesample

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainViewModel : ViewModel() {

    private val _logText = MutableStateFlow("Article Demo Ready...\n")
    val logText: StateFlow<String> = _logText.asStateFlow()

    /**
     * 【场景 A：陷阱版本】
     * 在已经具备异步能力的 TaskRunner 外层，多余地嵌套了 launch(Dispatchers.IO)。
     * 结果：会导致系统产生大量仅起“搬运任务”作用的空闲 Worker 线程。
     */
    fun runTrapDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            appendLog("\n>>> [TRAP] 场景开始：嵌套了 launch(Dispatchers.IO)")
            appendLog(getThreadInfoString())

            val taskTimes = listOf(1000L, 2000L, 3000L, 4000L)
            taskTimes.forEach { time ->
                launch { // 隐式继承父协程的 Dispatchers.IO
                    val result = TaskRunner.test(time)
                    appendLog(result)
                }
            }

            delay(4500)
            appendLog("\n--- [TRAP] 4.5s 后线程状态 ---")
            appendLog(getThreadInfoString())
        }
    }

    /**
     * 【场景 B：优化版本】
     * 直接在 viewModelScope (Main) 中启动。
     * 结果：任务从主线程直达私有执行池，不占用任何系统 Dispatchers.IO 资源。
     */
    fun runOptimizedDemo() {
        viewModelScope.launch {
            appendLog("\n>>> [OPTIMIZED] 场景开始：直接在主线程作用域分发")
            appendLog(getThreadInfoString())

            val taskTimes = listOf(1000L, 2000L, 3000L, 4000L)
            taskTimes.forEach { time ->
                launch {
                    // TaskRunner 内部通过 withContext 自动完成精确环境切换
                    val result = TaskRunner.test(time)
                    appendLog(result)
                }
            }

            delay(4500)
            appendLog("\n--- [OPTIMIZED] 4.5s 后线程状态 ---")
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
}
