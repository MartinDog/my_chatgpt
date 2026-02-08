package com.mychatgpt.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychatgpt.config.OllamaConfig;
import com.mychatgpt.tool.ToolDefinition;
import com.mychatgpt.tool.ToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaChatClient {

    private final OllamaConfig config;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        log.info("Ollama 채팅 클라이언트 초기화 완료. 모델: {}, URL: {}", config.getChatModel(), config.getBaseUrl());
    }

    /**
     * Chat completion with tool support via Ollama API.
     */
    public ChatCompletionResult chatCompletion(List<Map<String, String>> messages, boolean enableTools) {
        try {
            ObjectNode requestBody = buildRequest(messages, enableTools);

            String responseStr = webClient.post()
                    .uri("/api/chat")
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode response = objectMapper.readTree(responseStr);
            JsonNode message = response.path("message");

            // Check if tool call is requested
            if (message.has("tool_calls") && !message.path("tool_calls").isEmpty()) {
                return handleToolCalls(messages, message, enableTools);
            }

            String content = message.path("content").asText("");
            return new ChatCompletionResult(content, false);

        } catch (Exception e) {
            log.error("Ollama API 호출 실패", e);
            throw new RuntimeException("AI 응답 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildRequest(List<Map<String, String>> messages, boolean enableTools) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", config.getChatModel());
        requestBody.put("stream", false);

        ObjectNode options = requestBody.putObject("options");
        options.put("temperature", 0.7);
        options.put("num_predict", 4096);

        ArrayNode messagesArray = requestBody.putArray("messages");
        for (Map<String, String> msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.get("role"));
            msgNode.put("content", msg.get("content"));
        }

        if (enableTools) {
            List<ToolDefinition> tools = toolRegistry.getAllToolDefinitions();
            if (!tools.isEmpty()) {
                ArrayNode toolsArray = requestBody.putArray("tools");
                for (ToolDefinition tool : tools) {
                    ObjectNode toolNode = toolsArray.addObject();
                    toolNode.put("type", "function");
                    ObjectNode functionNode = toolNode.putObject("function");
                    functionNode.put("name", tool.getName());
                    functionNode.put("description", tool.getDescription());
                    functionNode.set("parameters", objectMapper.valueToTree(tool.getParameters()));
                }
            }
        }

        return requestBody;
    }

    private ChatCompletionResult handleToolCalls(List<Map<String, String>> originalMessages,
                                                  JsonNode assistantMessage,
                                                  boolean enableTools) {
        try {
            List<Map<String, String>> updatedMessages = new ArrayList<>(originalMessages);

            JsonNode toolCalls = assistantMessage.path("tool_calls");
            StringBuilder toolResults = new StringBuilder();

            for (JsonNode toolCall : toolCalls) {
                String functionName = toolCall.path("function").path("name").asText();
                // Ollama returns arguments as JSON object, convert to string
                JsonNode argumentsNode = toolCall.path("function").path("arguments");
                String arguments = argumentsNode.isTextual() ? argumentsNode.asText() : argumentsNode.toString();

                log.info("Executing tool: {} with args: {}", functionName, arguments);

                String result = toolRegistry.executeTool(functionName, arguments);
                toolResults.append("[Tool: ").append(functionName).append("] ").append(result).append("\n");
            }

            // Add tool results as a user message and re-call
            updatedMessages.add(Map.of("role", "assistant", "content",
                    "I need to use tools to answer this. Let me process the results."));
            updatedMessages.add(Map.of("role", "user", "content",
                    "Tool execution results:\n" + toolResults + "\nPlease provide a final answer based on these results."));

            // Re-call without tools to get final answer
            ObjectNode requestBody = buildRequest(updatedMessages, false);

            String responseStr = webClient.post()
                    .uri("/api/chat")
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode response = objectMapper.readTree(responseStr);
            String content = response.path("message").path("content").asText("");
            return new ChatCompletionResult(content, true);

        } catch (Exception e) {
            log.error("도구 호출 처리 실패", e);
            throw new RuntimeException("도구 실행에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
