package com.mychatgpt.vectordb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychatgpt.config.ChromaDbConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChromaDbClient {

    private final ChromaDbConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String collectionId;

    @PostConstruct
    public void init() {
        try {
            ensureCollection();
            log.info("ChromaDB collection '{}' ready (id: {})", config.getCollectionName(), collectionId);
        } catch (Exception e) {
            log.warn("ChromaDB initialization deferred - will retry on first use: {}", e.getMessage());
        }
    }

    /**
     * Ensure the collection exists, create if not.
     */
    private void ensureCollection() {
        try {
            // Try to get existing collection
            String response = webClient.get()
                    .uri(config.getBaseUrl() + "/api/v1/collections/" + config.getCollectionName())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(response);
            collectionId = node.path("id").asText();
        } catch (WebClientResponseException.NotFound e) {
            // Create collection
            ObjectNode body = objectMapper.createObjectNode();
            body.put("name", config.getCollectionName());
            body.putObject("metadata").put("hnsw:space", "cosine");

            String response = webClient.post()
                    .uri(config.getBaseUrl() + "/api/v1/collections")
                    .header("Content-Type", "application/json")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            try {
                JsonNode node = objectMapper.readTree(response);
                collectionId = node.path("id").asText();
                log.info("Created ChromaDB collection: {}", config.getCollectionName());
            } catch (Exception ex) {
                throw new RuntimeException("Failed to parse collection creation response", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to ChromaDB: " + e.getMessage(), e);
        }
    }

    private void ensureReady() {
        if (collectionId == null) {
            ensureCollection();
        }
    }

    /**
     * Add documents with embeddings to the collection.
     */
    public void addDocuments(List<String> ids, List<float[]> embeddings,
                             List<String> documents, List<Map<String, String>> metadatas) {
        ensureReady();
        try {
            ObjectNode body = objectMapper.createObjectNode();

            ArrayNode idsArray = body.putArray("ids");
            ids.forEach(idsArray::add);

            ArrayNode embeddingsArray = body.putArray("embeddings");
            for (float[] emb : embeddings) {
                ArrayNode embArray = embeddingsArray.addArray();
                for (float v : emb) {
                    embArray.add(v);
                }
            }

            ArrayNode docsArray = body.putArray("documents");
            documents.forEach(docsArray::add);

            if (metadatas != null) {
                ArrayNode metaArray = body.putArray("metadatas");
                for (Map<String, String> meta : metadatas) {
                    ObjectNode metaNode = metaArray.addObject();
                    meta.forEach(metaNode::put);
                }
            }

            webClient.post()
                    .uri(config.getBaseUrl() + "/api/v1/collections/" + collectionId + "/add")
                    .header("Content-Type", "application/json")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } catch (Exception e) {
            log.error("Failed to add documents to ChromaDB", e);
            throw new RuntimeException("VectorDB에 문서 추가 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Query similar documents by embedding.
     */
    public List<VectorSearchResult> query(float[] queryEmbedding, int nResults, Map<String, String> whereFilter) {
        ensureReady();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode queryEmbs = body.putArray("query_embeddings").addArray();
            for (float v : queryEmbedding) {
                queryEmbs.add(v);
            }
            body.put("n_results", nResults);
            body.putArray("include").add("documents").add("metadatas").add("distances");

            if (whereFilter != null && !whereFilter.isEmpty()) {
                ObjectNode where = body.putObject("where");
                whereFilter.forEach(where::put);
            }

            String responseStr = webClient.post()
                    .uri(config.getBaseUrl() + "/api/v1/collections/" + collectionId + "/query")
                    .header("Content-Type", "application/json")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode response = objectMapper.readTree(responseStr);
            List<VectorSearchResult> results = new ArrayList<>();

            JsonNode idsNode = response.path("ids").get(0);
            JsonNode docsNode = response.path("documents").get(0);
            JsonNode distancesNode = response.path("distances").get(0);
            JsonNode metadatasNode = response.path("metadatas").get(0);

            if (idsNode != null) {
                for (int i = 0; i < idsNode.size(); i++) {
                    VectorSearchResult result = new VectorSearchResult();
                    result.setId(idsNode.get(i).asText());
                    result.setDocument(docsNode.get(i).asText());
                    result.setDistance(distancesNode.get(i).asDouble());
                    if (metadatasNode.get(i) != null) {
                        Map<String, String> meta = new java.util.HashMap<>();
                        metadatasNode.get(i).fields().forEachRemaining(
                                entry -> meta.put(entry.getKey(), entry.getValue().asText())
                        );
                        result.setMetadata(meta);
                    }
                    results.add(result);
                }
            }

            return results;

        } catch (Exception e) {
            log.error("ChromaDB query failed", e);
            return new ArrayList<>();
        }
    }

    /**
     * Delete documents by IDs.
     */
    public void deleteByIds(List<String> ids) {
        ensureReady();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode idsArray = body.putArray("ids");
            ids.forEach(idsArray::add);

            webClient.post()
                    .uri(config.getBaseUrl() + "/api/v1/collections/" + collectionId + "/delete")
                    .header("Content-Type", "application/json")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } catch (Exception e) {
            log.error("ChromaDB delete failed", e);
            throw new RuntimeException("VectorDB 문서 삭제 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Delete documents by metadata filter.
     */
    public void deleteByFilter(Map<String, String> whereFilter) {
        ensureReady();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode where = body.putObject("where");
            whereFilter.forEach(where::put);

            webClient.post()
                    .uri(config.getBaseUrl() + "/api/v1/collections/" + collectionId + "/delete")
                    .header("Content-Type", "application/json")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } catch (Exception e) {
            log.error("ChromaDB delete by filter failed", e);
            throw new RuntimeException("VectorDB 필터 삭제 실패: " + e.getMessage(), e);
        }
    }
}
