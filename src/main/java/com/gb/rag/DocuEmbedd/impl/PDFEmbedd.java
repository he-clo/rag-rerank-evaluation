package com.gb.rag.DocuEmbedd.impl;


import com.gb.rag.DocuEmbedd.DocumentEmbedd;
import com.gb.rag.Enum.FileType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Component
//用PyMuPDF，如果是多栏等复杂情况 贵的可以走vlm LlamaParse，便宜的可以走Docling 扫描件OCR
public class PDFEmbedd implements DocumentEmbedd {


    @Override
    public Float[] embedding(List<String> chunk) {
        return new Float[0];
    }

    @Override
    public boolean supports(FileType type) { return type==FileType.PDF;}
}
