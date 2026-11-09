package com.gb.rag.DocuSplit.impl;


import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gb.rag.DocuSplit.DocumentSplit;
import com.gb.rag.Enum.FileType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
@Component
//用PyMuPDF，如果是多栏等复杂情况 贵的可以走vlm LlamaParse，便宜的可以走Docling 扫描件OCR
public class PDFSplit implements DocumentSplit {

    @Value("${embedding.batch-size}")
    private int chunkSize;

    @Value("${embedding.overflow}")
    private int overflow;


    @Override
    public SplitResult split(Map<String, Object> document) {
        SplitResult splitResult = getSplitResult(document);
        return splitResult;
    }

    private SplitResult getSplitResult(Map<String, Object> document) {
        HuggingFaceTokenizer tokenizer=null;
        try {
            tokenizer = HuggingFaceTokenizer.newInstance(
                    Paths.get("D:/IdeaProjects/RAG+Agent/src/main/resources/tokenizer")
            );
        } catch (IOException e) {
            throw new RuntimeException("初始化 HuggingFaceTokenizer 失败", e);
        }
        if (!(document.get("chunks") instanceof List<?> rawChunks)) {//java16新特性，模式匹配
            return new SplitResult(List.of(),List.of(),List.of());
        }
        List<Integer> page=new ArrayList<>();
        List<Integer> paragraph=new ArrayList<>();
        List<String> chunks = new ArrayList<>(rawChunks.size());
        s1:for (Object item : rawChunks) {
            if (item instanceof Map<?, ?> chunk && chunk.get("text") != null) {
                if(tokenizer.encode(chunk.get("text").toString()).getTokens().length>chunkSize){
                    List< String> chunkList=splitLongChunk(tokenizer,chunk.get("text").toString(),chunkSize,overflow);
                    for(String string:chunkList){
                        page.add((Integer) chunk.get("page"));
                        paragraph.add((Integer) chunk.get("paragraphid"));
                        chunks.add(string);
                    }
                    continue s1;
                }else {
                    chunks.add(chunk.get("text").toString());
                }
            }
            if (item instanceof Map<?, ?> pages && pages.get("page") != null) {
                page.add((Integer) pages.get("page"));
            }
            if (item instanceof Map<?, ?> para && para.get("paragraphid") != null) {
                paragraph.add((Integer) para.get("paragraphid"));
            }
        }
        //return chunks;
        return new SplitResult(page,paragraph,chunks);
    }

    private List<String> splitLongChunk(HuggingFaceTokenizer tokenizer,String text,Integer chunkSize,Integer overflow){
        List<String> result=new ArrayList<>();
        Encoding encode = tokenizer.encode(text);
        long[] ids = encode.getIds();
        int overlap = Math.max(0, Math.min(overflow, chunkSize - 1));//这里没有校验意识
        Integer bride=chunkSize-overlap;
        for (int i = 0; i < ids.length ; i+=bride) {
            i=Math.min(i, ids.length);
            long[] slice = Arrays.copyOfRange(ids, i, i + bride);
            String decoded=tokenizer.decode(slice, true);
            if(decoded!=null && !decoded.isBlank()) {
                result.add(decoded.trim());
            }
        }
        return result;
    }

    @Override
    public boolean supports(FileType type) { return type==FileType.PDF;}
}
