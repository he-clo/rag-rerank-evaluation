package com.gb.rag.DocuParser;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;// 工厂

import java.util.List;
import com.gb.rag.Enum.FileType;

@Component
public class ParserFactory {

    @Autowired
    private List<DocumentParser> parsers;  // Spring 自动注入所有实现

    public DocumentParser getParser(FileType type) {
        DocumentParser documentParser = parsers.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("No parser for: " + type));

        return documentParser;
    }
}

// 使用
/*FileType type = FileTypeDetector.detect(input);  // Magic Bytes
DocumentParser parser = parserFactory.getParser(type);
ParseResult result = parser.parse(input, fileName);*/

