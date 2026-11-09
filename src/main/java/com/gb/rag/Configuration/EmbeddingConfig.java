package com.gb.rag.Configuration;

import com.gb.rag.Enum.EmbedResult;
import com.gb.rag.Enum.EmbedTask;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Configuration
public class EmbeddingConfig {
    @Bean public BlockingQueue<EmbedTask> inputQueue() { return new LinkedBlockingQueue<>(); }
    @Bean public BlockingQueue<EmbedResult> outputQueue() { return new LinkedBlockingQueue<>(); }
    // 生产者限流器：控制"存"的速率
    @Bean
    public RateLimiter producerRateLimiter() {
        return RateLimiter.create(50.0);  // 每秒最多存 10 条
    }

    // 消费者限流器：控制"写"的速率
    @Bean
    public RateLimiter consumerRateLimiter() {
        return RateLimiter.create(32.0);  // 每秒最多处理 32 条
    }

    @Bean
    public ExecutorService embeddingExecutor() {
        return Executors.newFixedThreadPool(1);  // 1 个线程
    }
}