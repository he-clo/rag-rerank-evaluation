CREATE TABLE document_chunk (
                                id BIGSERIAL PRIMARY KEY,
                                doc_id VARCHAR(255) NOT NULL,          -- 文档标识（文件名/UUID）
                                doc_type VARCHAR(50) NOT NULL,          -- PDF / MD / DOCX（原来 parser 类型）
                                chunk_index INT NOT NULL,               -- 第几块
                                content TEXT NOT NULL,                  -- 文本块内容
                                embedding VECTOR(1024),                 -- bge-m3 向量（pgvector）

    -- 通用元数据（JSON，存各 parser 不同的字段）
                                metadata JSONB,

    -- 公共字段
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                UNIQUE(doc_id, chunk_index)
);

-- 向量索引
CREATE INDEX ON document_chunk USING ivfflat (embedding vector_cosine_ops);
-- 元数据查询索引
CREATE INDEX ON document_chunk USING GIN (metadata);
-- 文档类型过滤索引
CREATE INDEX ON document_chunk (doc_type);