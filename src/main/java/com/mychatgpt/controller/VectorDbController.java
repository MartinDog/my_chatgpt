package com.mychatgpt.controller;

import com.mychatgpt.service.VectorDbService;
import com.mychatgpt.vectordb.VectorSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vectordb")
@RequiredArgsConstructor
public class VectorDbController {

    private final VectorDbService vectorDbService;

    /**
     * Manually add data to the vector DB.
     */
    @PostMapping("/documents")
    public ResponseEntity<Map<String, String>> addDocument(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        String userId = request.get("userId");
        String source = request.getOrDefault("source", "manual");

        if (content == null || userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "content와 userId는 필수입니다."));
        }

        String docId = vectorDbService.storeDocument(content, userId, source, null);
        return ResponseEntity.ok(Map.of("documentId", docId, "message", "문서가 저장되었습니다."));
    }

    /**
     * Search the vector DB.
     */
    @PostMapping("/search")
    public ResponseEntity<List<VectorSearchResult>> search(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        String userId = request.get("userId");
        int nResults = Integer.parseInt(request.getOrDefault("nResults", "5"));

        if (query == null || userId == null) {
            return ResponseEntity.badRequest().build();
        }

        List<VectorSearchResult> results = vectorDbService.searchRelevantContext(query, userId, nResults);
        return ResponseEntity.ok(results);
    }

    /**
     * Delete specific documents from the vector DB.
     */
    @DeleteMapping("/documents")
    public ResponseEntity<Map<String, String>> deleteDocuments(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) request.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "삭제할 문서 ID가 필요합니다."));
        }

        vectorDbService.deleteDocuments(ids);
        return ResponseEntity.ok(Map.of("message", ids.size() + "개 문서가 삭제되었습니다."));
    }

    /**
     * Delete all documents for a user.
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, String>> deleteUserDocuments(@PathVariable String userId) {
        vectorDbService.deleteUserDocuments(userId);
        return ResponseEntity.ok(Map.of("message", "사용자의 모든 문서가 삭제되었습니다.", "userId", userId));
    }
}
