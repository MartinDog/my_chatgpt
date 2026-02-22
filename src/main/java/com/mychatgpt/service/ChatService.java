package com.mychatgpt.service;

import com.mychatgpt.dto.ChatRequest;
import com.mychatgpt.dto.AiChatResponse;
import com.mychatgpt.entity.ChatMessage;
import com.mychatgpt.entity.ChatSession;
import com.mychatgpt.repository.ChatMessageRepository;
import com.mychatgpt.vectordb.VectorSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMessageRepository messageRepository;
    private final ChatSessionService sessionService;
    private final VectorDbService vectorDbService;

    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int DEFAULT_SEARCH_RESULTS = 5;
    private static final int VECTOR_DB_STORE_THRESHOLD = 70;
    private static final Pattern RELEVANCE_SCORE_PATTERN = Pattern.compile("<relevance_score>(\\d+)</relevance_score>");

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful AI assistant with access to the company's Knowledge Base.

            The Knowledge Base includes:
            - YouTrack 이슈: 업무 요청, 버그 리포트, 개발 히스토리
            - Confluence 문서: 기술 문서, 가이드, 업무 프로세스
            - 이전 대화 내역: 사용자와의 과거 대화

            When answering questions:
            1. If relevant context is provided from the Knowledge Base, use it to inform your answers
            2. When citing information, mention the source (YouTrack 이슈, Confluence 문서 등)
            3. If you need more specific information, use the knowledge_base_search tool
            4. Be honest about what you know and don't know
            5. Respond in the same language as the user's message

            IMPORTANT: At the very end of every response, you MUST include a relevance score tag.
            Evaluate the knowledge value of this conversation and append: <relevance_score>XX</relevance_score>

            Scoring criteria:
            - 90-100: Reusable high-value info (work processes, technical docs, queries, architecture decisions)
            - 70-89: Specific work-related Q&A (debugging help, code explanations, tool usage)
            - 30-69: General questions or simple information lookups
            - 0-29: Greetings, small talk, simple confirmations, casual chat

            The score tag must be the very last thing in your response. Do not explain the score.
            """;

    public AiChatResponse chat(ChatRequest request) {
        ChatSession session = sessionService.getSession(request.getSessionId());

        List<ChatMessage> history = messageRepository.findBySessionIdOrderByCreatedAtAsc(request.getSessionId());

        List<VectorSearchResult> relevantDocs = vectorDbService.searchAllSources(
                request.getMessage(), request.getUserId(), DEFAULT_SEARCH_RESULTS);

        String systemPrompt = session.getSystemPrompt() != null && !session.getSystemPrompt().isBlank()
                ? session.getSystemPrompt()
                : DEFAULT_SYSTEM_PROMPT;

        List<Message> messages = buildMessages(history, relevantDocs, request.getMessage(), systemPrompt);

        AiChatResponse aiChatResponse =
                chatClient.prompt(new Prompt(messages))
                        .call().entity(AiChatResponse.class);

        log.info("Conversation relevance score: {} (threshold: {})", aiChatResponse.getRelevanceScore(), VECTOR_DB_STORE_THRESHOLD);

        saveMessage(request.getSessionId(), "user", request.getMessage());
        saveMessage(request.getSessionId(), "assistant", aiChatResponse.getMessage());

        if (aiChatResponse.getRelevanceScore() >= VECTOR_DB_STORE_THRESHOLD) {
            try {
                vectorDbService.storeConversation(
                        request.getSessionId(), request.getUserId(), "user", request.getMessage());
                vectorDbService.storeConversation(
                        request.getSessionId(), request.getUserId(), "assistant", aiChatResponse.getMessage());
                log.info("Conversation stored in vector DB (score: {})", aiChatResponse.getRelevanceScore());
            } catch (Exception e) {
                log.warn("Failed to store conversation in vector DB: {}", e.getMessage());
            }
        } else {
            log.info("Conversation skipped for vector DB storage (score: {} < {})", aiChatResponse.getRelevanceScore(), VECTOR_DB_STORE_THRESHOLD);
        }

        return new AiChatResponse(
                request.getSessionId(),
                aiChatResponse.getMessage(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                aiChatResponse.getRelevanceScore()
        );
    }

    public List<ChatMessage> getHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    private List<Message> buildMessages(List<ChatMessage> history,
                                         List<VectorSearchResult> relevantDocs,
                                         String userMessage,
                                         String systemPrompt) {
        List<Message> messages = new ArrayList<>();

        StringBuilder systemContent = new StringBuilder(systemPrompt);

        if (!relevantDocs.isEmpty()) {
            systemContent.append("\n\n--- Relevant Context from Knowledge Base ---\n");
            for (VectorSearchResult doc : relevantDocs) {
                String source = doc.getMetadata() != null ? doc.getMetadata().get("source") : "unknown";
                String sourceLabel = switch (source) {
                    case "youtrack" -> "[YouTrack 이슈]";
                    case "confluence" -> "[Confluence 문서]";
                    case "conversation" -> "[이전 대화]";
                    default -> "[문서]";
                };
                systemContent.append(sourceLabel).append(" ").append(doc.getDocument()).append("\n\n");
            }
            systemContent.append("--- End of Context ---\n");
        }

        messages.add(new SystemMessage(systemContent.toString()));

        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        messages.add(new UserMessage(userMessage));

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
