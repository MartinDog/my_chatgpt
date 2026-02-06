package com.mychatgpt.controller;

import com.mychatgpt.dto.YouTrackIssueDto;
import com.mychatgpt.service.KnowledgeBaseService;
import com.mychatgpt.vectordb.VectorSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Knowledge Base 관리 API (YouTrack + Confluence).
 *
 * 기존 VectorDbController와의 차이:
 * - VectorDbController: 범용 문서 CRUD. userId 기반, UUID ID, 텍스트 직접 입력.
 * - KnowledgeBaseController: YouTrack/Confluence 전용. 파일 업로드 또는 디렉토리 경로로
 *   일괄 처리하며, 문서 ID를 벡터DB 키로 사용하여 upsert(업데이트) 지원.
 *
 * API 설계:
 * - POST /api/knowledge-base/upload            → xlsx 파일 업로드 & 일괄 저장
 * - POST /api/knowledge-base/ingest-directory  → 디렉토리 경로에서 HTML+XLSX 일괄 저장
 * - POST /api/knowledge-base/ingest-html       → 디렉토리 경로에서 HTML만 일괄 저장
 * - PUT  /api/knowledge-base/issues            → 단건 이슈 upsert (JSON body)
 * - GET  /api/knowledge-base/search            → knowledge base 검색 (YouTrack)
 * - GET  /api/knowledge-base/search/confluence → Confluence 검색
 * - GET  /api/knowledge-base/search/all        → 전체 통합 검색
 * - DELETE /api/knowledge-base/issues/{id}     → 단건 삭제
 * - DELETE /api/knowledge-base/all             → YouTrack 전체 삭제
 * - DELETE /api/knowledge-base/confluence/all  → Confluence 전체 삭제
 */
