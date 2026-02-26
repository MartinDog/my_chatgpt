package com.mychatgpt.controller;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/logs")
@Slf4j
public class LogController {

    private static final int MAX_LINES = 10_000;
    private static final int DEFAULT_LINES = 100;

    @Value("${logging.file.path:/app/logs}")
    private String logDir;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(
            @RequestParam(defaultValue = "" + DEFAULT_LINES) int lines) {

        final int tailLines = Math.min(Math.max(1, lines), MAX_LINES);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean running = new AtomicBoolean(true);

        emitter.onCompletion(() -> running.set(false));
        emitter.onTimeout(() -> running.set(false));
        emitter.onError(e -> running.set(false));

        executor.execute(() -> {
            Path logFile = Paths.get(logDir, "app.log");

            if (!Files.exists(logFile)) {
                try {
                    emitter.send(SseEmitter.event().data("로그 파일이 없습니다: " + logFile));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
                return;
            }

            try {
                long position = sendLastLines(emitter, logFile, tailLines);

                Path dir = logFile.getParent();
                try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                    dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                    while (running.get()) {
                        WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                        if (key == null) continue;

                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                            Path changed = (Path) event.context();
                            if ("app.log".equals(changed.getFileName().toString())) {
                                position = sendNewContent(emitter, logFile, position);
                            }
                        }

                        if (!key.reset()) break;
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    log.error("SSE 로그 스트리밍 실패", e);
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ignored) {}
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        return emitter;
    }

    private long sendLastLines(SseEmitter emitter, Path file, int lineCount) throws IOException {
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

        for (String line : buffer) {
            emitter.send(SseEmitter.event().data(line));
        }

        return Files.size(file);
    }

    private long sendNewContent(SseEmitter emitter, Path file, long position) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long fileLength = raf.length();
            if (fileLength <= position) return position;

            raf.seek(position);
            byte[] data = new byte[(int) (fileLength - position)];
            int bytesRead = raf.read(data);
            if (bytesRead <= 0) return position;

            String content = new String(data, 0, bytesRead, StandardCharsets.UTF_8);
            for (String line : content.split("\n")) {
                emitter.send(SseEmitter.event().data(line));
            }

            return position + bytesRead;
        }
    }
}
