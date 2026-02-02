package com.mychatgpt.tool.impl;

import com.mychatgpt.tool.ChatTool;
import com.mychatgpt.tool.ToolDefinition;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class CurrentTimeTool implements ChatTool {

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "get_current_time",
                "현재 날짜와 시간을 반환합니다.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "timezone", Map.of(
                                        "type", "string",
                                        "description", "Timezone (e.g., Asia/Seoul). Default is Asia/Seoul."
                                )
                        ),
                        "required", java.util.List.of()
                )
        );
    }

    @Override
    public String execute(String argumentsJson) {
        LocalDateTime now = LocalDateTime.now();
        return "현재 시간: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
