package com.mychatgpt.tool.impl;

import com.mychatgpt.service.VectorDbService;
import com.mychatgpt.vectordb.VectorSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseSearchTools {

    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final double SIMILARITY_THRESHOLD = 0.5;

    /** YouTrack 이슈 ID 패턴: 대문자 프로젝트코드 + 하이픈 + 숫자 (예: PATALK-123, TOK-45) */
    private static final Pattern ISSUE_ID_PATTERN = Pattern.compile("[A-Z]+-\\d+");

    private final VectorDbService vectorDbService;

    @Tool(description = "회사의 Knowledge Base (YouTrack 이슈, Confluence 문서)에서 관련 정보를 검색합니다. 업무 관련 질문, 기능 문의, 과거 이슈 조회, 문서 검색 등에 사용하세요.")
    public String knowledgeBaseSearch(
            @ToolParam(description = "검색할 내용 (자연어 검색어). 예: 'LG디스플레이 배너', 'API 인증 방법', '로그인 오류'") String query,
            @ToolParam(description = "검색할 소스 (선택사항). 'youtrack': YouTrack 이슈만, 'confluence': Confluence 문서만, 미지정: 전체 검색", required = false) String source,
            @ToolParam(description = "반환할 최대 결과 수 (기본값: 5)", required = false) Integer maxResults) {
        try {
            if (query == null || query.isBlank()) {
                return "오류: query는 필수 파라미터입니다.";
            }

            int limit = (maxResults != null) ? maxResults : DEFAULT_MAX_RESULTS;

            log.info("Knowledge Base 검색: query='{}', source={}, maxResults={}", query, source, limit);

            // 이슈 ID 패턴 감지 시 메타데이터 정확 검색 우선 시도 (youtrack 또는 전체 검색일 때만)
            if (!"confluence".equals(source)) {
                Matcher matcher = ISSUE_ID_PATTERN.matcher(query.toUpperCase());
                if (matcher.find()) {
                    String issueId = matcher.group();
                    log.info("이슈 ID 패턴 감지: '{}' → 정확 검색 시도", issueId);
                    List<VectorSearchResult> exactResults = vectorDbService.searchByIssueId(issueId);
                    if (!exactResults.isEmpty()) {
                        log.info("이슈 ID 정확 검색 성공: {}", issueId);
                        return formatResults(exactResults);
                    }
                    log.info("이슈 ID 정확 검색 결과 없음, 의미론적 검색으로 대체: {}", issueId);
                }
            }

            List<VectorSearchResult> results = vectorDbService.searchKnowledgeBase(query, limit, source);

            results = results.stream()
                    .filter(r -> (1.0 - r.getDistance()) >= SIMILARITY_THRESHOLD)
                    .filter(r -> r.getMetadata() != null)
                    .filter(r -> {
                        if (source != null) return true;
                        String src = r.getMetadata().get("source");
                        return "youtrack".equals(src) || "confluence".equals(src);
                    })
                    .toList();

            if (results.isEmpty()) {
                return "검색 결과가 없습니다. Knowledge Base에 관련 정보가 없습니다.";
            }

            return formatResults(results);
        } catch (Exception e) {
            log.error("Knowledge Base 검색 오류", e);
            return "검색 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    private String formatResults(List<VectorSearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Knowledge Base 검색 결과 (").append(results.size()).append("건):\n\n");

        for (int i = 0; i < results.size(); i++) {
            VectorSearchResult result = results.get(i);
            Map<String, String> metadata = result.getMetadata();

            sb.append("--- 결과 ").append(i + 1).append(" ---\n");

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
            double similarity = result.getDistance() == 0.0 ? 1.0 : (1.0 - result.getDistance());
            sb.append("관련도 점수: ").append(String.format("%.4f", similarity)).append("\n\n");
        }

        return sb.toString();
    }
}
