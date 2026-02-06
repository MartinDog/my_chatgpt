package com.mychatgpt.ai;

/**
 * 텍스트를 벡터 임베딩으로 변환하는 서비스 인터페이스.
 *
 * 구현체:
 * - LocalEmbeddingService: 로컬 모델 사용 (DJL + Sentence Transformers)
 * - OpenAI embedding: OpenAiClient.getEmbedding() (deprecated)
 */
public interface EmbeddingService {

    /**
     * 텍스트를 벡터 임베딩으로 변환한다.
     *
     * @param text 임베딩할 텍스트
     * @return 임베딩 벡터 (float 배열)
     */
    float[] getEmbedding(String text);

    /**
     * 임베딩 벡터의 차원 수를 반환한다.
     *
     * @return 벡터 차원 수
     */
    int getEmbeddingDimension();

    /**
     * 사용 중인 모델 이름을 반환한다.
     *
     * @return 모델 이름
     */
    String getModelName();
}
