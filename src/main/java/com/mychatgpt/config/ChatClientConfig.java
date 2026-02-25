package com.mychatgpt.config;

import com.mychatgpt.tool.impl.CalculatorTools;
import com.mychatgpt.tool.impl.CurrentTimeTools;
import com.mychatgpt.tool.impl.KnowledgeBaseSearchTools;
import com.mychatgpt.tool.impl.VectorSearchTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

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
            
            If you get a question which is not related to the provided context, provide it 
            
            IMPORTANT: At the very end of every response, you MUST score the relevance score

            Scoring criteria:
            - 90-100: Reusable high-value info (work processes, technical docs, queries, architecture decisions)
            - 70-89: Specific work-related Q&A (debugging help, code explanations, tool usage)
            - 30-69: General questions or simple information lookups
            - 0-29: Greetings, small talk, simple confirmations, casual chat

            The score tag must be the very last thing in your response. Do not explain the score.
            """;
    @Bean
    public ChatClient chatClient(ChatModel chatModel,
                                  VectorStore vectorStore,
                                  CalculatorTools calculatorTools,
                                  CurrentTimeTools currentTimeTools,
                                  VectorSearchTools vectorSearchTools,
                                  KnowledgeBaseSearchTools knowledgeBaseSearchTools,
                                  ChatMemory    chatMemory
    ) {

        return ChatClient.builder(chatModel)
                .defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .defaultTools(calculatorTools, currentTimeTools,
                              vectorSearchTools, knowledgeBaseSearchTools)
                .defaultAdvisors(
                    MessageChatMemoryAdvisor.builder(
                        chatMemory
                    ).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(5)
                                        .build())
                                .build())
                .build();
    }
}
