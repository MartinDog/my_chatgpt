package com.mychatgpt.service;

import com.mychatgpt.ai.EmbeddingService;
import com.mychatgpt.dto.ConfluenceDocumentDto;
import com.mychatgpt.dto.YouTrackIssueDto;
import com.mychatgpt.vectordb.ChromaDbClient;
import com.mychatgpt.vectordb.VectorSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * YouTrack 이슈를 벡터DB에 knowledge base로 저장/업데이트하는 서비스.
 *
 * 기존 VectorDbService와의 차이:
 * - VectorDbService: 범용적인 문서/대화 저장. UUID를 ID로 사용하며 userId 기반 필터링.
 *   자유 텍스트를 벡터DB에 넣는 용도. 같은 문서를 다시 넣으면 중복이 발생.
 *
 * - KnowledgeBaseService: YouTrack 이슈 전용. 이슈 ID를 벡터DB의 키로 사용하며
 *   upsert를 통해 같은 ID가 들어오면 자동으로 기존 데이터를 덮어씀.
 *   source가 "youtrack"으로 고정되어 있어 나중에 knowledge base 데이터만 필터링/삭제 가능.
 *
 * upsert를 사용하는 이유:
 * - YouTrack 이슈는 계속 업데이트됨 (댓글 추가, 상태 변경 등)
 * - 매번 새 export를 업로드할 때 기존 데이터를 수동으로 삭제할 필요 없이
 *   같은 ID면 자동으로 최신 내용으로 교체됨
 * - 신규 이슈는 자동으로 추가됨 → 별도의 insert/update 분기 로직 불필요
 *
 * 배치 처리를 사용하는 이유:
 * - 1987건의 이슈를 한 건씩 처리하면 1987번의 HTTP 요청 + 1987번의 임베딩 API 호출 발생
 * - BATCH_SIZE(50건) 단위로 묶어서 처리하면 네트워크 오버헤드가 약 1/50로 감소
 * - ChromaDB의 upsert API가 배열 입력을 지원하므로 한 번의 요청으로 여러 문서 처리 가능
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    private final YouTrackExcelParser excelParser;
    private final ConfluenceHtmlParser confluenceParser;
    private final ChromaDbClient chromaDbClient;
    private final EmbeddingService embeddingService;

    /** ChromaDB에 한 번에 보내는 문서 수. 너무 크면 요청 크기 제한에 걸릴 수 있음 */
    private static final int BATCH_SIZE = 50;

    /** 벡터DB metadata의 source 값 — knowledge base 데이터를 다른 데이터와 구분하는 키 */
    private static final String SOURCE_YOUTRACK = "youtrack";
    private static final String SOURCE_CONFLUENCE = "confluence";

    /**
     * xlsx 파일을 파싱하여 벡터DB에 일괄 upsert한다.
     *
     * @param file 업로드된 YouTrack export xlsx
     * @return 처리 결과 요약 (총 건수, 성공/실패 건수)
     */
    public Map<String, Object> ingestYouTrackExcel(MultipartFile file) throws IOException {
        List<YouTrackIssueDto> issues = excelParser.parse(file);

        int totalCount = issues.size();
        int successCount = 0;
        int failCount = 0;
        List<String> failedIds = new ArrayList<>();

        // BATCH_SIZE 단위로 나누어 처리
        for (int i = 0; i < issues.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, issues.size());
            List<YouTrackIssueDto> batch = issues.subList(i, end);

            try {
                upsertBatch(batch);
                successCount += batch.size();
                log.info("배치 처리 완료: {}/{}", Math.min(i + BATCH_SIZE, totalCount), totalCount);
            } catch (Exception e) {
                log.error("배치 처리 실패 (index {}-{}): {}", i, end - 1, e.getMessage());
                failCount += batch.size();
                batch.forEach(issue -> failedIds.add(issue.getId()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalParsed", totalCount);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        if (!failedIds.isEmpty()) {
            result.put("failedIds", failedIds);
        }
        result.put("message", String.format("YouTrack 이슈 %d건 중 %d건 저장 완료", totalCount, successCount));

        return result;
    }

    /**
     * 단일 이슈를 벡터DB에 upsert한다.
     * API를 통한 개별 이슈 업데이트 시 사용.
     */
    public void upsertSingleIssue(YouTrackIssueDto issue) {
        String document = issue.toVectorDocument();
        float[] embedding = embeddingService.getEmbedding(document);
        Map<String, String> metadata = buildMetadata(issue);

        chromaDbClient.upsertDocuments(
                List.of(issue.getId()),
                List.of(embedding),
                List.of(document),
                List.of(metadata)
        );

        log.info("이슈 upsert 완료: {}", issue.getId());
    }

    /**
     * 배치 단위로 이슈를 벡터DB에 upsert한다.
     *
     * 임베딩 생성은 건별로 호출해야 하므로 (OpenAI embedding API는 단일 입력),
     * 임베딩은 루프로 생성하되 ChromaDB upsert는 배치로 한 번에 호출한다.
     */
    private void upsertBatch(List<YouTrackIssueDto> batch) {
        List<String> ids = new ArrayList<>();
        List<float[]> embeddings = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        List<Map<String, String>> metadatas = new ArrayList<>();

        for (YouTrackIssueDto issue : batch) {
            String document = issue.toVectorDocument();

            // 빈 문서는 건너뜀 (ID와 제목만 있는 경우에도 최소한의 텍스트가 있으므로 대부분 통과)
            if (document.isBlank()) {
                log.warn("빈 문서 건너뜀: {}", issue.getId());
                continue;
            }

            float[] embedding = embeddingService.getEmbedding(document);

            ids.add(issue.getId());
            embeddings.add(embedding);
            documents.add(document);
            metadatas.add(buildMetadata(issue));
        }

        if (!ids.isEmpty()) {
            chromaDbClient.upsertDocuments(ids, embeddings, documents, metadatas);
        }
    }

    /**
     * 이슈의 메타데이터를 구성한다.
     *
     * 메타데이터에 저장하는 이유:
     * - document(본문 텍스트)는 임베딩 & 전문 검색에 사용
     * - metadata는 필터 검색에 사용 (예: "담당자가 박준홍인 이슈만 검색")
     * - ChromaDB where 절에서 metadata로 필터링 가능
     *
     * null 값은 빈 문자열로 치환하는 이유:
     * - ChromaDB metadata는 null 값을 허용하지 않음
     * - null이 들어가면 전체 upsert가 실패할 수 있음
     */
    private Map<String, String> buildMetadata(YouTrackIssueDto issue) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("source", SOURCE_YOUTRACK);
        metadata.put("issueId", issue.getId());
        metadata.put("title", Objects.toString(issue.getTitle(), ""));
        metadata.put("priority", Objects.toString(issue.getPriority(), ""));
        metadata.put("stage", Objects.toString(issue.getStage(), ""));
        metadata.put("requester", Objects.toString(issue.getRequester(), ""));
        metadata.put("assignee", Objects.toString(issue.getAssignee(), ""));
        metadata.put("createdDate", Objects.toString(issue.getCreatedDate(), ""));
        return metadata;
    }

    /**
     * 특정 ID의 knowledge base 이슈를 삭제한다.
     */
    public void deleteIssue(String issueId) {
        chromaDbClient.deleteByIds(List.of(issueId));
        log.info("Knowledge base 이슈 삭제: {}", issueId);
    }

    /**
     * 모든 YouTrack knowledge base 데이터를 삭제한다.
     * source="youtrack" 메타데이터로 필터링하여 다른 데이터는 보존.
     */
    public void deleteAllYouTrackData() {
        chromaDbClient.deleteByFilter(Map.of("source", SOURCE_YOUTRACK));
        log.info("모든 YouTrack knowledge base 데이터 삭제 완료");
    }

    /**
     * knowledge base에서 유사 문서를 검색한다.
     * source="youtrack"으로 필터링하여 knowledge base 데이터만 대상으로 검색.
     */
    public List<VectorSearchResult> searchKnowledgeBase(String query, int nResults) {
        float[] queryEmbedding = embeddingService.getEmbedding(query);
        Map<String, String> filter = Map.of("source", SOURCE_YOUTRACK);
        return chromaDbClient.query(queryEmbedding, nResults, filter);
    }

    // ========== Confluence 문서 처리 ==========

    /**
     * 지정된 디렉토리에서 모든 HTML 파일을 파싱하여 벡터DB에 일괄 upsert한다.
     *
     * @param directoryPath HTML 파일들이 있는 디렉토리 경로
     * @return 처리 결과 요약 (총 건수, 성공/실패 건수)
     */
    public Map<String, Object> ingestConfluenceHtmlFromDirectory(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("유효한 디렉토리 경로가 아닙니다: " + directoryPath);
        }

        List<ConfluenceDocumentDto> documents = confluenceParser.parseDirectory(directory);

        // 유효성 검증으로 필터링
        List<ConfluenceDocumentDto> validDocuments = documents.stream()
                .filter(confluenceParser::isValidForVectorDb)
                .toList();

        log.info("전체 파싱: {}건, 유효한 문서: {}건", documents.size(), validDocuments.size());

        int totalCount = validDocuments.size();
        int successCount = 0;
        int failCount = 0;
        List<String> failedIds = new ArrayList<>();

        // BATCH_SIZE 단위로 나누어 처리
        for (int i = 0; i < validDocuments.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, validDocuments.size());
            List<ConfluenceDocumentDto> batch = validDocuments.subList(i, end);

            try {
                upsertConfluenceBatch(batch);
                successCount += batch.size();
                log.info("Confluence 배치 처리 완료: {}/{}", Math.min(i + BATCH_SIZE, totalCount), totalCount);
            } catch (Exception e) {
                log.error("Confluence 배치 처리 실패 (index {}-{}): {}", i, end - 1, e.getMessage());
                failCount += batch.size();
                batch.forEach(doc -> failedIds.add(doc.getId()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("directory", directoryPath);
        result.put("totalParsed", documents.size());
        result.put("validDocuments", validDocuments.size());
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        if (!failedIds.isEmpty()) {
            result.put("failedIds", failedIds);
        }
        result.put("message", String.format("Confluence 문서 %d건 중 %d건 저장 완료", totalCount, successCount));

        return result;
    }

    /**
     * 단일 Confluence 문서를 벡터DB에 upsert한다.
     */
    public void upsertSingleConfluenceDocument(ConfluenceDocumentDto document) {
        String docText = document.toVectorDocument();
        float[] embedding = embeddingService.getEmbedding(docText);
        Map<String, String> metadata = buildConfluenceMetadata(document);

        chromaDbClient.upsertDocuments(
                List.of(document.getId()),
                List.of(embedding),
                List.of(docText),
                List.of(metadata)
        );

        log.info("Confluence 문서 upsert 완료: {}", document.getId());
    }

    /**
     * 배치 단위로 Confluence 문서를 벡터DB에 upsert한다.
     */
    private void upsertConfluenceBatch(List<ConfluenceDocumentDto> batch) {
        List<String> ids = new ArrayList<>();
        List<float[]> embeddings = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        List<Map<String, String>> metadatas = new ArrayList<>();

        for (ConfluenceDocumentDto doc : batch) {
            String docText = doc.toVectorDocument();

            if (docText.isBlank()) {
                log.warn("빈 문서 건너뜀: {}", doc.getId());
                continue;
            }

            float[] embedding = embeddingService.getEmbedding(docText);

            ids.add(doc.getId());
            embeddings.add(embedding);
            documents.add(docText);
            metadatas.add(buildConfluenceMetadata(doc));
        }

        if (!ids.isEmpty()) {
            chromaDbClient.upsertDocuments(ids, embeddings, documents, metadatas);
        }
    }

    /**
     * Confluence 문서의 메타데이터를 구성한다.
     */
    private Map<String, String> buildConfluenceMetadata(ConfluenceDocumentDto doc) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("source", SOURCE_CONFLUENCE);
        metadata.put("documentId", doc.getId());
        metadata.put("title", Objects.toString(doc.getTitle(), ""));
        metadata.put("breadcrumb", Objects.toString(doc.getBreadcrumb(), ""));
        metadata.put("author", Objects.toString(doc.getAuthor(), ""));
        metadata.put("lastModified", Objects.toString(doc.getLastModified(), ""));
        metadata.put("fileName", Objects.toString(doc.getFileName(), ""));
        return metadata;
    }

    /**
     * Confluence knowledge base에서 유사 문서를 검색한다.
     */
    public List<VectorSearchResult> searchConfluenceKnowledgeBase(String query, int nResults) {
        float[] queryEmbedding = embeddingService.getEmbedding(query);
        Map<String, String> filter = Map.of("source", SOURCE_CONFLUENCE);
        return chromaDbClient.query(queryEmbedding, nResults, filter);
    }

    /**
     * 모든 source의 knowledge base에서 유사 문서를 검색한다.
     * (YouTrack + Confluence 통합 검색)
     */
    public List<VectorSearchResult> searchAllKnowledgeBase(String query, int nResults) {
        float[] queryEmbedding = embeddingService.getEmbedding(query);
        // 필터 없이 전체 검색
        return chromaDbClient.query(queryEmbedding, nResults, null);
    }

    /**
     * 특정 ID의 Confluence 문서를 삭제한다.
     */
    public void deleteConfluenceDocument(String documentId) {
        chromaDbClient.deleteByIds(List.of(documentId));
        log.info("Confluence 문서 삭제: {}", documentId);
    }

    /**
     * 모든 Confluence knowledge base 데이터를 삭제한다.
     */
    public void deleteAllConfluenceData() {
        chromaDbClient.deleteByFilter(Map.of("source", SOURCE_CONFLUENCE));
        log.info("모든 Confluence knowledge base 데이터 삭제 완료");
    }

    // ========== 디렉토리 기반 통합 Ingest (XLSX + HTML) ==========

    /**
     * 지정된 디렉토리에서 XLSX와 HTML 파일을 모두 읽어 벡터DB에 저장한다.
     *
     * @param directoryPath 파일들이 있는 디렉토리 경로
     * @return 처리 결과 요약
     */
    public Map<String, Object> ingestFromDirectory(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("유효한 디렉토리 경로가 아닙니다: " + directoryPath);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("directory", directoryPath);

        // XLSX 파일 처리
        File[] xlsxFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));
        if (xlsxFiles != null && xlsxFiles.length > 0) {
            log.info("XLSX 파일 {}개 발견", xlsxFiles.length);
            List<Map<String, Object>> xlsxResults = new ArrayList<>();
            for (File xlsxFile : xlsxFiles) {
                try {
                    Map<String, Object> xlsxResult = ingestYouTrackExcelFromFile(xlsxFile);
                    xlsxResults.add(xlsxResult);
                } catch (Exception e) {
                    log.error("XLSX 처리 실패: {}", xlsxFile.getName(), e);
                    xlsxResults.add(Map.of("file", xlsxFile.getName(), "error", e.getMessage()));
                }
            }
            result.put("xlsxResults", xlsxResults);
        }

        // HTML 파일 처리
        Map<String, Object> htmlResult = ingestConfluenceHtmlFromDirectory(directoryPath);
        result.put("htmlResult", htmlResult);

        return result;
    }

    /**
     * XLSX 파일을 직접 파일 경로로 처리한다.
     */
    public Map<String, Object> ingestYouTrackExcelFromFile(File file) throws IOException {
        // File을 MultipartFile처럼 처리하기 위해 직접 파싱
        try (FileInputStream is = new FileInputStream(file);
             XSSFWorkbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headerMap = buildHeaderMap(sheet.getRow(0));
            validateRequiredHeaders(headerMap);

            List<YouTrackIssueDto> issues = new ArrayList<>();

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String id = getCellValue(row, headerMap.get("ID"));
                if (id == null || id.isBlank()) continue;

                YouTrackIssueDto dto = YouTrackIssueDto.builder()
                        .id(id.strip())
                        .title(getCellValue(row, headerMap.get("제목")))
                        .body(getCellValue(row, headerMap.get("본문")))
                        .comments(getCellValue(row, headerMap.get("댓글목록")))
                        .priority(getCellValue(row, headerMap.get("Priority")))
                        .stage(getCellValue(row, headerMap.get("Stage")))
                        .requester(getCellValue(row, headerMap.get("업무 요청자")))
                        .assignee(getCellValue(row, headerMap.get("Assignee")))
                        .createdDate(getCellValue(row, headerMap.get("생성일")))
                        .build();

                issues.add(dto);
            }

            return processYouTrackIssues(issues, file.getName());
        }
    }

    private Map<String, Integer> buildHeaderMap(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (headerRow == null) {
            throw new IllegalArgumentException("xlsx 파일에 헤더 행이 없습니다.");
        }
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String headerName = cell.getStringCellValue().strip();
                if (!headerName.isBlank()) {
                    map.put(headerName, i);
                }
            }
        }
        return map;
    }

    private void validateRequiredHeaders(Map<String, Integer> headerMap) {
        List<String> required = List.of("ID", "제목");
        List<String> missing = required.stream()
                .filter(h -> !headerMap.containsKey(h))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("필수 컬럼이 누락되었습니다: " + missing);
        }
    }

    private String getCellValue(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.STRING
                    ? cell.getStringCellValue()
                    : String.valueOf(cell.getNumericCellValue());
            default -> null;
        };
    }

    private Map<String, Object> processYouTrackIssues(List<YouTrackIssueDto> issues, String fileName) {
        int totalCount = issues.size();
        int successCount = 0;
        int failCount = 0;
        List<String> failedIds = new ArrayList<>();

        for (int i = 0; i < issues.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, issues.size());
            List<YouTrackIssueDto> batch = issues.subList(i, end);

            try {
                upsertBatch(batch);
                successCount += batch.size();
            } catch (Exception e) {
                log.error("배치 처리 실패: {}", e.getMessage());
                failCount += batch.size();
                batch.forEach(issue -> failedIds.add(issue.getId()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("file", fileName);
        result.put("totalParsed", totalCount);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        if (!failedIds.isEmpty()) {
            result.put("failedIds", failedIds);
        }
        return result;
    }
}
