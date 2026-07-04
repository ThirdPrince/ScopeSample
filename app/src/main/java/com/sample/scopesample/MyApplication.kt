package com.sample.scopesample

import android.app.Application
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class MyApplication : Application() {

    // 1. 定义专属线程池，通过 ThreadFactory 命名，文章中可以对比默认命名的 pool-x-thread-y
    private val appExecutor = Executors.newFixedThreadPool(1) { runnable ->
        Thread(runnable, "MyApplication-Thread")
    }

    // 2. 创建应用级作用域。注意：这里直接绑定了 appExecutor，
    // 这样该 Scope 下启动的所有协程默认都在 "MyApplication-Thread" 上运行。
    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d("ScopeSample", "MyApplication: Starting background tasks...")
        startAppTasks()
    }

    private fun startAppTasks() {
        // 文章分析点：
        // 这里不再嵌套使用 launch(Dispatchers.IO)，而是直接在 applicationScope 启动。
        // 这能有效避免 Dispatchers.IO 产生不必要的 Worker 线程（搬运工线程）。
        applicationScope.launch {
            Log.d("ScopeSample", "App control logic running on: ${Thread.currentThread().name}")


                val taskTimes = listOf(1000L, 2000L, 3000L, 4000L)
                taskTimes.forEach { time ->
                    launch {
                        // TaskRunner.test 内部会再次切换到它自己的线程池
                        // 这种“父协程在 A 池，挂起函数切到 B 池”的模式是资源隔离的最佳实践。
                        Log.d("ScopeSample", "[AppTask Result] start")
                        val result = TaskRunner.test(time)
                        Log.d("ScopeSample", "[AppTask Result] $result")
                    }
                }


        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // 系统资源紧张时可以考虑取消作用域
        // applicationScope.cancel()
    }
}
