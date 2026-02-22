package com.mychatgpt.tool.impl;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class CurrentTimeTools {

    @Tool(description = "현재 날짜와 시간을 반환합니다.")
    public String getCurrentTime(
            @ToolParam(description = "Timezone (e.g., Asia/Seoul). Default is Asia/Seoul.", required = false) String timezone) {
        try {
            ZoneId zoneId = (timezone != null && !timezone.isBlank())
                    ? ZoneId.of(timezone)
                    : ZoneId.of("Asia/Seoul");
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            return "현재 시간: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            LocalDateTime now = LocalDateTime.now();
            return "현재 시간: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }
}
