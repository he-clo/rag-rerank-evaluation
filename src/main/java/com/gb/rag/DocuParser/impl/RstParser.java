package com.gb.rag.DocuParser.impl;


import com.gb.rag.DocuParser.DocumentParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.gb.rag.Enum.FileType;
import com.fasterxml.jackson.core.type.TypeReference;
@Component
public class RstParser implements DocumentParser {

    @Value("D:\\VS RAG+Agent\\rstReader.py")
    String pythonScript;

    @Override
    public Map<String, Object> parse(InputStream input, String fileName) {
        try {
            byte[] fileContent = input.readAllBytes();
            String jsonOutput = callPythonRST(fileContent, fileName);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(jsonOutput, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("RST parse failed: " + fileName, e);
        }
    }

    private String callPythonRST(byte[] fileContent, String fileName) throws Exception {
        Path tempFile = Files.createTempFile("upload_", "_" + fileName);
        try {
            Files.write(tempFile, fileContent);

            Process process = new ProcessBuilder(
                    "python",
                    pythonScript,
                    tempFile.toString()
            ).start();

            String jsonOutput = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String error = new String(
                        process.getErrorStream().readAllBytes(),
                        StandardCharsets.UTF_8
                );
                throw new RuntimeException("Python RST parser failed: " + error);
            }

            return jsonOutput;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.RST;
    }

}