package com.gb.rag.DocuParser.impl;

import com.gb.rag.DocuParser.DocumentParser;
import com.gb.rag.Enum.FileType;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
//这个最好不切，按行拿语义完整
public class XLSXParser implements DocumentParser {

    @Override
    public Map<String, Object> parse(InputStream input, String fileName) {
        try (Workbook workbook = WorkbookFactory.create(input)) {

            StringBuilder plain = new StringBuilder();
            List<Map<String, Object>> blocks = new ArrayList<>();

            for (Sheet sheet : workbook) {
                String sheetName = sheet.getSheetName();
                StringBuilder sheetText = new StringBuilder();
                List<Map<String, Object>> rows = new ArrayList<>();

                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    StringBuilder rowText = new StringBuilder();
                    int colIndex = 0;

                    for (Cell cell : row) {
                        String value = getCellText(cell);
                        cells.add(value);
                        rowText.append(value).append(" | ");
                    }

                    String rowTextStr = rowText.toString().replaceAll("\\s*\\|\\s*$", "").trim();
                    if (!rowTextStr.isEmpty()) {
                        plain.append(rowTextStr).append("\n");
                        sheetText.append(rowTextStr).append("\n");
                    }

                    if (!cells.isEmpty() && cells.stream().anyMatch(s -> !s.isEmpty())) {
                        Map<String, Object> rowBlock = new HashMap<>();
                        rowBlock.put("type", "row");
                        rowBlock.put("row_index", row.getRowNum());
                        rowBlock.put("text", rowTextStr);
                        rowBlock.put("cells", cells);
                        rows.add(rowBlock);
                    }
                }

                Map<String, Object> sheetBlock = new HashMap<>();
                sheetBlock.put("type", "sheet");
                sheetBlock.put("name", sheetName);
                sheetBlock.put("text", sheetText.toString().trim());
                sheetBlock.put("rows", rows);
                blocks.add(sheetBlock);
            }

            String plainText = plain.toString().trim();

            Map<String, Object> result = new HashMap<>();
            result.put("encoding", "UTF-8");
            result.put("file_name", fileName);
            result.put("title", "");
            result.put("plain", plainText);
            result.put("line_count", plainText.isEmpty() ? 0 : plainText.split("\\R", -1).length);
            result.put("blocks", blocks);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("XLSX parse failed: " + fileName, e);
        }
    }

    private String getCellText(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.XLSX;
    }

}