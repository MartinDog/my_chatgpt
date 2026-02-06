package com.mychatgpt.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychatgpt.config.OpenAiConfig;
import com.mychatgpt.tool.ToolDefinition;
import com.mychatgpt.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiClient {

    private final OpenAiConfig config;
    private final WebClient webClient;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Chat completion with tool support.
     * Returns the full response JSON node.
     */
    public ChatCompletionResult chatCompletion(List<Map<String, String>> messages, boolean enableTools) {
        try {
            ObjectNode requestBody = buildRequest(messages, enableTools);

            String responseStr = webClient.post()
                    .uri(config.getBaseUrl() + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode response = objectMapper.readTree(responseStr);
            JsonNode choice = response.path("choices").get(0);
            JsonNode message = choice.path("message");

            // Check if tool call is requested
            if (message.has("tool_calls") && !message.path("tool_calls").isEmpty()) {
                return handleToolCalls(messages, message, enableTools);
            }

            String content = message.path("content").asText("");
            return new ChatCompletionResult(content, false);

        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            throw new RuntimeException("AI 응답 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Generate embedding for a text.
     */
    public float[] getEmbedding(String text) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "text-embedding-3-small");
            requestBody.put("input", text);

            String responseStr = webClient.post()
                    .uri(config.getBaseUrl() + "/v1/embeddings")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode response = objectMapper.readTree(responseStr);
            JsonNode embeddingNode = response.path("data").get(0).path("embedding");

            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }
            return embedding;

        } catch (Exception e) {
            log.error("Embedding generation failed", e);
            throw new RuntimeException("임베딩 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildRequest(List<Map<String, String>> messages, boolean enableTools) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", config.getModel());
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 4096);

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
            // Add assistant message with tool_calls to the conversation
            List<Map<String, String>> updatedMessages = new ArrayList<>(originalMessages);

            // We need to add the raw assistant message including tool_calls
            // For simplicity, we'll execute tools and add results, then re-call
            JsonNode toolCalls = assistantMessage.path("tool_calls");
            StringBuilder toolResults = new StringBuilder();

            for (JsonNode toolCall : toolCalls) {
                String functionName = toolCall.path("function").path("name").asText();
                String arguments = toolCall.path("function").path("arguments").asText();

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
                    .uri(config.getBaseUrl() + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode response = objectMapper.readTree(responseStr);
            String content = response.path("choices").get(0).path("message").path("content").asText("");
            return new ChatCompletionResult(content, true);

        } catch (Exception e) {
            log.error("Tool call handling failed", e);
            throw new RuntimeException("도구 실행에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
