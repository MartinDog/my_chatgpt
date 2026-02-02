package com.mychatgpt.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatCompletionResult {
    private String content;
    private boolean toolUsed;
}