@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * YouTrack export xlsx 파일을 업로드하여 벡터DB에 저장한다.
     *
     * 같은 ID의 이슈가 이미 존재하면 최신 내용으로 덮어씌워진다 (upsert).
     * → 매번 전체 export를 업로드해도 안전하게 최신 상태 유지 가능.
     *
     * 사용 예시:
     *   curl -X POST http://localhost:8080/api/knowledge-base/upload \
     *        -F "file=@youtrack_export.xlsx"
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadYouTrackExcel(
            @RequestParam("file") MultipartFile file) {

        // 파일 확장자 검증
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "xlsx 파일만 업로드 가능합니다.",
                    "receivedFilename", String.valueOf(originalFilename)
            ));
        }

        try {
            Map<String, Object> result = knowledgeBaseService.ingestYouTrackExcel(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("YouTrack xlsx 처리 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "파일 처리 중 오류 발생: " + e.getMessage()));
        }
    }

    /**
     * 단건 이슈를 upsert한다 (JSON body).
     *
     * xlsx 업로드 없이 API로 직접 이슈를 추가/업데이트할 때 사용.
     * id가 이미 존재하면 해당 문서를 새 내용으로 교체하고,
     * 존재하지 않으면 새로 추가한다.
     *
     * 사용 예시:
     *   curl -X PUT http://localhost:8080/api/knowledge-base/issues \
     *        -H "Content-Type: application/json" \
     *        -d '{"id":"PATALK-1246", "title":"배너 적용", "body":"...", "comments":"..."}'
     */
    @PutMapping("/issues")
    public ResponseEntity<Map<String, String>> upsertIssue(@RequestBody YouTrackIssueDto issue) {
        if (issue.getId() == null || issue.getId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "이슈 ID는 필수입니다."));
        }
        if (issue.getTitle() == null || issue.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "이슈 제목은 필수입니다."));
        }

        try {
            knowledgeBaseService.upsertSingleIssue(issue);
            return ResponseEntity.ok(Map.of(
                    "message", "이슈가 저장(upsert)되었습니다.",
                    "issueId", issue.getId()
            ));
        } catch (Exception e) {
            log.error("이슈 upsert 실패: {}", issue.getId(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "이슈 저장 실패: " + e.getMessage()));
        }
    }

    /**
     * Knowledge base에서 유사 문서를 검색한다.
     *
     * source="youtrack" 필터를 자동 적용하므로,
     * 다른 source(conversation, manual 등)의 데이터는 검색 결과에 포함되지 않는다.
     */
    @GetMapping("/search")
    public ResponseEntity<List<VectorSearchResult>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int nResults) {

        if (query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<VectorSearchResult> results = knowledgeBaseService.searchKnowledgeBase(query, nResults);
        return ResponseEntity.ok(results);
    }

    /**
     * 특정 이슈를 knowledge base에서 삭제한다.
     */
    @DeleteMapping("/issues/{issueId}")
    public ResponseEntity<Map<String, String>> deleteIssue(@PathVariable String issueId) {
        knowledgeBaseService.deleteIssue(issueId);
        return ResponseEntity.ok(Map.of(
                "message", "이슈가 삭제되었습니다.",
                "issueId", issueId
        ));
    }

    /**
     * 모든 YouTrack knowledge base 데이터를 삭제한다.
     * 다른 source의 벡터 데이터(conversation, manual 등)는 보존된다.
     */
    @DeleteMapping("/all")
    public ResponseEntity<Map<String, String>> deleteAll() {
        knowledgeBaseService.deleteAllYouTrackData();
        return ResponseEntity.ok(Map.of("message", "모든 YouTrack knowledge base 데이터가 삭제되었습니다."));
    }

    // ========== Confluence 문서 처리 ==========

    /**
     * 지정된 디렉토리에서 HTML 파일들을 읽어 벡터DB에 저장한다.
     *
     * Confluence에서 export된 HTML 파일들을 파싱하여 의미 있는 데이터만 추출하고
     * 벡터DB에 upsert한다. 같은 ID의 문서가 이미 존재하면 최신 내용으로 덮어씌워진다.
     *
     * 데이터 정제(refining) 과정:
     * - 제목, breadcrumb, 본문 콘텐츠만 추출
     * - 네비게이션, 스타일, 스크립트, 푸터 등 불필요한 요소 제거
     * - HTML 태그를 제거하고 순수 텍스트만 추출
     *
     * 사용 예시:
     *   curl -X POST "http://localhost:8080/api/knowledge-base/ingest-html?path=/Users/hyunkyu/Downloads/IT"
     */
    @PostMapping("/ingest-html")
    public ResponseEntity<Map<String, Object>> ingestHtmlFromDirectory(
            @RequestParam("path") String directoryPath) {

        try {
            Map<String, Object> result = knowledgeBaseService.ingestConfluenceHtmlFromDirectory(directoryPath);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("HTML 디렉토리 처리 실패: {}", directoryPath, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "디렉토리 처리 중 오류 발생: " + e.getMessage()));
        }
    }

    /**
     * 지정된 디렉토리에서 HTML과 XLSX 파일 모두를 읽어 벡터DB에 저장한다.
     *
     * IT 팀의 knowledge base를 한 번에 구축할 때 사용.
     * - HTML 파일: Confluence export 문서 (제목, breadcrumb, 본문 추출)
     * - XLSX 파일: YouTrack export 이슈 (ID, 제목, 본문, 댓글 추출)
     *
     * 사용 예시:
     *   curl -X POST "http://localhost:8080/api/knowledge-base/ingest-directory?path=/Users/hyunkyu/Downloads/IT"
     */
    @PostMapping("/ingest-directory")
    public ResponseEntity<Map<String, Object>> ingestFromDirectory(
            @RequestParam("path") String directoryPath) {

        try {
            Map<String, Object> result = knowledgeBaseService.ingestFromDirectory(directoryPath);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("디렉토리 통합 처리 실패: {}", directoryPath, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "디렉토리 처리 중 오류 발생: " + e.getMessage()));
        }
    }

    /**
     * Confluence knowledge base에서 유사 문서를 검색한다.
     */
    @GetMapping("/search/confluence")
    public ResponseEntity<List<VectorSearchResult>> searchConfluence(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int nResults) {

        if (query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<VectorSearchResult> results = knowledgeBaseService.searchConfluenceKnowledgeBase(query, nResults);
        return ResponseEntity.ok(results);
    }

    /**
     * 모든 knowledge base (YouTrack + Confluence)에서 유사 문서를 검색한다.
     */
    @GetMapping("/search/all")
    public ResponseEntity<List<VectorSearchResult>> searchAll(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int nResults) {

        if (query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<VectorSearchResult> results = knowledgeBaseService.searchAllKnowledgeBase(query, nResults);
        return ResponseEntity.ok(results);
    }

    /**
     * 특정 Confluence 문서를 삭제한다.
     */
    @DeleteMapping("/confluence/{documentId}")
    public ResponseEntity<Map<String, String>> deleteConfluenceDocument(@PathVariable String documentId) {
        knowledgeBaseService.deleteConfluenceDocument(documentId);
        return ResponseEntity.ok(Map.of(
                "message", "Confluence 문서가 삭제되었습니다.",
                "documentId", documentId
        ));
    }

    /**
     * 모든 Confluence knowledge base 데이터를 삭제한다.
     */
    @DeleteMapping("/confluence/all")
    public ResponseEntity<Map<String, String>> deleteAllConfluence() {
        knowledgeBaseService.deleteAllConfluenceData();
        return ResponseEntity.ok(Map.of("message", "모든 Confluence knowledge base 데이터가 삭제되었습니다."));
    }
}
