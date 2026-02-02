package com.mychatgpt.controller;

import com.mychatgpt.entity.ChatSession;
import com.mychatgpt.service.ChatSessionService;
import com.mychatgpt.service.VectorDbService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService sessionService;
    private final VectorDbService vectorDbService;

    @PostMapping
    public ResponseEntity<ChatSession> createSession(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String title = request.get("title");
        String systemPrompt = request.get("systemPrompt");
        ChatSession session = sessionService.createSession(userId, title, systemPrompt);
        return ResponseEntity.ok(session);
    }

    @PutMapping("/{sessionId}")
    public ResponseEntity<ChatSession> updateSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        try {
            String title = request.get("title");
            String systemPrompt = request.get("systemPrompt");
            ChatSession session = sessionService.updateSession(sessionId, title, systemPrompt);
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatSession> getSession(@PathVariable String sessionId) {
        try {
            ChatSession session = sessionService.getSession(sessionId);
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ChatSession>> getUserSessions(@PathVariable String userId) {
        return ResponseEntity.ok(sessionService.getUserSessions(userId));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String sessionId) {
        try {
            sessionService.deleteSession(sessionId);
            // Also clean up vector DB data for this session
            try {
                vectorDbService.deleteSessionVectors(sessionId);
            } catch (Exception e) {
                // Non-critical, log and continue
            }
            return ResponseEntity.ok(Map.of("message", "세션이 삭제되었습니다.", "sessionId", sessionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
