package com.mychatgpt.config;

import com.mychatgpt.repository.ChatMessageRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * date           : 2026-02-25
 * description    : AI에게 이전 대화 내용을 공유하는 ChatMemory config
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2026-02-25        martin5       최초 생성
 */
@Configuration
public class ChatMemoryConfig {
    @Bean
    public ChatMemory chatMemory(ChatMessageRepository chatMessageRepository) {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(chatMessageRepository)
            .maxMessages(20)
            .build();
    }
}
