package com.mychatgpt.event;

import com.mychatgpt.service.VectorDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationEventListener {

    private final VectorDbService vectorDbService;

    @Async
    @EventListener
    public void handleConversationCompleted(ConversationCompletedEvent event) {
        try {
            vectorDbService.storeConversation(
                    event.getSessionId(), event.getUserId(), "user", event.getUserMessage());
            vectorDbService.storeConversation(
                    event.getSessionId(), event.getUserId(), "assistant", event.getAssistantMessage());
            log.info("Conversation stored in vector DB (sessionId: {})", event.getSessionId());
        } catch (Exception e) {
            log.warn("Failed to store conversation in vector DB: {}", e.getMessage());
        }
    }
}