package com.gb.rag.DocuParser.impl;

import com.gb.rag.DocuParser.DocumentParser;
import com.gb.rag.Enum.FileType;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Component
//需要切的
public class DocxParser implements DocumentParser {

    private final Tika tika = new Tika();

    @Override
    public Map<String, Object> parse(InputStream input, String fileName) {
        try {
            Metadata metadata = new Metadata();

            //String plain = tika.parseToString(input, metadata);

            byte[] bytes = input.readAllBytes();
            if (bytes.length == 0) {
                throw new IllegalStateException("empty stream: " + fileName);
            }
            String plain = tika.parseToString(new java.io.ByteArrayInputStream(bytes), metadata);

            if (plain == null) {
                plain = "";
            }

            String title = metadata.get("title");
            if (title == null || title.isBlank()) {
                title = "";
            }

            // blocks 用 List<Map> 而不是 ArrayNode
            Map<String, Object> block = new HashMap<>();
            block.put("type", "docx_body");
            block.put("text", plain);

            List<Map<String, Object>> blocks = new ArrayList<>();
            blocks.add(block);

            Map<String, Object> result = new HashMap<>();
            result.put("encoding", "UTF-8");
            result.put("file_name", fileName);
            result.put("title", title);
            result.put("plain", plain.trim());
            result.put("line_count", plain.isEmpty() ? 0 : plain.split("\\R", -1).length);
            result.put("blocks", blocks);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("DOCX parse failed: " + fileName, e);
        }
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.DOCX;
    }

}