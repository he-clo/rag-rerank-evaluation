package com.gb.rag.DocuSplit.impl;


//需要切的


import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.gb.rag.DocuSplit.DocumentSplit;
import com.gb.rag.Enum.FileType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.nio.file.Paths;

@Component
public class DocxSplit implements DocumentSplit {

    @Value("${embedding.batch-size}")
    private int chunkSize;

    @Value("${embedding.overflow}")
    private int overflow;


    public SplitResult geneChunkByStyle(Map<String, Object> document) {
        if (!(document.get("blocks") instanceof List<?> blocks)) {
            return new SplitResult(List.of(), List.of(), List.of());
        }

        List<String> chunks = new ArrayList<>();
        List<Integer> paragraphN = new ArrayList<>();
        int pn = 0;

        for (Object item : blocks) {
            if (!(item instanceof Map<?, ?> block) || !(block.get("text") instanceof String text)) {
                continue;
            }

            // 一级:按空行(段落)切
            for (String paragraph : text.split("\n\\s*\n")) {
                if (paragraph.isBlank()) {
                    continue;
                }

                // 二级:段内按单换行切
                for (String line : paragraph.split("\n")) {
                    if (line.isBlank()) {
                        continue;
                    }
                    chunks.add(line);
                    paragraphN.add(pn);   // 同一个段落内所有行共享段落号
                }

                pn++;   // 一个空行块处理完,段落号 +1
            }
        }

        // DOCX 无分页概念
        return new SplitResult(List.of(), paragraphN, chunks);
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.DOCX;
    }

    public SplitResult split(Map<String, Object> input) throws IOException {
        SplitResult splitResult = geneChunkByStyle(input);
        SplitResult strings = geneChunkByToken(splitResult, chunkSize, overflow);
        return strings;
    }

    public SplitResult geneChunkByToken(SplitResult splitResult, int chunkSize, int overflow) throws IOException {
// ✅ 用 Paths.get() 传 Path，不走字符串校验
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(
                Paths.get("D:/IdeaProjects/RAG+Agent/src/main/resources/tokenizer")
        );


        List<String> newChunks = new ArrayList<>();
        List<Integer> newPages = new ArrayList<>();
        List<Integer> newParagraphs = new ArrayList<>();

        for (int i = 0; i < splitResult.chunks().size(); i++) {
            String chunk = splitResult.chunks().get(i);
            if (chunk == null || chunk.isBlank()) continue;

            int tokenCount = tokenizer.encode(chunk).getTokens().length;
            int page = splitResult.pages().isEmpty() ? 1 : splitResult.pages().get(i);
            int para = splitResult.paragraphN().get(i); // paragraphN 是有值的，这个没问题
            if (tokenCount <= chunkSize) {
                newChunks.add(chunk);
                newPages.add(page);
                newParagraphs.add(para);
            } else {
                List<String> splits = splitLongChunk(tokenizer, chunk, chunkSize, overflow);
                for (String s : splits) {
                    newChunks.add(s);
                    newPages.add(page);      // 拆出来的继承原归属
                    newParagraphs.add(para);
                }
            }
        }

        return new SplitResult(newPages, newParagraphs, newChunks);
    }

    private List<String> splitLongChunk(HuggingFaceTokenizer tokenizer,
                                        String text,
                                        int chunkSize,
                                        int overflow) {
        List<String> result = new ArrayList<>();

        Encoding enc = tokenizer.encode(text);
        long[] ids = enc.getIds();   // ← 改这里

        if (ids.length <= chunkSize) {
            result.add(text);
            return result;
        }

        int overlap = Math.max(0, Math.min(overflow, chunkSize - 1));
        int stride = chunkSize - overlap;

        int start = 0;
        while (start < ids.length) {
            int end = Math.min(start + chunkSize, ids.length);
            long[] slice = Arrays.copyOfRange(ids, start, end);  // ← 改这里

            String decoded = tokenizer.decode(slice, true);  // ← 如果 decode 接收 long[]，直接传
            if (decoded != null && !decoded.isBlank()) {
                result.add(decoded.trim());
            }

            if (end == ids.length) break;
            start += stride;
        }

        return result;
    }
}