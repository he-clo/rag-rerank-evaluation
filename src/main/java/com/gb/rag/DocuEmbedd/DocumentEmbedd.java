package com.gb.rag.DocuEmbedd;

import com.gb.rag.Enum.FileType;

import java.util.List;
import java.util.Map;

public interface DocumentEmbedd {
    Float[] embedding(List<String> chunk) throws InterruptedException;
    boolean supports(FileType type);
}
