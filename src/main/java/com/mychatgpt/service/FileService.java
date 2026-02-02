package com.mychatgpt.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final Tika tika = new Tika();

    /**
     * Save uploaded file and return the saved path.
     */
    public String saveFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedFilename = UUID.randomUUID() + extension;
        Path filePath = uploadPath.resolve(storedFilename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("Saved file: {} -> {}", originalFilename, filePath);
        return filePath.toString();
    }

    /**
     * Extract text content from a file using Apache Tika.
     */
    public String extractText(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("파일을 찾을 수 없습니다: " + filePath);
        }

        try {
            String content = tika.parseToString(path);
            return content.trim();
        } catch (Exception e) {
            log.error("Failed to extract text from file: {}", filePath, e);
            throw new IOException("파일에서 텍스트를 추출할 수 없습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Extract text from a MultipartFile directly.
     */
    public String extractText(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.parseToString(inputStream);
        } catch (Exception e) {
            log.error("Failed to extract text from uploaded file", e);
            throw new IOException("파일에서 텍스트를 추출할 수 없습니다: " + e.getMessage(), e);
        }
    }
}
