package com.mychatgpt.ai;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Random;

/**
 * 로컬 임베딩 서비스 - DJL과 Sentence Transformers 모델 사용.
 *
 * 사용 모델: sentence-transformers/all-MiniLM-L6-v2
 * - 384 차원 임베딩
 * - 빠른 추론 속도
 * - 다국어 지원 (한국어 포함)
 *
 * OpenAI 임베딩 대비 장점:
 * - 네트워크 비용 없음
 * - API 비용 없음
 * - 오프라인 사용 가능
 * - 낮은 지연 시간
 */
@Service
@Slf4j
public class LocalEmbeddingService implements EmbeddingService {

    private static final String MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2";
    private static final int EMBEDDING_DIMENSION = 384;
    private static final int MAX_SEQUENCE_LENGTH = 256;

    @Value("${embedding.model.path:#{null}}")
    private String modelPath;

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private HuggingFaceTokenizer tokenizer;

    @PostConstruct
    public void init() {
        try {
            log.info("로컬 임베딩 모델 로딩 시작: {}", MODEL_NAME);
            loadModel();
            log.info("로컬 임베딩 모델 로딩 완료. 차원: {}", EMBEDDING_DIMENSION);
        } catch (Exception e) {
            log.error("임베딩 모델 로딩 실패. 폴백으로 간단한 임베딩 사용", e);
        }
    }

    private void loadModel() throws ModelNotFoundException, MalformedModelException, IOException {
        // HuggingFace 토크나이저 로드
        tokenizer = HuggingFaceTokenizer.builder()
                .optTokenizerName(MODEL_NAME)
                .optMaxLength(MAX_SEQUENCE_LENGTH)
                .optPadToMaxLength()
                .optTruncation(true)
                .build();

        // 모델 로드 기준 설정
        Criteria<String, float[]> criteria = Criteria.builder()
                .optApplication(Application.NLP.TEXT_EMBEDDING)
                .setTypes(String.class, float[].class)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/" + MODEL_NAME)
                .optTranslator(new SentenceTransformerTranslator(tokenizer))
                .optEngine("PyTorch")
                .optProgress(new ai.djl.training.util.ProgressBar())
                .build();

        model = criteria.loadModel();
        predictor = model.newPredictor();
    }

    @Override
    public float[] getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[EMBEDDING_DIMENSION];
        }

        // 텍스트 길이 제한 (토큰 제한 초과 방지)
        String truncatedText = truncateText(text, MAX_SEQUENCE_LENGTH * 4);

        try {
            if (predictor != null) {
                return predictor.predict(truncatedText);
            } else {
                // 폴백: 간단한 해시 기반 임베딩 (모델 로딩 실패 시)
                return fallbackEmbedding(truncatedText);
            }
        } catch (Exception e) {
            log.warn("임베딩 생성 실패, 폴백 사용: {}", e.getMessage());
            return fallbackEmbedding(truncatedText);
        }
    }

    @Override
    public int getEmbeddingDimension() {
        return EMBEDDING_DIMENSION;
    }

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    /**
     * 텍스트를 최대 길이로 자른다.
     */
    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    /**
     * 폴백 임베딩 - 모델 로딩 실패 시 사용.
     * 간단한 해시 기반 임베딩으로 의미적 유사성은 떨어지지만 동작은 함.
     */
    private float[] fallbackEmbedding(String text) {
        float[] embedding = new float[EMBEDDING_DIMENSION];
        int hash = text.hashCode();
        Random random = new Random(hash);

        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            embedding[i] = (random.nextFloat() - 0.5f) * 2;
        }

        // 정규화
        float norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= norm;
            }
        }

        return embedding;
    }

    @PreDestroy
    public void cleanup() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
        if (tokenizer != null) {
            tokenizer.close();
        }
        log.info("임베딩 모델 리소스 정리 완료");
    }

    /**
     * Sentence Transformer 모델용 Translator.
     * 입력 텍스트를 토큰화하고 출력을 임베딩 벡터로 변환.
     */
    private static class SentenceTransformerTranslator implements Translator<String, float[]> {

        private final HuggingFaceTokenizer tokenizer;

        public SentenceTransformerTranslator(HuggingFaceTokenizer tokenizer) {
            this.tokenizer = tokenizer;
        }

        @Override
        public NDList processInput(TranslatorContext ctx, String input) {
            ai.djl.huggingface.tokenizers.Encoding encoding = tokenizer.encode(input);

            NDManager manager = ctx.getNDManager();
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[] tokenTypeIds = encoding.getTypeIds();

            NDArray inputIdsArray = manager.create(inputIds).expandDims(0);
            NDArray attentionMaskArray = manager.create(attentionMask).expandDims(0);
            NDArray tokenTypeIdsArray = manager.create(tokenTypeIds).expandDims(0);

            return new NDList(inputIdsArray, attentionMaskArray, tokenTypeIdsArray);
        }

        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            // Mean pooling: 모든 토큰의 평균을 계산
            NDArray lastHiddenState = list.get(0);

            // [batch, seq_len, hidden_size] -> [batch, hidden_size]
            NDArray meanPooled = lastHiddenState.mean(new int[]{1});

            // 정규화
            NDArray norm = meanPooled.norm(new int[]{-1}, true);
            NDArray normalized = meanPooled.div(norm.add(1e-12f));

            return normalized.toFloatArray();
        }

        @Override
        public Batchifier getBatchifier() {
            return null;
        }
    }
}
