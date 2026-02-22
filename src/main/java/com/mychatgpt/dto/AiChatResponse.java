package com.mychatgpt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private String sessionId;
    private String message;
    private String timestamp;
    private int relevanceScore;
}
