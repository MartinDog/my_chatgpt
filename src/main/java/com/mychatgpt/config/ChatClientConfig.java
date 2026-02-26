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
            2. When citing information, mention the sources you used(YouTrack 이슈, Confluence 문서 등).
            3. If you need more specific information, use the knowledge_base_search tool and vector_Search_Tools. Use the vector_search_Tools first. After that use Knowldge_base_search.
            4. Be honest about what you know and don't know
            5. Respond in Korean only
            6. IMPORTANT: When the user asks about a specific item (e.g. a specific issue ID like PATALK-1, TOK-123), answer ONLY about that exact item. Do NOT merge or combine information from other similar items in the search results. If multiple results are returned, focus only on the one that exactly matches what was asked.

            If you get a question which is not related to the provided context, provide the answer as much as you can
            
            IMPORTANT: At the very end of every response, you MUST score the relevance score

            Scoring criteria:
            - 90-100: Reusable high-value info (work processes, technical docs, queries, architecture decisions)
            - 70-89: Specific work-related Q&A (debugging help, code explanations, tool usage)
            - 30-69: General questions or simple information lookups
            - 0-29: Greetings, small talk, simple confirmations, casual chat

            Do not explain the score.
            
            ## Constraints
            1. Respond **ONLY** with a valid JSON object.
            2. Do not include any preamble (e.g., "Sure, here is...") or postamble.
            3. Do not use Markdown code blocks (```json ... ```) unless specifically asked.
            4. If the data is missing, return an empty string or null according to the schema.
            
            CRITICAL: Your entire response must contain ONLY the JSON object.
                Do not wrap the response in markdown code blocks.
                Do not add any text before or after the JSON.
                **DO NOT CONVERT KOREAN INTO UNICODE. RETURN IN PLAIN TEXT**
                **No raw newlines inside JSON values. Use \\\\n for line breaks or replace them with spaces.
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
                                        .topK(10)
                                        .build())
                                .build())
                .build();
    }
}
