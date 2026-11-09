package com.gb.rag.DocuEmbedd.impl;


import com.gb.rag.DocuEmbedd.DocumentEmbedd;
import com.gb.rag.Enum.FileType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
@Component
//要切
public class HtmlEmbedd implements DocumentEmbedd {


    @Override
    public Float[] embedding(List<String> chunk) {
        return new Float[0];
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.HTML;
    }
}