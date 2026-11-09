package com.gb.rag.DocuSplit.impl;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.gb.rag.DocuSplit.DocumentSplit;
import com.gb.rag.Enum.FileType;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;

@Component
//这个最好不切，按行拿语义完整
public class XLSXSplit implements DocumentSplit {

    @Value("${embedding.batch-size}")
    private int chunkSize;

    @Value("${embedding.overflow}")
    private int overflow;

    @Override
    public SplitResult split(Map<String, Object> text) {
        return new SplitResult(List.of(),List.of(),List.of());
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.XLSX;
    }

    private  SplitResult splitChunk(Map<String, Object> document) {
        List<Integer> page = new ArrayList<>();
        List<Integer> paragraph = new ArrayList<>();
        List<String> chunk = new ArrayList<>();
        List<String> stack=new ArrayList<>();

        HuggingFaceTokenizer tokenizer=null;
        try {
            tokenizer = HuggingFaceTokenizer.newInstance(
                    Paths.get("D:/IdeaProjects/RAG+Agent/src/main/resources/tokenizer")
            );
        } catch (IOException e) {
            throw new RuntimeException("初始化 HuggingFaceTokenizer 失败", e);
        }

        if(document.get("blocks") instanceof List<?> blocks){
            for(Object block:blocks) {
                if(block instanceof Map<?,?> bloc){
                    if (bloc.get("rows") instanceof List<?>rwos){
                        for(Object row:rwos){
                            if(row instanceof Map<?,?> ro){
                                if(ro.get("row_index") instanceof Integer index){
                                    if(index==0){
                                        if(ro.get("cells") instanceof List<?> cells){
                                            for(Object cell:cells){
                                                stack.add(cell.toString());
                                            }
                                        }
                                    }else{
                                        if(ro.get("cells") instanceof List<?> cells){
                                            for(Object cell:cells){
                                                int i=0;
                                                if (tokenizer.encode(cell.toString()).getTokens().length > chunkSize) {
                                                    List<String> chunks = getChunkByToken(tokenizer, cell.toString(), chunkSize, overflow);
                                                    for(String chun:chunks){
                                                        page.add(1);
                                                        paragraph.add(index);
                                                        if(i<stack.size()) {
                                                            chunk.add(stack.get(i) + ":" + chun);
                                                        }else {
                                                            chunk.add(chun);
                                                        }
                                                    }
                                                }else{
                                                    page.add(1);
                                                    paragraph.add(index);
                                                    chunk.add(cell.toString());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
        return new SplitResult(page,paragraph,chunk);
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