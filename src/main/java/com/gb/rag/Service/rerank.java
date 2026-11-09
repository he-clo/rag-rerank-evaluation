package com.gb.rag.Service;

import com.gb.rag.DTO.ResultRow;
import com.gb.rag.DTO.SearchResult;
import com.gb.rag.DocuEmbedd.impl.DocxEmbedd;
import com.gb.rag.Enum.EmbedResult;
import com.gb.rag.Enum.FileType;
import com.gb.rag.util.JwtUtil;
import com.pgvector.PGvector;
import io.jsonwebtoken.Claims;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;
import org.postgresql.util.PGobject;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class rerank {
    @Autowired
    DataSource dataSource;

    @Autowired
    ChatClient chatClient;

    @Autowired
    DocxEmbedd embedd;

    @Value("${ollama.base-url}")
    String baseUrl;

    @Value("${ollama.model.reranker}")
    String rerankerModel;

    @Value("${Variable}")
    private Double q;


    public @Nullable String requestion(String question) {
        return chatClient.
                prompt("你是一个智能分类系统，可以顺利识别用户意图，并翻译成语句更通顺的形式进行提问，保持原有语句意思不变,并选择性保留部分关键词。" +
                        "不要增加修饰字符，比如{意图：概念解释/知识查询  \\n改写提问：请解释一下什么是并发编程？  \\n保留关键词：并发编程、解释} 就只需要保留'请解释一下什么是并发编程'这句话，其他的部分不要")
                .user(question)
                .call()
                .content();
    }

    public List<SearchResult> get50(String question,String token) throws SQLException {
        Claims claims = JwtUtil.parseToken(token);
        Integer userId = (Integer) claims.get("userId");
        String tableName="kb_tenant_"+userId;
        String sql = String.format(
                "SELECT * FROM %s ORDER BY embedding <=> ?::vector LIMIT 50",
                tableName
        );
        List<SearchResult> results;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            Float[] embeddingVector = embedd.embedding(List.of(question));
            //ps.setObject(1, toStr(embeddingVector));   // 向量参数绑定

            PGobject vector = new PGobject();
            vector.setType("vector");
            vector.setValue(toStr(embeddingVector).toString());          // ✅ 拼好的字符串
            ps.setObject(1, vector);

            results=new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // 处理结果
                    SearchResult r = new SearchResult();
                    r.setId(rs.getLong("id"));
                    r.setDocId(rs.getString("doc_id"));
                    String docType = rs.getString("doc_type");
                    FileType fileType = FileType.valueOf(docType);
                    r.setDocType(fileType);
                    r.setChunkIndex(rs.getInt("chunk_index"));
                    r.setContent(rs.getString("content"));
//                    float[] vec = (float[]) rs.getObject("embedding");
                    PGobject pgObject = (PGobject) rs.getObject("embedding");  // ✅ 取成 PGobject
                    String value = pgObject.getValue();                         // "[0.1,0.2,0.3,...]"

                    String[] parts = value.replace("[", "").replace("]", "").split(",");
                    float[] embedding = new float[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        embedding[i] = Float.parseFloat(parts[i].trim());
                    }
                    r.setCreatedAt(rs.getTimestamp("created_at"));
                    results.add(r);      // 每读一条，就加进 List
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return results;
    }

    public List<ResultRow> rerank(List<SearchResult> results,String requestion) throws IOException {

        RestTemplate restTemplate = new RestTemplate();

        // 构造请求体
        Map<String, Object> body = new HashMap<>();
        body.put("query", requestion);

        List<String> documents = new ArrayList<>();
        for (SearchResult r : results) {
            documents.add(r.getContent());  // 假设 SearchResult 有 getContent()
        }
        body.put("documents", documents);

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        // 发送 POST 请求到 FastAPI 服务
        String url = "http://localhost:8000/api/rerank";
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        // 解析返回的 scores
        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<Double> rankedResults = (List<Double>) responseBody.get("scores");

        int n = documents.size();
        if (n == 0) {
            return new ArrayList<>();
        }

        // ---- 1. 向量分，按文档下标严格对齐 ----
        // 要求 FastAPI 端按"输入 documents 的顺序"返回每个 doc 的原始分，
        // 数量必须与 documents 相等，否则无法按文档下标对齐。
        if (rankedResults == null || rankedResults.size() != n) {
            throw new IllegalStateException(
                    "rerank scores 数量与 documents 不一致: scores="
                            + (rankedResults == null ? "null" : rankedResults.size())
                            + ", documents=" + n + "，无法按文档下标对齐");
        }
        double[] vecScore = new double[n];
        for (int i = 0; i < n; i++) {
            vecScore[i] = rankedResults.get(i);
        }

        // ---- 2. 分词（IKSegmenter 非 AutoCloseable，只把 StringReader 放进 try） ----
        List<String> words = new ArrayList<>();
        try (StringReader reader = new StringReader(requestion)) {
            IKSegmenter ik = new IKSegmenter(reader, true);
            Lexeme lexeme;
            while ((lexeme = ik.next()) != null) {
                words.add(lexeme.getLexemeText());
            }
        }

        // ---- 3. BM25 按文档下标累加（bm25[d] 就是第 d 篇文档的 BM25 分） ----
        double avgdl = avgdi(documents);
        double k1 = 1.2, b = 0.75;
        double[] bm25 = new double[n];

        // 缓存每篇文档长度，避免重复分词
        double[] docLen = new double[n];
        for (int d = 0; d < n; d++) {
            docLen[d] = docLength(documents.get(d));
        }

        for (String word : words) {
            double idf = IDF(word, documents);
            for (int d = 0; d < n; d++) {
                int tf = countOccurrences(documents.get(d), word);
                if (tf == 0) continue;
                double dl = docLen[d];
                bm25[d] += idf * (tf * (k1 + 1))
                        / (tf + k1 * (1 - b + b * dl / avgdl));
            }
        }

        // ---- 4. 归一化（max==min 时返回全 0，避免 1e-9 假裁判制造假满分/常数） ----
        double[] vecNorm = normalize(vecScore);
        double[] bm25Norm = normalize(bm25);

        // ---- 5. 用文档下标排序，ResultRow 存回原始下标 ----
        List<ResultRow> ranked = IntStream.range(0, n)
                .boxed()
                .sorted(Comparator.comparingDouble(
                        (Integer i) -> vecNorm[i] * q + bm25Norm[i] * (1 - q)
                ).reversed())
                .limit(10)
                .map(i -> {
                    SearchResult sr = results.get(i);          // ← 取回对应原始记录
                    return new ResultRow(
                            i,                                  // 下标
                            vecNorm[i] * q + bm25Norm[i] * (1 - q),
                            0,                                  // rankNum
                            sr.getId(),                         // ★ 数据库主键
                            sr.getDocId(),                      // ★ 文档标识
                            sr.getChunkIndex(),                 // ★ chunk 序号
                            sr.getContent()                     // ★ 内容
                    );
                })
                .collect(Collectors.toList());

        // ---- 诊断打印：直接看每篇文档的原始分、归一化分和内容头部 ----
        System.out.println("==== rerank 对齐诊断 ==== n=" + n + ", words=" + words.size());
        System.out.println("raw vec  = " + Arrays.toString(vecScore));
        System.out.println("raw bm25 = " + Arrays.toString(bm25));
        System.out.println("vecNorm  = " + Arrays.toString(vecNorm));
        System.out.println("bm25Norm = " + Arrays.toString(bm25Norm));
        for (int i = 0; i < n; i++) {
            SearchResult sr = results.get(i);
            String head = documents.get(i).replaceAll("\\s+", "");
            if (head.length() > 25) head = head.substring(0, 25);
            System.out.printf("i=%d [id=%d, docId=%s, chunkIdx=%d] vec=%.4f bm25=%.4f mix=%.4f | %s%n",
                    i, sr.getId(), sr.getDocId(), sr.getChunkIndex(),
                    vecNorm[i], bm25Norm[i],
                    vecNorm[i] * q + bm25Norm[i] * (1 - q), head);
        }
        System.out.println("排名 result(原始下标) = "
                + ranked.stream().map(ResultRow::getSubscribe).collect(Collectors.toList()));

        return ranked;
    }





// 工具方法
private int countOccurrences(String text, String word) {
    if (word.isEmpty()) return 0;
    int c = 0, idx = 0;
    while ((idx = text.indexOf(word, idx)) != -1) { c++; idx += word.length(); }
    return c;
}

    private double docLength(String text) throws IOException {
        int c = 0;
        try (StringReader r = new StringReader(text)) {
            IKSegmenter ik = new IKSegmenter(r, true);
            while (ik.next() != null) c++;
        }
        return c;
    }


public double IDF(String word,List<String> documents){
        int qi=0;
        for (String document : documents) {
            if (document.contains(word)){
                qi++;
            }
        }
        int n = documents.size();
        // +1e-9 平滑，避免 log(0) / log(负数)
        return Math.log(((n - qi + 0.5) / (qi + 0.5)) + 1e-9);
    }

    public double avgdi(List<String> documents) throws IOException {
        if (documents.isEmpty()) return 0;
        double sum = 0;
        for (String document : documents) {
            sum += docLength(document);
        }
        return sum / documents.size();
    }

    // min-max 归一化；max==min 时返回全 0（不再用 1e-9 制造假满分/常数）
    public double[] normalize(double[] input) {
        double[] out = new double[input.length];
        if (input.length == 0) return out;

        double max = Arrays.stream(input).max().orElse(0.0);
        double min = Arrays.stream(input).min().orElse(0.0);
        double range = max - min;

        if (range < 1e-12) {
            // 所有值相同，无法区分，统一给 0
            return out;
        }
        for (int i = 0; i < input.length; i++) {
            out[i] = (input[i] - min) / range;
        }
        return out;
    }

    public StringBuilder toStr(Float[] vec) throws SQLException {

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");

        return sb;
    }
}
