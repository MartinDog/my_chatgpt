package com.mychatgpt.service;

import com.mychatgpt.entity.ChatSession;
import com.mychatgpt.repository.ChatMessageRepository;
import com.mychatgpt.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    public ChatSession createSession(String userId) {
        return createSession(userId, null, null);
    }

    public ChatSession createSession(String userId, String title, String systemPrompt) {
        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTitle(title);
        session.setSystemPrompt(systemPrompt);
        return sessionRepository.save(session);
    }

    public ChatSession updateSession(String sessionId, String title, String systemPrompt) {
        ChatSession session = getSession(sessionId);
        if (title != null) {
            session.setTitle(title);
        }
        if (systemPrompt != null) {
            session.setSystemPrompt(systemPrompt);
        }
        return sessionRepository.save(session);
    }

    public ChatSession getSession(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId));
    }

    public List<ChatSession> getUserSessions(String userId) {
        return sessionRepository.findByUserId(userId);
    }

    @Transactional
    public void deleteSession(String sessionId) {
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteBySessionId(sessionId);
    }
}
