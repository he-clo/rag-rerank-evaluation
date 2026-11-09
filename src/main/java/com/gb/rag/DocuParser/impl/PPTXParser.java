package com.gb.rag.DocuParser.impl;

import com.gb.rag.DocuParser.DocumentParser;
import com.gb.rag.Enum.FileType;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.apache.commons.io.IOUtils;
@Component
//要切
public class PPTXParser implements DocumentParser {

    private final Tika tika = new Tika();

    @Override
    public Map<String, Object> parse(InputStream input, String fileName) {
        TikaInputStream tis=null;
        try {
            tis = TikaInputStream.get(input);
            byte[] data = tis.readAllBytes();   // Java 9+
            tis.close();

            Metadata metadata = new Metadata();
            AutoDetectParser parser = new AutoDetectParser();

            // 1) 拿 XHTML 结构（用于按页切分）
            ToXMLContentHandler xmlHandler = new ToXMLContentHandler();
            parser.parse(new ByteArrayInputStream(data), xmlHandler, metadata, new ParseContext());
            String xhtml = xmlHandler.toString();

            // 2) 重置流，再拿纯文本（如果不用的话这步可以省）
            tis.reset();
            String plain = tika.parseToString(new ByteArrayInputStream(data), metadata);
            if (plain == null) plain = "";

            // 3) 解析页码结构
            Document doc = Jsoup.parse(xhtml);
            Elements slides = doc.select("div.slide-content");
            if (slides.isEmpty()) {
                slides = doc.select("div.slide");   // 降级匹配
            }

            // 4) 按页构建 blocks
            List<Map<String, Object>> blocks = new ArrayList<>();
            int pageNo = 0;
            for (Element slide : slides) {
                pageNo++;
                String pageText = clean(slide.text());   // 你的去 \n 逻辑
                if (pageText.isEmpty()) continue;

                Map<String, Object> block = new HashMap<>();
                block.put("type", "pptx_body");
                block.put("page", pageNo);          // ← 关键：带上页码
                block.put("text", pageText);
                blocks.add(block);
            }

            // 5) 拿总页数
            String slideCount = metadata.get("slide-count");
            if (slideCount == null) slideCount = metadata.get("xmpTPg:NPages");

            // 6) 组装结果
            Map<String, Object> result = new HashMap<>();
            result.put("encoding", "UTF-8");
            result.put("file_name", fileName);
            result.put("title", defaultIfBlank(metadata.get("title"), ""));
            result.put("slide_count", slideCount != null ? Integer.parseInt(slideCount) : slides.size());
            result.put("plain", plain.trim());
            result.put("blocks", blocks);
            return result;

        } catch (Exception e) {
            throw new RuntimeException("PPTX parse failed: " + fileName, e);
        } finally {
            IOUtils.closeQuietly(tis);
        }
    }

    private String clean(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\n\\t]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private String defaultIfBlank(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.PPTX;
    }

}