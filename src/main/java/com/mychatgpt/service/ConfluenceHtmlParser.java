package com.mychatgpt.service;

import com.mychatgpt.dto.ConfluenceDocumentDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Confluence에서 export된 HTML 파일을 파싱하여 ConfluenceDocumentDto로 변환하는 컴포넌트.
 *
 * HTML에서 의미 있는 데이터만 추출하는 "데이터 정제(refining)" 기능을 담당:
 * - 제목, breadcrumb, 본문 콘텐츠만 추출
 * - 네비게이션, 스타일, 스크립트, 푸터 등 불필요한 요소 제거
 * - HTML 태그를 제거하고 순수 텍스트만 추출
 * - 과도한 공백/줄바꿈 정리
 *
 * Confluence export HTML의 구조:
 * - <title>: 문서 제목
 * - <ol id="breadcrumbs">: 경로 정보
 * - <div id="main-content" class="wiki-content group">: 실제 콘텐츠
 * - <div class="page-metadata">: 작성자, 수정일 정보
 */
@Component
@Slf4j
public class ConfluenceHtmlParser {

    /** 파일명에서 ID를 추출하는 패턴 (예: "01.API_70451688.html" → "70451688") */
    private static final Pattern ID_PATTERN = Pattern.compile("_(\\d+)\\.html$");

    /** 순수 숫자 파일명 패턴 (예: "70451688.html" → "70451688") */
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("^(\\d+)\\.html$");

    /** 최소 콘텐츠 길이 (너무 짧은 문서는 의미 없음) */
    private static final int MIN_CONTENT_LENGTH = 50;

    /**
     * HTML 문자열을 파싱하여 DTO로 변환한다.
     * MultipartFile 업로드 시 사용.
     *
     * @param htmlContent HTML 문자열
     * @param fileName    원본 파일명
     * @return 파싱된 DTO, 또는 유효하지 않은 문서면 null
     */
    public ConfluenceDocumentDto parseHtmlContent(String htmlContent, String fileName) {
        Document doc = Jsoup.parse(htmlContent);
        return doParse(doc, fileName);
    }

    /**
     * 단일 HTML 파일을 파싱하여 DTO로 변환한다.
     *
     * @param file HTML 파일
     * @return 파싱된 DTO, 또는 유효하지 않은 문서면 null
     */
    public ConfluenceDocumentDto parse(File file) throws IOException {
        Document doc = Jsoup.parse(file, StandardCharsets.UTF_8.name());
        return doParse(doc, file.getName());
    }

    private ConfluenceDocumentDto doParse(Document doc, String fileName) {
        // ID 추출
        String id = extractId(fileName);
        if (id == null) {
            log.warn("ID를 추출할 수 없는 파일: {}", fileName);
            return null;
        }

        // 제목 추출
        String title = extractTitle(doc);
        if (title == null || title.isBlank()) {
            log.warn("제목이 없는 파일: {}", fileName);
            return null;
        }

        // Breadcrumb 추출
        String breadcrumb = extractBreadcrumb(doc);

        // 본문 콘텐츠 추출 및 정제
        String content = extractAndRefineContent(doc);
        if (content == null || content.length() < MIN_CONTENT_LENGTH) {
            log.debug("콘텐츠가 너무 짧거나 없음 ({}자): {}",
                    content == null ? 0 : content.length(), fileName);
        }

        // 메타데이터 추출
        String author = extractAuthor(doc);
        String lastModified = extractLastModified(doc);

        return ConfluenceDocumentDto.builder()
                .id(id)
                .title(title)
                .breadcrumb(breadcrumb)
                .content(content)
                .author(author)
                .lastModified(lastModified)
                .fileName(fileName)
                .build();
    }

    /**
     * 디렉토리 내 모든 HTML 파일을 파싱한다.
     *
     * @param directory HTML 파일들이 있는 디렉토리
     * @return 파싱된 DTO 리스트 (유효하지 않은 파일은 제외)
     */
    public List<ConfluenceDocumentDto> parseDirectory(File directory) throws IOException {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("유효한 디렉토리가 아닙니다: " + directory.getPath());
        }

