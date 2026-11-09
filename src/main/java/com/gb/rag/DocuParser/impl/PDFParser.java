package com.gb.rag.DocuParser.impl;


import com.gb.rag.DocuParser.DocumentParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;// 具体产品

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;

import com.gb.rag.Enum.FileType;
@Component
//用PyMuPDF，如果是多栏等复杂情况 贵的可以走vlm LlamaParse，便宜的可以走Docling 扫描件OCR
public class PDFParser implements DocumentParser {

    @Value("D:\\VS RAG+Agent\\pdfReader.py")
    String pythonScript;


    @Override
    public Map<String, Object> parse(InputStream input, String fileName) {
        try {
            byte[] fileContent = input.readAllBytes();
            String result = parseWithPython(FileType.PDF, fileContent, fileName);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> stringObjectMap = mapper.readValue(result, new TypeReference<Map<String, Object>>() {});
            return stringObjectMap;

        } catch (Exception e) {
            throw new RuntimeException("PDF parse failed: " + fileName, e);
        }
    }

    // 独立方法，不是嵌套在 parse 里
    public String parseWithPython(FileType type, byte[] fileContent, String fileName) {
        try {
            // 1. 写临时文件
            Path tempFile = Files.createTempFile("upload_", "_" + fileName);
            Files.write(tempFile, fileContent);

            // 2. 调用 Python
            Process process = new ProcessBuilder(
                    "python",
                    pythonScript,
                    type.name(),
                    tempFile.toString() //只要这个参数
            ).start();

            // 3. 读取输出
            String jsonOutput = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            int exitCode = process.waitFor();
            Files.delete(tempFile);

            if (exitCode != 0) {
                String error = new String(
                        process.getErrorStream().readAllBytes(),
                        StandardCharsets.UTF_8
                );
                throw new RuntimeException("Python parser failed: " + error);
            }

            // 4. 反序列化
            return jsonOutput;

        } catch (Exception e) {
            throw new RuntimeException("Failed to call Python parser", e);
        }
    }



    @Override
    public boolean supports(FileType type) { return type==FileType.PDF;}


    public List<Integer> getParagraphN(Map<String,Object> document) {
        List<Integer> paragraphid=new ArrayList<Integer>();
        Object text = document.get("chunks");
        if(!(text instanceof List<?>)){
            return List.of();
        }

        List<Map<String,Object>> chunks=((List<?>) text)
                .stream()
                .filter(item-> item instanceof Map)
                .map(item->(Map<String,Object>) item)
                .toList();

        for(Map<String,Object> chunk:chunks){
            Integer id= (Integer)chunk.get("paragraphid");
            paragraphid.add(id);
        }

        return paragraphid;
    }

    public List<Integer> getPages(Map<String,Object> document){
        List<Integer> page=new ArrayList<Integer>();
        Object text = document.get("chunks");
        if(!(text instanceof List<?>)){
            return List.of();
        }

        List<Map<String,Object>> chunks=((List<?>) text)
                .stream()
                .filter(item-> item instanceof Map)
                .map(item->(Map<String,Object>) item)
                .toList();

        for(Map<String,Object> chunk:chunks){
            Integer pages= (Integer)chunk.get("page");
            page.add(pages);
        }

        return page;
    }
}
