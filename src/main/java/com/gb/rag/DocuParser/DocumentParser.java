package com.gb.rag.DocuParser;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.gb.rag.Enum.FileType;

// 抽象产品
public interface DocumentParser {
    Map<String, Object> parse(InputStream input, String fileName);
    boolean supports(FileType type);
//    List<Integer> getParagraphN(Map<String,Object> parser);不一定有
//    List<Integer> getPages(Map<String,Object> parser);
}
