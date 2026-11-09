package com.gb.rag.DocuSplit.impl;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.gb.rag.DocuSplit.DocumentSplit;
import com.gb.rag.Enum.FileType;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;

@Component
//要切
//部分清洗逻辑在解析时就处理了，只有ppt是这样
public class PPTXSplit implements DocumentSplit {

    @Value("${embedding.batch-size}")
    private int chunkSize;

    @Value("${embedding.overflow}")
    private int overflow;

    @Override
    public SplitResult split(Map<String, Object> document) {
        return getTextAndPage(document);
    }

    private SplitResult getTextAndPage(Map<String, Object> document) {
        HuggingFaceTokenizer tokenizer=null;
        try {
            tokenizer = HuggingFaceTokenizer.newInstance(
                    Paths.get("D:/IdeaProjects/RAG+Agent/src/main/resources/tokenizer")
            );
        } catch (IOException e) {
            throw new RuntimeException("初始化 HuggingFaceTokenizer 失败", e);
        }

        if (!(document.get("blocks") instanceof List<?> blocks)) {
            return new SplitResult(List.of(),List.of(),List.of());
        }

        List<String> chunk = new ArrayList<>();
        List<Integer> page=new ArrayList<>();
        List<Integer> paragraph=new ArrayList<>();
        for (Object item : blocks) {
            if (!(item instanceof Map<?, ?> block) || !(block.get("text") instanceof String text)) {
                continue;
            }

            if (!(item instanceof Map<?, ?>) || !(block.get("page") instanceof Integer pages)) {
                continue;
            }
//            // 去除多余的换行符
//            text=text.replaceAll("\n+", "\n");
//             清洗逻辑在解析时就已经清洗了
//            // 将换行符替换为一个正则
//            text=text.replaceAll("\n", " ");
           // String textClean=text; 作用域问题
            if(tokenizer.encode(text.toString()).getTokens().length>chunkSize) {
                List<String> chunks = getChunkByToken(tokenizer, text, chunkSize, overflow);
                for (String string : chunks) {
                    chunk.add(string);
                    page.add(pages);
                    paragraph.add(1);
                }
            }else {
                chunk.add(text);
                page.add(pages);
                paragraph.add(1);
            }
        }

         return new SplitResult(page,paragraph,chunk);
    }

    private List<String> getChunkByToken(HuggingFaceTokenizer tokenizer,String text,Integer chunkSize,Integer overflow){
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
    public boolean supports(FileType type) {
        return type == FileType.PPTX;
    }
}