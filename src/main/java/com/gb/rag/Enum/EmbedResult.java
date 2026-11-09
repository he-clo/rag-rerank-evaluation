package com.gb.rag.Enum;

import lombok.Getter;
import lombok.Setter;

// 结果对象
@Getter
@Setter
public class EmbedResult {
    public final String text;
    public final Float[] vector;
    public static final EmbedResult POISON_PILL = new EmbedResult(null, null);
    public EmbedResult(String text, Float[] vector) {
        this.text = text; this.vector = vector;
    }
}
