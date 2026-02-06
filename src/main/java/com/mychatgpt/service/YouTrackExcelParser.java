package com.mychatgpt.service;

import com.mychatgpt.dto.YouTrackIssueDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * YouTrack에서 export된 xlsx 파일을 파싱하여 YouTrackIssueDto 리스트로 변환하는 컴포넌트.
 *
 * 왜 Apache POI를 사용하는가 (기존 Tika 대신):
 * - Tika: 파일 전체를 하나의 plain text로 추출. 셀 경계가 사라져서 어떤 값이 "ID"이고
 *         어떤 값이 "제목"인지 구분 불가. 문서 전체 텍스트 추출에는 적합하지만 구조적 파싱에는 부적합.
 * - POI:  셀 단위로 접근 가능. 헤더 행에서 컬럼 이름을 읽고, 각 행의 특정 컬럼 값을
 *         정확하게 추출 가능. xlsx의 구조를 유지한 채 파싱해야 하는 이 케이스에 적합.
 *
 * 헤더 기반 동적 매핑을 사용하는 이유:
 * - YouTrack export의 컬럼 순서가 변경되더라도 정상 동작
 * - 헤더명으로 매핑하므로 컬럼 인덱스 하드코딩 불필요
 */
@Component
@Slf4j
public class YouTrackExcelParser {

    /**
     * xlsx 파일을 파싱하여 이슈 목록을 반환한다.
     *
     * @param file MultipartFile로 업로드된 xlsx 파일
     * @return 파싱된 이슈 DTO 리스트
     * @throws IOException 파일 읽기 실패 시
     */
    public List<YouTrackIssueDto> parse(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headerMap = buildHeaderMap(sheet.getRow(0));

            validateRequiredHeaders(headerMap);

            List<YouTrackIssueDto> issues = new ArrayList<>();

            // 데이터 행은 1번(0-based)부터 시작
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String id = getCellValue(row, headerMap.get("ID"));
                if (id == null || id.isBlank()) {
                    log.warn("Row {} 에 ID가 없어 건너뜀", rowIdx + 1);
                    continue;
                }

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

            log.info("YouTrack xlsx 파싱 완료: {}건", issues.size());
            return issues;
        }
    }

    /**
     * 헤더 행(첫 번째 행)을 읽어서 {컬럼명 → 컬럼인덱스} 맵을 생성.
     * 컬럼 순서가 바뀌어도 이름으로 찾기 때문에 안전하다.
     */
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
        log.debug("헤더 매핑: {}", map);
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

    /**
     * 셀의 값을 String으로 안전하게 추출한다.
     * 숫자, 날짜, boolean 등 다양한 타입에 대응.
     */
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
                // 정수인 경우 소수점 제거
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
}
