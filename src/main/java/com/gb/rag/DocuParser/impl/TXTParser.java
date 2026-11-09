package com.gb.rag.DocuParser.impl;


import com.gb.rag.DocuParser.DocumentParser;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import com.gb.rag.Enum.FileType;


import java.io.BufferedReader;

import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
//先按\n切，再按token切
public class TXTParser implements DocumentParser {

    @Override
    public Map<String, Object> parse(InputStream input, String fileName) {
        try {
            Charset charset = detectCharset(input);
            String content = readContent(input, charset);
            List<String> paragraphs = splitParagraphs(content);

            Map<String, Object> result = new HashMap<>();
            result.put("encoding", charset.name());
            result.put("file_name", fileName);
            result.put("line_count", content.split("\n").length);
            result.put("plain", content.trim());
            result.put("paragraphs", paragraphs);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("TXT parse failed: " + fileName, e);
        }
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.TXT;
    }

    private Charset detectCharset(InputStream input) {
        return StandardCharsets.UTF_8;
    }

    private String readContent(InputStream input, Charset charset) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private List<String> splitParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        String[] blocks = content.split("\n\\s*\n");
        for (String block : blocks) {
            String trimmed = block.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

}