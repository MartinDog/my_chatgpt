package com.mychatgpt.service;

import com.mychatgpt.dto.ChatRequest;
import com.mychatgpt.dto.AiChatResponse;
import com.mychatgpt.entity.ChatMessage;
import com.mychatgpt.event.ConversationCompletedEvent;
import com.mychatgpt.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final int VECTOR_DB_STORE_THRESHOLD = 70;

    public AiChatResponse chat(ChatRequest request) {
        AiChatResponse aiChatResponse =
            chatClient.prompt()
                .user(request.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, request.getSessionId()))
                .call().entity(AiChatResponse.class);

        int relevanceScore = aiChatResponse.getRelevanceScore();
        log.info("Conversation relevance score: {} (threshold: {})", relevanceScore, VECTOR_DB_STORE_THRESHOLD);

        if (relevanceScore >= VECTOR_DB_STORE_THRESHOLD) {
            eventPublisher.publishEvent(new ConversationCompletedEvent(
                    request.getSessionId(), request.getUserId(),
                    request.getMessage(), aiChatResponse.getMessage()));
            log.info("Conversation event published for async vector DB storage (score: {})", relevanceScore);
        } else {
            log.info("Conversation skipped for vector DB storage (score: {} < {})", relevanceScore, VECTOR_DB_STORE_THRESHOLD);
        }

        return new AiChatResponse(
                request.getSessionId(),
                aiChatResponse.getMessage(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                relevanceScore
        );
    }

    public List<ChatMessage> getHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}
