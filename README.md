# ScopeSample: 协程调度陷阱与 Flow 共享策略深度解析

本项目通过一个直观的 Android 示例，揭示了协程开发中两个极其隐蔽的性能与逻辑坑：**调度冗余导致的线程膨胀**，以及 **`stateIn` 共享流的生命周期失控**。

---

## 1. 模拟任务背景

项目中模拟了 **8 个并发后台任务**，通过自定义具名线程池来观察真实的执行轨迹。

### A. 核心执行逻辑 (`TaskRunner.kt`)
采用“异步执行模型”，内部封装了私有线程池，并负责环境自声明。
```kotlin
object TaskRunner {
    // 独占 10 个线程，具名化：TaskRunner-Pool-x
    private val myExecutor = Executors.newFixedThreadPool(10) { r ->
        Thread(r, "TaskRunner-Pool-${threadId.getAndIncrement()}")
    }
    private val dispatcher = myExecutor.asCoroutineDispatcher()

    suspend fun test(time: Long): String = withContext(dispatcher) {
        Thread.sleep(time) // 模拟真实的 IO 阻塞
        "End with $time on thread: ${Thread.currentThread().name}"
    }
}
```

### B. 任务分布
*   **Application 层**：启动 4 个全局初始化任务。
*   **ViewModel 层**：启动 4 个并发业务请求。

---

## 2. 调度冗余分析：为何线程数会“爆炸”？

### 场景一：ViewModel 中的 `launch(Dispatchers.IO)`
```kotlin
// ❌ 陷阱：多余的“中间商”
viewModelScope.launch(Dispatchers.IO) { 
    repeat(4) {
        launch { // 隐式继承父协程，又向系统申请了 4 个 Worker
            TaskRunner.test(1000) // 内部再次切换到私有池
        }
    }
}
```

### 场景二：Application 中的 `CoroutineScope(Dispatchers.IO)`
给全局作用域绑定 `Dispatchers.IO` 会让极轻量的分发逻辑去挤占公共资源，导致所有任务默认在系统池排队。

### 🚩 现场日志采样 (陷阱场景)
虽然任务最终运行在私有池，但 `DefaultDispatcher` 却产生了大量无用的“搬运工”线程：
```text
Filtered thread count: 17 (Total: 30)
1. [ID: 1805] MyApplication-Thread | WAITING
...
6. [ID: 1811] DefaultDispatcher-worker-1 | TIMED_WAITING  <-- 搬运工 1 号
7. [ID: 1812] DefaultDispatcher-worker-2 | RUNNABLE       <-- 搬运工 2 号
...
```

---

## 3. Flow 共享策略：`stateIn` 的深度控制

项目中通过 `FlowViewModel` 演示了 `SharingStarted` 三种策略对上游流生命周期的精确控制。

### A. 策略选择指南
| 策略 | 启动时机 | 停止时机 | 适用场景 |
| :--- | :--- | :--- | :--- |
| **Eagerly** | 立即启动 | 永不停止 (随 Scope 销毁) | 必须全局活跃的数据 |
| **Lazily** | 首次订阅启动 | 永不停止 | 懒加载，一旦启动不再关闭 |
| **WhileSubscribed** | 有订阅者时启动 | 订阅者清零并超时 | **UI 绑定的最优解** |

### B. WhileSubscribed 的“防抖”与“过期”
`WhileSubscribed(stopTimeoutMillis, replayExpirationMillis)`

1.  **stopTimeoutMillis (5000ms)**：当用户切到后台（订阅者清零），上游会多存活 5 秒。这防止了屏幕旋转或短暂切换 App 导致的频繁重启。
2.  **replayExpirationMillis (0ms)**：上游停止后，缓存的值立刻过期。
    *   **现象验证**：我们在上游增加了 `delay(2000)`。
    *   **正常情况**：重新进入页面，先看到旧值，2s 后变新值。
    *   **设置过期后**：重新进入页面，缓存被清空，直接看到初始值 0，确保了数据的新鲜度。

---

## 4. 优化方案与总结

### ✅ 去除 `Dispatchers.IO` 外壳
*   **ViewModel**：直接在 `viewModelScope`（主线程）分发。任务直达私有池，跳过中间商。
*   **Application**：使用单线程具名调度器 `MyApplication-Thread` 实现真正的资源隔离。

### 🔍 最终结论
1.  **Dispatcher 继承陷阱**：在自定义池内部已实现 `withContext` 时，外部不要再包 `Dispatchers.IO`。
2.  **环境自包含**：挂起函数应内部负责执行环境。
3.  **缓存意识**：理解 `stateIn` 的过期机制，避免 UI 展示过时的陈旧数据。

**性能优化不是开更多的线程，而是为了效率而消除不必要的上下文切换。**
