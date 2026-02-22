package com.mychatgpt.controller;

import com.mychatgpt.dto.ChatRequest;
import com.mychatgpt.dto.AiChatResponse;
import com.mychatgpt.entity.ChatMessage;
import com.mychatgpt.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<AiChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getSessionId() == null || request.getUserId() == null || request.getMessage() == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            AiChatResponse response = chatService.chat(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new AiChatResponse(request.getSessionId(), "오류: " + e.getMessage(), null, 0));
        }
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.getHistory(sessionId));
    }
}
