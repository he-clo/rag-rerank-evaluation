# EmbeddingPipeline 说明文档

## 概述

`EmbeddingPipeline` 是一个基于**生产者-消费者模型**的 embedding 处理流水线，支持：

- 批量攒批（32 条/批）
- 超时触发（1 秒）
- 双限流器（生产者 / 消费者差速）
- 多 embedding 策略可插拔（通过 `EmbeddFactory`）
- 背压控制（队列满自动阻塞）

本文档说明其设计、问题及改进方案。

---

## 目录

- [1. 架构概览](#1-架构概览)
- [2. 核心组件](#2-核心组件)
- [3. 数据流](#3-数据流)
- [4. 攒批与超时机制](#4-攒批与超时机制)
- [5. 毒丸机制](#5-毒丸机制)
- [6. 双限流器与差速](#6-双限流器与差速)
- [7. 多策略支持（EmbeddFactory）](#7-多策略支持embeddfactory)
- [8. 原实现的问题](#8-原实现的问题)
- [9. 改进方案](#9-改进方案)
- [10. 配置参考](#10-配置参考)

---

## 1. 架构概览

```
主线程/生产者 ──put──→ inputQueue ──take/poll──→ 消费线程 ──embed──→ outputQueue ──take──→ 主线程/存储
                        (BlockingQueue)            (攒批+超时)           (BlockingQueue)
                                                  ↓
                                           EmbeddFactory.getEmbedd(type)
```

- **inputQueue**：存储待 embedding 的文本
- **outputQueue**：存储 embedding 后的向量结果
- **消费线程**：从 inputQueue 取数据，攒批后调用 embedding，结果放入 outputQueue
- **双限流器**：producerRateLimiter 控制生产速率，consumerRateLimiter 控制消费速率

---

## 2. 核心组件

| 组件 | 类型 | 作用 |
|---|---|---|
| `EmbeddingPipeline` | `@Component` | 流水线主类，管理生产/消费逻辑 |
| `inputQueue` | `BlockingQueue<String>` | 输入队列，存储待处理文本 |
| `outputQueue` | `BlockingQueue<float[]>` | 输出队列，存储向量结果 |
| `producerRateLimiter` | `RateLimiter` | 生产者限速器 |
| `consumerRateLimiter` | `RateLimiter` | 消费者限速器 |
| `executor` | `ExecutorService` | 消费线程池 |
| `embeddFactory` | `EmbeddFactory` | embedding 策略工厂 |
| `fileType` | `FileType` | embedding 策略类型（枚举） |

---

## 3. 数据流

### 3.1 生产（submit）

```java
public void submit(List<String> texts) throws InterruptedException {
    for (String text : texts) {
        producerRateLimiter.acquire();  // 生产限速
        inputQueue.put(text);           // 放入输入队列（满则阻塞）
    }
    inputQueue.put(POISON_PILL);        // 投毒丸，通知消费结束
}
```

### 3.2 消费（consume）

```java
private void consume(FileType fileType) throws InterruptedException {
    List<String> batch = new ArrayList<>(BATCH_SIZE);
    long lastFlush = System.currentTimeMillis();

    while (!Thread.currentThread().isInterrupted()) {
        long remaining = TIMEOUT - (System.currentTimeMillis() - lastFlush);
        if (remaining <= 0) remaining = 1;

        String task = inputQueue.poll(remaining, TimeUnit.MILLISECONDS);
        if (task != null) {
            if (task == POISON_PILL) break;
            batch.add(task);
        }
        boolean full = batch.size() >= BATCH_SIZE;
        boolean timeout = System.currentTimeMillis() - lastFlush >= TIMEOUT;
        if ((full || timeout) && !batch.isEmpty()) {
            flush(batch, fileType);
            lastFlush = System.currentTimeMillis();
        }
    }
    if (!batch.isEmpty()) flush(batch, fileType);  // 收尾
}
```

### 3.3 flush

```java
private void flush(List<String> batch, FileType type) throws InterruptedException {
    consumerRateLimiter.acquire(batch.size());
    List<float[]> vectors = embeddFactory.getEmbedd(type).embedding(batch);
    for (float[] v : vectors) outputQueue.put(v);
    batch.clear();
}
```

### 3.4 采集（collect）

```java
public List<float[]> collect(int expectedSize) throws InterruptedException {
    List<float[]> results = new ArrayList<>(expectedSize);
    for (int i = 0; i < expectedSize; i++) {
        results.add(outputQueue.take());
    }
    return results;
}
```

---

## 4. 攒批与超时机制

### 4.1 双触发策略

| 触发条件 | 行为 |
|---|---|
| `batch.size() >= BATCH_SIZE`（32 条） | 立即 flush |
| 距离上次 flush 超过 `TIMEOUT`（1000ms） | 超时 flush（即使不足 32 条） |

### 4.2 poll 的语义

```java
inputQueue.poll(remaining, TimeUnit.MILLISECONDS);
```

- 在 `remaining` 时间内有数据 → 立即返回
- 超时 → 返回 `null`，触发超时 flush

**这是"软超时"，不是硬卡死。**

---

## 5. 毒丸机制

### 5.1 为什么需要毒丸

消费线程是死循环，`inputQueue.take()` 会一直阻塞等待。生产结束后，需要一种方式通知消费线程"下班了"。

### 5.2 毒丸定义

```java
private static final String POISON_PILL = new String("__POISON__");
```

用 `new String(...)` 保证**不在字符串常量池**，避免与真实文本冲突。

### 5.3 使用

```java
// 生产者投毒
inputQueue.put(POISON_PILL);

// 消费者识别
if (task == POISON_PILL) break;  // 用 == 比较对象身份
```

### 5.4 多消费者

如果有 N 个消费线程，需投 N 颗毒丸，每个线程吃到一颗后退出。

---

## 6. 双限流器与差速

### 6.1 设计

| 限流器 | 控制对象 | 默认值 |
|---|---|---|
| `producerRateLimiter` | 生产速率（存 inputQueue） | 10 QPS |
| `consumerRateLimiter` | 消费速率（调 embedding） | 32 QPS |

两个限流器**独立维护令牌桶**，互不影响，由此形成**差速**。

### 6.2 差速效果

| 场景 | producer | consumer | 效果 |
|---|---|---|---|
| 生产慢消费快 | 10 | 32 | 队列常空，消费等数据 |
| 生产快消费慢 | 50 | 20 | 队列堆积，靠容量(50)背压 |
| 均衡 | 32 | 32 | 同步走 |

### 6.3 背压

当 inputQueue 满（50 条），`put()` 自动阻塞，防止 OOM。

---

## 7. 多策略支持（EmbeddFactory）

### 7.1 设计

```java
embeddFactory.getEmbedd(type).embedding(batch);
```

通过 `FileType` 枚举选择不同的 embedding 实现（如 bge-m3、OpenAI、mxbai 等）。

### 7.2 策略选择

原实现通过 `setType()` 设置 `fileType`，flush 时根据 `fileType` 选择策略：

```java
List<float[]> vectors = embeddFactory.getEmbedd(type).embedding(batch);
```

---

## 8. 原实现的问题

### 8.1 fileType 线程安全问题（致命）

```java
private FileType fileType;  // 非 final，非 volatile

@PostConstruct
public void start() {
    executor.execute(() -> consume(fileType));  // 另一线程读
}

public void setType(FileType fileType) {  // 主线程写
    this.fileType = fileType;
}
```

**问题**：
1. `fileType` 无可见性保证，消费线程可能永远看到 `null`
2. `@PostConstruct` 在 Spring 初始化时就执行，此时 `fileType` 还是 `null`
3. `setType()` 是事后调用，时序上已来不及

### 8.2 @PostConstruct 启动 + setType 后设值 = 时序陷阱

消费线程启动时 `fileType = null`，必然 NPE。

### 8.3 单例可变状态导致并发问题

多个调用方用不同 `fileType` 调用 `submit()`，共享同一 inputQueue 和消费线程，数据流混在一起。

### 8.4 consume 中 throw RuntimeException 吞掉中断

```java
} catch (InterruptedException e) {
    throw new RuntimeException(e);  // 中断状态丢失
}
```

### 8.5 异常吞掉

`flush` 中若 embedding 调用失败，异常会一路冒泡到 `consume`，最终被包成 RuntimeException，无日志记录。

---

## 9. 改进方案

### 9.1 核心思路

**把 fileType 从"类成员变量"改为"跟着任务走"**，消除共享可变状态。

### 9.2 任务对象携带策略

```java
public class EmbedTask {
    public final String text;
    public final FileType fileType;
    public static final EmbedTask POISON_PILL = new EmbedTask(null, null);

    public EmbedTask(String text, FileType fileType) {
        this.text = text;
        this.fileType = fileType;
    }
}
```

### 9.3 submit 带类型

```java
public void submit(List<String> texts, FileType fileType) throws InterruptedException {
    for (String text : texts) {
        producerRateLimiter.acquire();
        inputQueue.put(new EmbedTask(text, fileType));  // fileType 跟着任务走
    }
    inputQueue.put(EmbedTask.POISON_PILL);
}
```

### 9.4 flush 按策略分组

```java
private void flush(List<EmbedTask> batch) throws InterruptedException {
    Map<FileType, List<EmbedTask>> groups = batch.stream()
            .collect(Collectors.groupingBy(t -> t.fileType));

    for (Map.Entry<FileType, List<EmbedTask>> entry : groups.entrySet()) {
        FileType type = entry.getKey();
        List<EmbedTask> group = entry.getValue();
        consumerRateLimiter.acquire(group.size());
        List<String> texts = group.stream().map(t -> t.text).collect(Collectors.toList());
        List<float[]> vectors = embeddFactory.getEmbedd(type).embedding(texts);
        for (int i = 0; i < group.size(); i++) {
            outputQueue.put(new EmbedResult(texts.get(i), vectors.get(i)));
        }
    }
    batch.clear();
}
```

### 9.5 构造器注入 + final

```java
public EmbeddingPipeline(BlockingQueue<EmbedTask> inputQueue,
                         BlockingQueue<EmbedResult> outputQueue,
                         @Qualifier("producerRateLimiter") RateLimiter producerRateLimiter,
                         @Qualifier("consumerRateLimiter") RateLimiter consumerRateLimiter,
                         ExecutorService embeddingExecutor,
                         EmbeddFactory embeddFactory) {
    this.inputQueue = inputQueue;
    this.outputQueue = outputQueue;
    this.producerRateLimiter = producerRateLimiter;
    this.consumerRateLimiter = consumerRateLimiter;
    this.executor = embeddingExecutor;
    this.embeddFactory = embeddFactory;
}
```

### 9.6 手动启动

```java
public void start() {
    executor.execute(this::consume);  // 无参方法引用
}
```

### 9.7 异常处理

```java
private void consume() {
    try {
        // ... 消费逻辑
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();  // 恢复中断状态
        break;
    } catch (Exception e) {
        log.error("consume 处理异常", e);   // 日志记录
    }
}
```

### 9.8 改进前后对照

| 问题 | 原实现 | 改进后 |
|---|---|---|
| fileType 线程安全 | ❌ 非 final，无可见性 | ✅ 跟任务走，无共享 |
| 启动时 fileType 为 null | ❌ NPE | ✅ 任务自带类型 |
| 并发混流 | ❌ 单例可变状态 | ✅ 任务隔离 |
| 中断处理 | ❌ 包成 RuntimeException | ✅ 恢复中断状态 |
| 依赖注入 | 字段注入 | 构造器注入 + final |
| 多策略支持 | 单次只能一种 | 同批可混多种策略 |

---

## 10. 配置参考

### 10.1 application.yml

```yaml
embedding:
  batch-size: 32          # 攒批大小
  timeout-ms: 1000        # 超时时间
  queue-capacity: 50      # inputQueue 容量
  producer-rate-limit: 10.0   # 生产者 QPS
  consumer-rate-limit: 32.0   # 消费者 QPS
  collect-batch-size: 16  # 每次 collect 取多少条
  strategies:
    bge-m3:
      enabled: true
    openai:
      enabled: false
```

### 10.2 超时时间建议

本地 bge-m3 单次 batch 请求约 250~650ms，建议：

| 场景 | 超时设置 |
|---|---|
| 数据密集持续流入 | 500~1000ms |
| 数据稀疏 | 1000~2000ms |
| 实时查询 | < 500ms |

### 10.3 collect-batch-size 建议

根据目标向量库的批量写入最优大小设置：

| 向量库 | 建议 N |
|---|---|
| Milvus / Qdrant | 16~100 |
| pgvector | 50~200 |
| Elasticsearch | 100~500 |

---

## 附录：关键设计决策

| 决策 | 原因 |
|---|---|
| 用 BlockingQueue | 自带阻塞/唤醒，简化线程协作 |
| 毒丸而非 shutdown | 允许重复使用，优雅结束 |
| 双限流器 | 生产与消费解耦，可独立调优 |
| 攒批 + 超时 | 兼顾吞吐与延迟 |
| 任务携带策略 | 消除共享状态，支持多策略混批 |
| 构造器注入 | 不可变、线程安全、易测试 |

---

> **文档版本**：v1.0
> **最后更新**：2026-10-13
