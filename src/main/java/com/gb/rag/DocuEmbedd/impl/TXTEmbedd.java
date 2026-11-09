package com.gb.rag.DocuEmbedd.impl;


import com.gb.rag.DocuEmbedd.DocumentEmbedd;
import com.gb.rag.Enum.FileType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
//先按\n切，再按token切
public class TXTEmbedd implements DocumentEmbedd {

    @Override
    public Float[] embedding(List<String> chunk) {
        return new Float[0];
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.TXT;
    }

}