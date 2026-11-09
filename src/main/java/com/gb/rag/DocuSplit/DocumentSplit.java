package com.gb.rag.DocuSplit;

import com.gb.rag.Enum.FileType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface DocumentSplit {
    SplitResult split(Map<String,Object> text) throws IOException;
    boolean supports(FileType type);

    public record SplitResult(
            List<Integer> pages,
            List<Integer> paragraphN,
            List<String>  chunks
    ){}
}
