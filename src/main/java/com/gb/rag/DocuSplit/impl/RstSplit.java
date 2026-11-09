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
import java.util.*;

@Component
public class RstSplit implements DocumentSplit {

    /*"""
    type                       count   percent
title                         30    35.29%         根据md，贴在paragraph的前面
paragraph                     26    30.59%         判断是否需要切分
system_message                10    11.76%          截取\n\n***\n中间的值
bullet_list                    6     7.06%          需要截取上一行类似于【特点】等关键词，5-8个token// 还是截取所有字符再切分
table_flattened                3     3.53%          压扁的表格 拿到flattened
block_quote                    3     3.53%          引用，比较短，最好拼接上一行
enumerated_list                2     2.35%          同bullet_list
admonition                     2     2.35%          说明，语义上有相关含义，可以拼成一句话再切开.段内拼
math                           1     1.18%          数学公式，转义\需要为原来的一半
glossary                       1     1.18%          定义 term+definition拼接，但彼此之间不是太相关
literal_block                  1     1.18%          流程图，没有解析好，直接拿值
    """*/

    @Value("${embedding.batch-size}")
    private int chunkSize;

    @Value("${embedding.overflow}")
    private int overflow;

    @Override
    public SplitResult split(Map<String, Object> document) {
        Map<String, Object> map = getMap(document);
        SplitResult splitResult = splitChunk(map);
        //   return new SplitResult(List.of(),List.of(),List.of(map.toString()));
        return splitResult;
    }

    private Map<String, Object> getMap(Map<String, Object> document) {
        Map<String, Object> result = new HashMap<>();
        List<List> maps = new ArrayList<>();
        if (!(document.get("sections") instanceof List<?> sections)) {
            return result;
            //return new SplitResult(List.of(), List.of(), List.of());
        }

        for (Object item : sections) {
            if (!(item instanceof Map<?, ?> block) || !(block.get("content") instanceof List<?> content)) {
                continue;
            }
            maps.add(content);
            if (block.containsKey("sections")) {
                if (block.get("sections") instanceof List<?> section) {
                    for (Object s1 : section) {
                        if (s1 instanceof Map<?, ?> se) {
                            if (se.get("content") instanceof List<?> co) {
                                maps.add(co);
                            }
                        }
                    }
                }
            }
        }
        result.put("content", maps);
        return result;
    }

