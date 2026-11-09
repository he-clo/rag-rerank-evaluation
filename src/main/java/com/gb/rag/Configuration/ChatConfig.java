package com.gb.rag.Configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    /**
     * Spring Boot 会自动注册 ChatClient.Builder（前提是引入了 starter 并配了 API key）
     * 我们用它来构建一个 ChatClient Bean
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是一个有帮助的AI助手")
                .build();
    }
}