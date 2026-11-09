package com.gb.rag.Service;

import com.gb.rag.DocuEmbedd.EmbeddFactory;
import com.gb.rag.DocuParser.DocumentParser;
import com.gb.rag.DocuParser.ParserFactory;
import com.gb.rag.DocuSplit.DocumentSplit;
import com.gb.rag.DocuSplit.SplitFactory;
import com.gb.rag.Enum.FileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class GetChunckAndEmbedding {

    @Autowired
    ParserFactory parserFactory;

    @Autowired
    SplitFactory splitFactory;

    @Autowired
    EmbeddFactory embeddFactory;

    @Autowired
    GetDocumentType documentType;

    public record RepoResult(
            String fileName,
            FileType type,
            List<Integer> pages,
            List<Integer> paragraphN,
            List<String> content,
            Float[] vector
    ) {
    }

    public RepoResult run(InputStream input, String fileName) throws IOException, InterruptedException {
        ResolvedDoc doc = resolve(input, fileName);
        FileType type = doc.type();
        InputStream stream = doc.inputStream();
        //paragraphN
        DocumentParser parser = parserFactory.getParser(type);
        Map<String, Object> document = parser.parse(stream, fileName);

        //content
        DocumentSplit split = splitFactory.getSplit(type);
        List<String> chunk = split.split(document).chunks();
        //限速
        Float[] embedding = embeddFactory.getEmbedd(type).embedding(chunk);
        //write;通过test方法写入，用户文档无价值
        return new RepoResult(
                fileName,
                type,
                List.of(),//不是所有文件都有页数
                List.of(),
                chunk,
                embedding
        );
    }

    private record ResolvedDoc(FileType type, ByteArrayInputStream inputStream) {}

//    public ObjectUtils.Null getType(InputStream input, String fileName) {
//        GetDocumentType.ParsePara outputAndtype = documentType.getDocumentType(input, fileName);
//        FileType type = outputAndtype.type();
//        ByteArrayInputStream byteArrayInputStream = outputAndtype.inputStream();
//        this.type = type;
//        this.inputStream = byteArrayInputStream;
//        return null;
//    }

    private ResolvedDoc resolve(InputStream input, String fileName) {
        GetDocumentType.ParsePara p = documentType.getDocumentType(input, fileName);
        return new ResolvedDoc(p.type(), p.inputStream());
    }

//    public List<Integer> getPages(Map<String,Object> document,DocumentParser parser){
//        return parser.getPages(document);
//    }
//
//    public List<Integer> getParagraphN(Map<String,Object> document,DocumentParser parser){
//        return parser.getParagraphN(document);
//    }

}

