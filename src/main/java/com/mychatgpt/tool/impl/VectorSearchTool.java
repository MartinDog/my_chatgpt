package com.mychatgpt.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychatgpt.service.VectorDbService;
import com.mychatgpt.tool.ChatTool;
import com.mychatgpt.tool.ToolDefinition;
import com.mychatgpt.vectordb.VectorSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class VectorSearchTool implements ChatTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final VectorDbService vectorDbService;

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "vector_search",
                "사용자의 지식 베이스(업로드된 파일, 이전 대화 내용 등)에서 관련 정보를 검색합니다. 사용자가 저장한 문서나 과거 대화에서 정보를 찾을 때 사용하세요.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "검색할 내용 (자연어 검색어)"
                                ),
                                "userId", Map.of(
                                        "type", "string",
                                        "description", "검색 대상 사용자 ID"
                                ),
                                "maxResults", Map.of(
                                        "type", "integer",
                                        "description", "반환할 최대 결과 수 (기본값: 5)",
                                        "default", 5
                                )
                        ),
                        "required", List.of("query", "userId")
                )
        );
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            JsonNode args = OBJECT_MAPPER.readTree(argumentsJson);
            String query = args.path("query").asText();
            String userId = args.path("userId").asText();
            int maxResults = args.path("maxResults").asInt(DEFAULT_MAX_RESULTS);

            if (query.isBlank() || userId.isBlank()) {
                return "오류: query와 userId는 필수 파라미터입니다.";
            }

            List<VectorSearchResult> results = vectorDbService.searchRelevantContext(query, userId, maxResults);

            if (results.isEmpty()) {
                return "검색 결과가 없습니다. 해당 사용자의 지식 베이스에 관련 정보가 없습니다.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("검색 결과 (").append(results.size()).append("건):\n\n");

            for (int i = 0; i < results.size(); i++) {
                VectorSearchResult result = results.get(i);
                sb.append("--- 결과 ").append(i + 1).append(" ---\n");
                sb.append("내용: ").append(result.getDocument()).append("\n");
                if (result.getMetadata() != null) {
                    String source = result.getMetadata().get("source");
                    if (source != null) {
                        sb.append("출처: ").append(source).append("\n");
                    }
                }
                sb.append("관련도 점수: ").append(String.format("%.4f", 1.0 - result.getDistance())).append("\n\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Vector search error", e);
            return "검색 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
