package com.gb.rag.DocuSplit;


import com.gb.rag.Enum.FileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SplitFactory {

    @Autowired
    private List<DocumentSplit> split;  // Spring 自动注入所有实现

    public DocumentSplit getSplit(FileType type) {
        return split.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("No split for: " + type));
    }
}


