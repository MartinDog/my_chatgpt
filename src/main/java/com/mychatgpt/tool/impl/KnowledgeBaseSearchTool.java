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

/**
 * AI가 Knowledge Base (YouTrack 이슈 + Confluence 문서)를 검색할 수 있는 도구.
 *
 * 사용 시나리오:
 * - 사용자가 특정 업무나 기능에 대해 질문할 때
 * - YouTrack 이슈 내역을 참조해야 할 때
 * - Confluence 문서에서 정보를 찾아야 할 때
 *
 * VectorSearchTool과의 차이:
 * - VectorSearchTool: userId 기반 개인 데이터 검색 (업로드한 파일, 대화 내역)
 * - KnowledgeBaseSearchTool: 공용 Knowledge Base 검색 (YouTrack, Confluence)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseSearchTool implements ChatTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final VectorDbService vectorDbService;

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "knowledge_base_search",
                "회사의 Knowledge Base (YouTrack 이슈, Confluence 문서)에서 관련 정보를 검색합니다. " +
                        "업무 관련 질문, 기능 문의, 과거 이슈 조회, 문서 검색 등에 사용하세요.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "검색할 내용 (자연어 검색어). 예: 'LG디스플레이 배너', 'API 인증 방법', '로그인 오류'"
                                ),
                                "source", Map.of(
                                        "type", "string",
                                        "description", "검색할 소스 (선택사항). 'youtrack': YouTrack 이슈만, 'confluence': Confluence 문서만, 미지정: 전체 검색",
                                        "enum", List.of("youtrack", "confluence")
                                ),
                                "maxResults", Map.of(
                                        "type", "integer",
                                        "description", "반환할 최대 결과 수 (기본값: 5)",
                                        "default", 5
                                )
                        ),
                        "required", List.of("query")
                )
        );
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            JsonNode args = OBJECT_MAPPER.readTree(argumentsJson);
            String query = args.path("query").asText();
            String source = args.has("source") && !args.path("source").isNull()
                    ? args.path("source").asText()
                    : null;
            int maxResults = args.path("maxResults").asInt(DEFAULT_MAX_RESULTS);

            if (query.isBlank()) {
                return "오류: query는 필수 파라미터입니다.";
            }

            log.info("Knowledge Base 검색: query='{}', source={}, maxResults={}", query, source, maxResults);

            List<VectorSearchResult> results = vectorDbService.searchKnowledgeBase(query, maxResults, source);

            // Filter results to only include youtrack and confluence if source is not specified
            if (source == null) {
                results = results.stream()
                        .filter(r -> r.getMetadata() != null)
                        .filter(r -> {
                            String src = r.getMetadata().get("source");
                            return "youtrack".equals(src) || "confluence".equals(src);
                        })
                        .toList();
            }

            if (results.isEmpty()) {
                return "검색 결과가 없습니다. Knowledge Base에 관련 정보가 없습니다.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Knowledge Base 검색 결과 (").append(results.size()).append("건):\n\n");

            for (int i = 0; i < results.size(); i++) {
                VectorSearchResult result = results.get(i);
                Map<String, String> metadata = result.getMetadata();

                sb.append("--- 결과 ").append(i + 1).append(" ---\n");

                // Source-specific formatting
                String resultSource = metadata != null ? metadata.get("source") : "unknown";
                if ("youtrack".equals(resultSource)) {
                    sb.append("[YouTrack 이슈]\n");
                    if (metadata.get("issueId") != null) {
                        sb.append("이슈 ID: ").append(metadata.get("issueId")).append("\n");
                    }
                    if (metadata.get("title") != null && !metadata.get("title").isBlank()) {
                        sb.append("제목: ").append(metadata.get("title")).append("\n");
                    }
                    if (metadata.get("stage") != null && !metadata.get("stage").isBlank()) {
                        sb.append("상태: ").append(metadata.get("stage")).append("\n");
                    }
                    if (metadata.get("assignee") != null && !metadata.get("assignee").isBlank()) {
                        sb.append("담당자: ").append(metadata.get("assignee")).append("\n");
                    }
                } else if ("confluence".equals(resultSource)) {
                    sb.append("[Confluence 문서]\n");
                    if (metadata.get("title") != null && !metadata.get("title").isBlank()) {
                        sb.append("제목: ").append(metadata.get("title")).append("\n");
                    }
                    if (metadata.get("breadcrumb") != null && !metadata.get("breadcrumb").isBlank()) {
                        sb.append("경로: ").append(metadata.get("breadcrumb")).append("\n");
                    }
                }

                sb.append("내용:\n").append(result.getDocument()).append("\n");
                sb.append("관련도 점수: ").append(String.format("%.4f", 1.0 - result.getDistance())).append("\n\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Knowledge Base 검색 오류", e);
            return "검색 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
