package com.mychatgpt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * YouTrack에서 export된 이슈 한 건을 표현하는 DTO.
 *
 * xlsx의 각 행(row)이 이 객체 하나에 매핑되며,
 * 벡터DB에 저장하기 전 전처리(toVectorDocument)를 거쳐
 * 하나의 검색 가능한 문서로 변환된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YouTrackIssueDto {

    /** YouTrack 이슈 ID (예: "PATALK-1246") — 벡터DB의 고유 키로 사용 */
    private String id;

    /** 이슈 제목 */
    private String title;

    /** 이슈 본문 (마크다운 형식일 수 있음) */
    private String body;

    /** 댓글 목록 (원본 텍스트 그대로, "[작성자 / 날짜]: 내용" 형식) */
    private String comments;

    /** 우선순위 (Normal, Critical 등) */
    private String priority;

    /** 진행 상태 (Backlog, Staging 등) */
    private String stage;

    /** 업무 요청자 */
    private String requester;

    /** 담당자 */
    private String assignee;

    /** 생성일 */
    private String createdDate;

    /**
     * 벡터DB에 저장할 document 텍스트를 생성한다.
     *
     * 왜 하나의 텍스트로 합치는가:
     * - 임베딩 모델은 하나의 텍스트 입력을 받아 벡터로 변환하므로,
     *   관련 정보(제목, 본문, 댓글)를 하나로 합쳐야 검색 시 맥락이 유지됨
     * - 만약 제목/본문/댓글을 별도 문서로 분리하면 "이 댓글이 어떤 이슈에 대한 것인지" 연결이 끊김
     * - 하나로 합치면 "LG디스플레이 배너"를 검색했을 때 해당 이슈의 댓글까지 함께 조회 가능
     *
     * 구조화 포맷을 사용하는 이유:
     * - "[제목]", "[본문]" 등의 태그로 구분하면 AI가 응답 시 어디까지가 요청이고
     *   어디부터가 처리 결과인지 구별 가능
     * - 비정형 텍스트보다 정형화된 구조가 임베딩 품질과 검색 정확도를 높임
     */
    public String toVectorDocument() {
        StringBuilder sb = new StringBuilder();

        sb.append("[이슈 ID] ").append(id).append("\n");
        sb.append("[제목] ").append(title).append("\n");

        if (body != null && !body.isBlank()) {
            sb.append("[본문]\n").append(body.strip()).append("\n");
        }

        if (comments != null && !comments.isBlank()) {
            sb.append("[댓글]\n").append(comments.strip()).append("\n");
        }

        return sb.toString();
    }
}
