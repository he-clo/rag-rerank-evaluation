package com.gb.rag.DocuEmbedd;


import com.gb.rag.Enum.FileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmbeddFactory {

    @Autowired
    private List<DocumentEmbedd> split;  // Spring 自动注入所有实现

    public DocumentEmbedd getEmbedd(FileType type) {
        return split.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("can not embedding for: " + type));
    }
}


