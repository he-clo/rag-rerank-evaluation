package com.gb.rag;

import com.example.Document;
import com.example.TestCase;
import com.example.TestSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gb.rag.DTO.ResultRow;
import com.gb.rag.DTO.SearchResult;
import com.gb.rag.Enum.FileType;
import com.gb.rag.Service.rerank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class Demo3ApplicationTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private rerank rerank;

    @Test
    void contextLoads() {
    }

    /**
     * 用测试集跑 rerank 的 Hit@1 / Hit@3。
     * rerank() 的入参是 (List<SearchResult>, String query)，
     * 而 testset.json 里是 { query, documents:[{content,label}] }，
     * 所以这里把每个 Document 包一层成 SearchResult 再喂进去。
     */
    @Test
    void rerank_Hit() throws Exception {
        List<TestCase> testCases = loadTestCases();

        int hit1 = 0, hit3 = 0, total = 0;
        List<Double> positiveScore = new ArrayList<>();
        List<Double> negativeScore = new ArrayList<>();

        for (TestCase tc : testCases) {
            String rawQuery = tc.getQuery();
            List<Document> docs = tc.getDocuments();
            if (rawQuery == null || docs == null || docs.isEmpty()) {
                continue;
            }

            // 1. 测试集文档 -> rerank 需要的 SearchResult
            List<SearchResult> candidates = toSearchResults(docs);

            // 2. 测试集没有 token / 数据库，所以只测 rerank 本身，用原始 query
            //    想测改写后的效果就打开下面这行
            String question = rawQuery;
            // String question = rerank.requestion(rawQuery);

            // 3. rerank：向量分(fastapi) + BM25 混合，返回 top10
            List<ResultRow> ranked = rerank.rerank(candidates, question);

            total++;

            // 4. ResultRow.subscribe 是候选列表的原始下标，靠它回查 label
            boolean firstIsPositive = false;
            boolean inTop3 = false;
            for (int rank = 0; rank < ranked.size(); rank++) {
                ResultRow row = ranked.get(rank);
                int originIdx = row.getSubscribe();
                if (originIdx < 0 || originIdx >= docs.size()) {
                    continue;
                }
                Document d = docs.get(originIdx);
                boolean positive = d.getLabel() != null && d.getLabel() == 1;

                if (positive) {
                    positiveScore.add(row.getScore());
                    if (rank == 0) firstIsPositive = true;
                    if (rank < 3) inTop3 = true;
                } else {
                    negativeScore.add(row.getScore());
                }
            }

            if (firstIsPositive) hit1++;
            if (inTop3) hit3++;

            System.out.printf("[%s] top1=%s top3=%s | order=%s%n",
                    rawQuery, firstIsPositive, inTop3, orderLabels(ranked, docs));
        }

        if (total == 0) {
            System.out.println("测试集为空，没跑任何样本");
            return;
        }

        System.out.println("================ rerank 评测结果 ================");
        System.out.printf("样本数 = %d%n", total);
        System.out.printf("Hit@1  = %.4f  (%d/%d)%n", hit1 * 1.0 / total, hit1, total);
        System.out.printf("Hit@3  = %.4f  (%d/%d)%n", hit3 * 1.0 / total, hit3, total);
        System.out.printf("正例平均分 = %.4f, 负例平均分 = %.4f%n",
                avg(positiveScore), avg(negativeScore));
        System.out.println("================================================");
    }

    private List<TestCase> loadTestCases() throws Exception {
        try (InputStream is = new ClassPathResource("testset.json").getInputStream()) {
            TestSet set = objectMapper.readValue(is, TestSet.class);
            return set.getGroups() == null ? new ArrayList<>() : set.getGroups();
        }
    }

    /** Document -> SearchResult，rerank 只用到 content，其余字段填占位值 */
    private List<SearchResult> toSearchResults(List<Document> docs) {
        List<SearchResult> list = new ArrayList<>(docs.size());
        int idx = 0;
        for (Document d : docs) {
            SearchResult r = new SearchResult();
            r.setId((long) idx);
            r.setDocId("testset-chunk-" + idx);
            r.setDocType(FileType.TXT);
            r.setChunkIndex(idx);
            r.setContent(d.getContent());
            r.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            list.add(r);
            idx++;
        }
        return list;
    }

    private String orderLabels(List<ResultRow> ranked, List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (ResultRow row : ranked) {
            int i = row.getSubscribe();
            sb.append(i >= 0 && i < docs.size() ? docs.get(i).getLabel() : "?");
        }
        return sb.toString();
    }

    private double avg(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0;
        for (Double v : values) sum += v;
        return sum / values.size();
    }
}
