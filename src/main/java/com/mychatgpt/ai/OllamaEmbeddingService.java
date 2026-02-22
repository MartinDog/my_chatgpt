package com.mychatgpt.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OllamaEmbeddingService implements EmbeddingService {

    private static final int EMBEDDING_DIMENSION = 1024;

    private final EmbeddingModel embeddingModel;

    @Override
    public float[] getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[EMBEDDING_DIMENSION];
        }
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.error("임베딩 생성 실패: {}", e.getMessage());
            throw new RuntimeException("임베딩 생성 실패", e);
        }
    }

    @Override
    public int getEmbeddingDimension() {
        return EMBEDDING_DIMENSION;
    }

    @Override
    public String getModelName() {
        return "bge-m3";
    }
}
