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

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long>, ChatMemoryRepository {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
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

    @Override
    default void saveAll(String conversationId, List<Message> messages) {
        this.deleteBySessionId(conversationId);
        List<ChatMessage> entities = messages.stream().map(e -> {
            ChatMessage msg = new ChatMessage();
            msg.setSessionId(conversationId);
            msg.setRole(e.getMessageType().getValue());
            msg.setContent(e.getText());
            return msg;
        }).toList();
        this.saveAll(entities);
    }

    @Override
    default void deleteByConversationId(String conversationId) {
        this.deleteBySessionId(conversationId);
    }
}
