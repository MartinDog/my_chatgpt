package com.mychatgpt.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;

@RestController
@RequestMapping("/api/logs")
@Slf4j
public class LogController {

    private static final int MAX_LINES = 10_000;
    private static final int DEFAULT_LINES = 1_000;

    @Value("${logging.file.path:/app/logs}")
    private String logDir;

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getLogs(
            @RequestParam(defaultValue = "" + DEFAULT_LINES) int lines) {

        lines = Math.min(Math.max(1, lines), MAX_LINES);

        Path logFile = Paths.get(logDir, "app.log");
        if (!Files.exists(logFile)) {
            return ResponseEntity.notFound().build();
        }

        try {
            String content = readLastLines(logFile, lines);
            return ResponseEntity.ok(content);
        } catch (IOException e) {
            log.error("로그 파일 읽기 실패: {}", logFile, e);
            return ResponseEntity.internalServerError()
                    .body("로그 파일 읽기 실패: " + e.getMessage());
        }
    }

    private String readLastLines(Path file, int lineCount) throws IOException {
        Deque<String> buffer = new ArrayDeque<>(lineCount + 1);

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.addLast(line);
                if (buffer.size() > lineCount) {
                    buffer.removeFirst();
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String line : buffer) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
