package com.mychatgpt.repository;

import com.mychatgpt.entity.ChatMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.regex.Pattern;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long>, ChatMemoryRepository {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    @Transactional
    void deleteBySessionId(String sessionId);

    @Query("SELECT DISTINCT c.sessionId FROM ChatMessage c")
    List<String> findDistinctSessionIds();

    @Override
    default List<String> findConversationIds() {
        return this.findDistinctSessionIds();
    }

    @Override
    default List<Message> findByConversationId(String conversationId) {
        return this.findBySessionIdOrderByCreatedAtAsc(conversationId).stream().map(e ->
            switch (e.getRole()) {
                case "assistant" -> (Message) new AssistantMessage(e.getContent());
                case "system" -> new SystemMessage(e.getContent());
                default -> new UserMessage(e.getContent());
            }
        ).toList();
    }

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    default void saveAll(String conversationId, List<Message> messages) {
        this.deleteBySessionId(conversationId);
        List<ChatMessage> entities = messages.stream().map(e -> {
            ChatMessage msg = new ChatMessage();
            msg.setSessionId(conversationId);
            msg.setRole(e.getMessageType().getValue());
            String content = e.getText();
            if ("assistant".equals(e.getMessageType().getValue())) {
                content = extractAssistantContent(content);
            }
            msg.setContent(content);
            return msg;
        }).toList();
        this.saveAll(entities);
    }

    default String extractAssistantContent(String raw) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            if (node.has("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return raw;
    }

    @Override
    default void deleteByConversationId(String conversationId) {
        this.deleteBySessionId(conversationId);
    }
}
