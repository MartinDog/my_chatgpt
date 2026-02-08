package com.mychatgpt.ai;

import com.mychatgpt.config.OllamaConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Ollama 임베딩 서비스 - bge-m3 모델 사용.
 *
 * 사용 모델: bge-m3
 * - 1024 차원 임베딩
 * - 100+ 언어 지원 (한국어 포함)
 * - 최대 8192 토큰 처리
 *
 * Ollama REST API를 통해 임베딩 생성.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OllamaEmbeddingService implements EmbeddingService {

    private static final int EMBEDDING_DIMENSION = 1024;

    private final OllamaConfig ollamaConfig;
    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(ollamaConfig.getBaseUrl())
                .build();
        log.info("Ollama 임베딩 서비스 초기화 완료. 모델: {}, URL: {}", ollamaConfig.getEmbeddingModel(), ollamaConfig.getBaseUrl());
    }

    @Override
    public float[] getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[EMBEDDING_DIMENSION];
        }

        try {
            Map<String, Object> request = Map.of(
                    "model", ollamaConfig.getEmbeddingModel(),
                    "input", text
            );

            Map response = webClient.post()
                    .uri("/api/embed")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("embeddings")) {
                List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
                if (!embeddings.isEmpty()) {
                    List<Double> embedding = embeddings.get(0);
                    float[] result = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        result[i] = embedding.get(i).floatValue();
                    }
                    return result;
                }
            }

            log.warn("Ollama 임베딩 응답이 비어있습니다.");
            return new float[EMBEDDING_DIMENSION];
        } catch (Exception e) {
            log.error("Ollama 임베딩 생성 실패: {}", e.getMessage());
            throw new RuntimeException("임베딩 생성 실패", e);
        }
    }

    @Override
    public int getEmbeddingDimension() {
        return EMBEDDING_DIMENSION;
    }

    @Override
    public String getModelName() {
        return ollamaConfig.getEmbeddingModel();
    }
}
