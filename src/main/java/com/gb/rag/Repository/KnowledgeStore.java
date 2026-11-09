package com.gb.rag.Repository;

import com.gb.rag.Enum.FileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Repository
public class KnowledgeStore {
//    id BIGSERIAL PRIMARY KEY,
//    doc_id VARCHAR(255) NOT NULL,          -- 文档标识（文件名/UUID）
//    doc_type VARCHAR(50) NOT NULL,          -- PDF / MD / DOCX（原来 parser 类型）
//    chunk_index INT NOT NULL,               -- 第几块
//    content TEXT NOT NULL,                  -- 文本块内容
//    embedding VECTOR(1024),                 -- bge-m3 向量（pgvector）
@Autowired
private DataSource dataSource;

    /**
     * 为一个文档建表，并批量插入所有 chunk。
     * 一次调用完成「建表 + 灌数据」。
     */
    public boolean saveDocument(Long docId, String fileName,FileType docType,
                             String chunk, Float[] vector)
            throws SQLException {

        String table = tableName(docId);

        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {

            // 1) 建表（幂等，重复插入同一文档不会报错）
            st.execute("CREATE EXTENSION IF NOT EXISTS vector");
            st.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                            id          BIGSERIAL PRIMARY KEY,
                            doc_id      VARCHAR(255) NOT NULL,
                            doc_type    VARCHAR(100),
                            chunk_index INT          NOT NULL,        -- ← INT，不再自增
                            content     TEXT         NOT NULL,
                            embedding   vector(1024),
                            created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
            """, table));

            // 2) 唯一索引
            st.execute(String.format("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_%1$s_chunk
                ON %1$s(doc_id, chunk_index)
            """, table));

            st.execute(String.format("""
                CREATE INDEX IF NOT EXISTS idx_%1$s_embedding_hnsw
                ON %1$s USING hnsw (embedding vector_cosine_ops)
                WITH (m = 16, ef_construction = 64)
            """, table));
        }

        // 2) 批量插入（id 自增留空，靠 RETURNING id 拿回主键）
        String insertSql = String.format("""
            INSERT INTO %s (doc_id, doc_type, chunk_index, content, embedding)
                        SELECT ?, ?, COALESCE(MAX(chunk_index), 0) + 1, ?, ?::vector
                        FROM %s WHERE doc_id = ?
        """, table, table);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {


            ps.setString(1, fileName);       // 真正的文档标识
            ps.setObject(2, docType.name());
            ps.setString(3, chunk);
            ps.setString(4, toPgVector(vector));
            ps.setString(5, fileName);       // 真正的文档标识
                ps.addBatch();


            int i = ps.executeUpdate();// 一次批量提交
            return i>0;
        }
    }

    private String toPgVector(Float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    public String tableName(Long docId) {
        return "kb_tenant_"+docId;
    }
}