    private SplitResult splitChunk(Map map) {
        HuggingFaceTokenizer tokenizer = null;
        try {
            tokenizer = HuggingFaceTokenizer.newInstance(
                    Paths.get("D:/IdeaProjects/RAG+Agent/src/main/resources/tokenizer")
            );
        } catch (IOException e) {
            throw new RuntimeException("初始化 HuggingFaceTokenizer 失败", e);
        }
        List<Integer> page = new ArrayList<>();
        List<Integer> paragraph = new ArrayList<>();
        List<String> chunk = new ArrayList<>();
        String prefix = "", text = "";
        if (map.get("content") instanceof List<?> content) {
            for (Object item : content) {
                if (item instanceof List<?> list) {//里面还是一个list，需要两层遍历
                    for (Object s0 : list) {
                        if (s0 instanceof Map<?, ?> value) {
                            switch ((String) value.get("type")) {
                                case "title":
                                    prefix = value.get("text") != null ? "【" + value.get("text") + "】" : "【" + value.get("unknow") + "】";
                                    break;
                                case "paragraph":
                                    text = value.get("text") != null ? value.get("text").toString() : value.get("unknow").toString();
                                    if (!prefix.isEmpty()) {
                                        if (tokenizer.encode(text).getTokens().length > chunkSize) {
                                            List<String> chunks = getChunkByToken(tokenizer, text, chunkSize, overflow);
                                            for (String chunk1 : chunks) {
                                                page.add(1);
                                                paragraph.add(1);
                                                chunk.add(prefix + chunk1);
                                            }
                                        } else {
                                            page.add(1);
                                            paragraph.add(1);
                                            chunk.add(prefix + text);
                                        }
                                        //chunk.add(prefix + text);
                                        prefix = "";
                                    }
                                    break;
                                case "system_message":
                                    String unkonwn = value.get("unknow").toString();
                                    int first = unkonwn.indexOf("\\n");
                                    int second = unkonwn.indexOf("\\n", first + 1);
                                    int la = unkonwn.lastIndexOf("\\n");
                                    if (second != -1 && la != -1 && la > second) {
                                        String sys_mess = unkonwn.substring(second, la);
                                        if (tokenizer.encode(sys_mess).getTokens().length > chunkSize) {
                                            List<String> chunks2 = getChunkByToken(tokenizer, sys_mess, chunkSize, overflow);
                                            for (String chunk2 : chunks2) {
                                                page.add(1);
                                                paragraph.add(1);
                                                chunk.add(chunk2);
                                            }
                                        } else {
                                            page.add(1);
                                            paragraph.add(1);
                                            chunk.add(sys_mess);
                                        }
                                    }
                                    break;
                                case "bullet_list":
                                    if (value.get("items") instanceof List<?> items) {
                                        String last = chunk.remove(chunk.size() - 1);
                                        for (Object s1 : items) {
                                            if (tokenizer.encode(s1.toString()).getTokens().length > chunkSize) {
                                                List<String> chunks3 = getChunkByToken(tokenizer, s1.toString(), chunkSize, overflow);
                                                for (String chunk3 : chunks3) {
                                                    page.add(1);
                                                    paragraph.add(1);
                                                    chunk.add(last + ";" + chunk3);
                                                }
                                            } else {
                                                page.add(1);
                                                paragraph.add(1);
                                                chunk.add(last + ";" + s1);
                                            }
                                        }
                                    }
                                    break;
                                case "table_flattened":
                                    //这里本来想用字符串拼接，但是失败了。
                                    if (value.get("flattened") instanceof List<?> items) {
                                        StringBuilder flatten = new StringBuilder();
                                        for (Object s1 : items) {
                                            flatten.append(s1.toString());
                                        }
                                        if (tokenizer.encode(flatten.toString()).getTokens().length > chunkSize) {
                                            List<String> chunks4 = getChunkByToken(tokenizer, flatten.toString(), chunkSize, overflow);
                                            for (String chunk4 : chunks4) {
                                                page.add(1);
                                                paragraph.add(1);
                                                chunk.add(chunk4);
                                            }
                                        } else {
                                            page.add(1);
                                            paragraph.add(1);
                                            chunk.add(flatten.toString());
                                        }
                                    }
                                    break;
                                case "block_quote":
                                    if (value.get("unknow") instanceof List<?> items) {
                                        String last = chunk.remove(chunk.size() - 1);
                                        for (Object s1 : items) {
                                            s1 = s1.toString().replaceAll("\\n", "");
                                            if (tokenizer.encode(s1.toString()).getTokens().length > chunkSize) {
                                                List<String> chunks5 = getChunkByToken(tokenizer, s1.toString(), chunkSize, overflow);
                                                for (String chunk5 : chunks5) {
                                                    page.add(1);
                                                    paragraph.add(1);
                                                    chunk.add(last + ";" + chunk5);
                                                }
                                            } else {
                                                page.add(1);
                                                paragraph.add(1);
                                                chunk.add(last + ";" + s1);
                                            }
                                        }
                                        break;
                                    }
                                    case "enumerated_list":
                                        if (value.get("items") instanceof List<?> items) {
                                            String last = chunk.remove(chunk.size() - 1);
                                            for (Object s1 : items) {
                                                if (tokenizer.encode(s1.toString()).getTokens().length > chunkSize) {
                                                    List<String> chunks6 = getChunkByToken(tokenizer, s1.toString(), chunkSize, overflow);
                                                    for (String chunk6 : chunks6) {
                                                        page.add(1);
                                                        paragraph.add(1);
                                                        chunk.add(last + ";" + chunk6);
                                                    }
                                                } else if (!chunk.isEmpty()) {
                                                    page.add(1);
                                                    paragraph.add(1);
                                                    chunk.add(last + ";" + s1);
                                                }
                                            }
                                        }
                                        break;
                                    case "admonition":
                                        if (value.get("text") instanceof String items) {
                                            if (tokenizer.encode(items.toString()).getTokens().length > chunkSize) {
                                                List<String> chunks7 = getChunkByToken(tokenizer, items.toString(), chunkSize, overflow);
                                                for (String chunk7 : chunks7) {
                                                    page.add(1);
                                                    paragraph.add(1);
                                                    chunk.add(chunk7.replaceAll("\\n", "").strip());
                                                }
                                            } else {
                                                page.add(1);
                                                paragraph.add(1);
                                                chunk.add(items.replaceAll("\\n", "").strip());
                                            }
                                        }
                                        break;
                                    case "math":
                                        if (value.get("latex") instanceof String items) {
                                            if (tokenizer.encode(items.toString()).getTokens().length > chunkSize) {
                                                List<String> chunks8 = getChunkByToken(tokenizer, items.toString(), chunkSize, overflow);
                                                for (String chunk8 : chunks8) {
                                                    page.add(1);
                                                    paragraph.add(1);
                                                    chunk.add(chunk8.replace("\\\\", "\\").strip());
                                                }
                                            } else {
                                                page.add(1);
                                                paragraph.add(1);
                                                chunk.add(items.replace("\\\\", "\\").strip());
                                            }
                                        }
                                        break;
                                    case "glossary":
                                        if (value.get("terms") instanceof List<?> terms) {
                                            StringBuilder result2 = new StringBuilder();
                                            for (Object s1 : terms) {
                                                if (s1 instanceof Map<?, ?> map2) {
                                                    String result = map2.get("term").toString() + map2.get("definition").toString();
                                                    result2.append(result);
                                                }
                                            }
                                            if (tokenizer.encode(result2.toString()).getTokens().length > chunkSize) {
                                                List<String> chunks9 = getChunkByToken(tokenizer, result2.toString(), chunkSize, overflow);
                                                for (String chunk9 : chunks9) {
                                                    page.add(1);
                                                    paragraph.add(1);
                                                    chunk.add(chunk9);
                                                }
                                            } else {
                                                page.add(1);
                                                paragraph.add(1);
                                                chunk.add(result2.toString());
                                            }
                                        }
                                        break;
                                    case "literal_block":
                                        if (value.get("unknow") instanceof String items) {
                                            if (tokenizer.encode(items.toString()).getTokens().length > chunkSize) {
                                                List<String> chunks10 = getChunkByToken(tokenizer, items.toString(), chunkSize, overflow);
                                                for (String chunk10 : chunks10) {
                                                    page.add(1);
                                                    paragraph.add(1);
                                                    chunk.add(items.replaceAll("\\n\\s*\"\"\"", "").strip());
                                                }
                                            } else {
                                                page.add(1);
                                                paragraph.add(1);
                                                chunk.add(items.replaceAll("\\n\\s*\"\"\"", "").strip());
                                            }
                                        }
                                        break;
                            }
                        }
                    }
                }
            }
        }
        return new SplitResult(page, paragraph, chunk);
    }


        @Override
        public boolean supports (FileType type){
            return type == FileType.RST;
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