        File[] htmlFiles = directory.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".html") && !name.equals("index.html"));

        if (htmlFiles == null || htmlFiles.length == 0) {
            log.warn("HTML 파일이 없습니다: {}", directory.getPath());
            return new ArrayList<>();
        }

        List<ConfluenceDocumentDto> documents = new ArrayList<>();
        int skippedCount = 0;

        for (File file : htmlFiles) {
            try {
                ConfluenceDocumentDto dto = parse(file);
                if (dto != null) {
                    documents.add(dto);
                } else {
                    skippedCount++;
                }
            } catch (Exception e) {
                log.error("파일 파싱 실패: {}", file.getName(), e);
                skippedCount++;
            }
        }

        log.info("HTML 파싱 완료: {}개 성공, {}개 스킵", documents.size(), skippedCount);
        return documents;
    }

    /**
     * 파일명에서 문서 ID를 추출한다.
     * 예: "01.API_70451688.html" → "confluence-70451688"
     *     "70451688.html" → "confluence-70451688"
     */
    private String extractId(String fileName) {
        // 먼저 "_숫자.html" 패턴 시도
        Matcher matcher = ID_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return "confluence-" + matcher.group(1);
        }

        // 순수 숫자 파일명 시도
        matcher = NUMERIC_ID_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return "confluence-" + matcher.group(1);
        }

        // 확장자 제거 후 전체를 ID로 사용 (특수문자는 하이픈으로 치환)
        String baseName = fileName.replaceFirst("\\.html$", "");
        String cleanId = baseName.replaceAll("[^a-zA-Z0-9가-힣-_]", "-");
        return "confluence-" + cleanId;
    }

    /**
     * 문서 제목을 추출한다.
     * 우선순위: title-text span > h1#title-heading > title 태그
     */
    private String extractTitle(Document doc) {
        // 1. Confluence 특유의 title-text span 시도
        Element titleSpan = doc.selectFirst("#title-text");
        if (titleSpan != null) {
            String text = titleSpan.text().trim();
            // "통합지원실-IT전략팀 : 01.API" 형태에서 실제 제목만 추출
            if (text.contains(" : ")) {
                text = text.substring(text.lastIndexOf(" : ") + 3);
            }
            if (!text.isBlank()) return text;
        }

        // 2. h1#title-heading 시도
        Element h1 = doc.selectFirst("h1#title-heading");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text().trim();
        }

        // 3. title 태그 사용
        String title = doc.title();
        if (title != null && title.contains(" : ")) {
            title = title.substring(title.lastIndexOf(" : ") + 3);
        }
        return title;
    }

    /**
     * Breadcrumb 경로를 추출한다.
     * 예: "통합지원실-IT센터 > 업무별 공간 > TALKOOL"
     */
    private String extractBreadcrumb(Document doc) {
        Element breadcrumbsOl = doc.selectFirst("#breadcrumbs");
        if (breadcrumbsOl == null) return null;

        Elements items = breadcrumbsOl.select("li");
        if (items.isEmpty()) return null;

        return items.stream()
                .map(Element::text)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining(" > "));
    }

    /**
     * 본문 콘텐츠를 추출하고 정제한다.
     *
     * 데이터 정제(refining) 로직:
     * 1. wiki-content div에서 콘텐츠 추출
     * 2. 의미 없는 HTML 요소 제거 (스크립트, 스타일, 이미지 alt 등)
     * 3. HTML 태그 제거하고 순수 텍스트만 추출
     * 4. 과도한 공백/줄바꿈 정리
     * 5. 반복되는 구조적 텍스트 제거
     */
    private String extractAndRefineContent(Document doc) {
        Element contentDiv = doc.selectFirst("#main-content.wiki-content");
        if (contentDiv == null) {
            contentDiv = doc.selectFirst(".wiki-content");
        }
        if (contentDiv == null) {
            contentDiv = doc.selectFirst("#content");
        }
        if (contentDiv == null) {
            return null;
        }

        // 의미 없는 요소 제거
        contentDiv.select("script, style, noscript, svg, .confluence-embedded-image").remove();
        contentDiv.select(".page-metadata, .footer-body, #footer").remove();
        contentDiv.select("nav, .navigation, .sidebar").remove();

        // 이미지의 alt 텍스트 제거 (보통 의미 없음)
        contentDiv.select("img").remove();

        // HTML을 텍스트로 변환
        String text = contentDiv.text();

        // 정제 작업
        text = refineText(text);

        return text;
    }

    /**
     * 텍스트를 정제한다.
     * - 과도한 공백 제거
     * - 불필요한 특수문자 정리
     * - 의미 없는 반복 패턴 제거
     */
    private String refineText(String text) {
        if (text == null) return null;

        // 여러 개의 공백을 하나로
        text = text.replaceAll("\\s+", " ");

        // 여러 개의 줄바꿈을 하나로
        text = text.replaceAll("(\\r?\\n)+", "\n");

        // 앞뒤 공백 제거
        text = text.trim();

        // 빈 괄호 제거
        text = text.replaceAll("\\(\\s*\\)", "");
        text = text.replaceAll("\\[\\s*\\]", "");

        // 연속된 특수문자 정리
        text = text.replaceAll("[-=_]{3,}", "---");

        // "Document generated by Confluence" 같은 푸터 텍스트 제거
        text = text.replaceAll("Document generated by Confluence.*$", "");

        return text.trim();
    }

    /**
     * 작성자 정보를 추출한다.
     */
    private String extractAuthor(Document doc) {
        Element metadata = doc.selectFirst(".page-metadata");
        if (metadata == null) return null;

        Element author = metadata.selectFirst(".author");
        if (author != null) {
            return author.text().trim();
        }

        // "Created by [작성자]" 패턴에서 추출
        String text = metadata.text();
        Pattern pattern = Pattern.compile("Created by\\s+(.+?)(?:,|$)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    /**
     * 마지막 수정일을 추출한다.
     */
    private String extractLastModified(Document doc) {
        Element metadata = doc.selectFirst(".page-metadata");
        if (metadata == null) return null;

        String text = metadata.text();
        // "last modified on 11월 27, 2023" 패턴
        Pattern pattern = Pattern.compile("last modified on\\s+(.+)$");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // "Created ... on [날짜]" 패턴
        pattern = Pattern.compile("on\\s+(\\d+월\\s+\\d+,\\s+\\d+)");
        matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    /**
     * 파싱 결과가 벡터DB에 저장할 만한 가치가 있는지 검증한다.
     *
     * 필터링 기준:
     * - 제목이 있어야 함
     * - 콘텐츠가 최소 길이 이상이거나, breadcrumb이 있어야 함
     */
    public boolean isValidForVectorDb(ConfluenceDocumentDto dto) {
        if (dto == null) return false;
        if (dto.getTitle() == null || dto.getTitle().isBlank()) return false;

        // 콘텐츠가 충분하거나 breadcrumb이 있으면 유효
        boolean hasContent = dto.getContent() != null && dto.getContent().length() >= MIN_CONTENT_LENGTH;
        boolean hasBreadcrumb = dto.getBreadcrumb() != null && !dto.getBreadcrumb().isBlank();

        return hasContent || hasBreadcrumb;
    }
}
