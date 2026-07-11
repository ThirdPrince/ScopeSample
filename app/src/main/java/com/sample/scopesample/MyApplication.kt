package com.sample.scopesample

import android.app.Application
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class MyApplication : Application() {

    // 1. 定义专属单线程池，用于分发 App 级任务
    private val appExecutor = Executors.newFixedThreadPool(1) { runnable ->
        Thread(runnable, "MyApplication-Thread")
    }

    // 2. 创建应用级作用域，显式绑定自定义分发器
    private val applicationScope = CoroutineScope(SupervisorJob()+ appExecutor.asCoroutineDispatcher())

    override fun onCreate() {
        super.onCreate()
        Log.d("ScopeSample", "MyApplication: Starting background tasks...")
      //  startAppTasks()
    }

    private fun startAppTasks() {
        // 直接在绑定的自定义线程上分发任务
        applicationScope.launch {
            Log.d("ScopeSample", "App distributor running on: ${Thread.currentThread().name}")

            val taskTimes = listOf(1000L, 2000L, 2500L,3000L, 4000L)
            taskTimes.forEach { time ->
                launch {
                    // 任务会从 "MyApplication-Thread" 精确切换到 "TaskRunner-Pool-x"
                    val result = TaskRunner.test(time)
                    Log.d("ScopeSample", "[AppTask Result] $result")
                }
            }
            delay(5000)
            Log.d("ScopeSample", "App distributor finished.")

        }
    }
}
