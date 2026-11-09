package com.gb.rag.DocuEmbedd.impl;

import com.gb.rag.DocuEmbedd.DocumentEmbedd;
import com.gb.rag.Enum.EmbedResult;
import com.gb.rag.Enum.EmbedTask;
import com.gb.rag.Enum.FileType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

@Component
//需要切的
@Slf4j
public class DocxEmbedd implements DocumentEmbedd {

    @Autowired
    BlockingQueue<EmbedTask> inputQueue;
    @Autowired
    BlockingQueue<EmbedResult> outputQueue;

    @Value("${ollama.base-url}")
    private String baseUrl;

    @Value("${ollama.model.embed}")
    private String embedModel;

    @Value("${ollama.timeout-ms}")
    private int timeoutMs;

    @Autowired
    private RestTemplate restTemplate;


    public float[] embedBatch(List<String> texts) {
        String url = baseUrl + "/api/embed";  // ← 注意：embed，不是 embeddings

        // 请求体：input 传数组
        Map<String, Object> body = Map.of(
                "model", embedModel,
                "input", texts           // ← input 是数组，一次传多条
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // /api/embed 返回的是 "embeddings"（复数数组），不是 "embedding"
                List<List<Number>> embeddings =
                        (List<List<Number>>) response.getBody().get("embeddings");

                // 顺序与输入一一对应
                float[] result = new float[embeddings.size()];
                for (List<Number> emb : embeddings) {
                    float[] vec = new float[emb.size()];
                    for (int i = 0; i < emb.size(); i++) {
                        vec[i] = emb.get(i).floatValue();
                    }
                    result=vec;
                }
                return result;
            }
        } catch (RestClientException e) {
            // 打日志，别吞
            log.error("Ollama embed 调用失败, model={}, size={}", embedModel, texts.size(), e);
            throw e;  // 要么抛出，要么返回 Optional，别静默返回空
        }

        return new float[0];
    }

    //差速齿轮实现方法：1.Semaphore + 定时释放令牌（推荐）
    //2.Guava RateLimiter（更简洁）
    //3.在队列外面套一层限流
    @Override
    public Float[] embedding(List<String> chunk) throws InterruptedException {
//        RateLimiter limiter = RateLimiter.create(50.0);
////            方法	队列满了怎么办	返回值	什么时候用
////            add(e)	抛 IllegalStateException	boolean（成功返回 true）	几乎不用，太暴力
////            put(e)	阻塞等待，直到有空位	void	生产者场景首选
////            offer(e)	立即返回 false	boolean	不想阻塞、自己处理失败
////            offer(e, 3, TimeUnit.SECONDS)	等 3 秒，还满就返回 false	boolean	想超时兜底
//            limiter.acquire();  // 自动限速，拿不到令牌就等
//            // 处理逻辑
//            while(!chunk.isEmpty()) {
//                for(String text:chunk){
//                    inputQueue.put(text);
//                }
//            }
        float[] floats = embedBatch(chunk);
        Float[] aFloat = toFloat(floats);
        return aFloat;
    }


    public Float[] toFloat(float[] floats){
        int len=floats.length;
        Float[] result=new Float[len];
        int i=0;
        for(float f:floats){
            result[i]=f;
            i++;
        }
        return result;
    }

    @Override
    public boolean supports(FileType type) {
        return type == FileType.DOCX || type == FileType.HTML ||type == FileType.MARKDOWN || type == FileType.PDF || type == FileType.PPTX ||type == FileType.RST || type == FileType.TXT || type == FileType.XLSX;
    }
}