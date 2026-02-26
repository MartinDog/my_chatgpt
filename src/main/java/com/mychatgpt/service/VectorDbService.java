package com.mychatgpt.service;

import com.mychatgpt.ai.EmbeddingService;
import com.mychatgpt.vectordb.ChromaDbClient;
import com.mychatgpt.vectordb.VectorSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorDbService {

    private final ChromaDbClient chromaDbClient;
    private final EmbeddingService embeddingService;

    /**
     * Store a document in the vector DB.
     */
    @CacheEvict(value = "vectorSearch", allEntries = true)
    public String storeDocument(String content, String userId, String source, Map<String, String> extraMetadata) {
        String docId = UUID.randomUUID().toString();
        float[] embedding = embeddingService.getEmbedding(content);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", userId);
        metadata.put("source", source);
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }

        chromaDbClient.addDocuments(
                List.of(docId),
                List.of(embedding),
                List.of(content),
                List.of(metadata)
        );

        log.info("Stored document {} for user {} from source {}", docId, userId, source);
        return docId;
    }

    /**
     * Store a conversation turn in the vector DB for context retrieval.
     */
    @CacheEvict(value = "vectorSearch", allEntries = true)
    public void storeConversation(String sessionId, String userId, String role, String content) {
        String docId = "conv_" + UUID.randomUUID().toString();
        float[] embedding = embeddingService.getEmbedding(content);

        Map<String, String> metadata = Map.of(
                "userId", userId,
                "sessionId", sessionId,
                "role", role,
                "source", "conversation"
        );

        chromaDbClient.addDocuments(
                List.of(docId),
                List.of(embedding),
                List.of("[" + role + "] " + content),
                List.of(metadata)
        );
    }

    /**
     * Search for relevant context from the vector DB (user's personal data only).
     */
    public List<VectorSearchResult> searchRelevantContext(String query, String userId, int nResults) {
        log.info("[VectorSearch] 개인 컨텍스트 검색: query='{}', userId={}, nResults={}", query, userId, nResults);
        float[] queryEmbedding = embeddingService.getEmbedding(query);
        Map<String, String> filter = Map.of("userId", userId);
        List<VectorSearchResult> results = chromaDbClient.query(queryEmbedding, nResults, filter);
        logSearchResults(results);
        return results;
    }

    /**
     * Search the knowledge base (YouTrack + Confluence) for relevant context.
     * This searches documents stored via KnowledgeBaseService.
     *
     * @param query     검색 쿼리
     * @param nResults  반환할 결과 수
     * @param source    검색할 소스 ("youtrack", "confluence", or null for all)
     * @return 검색 결과 리스트
     */
    public List<VectorSearchResult> searchKnowledgeBase(String query, int nResults, String source) {
        log.info("[VectorSearch] 지식베이스 검색: query='{}', source={}, nResults={}", query, source, nResults);
        float[] queryEmbedding = embeddingService.getEmbedding(query);
        Map<String, String> filter = source != null ? Map.of("source", source) : null;
        List<VectorSearchResult> results = chromaDbClient.query(queryEmbedding, nResults, filter);
        logSearchResults(results);
        return results;
    }

    /**
     * Search all sources: user's data + knowledge base (YouTrack + Confluence).
     * Combines results from multiple queries for comprehensive context.
     *
     * @param query     검색 쿼리
     * @param userId    사용자 ID (사용자 개인 데이터 검색용)
     * @param nResults  각 소스별 반환할 결과 수
     * @return 통합 검색 결과 리스트
     */
    @Cacheable(value = "vectorSearch", key = "#query + '_' + #userId + '_' + #nResults")
    public List<VectorSearchResult> searchAllSources(String query, String userId, int nResults) {
        log.info("[VectorSearch] 전체 소스 검색: query='{}', userId={}, nResults={}", query, userId, nResults);
        float[] queryEmbedding = embeddingService.getEmbedding(query);
        List<VectorSearchResult> allResults = new ArrayList<>();

        // 1. Search knowledge base (YouTrack + Confluence) - no userId filter
        List<VectorSearchResult> kbResults = chromaDbClient.query(queryEmbedding, nResults, null);
        // Filter to only include youtrack and confluence sources
        kbResults.stream()
                .filter(r -> r.getMetadata() != null)
                .filter(r -> {
                    String src = r.getMetadata().get("source");
                    return "youtrack".equals(src) || "confluence".equals(src);
                })
                .forEach(allResults::add);

        // 2. Search user's personal data (userId filter)
        if (userId != null && !userId.isBlank()) {
            Map<String, String> userFilter = Map.of("userId", userId);
            List<VectorSearchResult> userResults = chromaDbClient.query(queryEmbedding, nResults, userFilter);
            allResults.addAll(userResults);
        }

        // Sort by distance (lower is better) and limit total results
        List<VectorSearchResult> finalResults = allResults.stream()
                .sorted(Comparator.comparingDouble(VectorSearchResult::getDistance))
                .limit(nResults * 2L)  // Return up to 2x nResults for comprehensive context
                .toList();

        log.info("[VectorSearch] 전체 소스 검색 완료: {}건 반환", finalResults.size());
        logSearchResults(finalResults);
        return finalResults;
    }

    /**
     * Delete documents by IDs.
     */
    public void deleteDocuments(List<String> ids) {
        chromaDbClient.deleteByIds(ids);
    }

    /**
     * Delete all documents for a user.
     */
    public void deleteUserDocuments(String userId) {
        chromaDbClient.deleteByFilter(Map.of("userId", userId));
    }

    /**
     * Delete all conversation data for a session.
     */
    public void deleteSessionVectors(String sessionId) {
        chromaDbClient.deleteByFilter(Map.of("sessionId", sessionId));
    }

    private void logSearchResults(List<VectorSearchResult> results) {
        if (results.isEmpty()) {
            log.info("[VectorSearch] 결과 없음");
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            VectorSearchResult r = results.get(i);
            String source = r.getMetadata() != null ? r.getMetadata().getOrDefault("source", "-") : "-";
            String docSnippet = r.getDocument() != null
                    ? r.getDocument().substring(0, Math.min(80, r.getDocument().length())).replace("\n", " ")
                    : "";
            log.info("[VectorSearch] [{}] distance={} source={} doc='{}'",
                    i + 1, String.format("%.4f", r.getDistance()), source, docSnippet);
        }
    }
}
