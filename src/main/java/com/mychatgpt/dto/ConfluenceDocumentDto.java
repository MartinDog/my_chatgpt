package com.mychatgpt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Confluence에서 export된 HTML 문서 한 건을 표현하는 DTO.
 *
 * HTML 파일에서 파싱된 데이터가 이 객체에 매핑되며,
 * 벡터DB에 저장하기 전 toVectorDocument()를 통해
 * 검색 가능한 문서 텍스트로 변환된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfluenceDocumentDto {

    /** 문서 고유 ID (파일명에서 추출, 예: "70451688") — 벡터DB의 고유 키로 사용 */
    private String id;

    /** 문서 제목 */
    private String title;

    /** Breadcrumb 경로 (예: "통합지원실-IT센터 > 업무별 공간 > TALKOOL") */
    private String breadcrumb;

    /** 문서 본문 (HTML에서 추출한 텍스트) */
    private String content;

    /** 작성자 */
    private String author;

    /** 마지막 수정일 */
    private String lastModified;

    /** 원본 파일명 */
    private String fileName;

    /**
     * 벡터DB에 저장할 document 텍스트를 생성한다.
     *
     * Confluence 문서의 특성:
     * - breadcrumb은 문서의 카테고리/위치 정보를 담고 있어 검색 맥락에 도움
     * - 본문은 이미 HTML에서 텍스트만 추출된 상태
     * - 제목과 breadcrumb을 함께 포함해 "API 문서" 검색 시 관련 문서를 쉽게 찾도록 함
     */
    public String toVectorDocument() {
        StringBuilder sb = new StringBuilder();

        sb.append("[문서 ID] ").append(id).append("\n");
        sb.append("[제목] ").append(title).append("\n");

        if (breadcrumb != null && !breadcrumb.isBlank()) {
            sb.append("[경로] ").append(breadcrumb).append("\n");
        }

        if (content != null && !content.isBlank()) {
            sb.append("[내용]\n").append(content.strip()).append("\n");
        }

        return sb.toString();
    }
}
