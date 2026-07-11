# ScopeSample: 协程调度陷阱与 Flow 共享策略深度解析

本项目包含两个核心技术演示模块：
1. **线程调度分析**：揭示 `Dispatchers.IO` 嵌套导致的线程虚假膨胀。
2. **Flow 共享策略**：对比 `stateIn` 中不同 `SharingStarted` 参数对生命周期和缓存的影响。

---

## 1. Flow 共享策略演示 (`FlowViewModel`)

我们使用 `flow { ... }` 模拟了一个冷流数据源，并使用 `stateIn` 将其转换为热流 `StateFlow`。

### A. 策略对比

| 策略 | 启动时机 | 停止时机 | 适用场景 |
| :--- | :--- | :--- | :--- |
| **Eagerly** | 立即启动 | 永不停止 (随 Scope 销毁) | 必须全局活跃的数据 |
| **Lazily** | 首次订阅时启动 | 永不停止 | 仅在需要时初始化的数据 |
| **WhileSubscribed** | 有订阅者时启动 | 订阅者清零且超时后停止 | 大多数 UI 绑定场景 |

### B. WhileSubscribed 的深度参数分析
`WhileSubscribed(stopTimeoutMillis, replayExpirationMillis)`

#### 第一个参数：`stopTimeoutMillis` (停止延迟)
* **作用**：当最后一个订阅者离开（如 Activity 切到后台）后，等待多久才关闭上游。
* **目的**：防止配置更改（如屏幕旋转）导致的频繁重启。

#### 第二个参数：`replayExpirationMillis` (缓存过期)
* **作用**：上游停止后，之前缓存的那个值（Replay Value）还能活多久。
* **避坑指南**：如果你发现重新订阅后“没拿到历史值”，通常是因为上游启动太快。
    * **逻辑**：StateFlow 重启时会先发送旧值，但如果上游立即发射了 0，旧值会被瞬间覆盖。
    * **验证**：在我们的演示中，通过给上游增加 `delay(2000)`，你可以清晰看到“旧值出现 -> 2秒后变0”的过程。

---

## 2. 线程调度陷阱分析 (`MainViewModel`)

### 🚀 核心命题
> **当底层逻辑已自带线程池时，上层显式指定 `Dispatchers.IO` 会造成调度冗余，引入不必要的上下文切换。**

### 陷阱场景：Dispatcher 继承导致的线程膨胀
如果在 ViewModel 中错误地使用了 `launch(Dispatchers.IO)`：
```kotlin
// ❌ 冗余代码：产生大量“搬运工”线程
viewModelScope.launch(Dispatchers.IO) { 
    launch { 
        TaskRunner.test(time) // 内部再次切换到私有池
    }
}
```

### 🚩 采样数据 (Trap Scenario)
虽然只有 8 个逻辑任务，但 `DefaultDispatcher` 却产生了一堆处于 `TIMED_WAITING` 的空闲 Worker：
```text
--- Threads (Trap Scenario) ---
Filtered thread count: 17 (Total: 30)
...
6. [ID: 1811] DefaultDispatcher-worker-1 | TIMED_WAITING  <-- 冗余 Worker
7. [ID: 1812] DefaultDispatcher-worker-2 | RUNNABLE       <-- 冗余 Worker
...
```

### ✅ 优化场景：环境自声明
去除不必要的 `Dispatchers.IO` 包裹，任务直接从分发层直达执行层。
```text
--- Threads (Optimized) ---
Filtered thread count: 11 (Total: 22)
(DefaultDispatcher 相关线程几乎消失，资源利用率提升 60%)
```

---

## 🔍 技术总结

1. **挂起函数环境安全化**：`suspend` 函数应内部负责 `withContext`，对调用者透明。
2. **拒绝调度套娃**：识别底层异步模型，保持上层调用纯净。
3. **具名线程池**：通过 `ThreadFactory` 为线程命名（如 `TaskRunner-Pool`），是并发调试的“救命稻草”。
