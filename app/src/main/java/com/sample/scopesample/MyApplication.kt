package com.sample.scopesample

import android.app.Application
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class MyApplication : Application() {

    // 1. 定义专属单线程池，用于分发 App 级初始化任务
    // 通过自定义 ThreadFactory 命名，方便在线程快照中识别：MyApplication-Thread
    private val appExecutor = Executors.newFixedThreadPool(1) { runnable ->
        Thread(runnable, "MyApplication-Thread")
    }

    // 2. 创建应用级作用域，显式绑定自定义分发器
    // 这样该 Scope 下启动的所有协程默认都在 "MyApplication-Thread" 上运行，实现了与系统池的物理隔离
    private val applicationScope = CoroutineScope(SupervisorJob() + appExecutor.asCoroutineDispatcher())

    override fun onCreate() {
        super.onCreate()
        Log.d("ScopeSample", "MyApplication: Starting background tasks...")
        startAppTasks()
    }

    private fun startAppTasks() {
        // 直接在绑定的自定义分发线程上启动控制逻辑
        applicationScope.launch {
            Log.d("ScopeSample", "App distributor running on: ${Thread.currentThread().name}")

            val taskTimes = listOf(1000L, 2000L, 3000L, 4000L)
            taskTimes.forEach { time ->
                launch {
                    // 任务会从 "MyApplication-Thread" 分发并精确切换到 "TaskRunner-Pool-x"
                    val result = TaskRunner.test(time)
                    Log.d("ScopeSample", "[AppTask Result] $result")
                }
            }
        }
    }
}
