package com.mychatgpt.config;

import com.mychatgpt.tool.impl.CalculatorTools;
import com.mychatgpt.tool.impl.CurrentTimeTools;
import com.mychatgpt.tool.impl.KnowledgeBaseSearchTools;
import com.mychatgpt.tool.impl.VectorSearchTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    @Bean
    public ChatClient chatClient(ChatModel chatModel,
                                  VectorStore vectorStore,
                                  CalculatorTools calculatorTools,
                                  CurrentTimeTools currentTimeTools,
                                  VectorSearchTools vectorSearchTools,
                                  KnowledgeBaseSearchTools knowledgeBaseSearchTools) {
        return ChatClient.builder(chatModel)
                .defaultTools(calculatorTools, currentTimeTools,
                              vectorSearchTools, knowledgeBaseSearchTools)
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(5)
                                        .build())
                                .build())
                .build();
    }
}
