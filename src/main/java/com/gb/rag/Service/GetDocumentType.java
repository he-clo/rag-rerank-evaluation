package com.gb.rag.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gb.rag.Enum.FileType;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
public class GetDocumentType {

    @Autowired
    FileTypeDetector fileTypeDetector;


//    public record ParseResult(//java16新特性，不可变数据类型
//            Map<String, Object> data,
//            FileType fileType,
//            Class<? extends DocumentParser> parserClass,
//            String parserName
//    ) {}

    public record ParsePara(
            FileType type,
            ByteArrayInputStream inputStream
    ){}

    public ParsePara getDocumentType(InputStream input, String fileName) {
        try {
            // 一次性把文件完整读进内存。
            // 原因：类型探测会消费流的前 8 字节，而 multipart 流往往不支持 mark/reset，
            // 一旦被消耗就无法恢复。对于 DOCX/ZIP，前 8 字节是 ZIP 文件头，
            // 丢了会导致 Tika 无法识别 / 解包，最终解析结果为空。
            // 因此先把全部字节读出来，给“探测”和“解析”各自独立、完整的字节流。
            byte[] fileBytes = input.readAllBytes();
            if (fileBytes.length == 0) {
                throw new RuntimeException("上传文件为空:" + fileName);
            }

            // 1. 检测文件类型（Magic Bytes）
            FileType type = fileTypeDetector.detect(new ByteArrayInputStream(fileBytes));
            if (type == FileType.UNKNOWN) {
                type = fileTypeDetector.detectByExtension(fileName);
            }
            if (type == FileType.UNKNOWN) {
                throw new RuntimeException("无法从流中检测文件类型，请确认文件格式是否正确");
            }

            // 2. 获取解析器
//            DocumentParser parser = parserFactory.getParser(type);
//            // 3. 解析（每次给解析器一个全新的、完整的字节流）
//            Map<String, Object> data = parser.parse(new ByteArrayInputStream(fileBytes), fileName);
            return  new ParsePara(type, new ByteArrayInputStream(fileBytes));
        } catch (java.io.IOException e) {
            throw new RuntimeException("读取上传文件失败:" + fileName, e);
        }
    }


}
