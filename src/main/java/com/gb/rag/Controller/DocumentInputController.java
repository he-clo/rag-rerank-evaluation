package com.gb.rag.Controller;

import com.gb.rag.DTO.ResultRow;
import com.gb.rag.DTO.SearchResult;
import com.gb.rag.DocuParser.DocumentParser;
import com.gb.rag.DocuParser.ParserFactory;
import com.gb.rag.DocuSplit.DocumentSplit;
import com.gb.rag.DocuSplit.SplitFactory;
import com.gb.rag.Enum.EmbedResult;
import com.gb.rag.Enum.FileType;
import com.gb.rag.Repository.KnowledgeStore;
import com.gb.rag.Service.EmbeddingPipeline;
import com.gb.rag.Service.GetChunckAndEmbedding;
import com.gb.rag.Service.GetDocumentType;
import com.gb.rag.Service.rerank;
import com.gb.rag.util.JwtUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;

@RestController
@RequestMapping("/test01")
public class DocumentInputController {

    @Autowired
    GetChunckAndEmbedding getChunckAndEmbedding;

    @Autowired
    ParserFactory parserFactory;

    @Autowired
    GetDocumentType getDocumentType;

    @Autowired
    SplitFactory splitFactory;

    @Autowired
    EmbeddingPipeline embeddingPipeline;

    @Autowired
    KnowledgeStore knowledgeStore;

    @Autowired
    rerank rerank;

    @Autowired
    private ChatModel chatModel;

    @PostMapping("/input")
    public Map<String, Object> getDocumentParser(@RequestParam MultipartFile file) {
//        FileType type = FileTypeDetector.detect(file);  // Magic Bytes
//        DocumentParser parser = parserFactory.getParser(type);
//        ParseResult result = parser.parse(input, fileName);
        try {
            String fileName = file.getOriginalFilename();
            try (InputStream inputStream = file.getInputStream()) {
                //GetChunckAndEmbedding.RepoResult run = getChunckAndEmbedding.run(inputStream, file.getOriginalFilename());
                //return run;
                byte[] stream = file.getBytes();
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(stream);
                DocumentParser parser = parserFactory.getParser(getDocumentType.getDocumentType(inputStream, fileName).type());
                Map<String, Object> document = parser.parse(byteArrayInputStream, fileName);
                return document;
            }
        } catch (IOException e) {
            throw new RuntimeException("读取上传文件失败", e);
        }
    }

