package com.gb.rag.DTO;

import com.gb.rag.Enum.FileType;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class SearchResult {
    private Long id;
    private String docId;
    private FileType docType;
    private Integer chunkIndex;
    private String content;
    private float[] embedding;
    private Timestamp createdAt;
}
