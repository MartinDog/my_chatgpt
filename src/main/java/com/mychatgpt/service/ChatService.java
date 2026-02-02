package com.mychatgpt.service;

import com.mychatgpt.ai.ChatCompletionResult;
import com.mychatgpt.ai.OpenAiClient;
import com.mychatgpt.dto.ChatRequest;
import com.mychatgpt.dto.ChatResponse;
import com.mychatgpt.entity.ChatMessage;
import com.mychatgpt.entity.ChatSession;
import com.mychatgpt.repository.ChatMessageRepository;
import com.mychatgpt.vectordb.VectorSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final OpenAiClient openAiClient;
    private final ChatMessageRepository messageRepository;
    private final ChatSessionService sessionService;
    private final VectorDbService vectorDbService;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful AI assistant. You answer questions based on provided context and conversation history.
            If relevant context is provided from the knowledge base, use it to inform your answers.
            Always be honest about what you know and don't know.
            Respond in the same language as the user's message.
            """;

    public ChatResponse chat(ChatRequest request) {
        // Validate session
        ChatSession session = sessionService.getSession(request.getSessionId());

        // Load previous messages from DB
        List<ChatMessage> history = messageRepository.findBySessionIdOrderByCreatedAtAsc(request.getSessionId());

        // Search relevant context from vector DB
        List<VectorSearchResult> relevantDocs = vectorDbService.searchRelevantContext(
                request.getMessage(), request.getUserId(), 5);

        // Get system prompt (custom or default)
        String systemPrompt = session.getSystemPrompt() != null && !session.getSystemPrompt().isBlank()
                ? session.getSystemPrompt()
                : DEFAULT_SYSTEM_PROMPT;

        // Build messages for OpenAI
        List<Map<String, String>> messages = buildMessages(history, relevantDocs, request.getMessage(), systemPrompt);

        // Call OpenAI
        ChatCompletionResult result = openAiClient.chatCompletion(messages, true);
        String aiResponse = result.getContent();

        // Save user message
        saveMessage(request.getSessionId(), "user", request.getMessage());

        // Save assistant message
        saveMessage(request.getSessionId(), "assistant", aiResponse);

        // Store conversation in vector DB (async would be better, but keeping it simple)
        try {
            vectorDbService.storeConversation(
                    request.getSessionId(), request.getUserId(), "user", request.getMessage());
            vectorDbService.storeConversation(
                    request.getSessionId(), request.getUserId(), "assistant", aiResponse);
        } catch (Exception e) {
            log.warn("Failed to store conversation in vector DB: {}", e.getMessage());
        }

        return new ChatResponse(
                request.getSessionId(),
                aiResponse,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    public List<ChatMessage> getHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    private List<Map<String, String>> buildMessages(List<ChatMessage> history,
                                                     List<VectorSearchResult> relevantDocs,
                                                     String userMessage,
                                                     String systemPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();

        // System prompt with context
        StringBuilder systemContent = new StringBuilder(systemPrompt);

        if (!relevantDocs.isEmpty()) {
            systemContent.append("\n\n--- Relevant Context from Knowledge Base ---\n");
            for (VectorSearchResult doc : relevantDocs) {
                systemContent.append("- ").append(doc.getDocument()).append("\n");
            }
            systemContent.append("--- End of Context ---\n");
        }

        messages.add(Map.of("role", "system", "content", systemContent.toString()));

        // Add conversation history (last 20 messages to control token usage)
        int start = Math.max(0, history.size() - 20);
        for (int i = start; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        // Add current user message
        messages.add(Map.of("role", "user", "content", userMessage));

        return messages;
    }

    private void saveMessage(String sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        messageRepository.save(message);
    }
}