    @PostMapping("/split")
    public List<String> getDocumentSplit(@RequestParam MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            try (InputStream inputStream = file.getInputStream()) {
//                byte[] stream=file.getBytes();
//                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(stream);
                GetDocumentType.ParsePara documentType = getDocumentType.getDocumentType(inputStream, fileName);
                FileType type = documentType.type();
                ByteArrayInputStream byteArrayInputStream = documentType.inputStream();

                DocumentParser parser = parserFactory.getParser(type);
                Map<String, Object> document = parser.parse(byteArrayInputStream, fileName);
                DocumentSplit.SplitResult split = splitFactory.getSplit(type).split(document);
                return split.chunks();
            }
        } catch (IOException e) {
            throw new RuntimeException("切分上传文件失败", e);
        }
    }

    @PostMapping("/split/result")
    public DocumentSplit.SplitResult getDocumentSplitResult(@RequestParam MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            try (InputStream inputStream = file.getInputStream()) {
//                byte[] stream=file.getBytes();
//                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(stream);
                GetDocumentType.ParsePara documentType = getDocumentType.getDocumentType(inputStream, fileName);
                FileType type = documentType.type();
                ByteArrayInputStream byteArrayInputStream = documentType.inputStream();

                DocumentParser parser = parserFactory.getParser(type);
                Map<String, Object> document = parser.parse(byteArrayInputStream, fileName);
                DocumentSplit.SplitResult split = splitFactory.getSplit(type).split(document);
                return split;
            }
        } catch (IOException e) {
            throw new RuntimeException("切分上传文件失败", e);
        }
    }

    @PostConstruct
    void init() {
        embeddingPipeline.start();
    }

    @PostMapping(path = "/embedding")
    public List<EmbedResult> getBlockingQueue(@RequestParam MultipartFile file) {
        try {
            FutureTask<List<EmbedResult>> task = new FutureTask<>(() -> {
                String fileName = file.getOriginalFilename();
                try (InputStream inputStream = file.getInputStream()) {
                    GetDocumentType.ParsePara documentType = getDocumentType.getDocumentType(inputStream, fileName);
                    FileType type = documentType.type();
                    ByteArrayInputStream byteArrayInputStream = documentType.inputStream();

                    DocumentParser parser = parserFactory.getParser(type);
                    Map<String, Object> document = parser.parse(byteArrayInputStream, fileName);
                    DocumentSplit.SplitResult split = splitFactory.getSplit(type).split(document);


                    embeddingPipeline.submit(split.chunks(), type);
                    return embeddingPipeline.collect();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            new Thread(task).start();
            return task.get();          // 阻塞等待结果
        } catch (Exception e) {
            throw new RuntimeException("embedded文件失败", e);
        }
    }


    @PostMapping("/vectorStore")
    public boolean vectorStore(@RequestParam MultipartFile file,@RequestParam Long userId) {
        String fileName = file.getOriginalFilename();
        record DocContext(
                List<EmbedResult> result,
                FileType fileType
        ) {}
        try {
            FutureTask<DocContext> task = new FutureTask<>(() -> {
                try (InputStream inputStream = file.getInputStream()) {
                    GetDocumentType.ParsePara documentType = getDocumentType.getDocumentType(inputStream, fileName);
                    FileType type = documentType.type();
                    ByteArrayInputStream byteArrayInputStream = documentType.inputStream();

                    DocumentParser parser = parserFactory.getParser(type);
                    Map<String, Object> document = parser.parse(byteArrayInputStream, fileName);
                    DocumentSplit.SplitResult split = splitFactory.getSplit(type).split(document);


                    embeddingPipeline.submit(split.chunks(), type);
                    //return embeddingPipeline.collect();
                    DocContext docContext = new DocContext(
                            embeddingPipeline.collect(),
                            type
                    );
                    return docContext;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            new Thread(task).start();
            //return task.get();          // 阻塞等待结果
            List<EmbedResult> result = (List<EmbedResult>) task.get().result;
            FileType type=task.get().fileType;
            boolean b=false;
            for (EmbedResult embedResult : result) {
               // b = knowledgeStore.saveDocument(fileName.substring(0,fileName.indexOf(".")), type, embedResult.getText(), embedResult.getVector());
                b = knowledgeStore.saveDocument(userId,fileName.substring(0,fileName.lastIndexOf(".")) ,type, embedResult.getText(), embedResult.getVector());

            }
            return b;
        } catch (Exception e) {
            throw new RuntimeException("embedded文件失败", e);
        }
    }

    @PostMapping("/login")
    public String login(@RequestParam Long userId,@RequestParam String userName){
        return JwtUtil.createToken(userId,userName);
    }


    @PostMapping("/reranker")
    public List<ResultRow> reranker(@RequestParam String input, @RequestParam String token) throws IOException {//token指的是jwt令牌
        String Newquestion =rerank.requestion(input);
        List<SearchResult> fellback= null;
        try {
            fellback = rerank.get50(Newquestion,token);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rerank.rerank(fellback,Newquestion);
    }

    @PostMapping("/ragResult")
    public List<ResultRow> ragResult(@RequestParam MultipartFile file,@RequestParam String input,@RequestParam String token){
        try {
            String fileName = file.getOriginalFilename();
            try (InputStream inputStream = file.getInputStream()) {
//                byte[] stream=file.getBytes();
//                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(stream);
                GetDocumentType.ParsePara documentType = getDocumentType.getDocumentType(inputStream, fileName);
                FileType type = documentType.type();
                ByteArrayInputStream byteArrayInputStream = documentType.inputStream();

                DocumentParser parser = parserFactory.getParser(type);
                Map<String, Object> document = parser.parse(byteArrayInputStream, fileName);
                DocumentSplit.SplitResult split = splitFactory.getSplit(type).split(document);


                String Newquestion =rerank.requestion(input);
                List<SearchResult> fellback= null;
                try {
                    fellback = rerank.get50(Newquestion,token);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                List<ResultRow> rerank1 = rerank.rerank(fellback, Newquestion);

                List<ResultRow> listRow=new ArrayList<>();
                for (ResultRow rank:rerank1){
                    List<Integer> pages = split.pages();
                    List<Integer> paragraphN = split.paragraphN();
                    ResultRow resultRow = new ResultRow(rank.getSubscribe(), rank.getScore(), rank.getRankNum(), rank.getId(), rank.getDocId(), rank.getChunkIndex(), rank.getContent(), pages.get(rank.getId().intValue()-1), paragraphN.get(rank.getId().intValue()-1),fileName);
                    listRow.add(resultRow);
                }
                return listRow;
            }
        } catch (IOException e) {
            throw new RuntimeException("切分上传文件失败", e);
        }
    }
}

