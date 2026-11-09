package com.gb.rag.DocuParser.impl;



import com.gb.rag.DocuParser.DocumentParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import com.gb.rag.Enum.FileType;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Component
//要切
public class HtmlParser implements DocumentParser {

    @Override
    public Map<String, Object> parse(InputStream input, String fileName) {
        try {
            Document doc = Jsoup.parse(input, "UTF-8", "");

            // 去掉脚本/样式/注释区域，避免噪声
            doc.select("script, style, noscript, iframe, nav, footer, aside, header, form, button, input, select, textarea, svg, path, canvas").remove();

            String title = doc.title();
            String plain = doc.body() != null ? doc.body().text() : doc.text();

            // blocks 用 List<Map>
            Map<String, Object> block = new HashMap<>();
            block.put("type", "html_body");
            block.put("text", plain);

            List<Map<String, Object>> blocks = new ArrayList<>();
            blocks.add(block);

            Map<String, Object> result = new HashMap<>();
            result.put("encoding", "UTF-8");
            result.put("file_name", fileName);
            result.put("title", title == null ? "" : title);
            result.put("plain", plain);
            result.put("line_count", plain.isEmpty() ? 0 : plain.split("\\R", -1).length);
            result.put("blocks", blocks);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("HTML parse failed: " + fileName, e);
        }
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.HTML;
    }
}