package com.mychatgpt.service;

import com.mychatgpt.ai.OpenAiClient;
import com.mychatgpt.vectordb.ChromaDbClient;
import com.mychatgpt.vectordb.VectorSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorDbService {

    private final ChromaDbClient chromaDbClient;
    private final OpenAiClient openAiClient;

    /**
     * Store a document in the vector DB.
     */
    public String storeDocument(String content, String userId, String source, Map<String, String> extraMetadata) {
        String docId = UUID.randomUUID().toString();
        float[] embedding = openAiClient.getEmbedding(content);

        Map<String, String> metadata = new java.util.HashMap<>();
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
    public void storeConversation(String sessionId, String userId, String role, String content) {
        String docId = "conv_" + UUID.randomUUID().toString();
        float[] embedding = openAiClient.getEmbedding(content);

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
     * Search for relevant context from the vector DB.
     */
    public List<VectorSearchResult> searchRelevantContext(String query, String userId, int nResults) {
        float[] queryEmbedding = openAiClient.getEmbedding(query);
        Map<String, String> filter = Map.of("userId", userId);
        return chromaDbClient.query(queryEmbedding, nResults, filter);
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
}
