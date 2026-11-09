package com.gb.rag.Service;

import com.gb.rag.DocuEmbedd.EmbeddFactory;
import com.gb.rag.Enum.EmbedResult;
import com.gb.rag.Enum.EmbedTask;
import com.gb.rag.Enum.FileType;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EmbeddingPipeline {
    private final BlockingQueue<EmbedTask> inputQueue;
    private final BlockingQueue<EmbedResult> outputQueue;
    private final RateLimiter producerRateLimiter;
    private final RateLimiter consumerRateLimiter;
    private final ExecutorService executor;

    private final EmbeddFactory embeddFactory;

    // ★ 构造器注入（final，线程安全，不用 @Autowired 字段注入）
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

    @Value("${ollama.batch-size}")
    private Integer BATCH_SIZE;
    @Value("${ollama.timeout-ms}")
    private long TIMEOUT;

    // ★ 手动启动，不要 @PostConstruct
    public void start() {
        executor.execute(this::consume);  // 无参方法引用，干净
    }

    // 生产者：每次调用都带 fileType，存进任务里
    public void submit(List<String> texts, FileType fileType) throws InterruptedException {
        for (String text : texts) {
            producerRateLimiter.acquire();
            inputQueue.put(new EmbedTask(text, fileType));
        }
        inputQueue.put(EmbedTask.POISON_PILL);
    }

    // 消费：从任务里读 fileType，不依赖外部状态
    private void consume() {
        List<EmbedTask> batch = new ArrayList<>(BATCH_SIZE);
        long lastFlush = System.currentTimeMillis();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                long remaining = TIMEOUT - (System.currentTimeMillis() - lastFlush);
                if (remaining <= 0) remaining = 1;

                EmbedTask task = inputQueue.poll(remaining, TimeUnit.MILLISECONDS);
                if (task==EmbedTask.POISON_PILL){
                    if (!batch.isEmpty()) { flush(batch); lastFlush=System.currentTimeMillis(); }
                    outputQueue.put(EmbedResult.POISON_PILL);
                    continue;
                }
                if(task!=null)batch.add(task);
                boolean full = batch.size() >= BATCH_SIZE;
                boolean timeout = System.currentTimeMillis() - lastFlush >= TIMEOUT;
                if ((full || timeout) && !batch.isEmpty()) {
                    flush(batch);
                    lastFlush = System.currentTimeMillis();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                batch.clear();
                break;
            } catch (Exception e) {
                batch.clear();
                log.error("consume 处理异常", e);
            }
        }
    }

    // ★ flush 按 fileType 分组，同批可混多种策略
    private void flush(List<EmbedTask> batch) throws InterruptedException {

        // 按策略分组
        Map<FileType, List<EmbedTask>> groups = batch.stream()
                .collect(Collectors.groupingBy(t -> t.fileType));

        v1:for (Map.Entry<FileType, List<EmbedTask>> entry : groups.entrySet()) {
            FileType type = entry.getKey();
            List<EmbedTask> group = entry.getValue();
            consumerRateLimiter.acquire(group.size());

            List<String> texts = group.stream().map(t -> t.text).collect(Collectors.toList());
            Float[] vectors = embeddFactory.getEmbedd(type).embedding(texts);

            for (int i = 0; i < group.size(); i++) {
                outputQueue.put(new EmbedResult(texts.get(i), vectors));
            }
        }
        batch.clear();
    }

    // 采集：按 N 分批拿 + 立即处理
    public List<EmbedResult> collect() throws InterruptedException, ExecutionException {
        List<EmbedResult> callback=new ArrayList<>();
        FutureTask<List<EmbedResult>> task = new FutureTask<>(() -> {
                while (!outputQueue.isEmpty()) {
                    EmbedResult poll = null;
                    try {
                        poll = outputQueue.poll(300, TimeUnit.MILLISECONDS);
                        if (poll == null) break;
                        if(poll == EmbedResult.POISON_PILL) break;
                        callback.add(poll);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                return callback;
            });
            new Thread(task).start();
            return task.get();

    }
}