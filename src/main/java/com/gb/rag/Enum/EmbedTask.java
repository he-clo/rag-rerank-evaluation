package com.gb.rag.Enum;

// 任务对象：自带策略
public class EmbedTask {
    public final String text;
    public final FileType fileType;
    public static final EmbedTask POISON_PILL = new EmbedTask("终止任务", FileType.DOCX);

    public EmbedTask(String text, FileType fileType) {
        this.text = text;
        this.fileType = fileType;
    }
}
