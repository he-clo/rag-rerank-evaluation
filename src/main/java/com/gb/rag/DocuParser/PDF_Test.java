package com.gb.rag.DocuParser;


import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;

import java.util.List;

public class PDF_Test {
    public static void main(String[] args) {
        // 指定文档路径
        String filePath = "D:\\b085f-main\\b085f-main\\数据密集型应用系统设计高清版1\\数据密集型应用系统设计高清版1.pdf";
//
        // 创建FileSystemResource对象，表示文档资源
        FileSystemResource resource = new FileSystemResource(filePath);
//
//        // 创建TikaDocumentReader对象，并读取文档
//        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
//        List<Document> documents = tikaDocumentReader.read();

       // PagePdfDocumentReader pagePdfDocumentReader = new PagePdfDocumentReader(resource);
        ParagraphPdfDocumentReader reader = new ParagraphPdfDocumentReader(
                resource,
                PdfDocumentReaderConfig.builder().build()
        );
        List<Document> documents = reader.read();
//
//
//
//
        // 输出文档内容
        for (Document document : documents) {
            //System.out.println(document.getMedia());  图片
            //System.out.println(document.getText());

            System.out.println(document.getMetadata());
        }


    }
}

