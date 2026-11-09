package com.gb.rag.Service;

import com.gb.rag.Enum.FileType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Component
public class FileTypeDetector {

    /**
     * 仅通过 Magic Bytes 从 InputStream 检测文件类型
     * 读完后会 reset 流，不影响后续解析
     */
    public FileType detect(InputStream input) {
        if (input == null) {
            return FileType.UNKNOWN;
        }

        try {
            // 标记当前位置
            if (input.markSupported()) {
                input.mark(8);
            }

            byte[] header = new byte[8];
            int len = input.read(header);

            // 重置回标记位置
            if (input.markSupported()) {
                input.reset();
            }

            if (len < 4) {
                return FileType.UNKNOWN;
            }

            // PDF: %PDF (25 50 44 46)
            if (header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F') {
                return FileType.PDF;
            }

            // ZIP / Office Open XML: PK\x03\x04 (50 4B 03 04)
            if (header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04) {
                // ZIP 格式，但 docx/pptx/xlsx 都是 ZIP，无法仅从 magic bytes 区分
                // 返回 UNKNOWN，让上层处理
                return FileType.UNKNOWN;
            }

            // HTML: <!DOCTYPE 或 <html
            if ((header[0] == '<' && header[1] == '!' && header[2] == 'D' && header[3] == 'O') ||
                    (header[0] == '<' && header[1] == 'h' && header[2] == 't' && header[3] == 'm')) {
                return FileType.HTML;
            }

            // 其他纯文本类型（MD/RST/TXT）无法从 magic bytes 区分，返回 UNKNOWN
            return FileType.UNKNOWN;

        } catch (IOException e) {
            return FileType.UNKNOWN;
        }
    }

    /**
     * 按文件名后缀检测
     */
    public FileType detectByExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return FileType.UNKNOWN;
        }

        String name = fileName.toLowerCase(Locale.ROOT);//忽略时区小写，即拿到英语的小写
        String ext = "";
        int idx = name.lastIndexOf('.');
        if (idx >= 0 && idx < name.length() - 1) {
            ext = name.substring(idx + 1);
        }

        switch (ext) {
            case "pdf":
                return FileType.PDF;
            case "docx":
                return FileType.DOCX;
            case "pptx":
                return FileType.PPTX;
            case "xlsx":
                return FileType.XLSX;
            case "html":
            case "htm":
                return FileType.HTML;
            case "md":
            case "markdown":
                return FileType.MARKDOWN;
            case "rst":
                return FileType.RST;
            case "txt":
                return FileType.TXT;
            default:
                return FileType.UNKNOWN;
        }
    }
}