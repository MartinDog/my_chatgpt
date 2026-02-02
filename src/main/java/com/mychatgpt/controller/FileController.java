package com.mychatgpt.controller;

import com.mychatgpt.service.FileService;
import com.mychatgpt.service.VectorDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileService fileService;
    private final VectorDbService vectorDbService;

    /**
     * Upload a file and optionally ingest it into the vector DB.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId,
            @RequestParam(value = "ingestToVectorDb", defaultValue = "true") boolean ingest) {

        try {
            String savedPath = fileService.saveFile(file);
            String extractedText = fileService.extractText(savedPath);

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("filePath", savedPath);
            response.put("originalFilename", file.getOriginalFilename());
            response.put("textLength", extractedText.length());

            if (ingest && !extractedText.isBlank()) {
                // Split large text into chunks for better retrieval
                var chunks = splitIntoChunks(extractedText, 1000);
                var docIds = new java.util.ArrayList<String>();

                for (String chunk : chunks) {
                    String docId = vectorDbService.storeDocument(
                            chunk, userId, file.getOriginalFilename(),
                            Map.of("filename", file.getOriginalFilename())
                    );
                    docIds.add(docId);
                }

                response.put("vectorDbDocIds", docIds);
                response.put("chunksStored", docIds.size());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("File upload failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "파일 업로드 실패: " + e.getMessage()));
        }
    }

    private java.util.List<String> splitIntoChunks(String text, int chunkSize) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        int overlap = 200;

        for (int i = 0; i < text.length(); i += chunkSize - overlap) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
            if (end == text.length()) break;
        }

        return chunks;
    }
}
