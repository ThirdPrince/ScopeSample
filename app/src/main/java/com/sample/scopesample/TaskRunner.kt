package com.sample.scopesample

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * 专门封装耗时任务逻辑的执行器
 */
object TaskRunner {
    // 封装线程池，不再由外部直接管理
    private val myExecutor = Executors.newFixedThreadPool(10)
    private val dispatcher = myExecutor.asCoroutineDispatcher()

    /**
     * 执行耗时测试任务并返回执行结果
     */
    suspend fun test(time: Long): String = withContext(dispatcher) {
        // 模拟阻塞 (Thread.sleep)
        Thread.sleep(time)
        "End with $time on thread: ${Thread.currentThread().name}"
    }

    /**
     * 释放资源，关闭线程池
     */
    fun shutdown() {
        myExecutor.shutdown()
    }
}
