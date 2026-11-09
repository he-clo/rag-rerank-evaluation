package com.gb.rag.DocuSplit.impl;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gb.rag.DocuParser.impl.MdParser;
import com.gb.rag.DocuSplit.DocumentSplit;
import com.gb.rag.Enum.FileType;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Component
//要切
public class MdSplit implements DocumentSplit {
    //切的逻辑是在补全元数据的情况下，保留小标题，使搜索时能知道属于哪一段内容

    @Value("${embedding.batch-size}")
    private int chunkSize;

    @Value("${embedding.overflow}")
    private int overflow;


    @Override
    public SplitResult split(Map<String, Object> text) {
        SplitResult textList = getTextList(text);
        return textList;
    }

    @Override
    public boolean supports(FileType type) { return type==FileType.MARKDOWN;}

    public SplitResult getTextList(Map<String, Object> document) {
        HuggingFaceTokenizer tokenizer=null;
        try {
            tokenizer = HuggingFaceTokenizer.newInstance(
                    Paths.get("D:/IdeaProjects/RAG+Agent/src/main/resources/tokenizer")
            );
        } catch (IOException e) {
            throw new RuntimeException("初始化 HuggingFaceTokenizer 失败", e);
        }

        int pn=0;
        List<Integer> page=new ArrayList<>();
        List<Integer> paragraph=new ArrayList<>();

        List<Integer> headPath= new ArrayList<>();
        List<String>  headText=new ArrayList<>();
        List<String> result=new ArrayList<>();
        if (!(document.get("blocks") instanceof List<?> blocks)) {
            return new SplitResult(List.of(),List.of(),List.of());
        }
        for (Object item : blocks) {
            if (!(item instanceof MdParser.MDNode node)) {
                continue;
            }
            page.add(1);
            if(node.level instanceof Number levelNum ){
                Integer level= (Integer) node.level;
                if (headPath.size() < 2){//&&(headPath.get(headPath.size()-1) <= level)
                    headPath.add(level);
                    headText.add((String)node.text);
                }
                if(headPath.size()>0 && headPath.get(headPath.size()-1) >= level){
                    headPath.clear();
                    headText.clear();
                    headPath.add(level);
                    headText.add((String) node.text);
                }
                pn++;
            }else if(((String)node.type).equals("paragraph") ||((String)node.type).equals("blockquote")){
                StringBuffer buffer=new StringBuffer();
                for (String text:headText){
                    buffer.append("【"+text+"】");
                }
                //buffer.append((String) node.text);
                String text=buffer.toString();
                if(tokenizer.encode(node.text).getTokens().length>chunkSize){
                    List<String> splits = splitLongChunk(tokenizer, node.text, chunkSize, overflow);
                    for (String chunk: splits){
                        String s = text + chunk;
                        result.add(s);
                    }
                }else {
                    buffer.append(node.text);
                    result.add(buffer.toString());//还要加前两个小标题
                }
                pn++;
                paragraph.add(pn);
            }
            else if(((String)node.type).equals("table") || ((String)node.type).equals("bullet_list")){
                StringBuffer buffer2=new StringBuffer();
                for (String text:headText){
                    buffer2.append("【"+text+"】");
                }
                Object itemsObj = node.items;
                if (itemsObj instanceof List<?> items && !items.isEmpty()) {
                    String text = items.stream()
                            .map(String::valueOf)
                            .filter(s -> !s.isBlank())
                            .collect(Collectors.joining(";"));
                    //result.add(text);
                    String title=buffer2.toString();
                    if(tokenizer.encode(text).getTokens().length>chunkSize){
                        List<String> splits = splitLongChunk(tokenizer, text, chunkSize, overflow);
                        for (String chunk: splits){
                            String s = title + chunk;
                            result.add(s);
                        }
                    }else {
                        buffer2.append(text);
                        result.add(buffer2.toString());//还要加前两个小标题
                    }
                }
                pn++;
                paragraph.add(pn);
            }
        }
        return new SplitResult(
                page,
                paragraph,
                result
        );
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

}
