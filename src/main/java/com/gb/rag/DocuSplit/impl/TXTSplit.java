package com.gb.rag.DocuSplit.impl;


import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.gb.rag.DocuSplit.DocumentSplit;
import com.gb.rag.Enum.FileType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

@Component
//先按\n切，再按token切
public class TXTSplit implements DocumentSplit {

    @Value("${embedding.batch-size}")
    private int chunkSize;

    @Value("${embedding.overflow}")
    private int overflow;

    @Override
    public SplitResult split(Map<String, Object> document) {
        SplitResult splitResult = splitChunk(document);
        return splitResult;
    }

    private  SplitResult splitChunk(Map<String, Object> document) {
        List<Integer> page = new ArrayList<>();
        List<Integer> paragraph = new ArrayList<>();
        List<String> chunk = new ArrayList<>();

        HuggingFaceTokenizer tokenizer=null;
        try {
            tokenizer = HuggingFaceTokenizer.newInstance(
                    Paths.get("D:/IdeaProjects/RAG+Agent/src/main/resources/tokenizer")
            );
        } catch (IOException e) {
            throw new RuntimeException("初始化 HuggingFaceTokenizer 失败", e);
        }

        if(document.get("paragraphs") instanceof List<?> paragraphs){
            for(Object paragh:paragraphs) {
                if (tokenizer.encode(paragh.toString()).getTokens().length > chunkSize) {
                    List<String> chunks = getChunkByToken(tokenizer, paragh.toString(), chunkSize, overflow);
                    for (String chunk1 : chunks) {
                        page.add(1);
                        paragraph.add(1);
                        chunk.add(chunk1);
                    }
                } else {
                    page.add(1);
                    paragraph.add(1);
                    chunk.add(paragh.toString());
                }
            }
        }
        return new SplitResult(page,paragraph,chunk);
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.TXT;
    }

    private List<String> getChunkByToken(HuggingFaceTokenizer tokenizer, String text, int chunkSize, int overflow) {
        List<String> result = new ArrayList<>();
        Encoding encode = tokenizer.encode(text);
        long[] ids = encode.getIds();
        int overlap = Math.max(0, Math.min(overflow, chunkSize - 1));//这里没有校验意识
        Integer bride = chunkSize - overlap;
        for (int i = 0; i < ids.length; i += bride) {
            i = Math.min(i, ids.length);
            long[] slice = Arrays.copyOfRange(ids, i, i + bride);
            String decoded = tokenizer.decode(slice, true);
            if (decoded != null && !decoded.isBlank()) {
                result.add(decoded.trim());
            }
        }
        return result;
    }

}