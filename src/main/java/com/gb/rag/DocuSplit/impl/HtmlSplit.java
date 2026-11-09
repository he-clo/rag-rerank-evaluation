package com.gb.rag.DocuSplit.impl;


import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.gb.rag.DocuSplit.DocumentSplit;
import com.gb.rag.Enum.FileType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;

@Component
//要切
public class HtmlSplit implements DocumentSplit {


    @Override
    public SplitResult split(Map<String, Object> input) throws IOException {
        String text = getText(input);
        SplitResult splitResult = geneChunkByToken(text, chunkSize, overflow);
        return splitResult;
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.HTML;
    }


    @Value("${embedding.batch-size}")
    private int chunkSize;

    @Value("${embedding.overflow}")
    private int overflow;


    public String getText(Map<String, Object> document) throws IOException {
        String text = "";
        if (!(document.get("blocks") instanceof List<?> blocks)) {
            return "";
        }

        for (Object item : blocks) {
            if (!(item instanceof Map<?, ?> block) || !(block.get("text") instanceof String content)) {
                continue;
            }
            text=content;
        }
        return text;
    }

    public SplitResult geneChunkByToken(String text, int chunkSize, int overflow) throws IOException {
    HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(
            Paths.get("D:/IdeaProjects/RAG+Agent/src/main/resources/tokenizer")
    );


    List<String> newChunks = new ArrayList<>();
    List<Integer> newPages = new ArrayList<>();
    List<Integer> newParagraphs = new ArrayList<>();


    if (text == null || text.isBlank()) {
        return new SplitResult(newPages,newParagraphs,newChunks);
    }

    int tokenCount = tokenizer.encode(text).getTokens().length;
    int page = 1;
    int para =1;
    if (tokenCount <= chunkSize) {
        newChunks.add(text);
        newPages.add(page);
        newParagraphs.add(para);
    } else {
        List<String> splits = splitLongChunk(tokenizer, text, chunkSize, overflow);
        for (String s : splits) {
            newChunks.add(s);
            newPages.add(page);      // 拆出来的继承原归属
            newParagraphs.add(para);
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