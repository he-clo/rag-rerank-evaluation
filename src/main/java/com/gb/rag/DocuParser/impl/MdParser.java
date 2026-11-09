package com.gb.rag.DocuParser.impl;

import com.gb.rag.DocuParser.DocumentParser;
import com.gb.rag.Enum.FileType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;

import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
//要切
public class MdParser implements DocumentParser {
    private final ObjectMapper mapper = new ObjectMapper();

    // 配置解析器：启用表格、任务列表等扩展
    //flexmark
    private final Parser parser;

    public MdParser() {
        MutableDataSet options = new MutableDataSet();

        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create()
        ));

        this.parser = Parser.builder(options).build();
    }

    @Override
    public Map<String, Object> parse(InputStream input, String fileName) {
        try {
            String content = readContent(input);
            Node document = parser.parse(content);
            List<MDNode> blocks = new ArrayList<>();
            traverseAst(document, blocks);
            String plainText = buildPlainText(blocks);

            Map<String, Object> result = new HashMap<>();
            result.put("encoding", "UTF-8");
            result.put("file_name", fileName);
            result.put("line_count", content.split("\n").length);
            result.put("plain", plainText);
            result.put("blocks", blocks); // MDNode 列表，Jackson 会序列化为 JSON 数组

            return result;

        } catch (Exception e) {
            throw new RuntimeException("MD parse failed: " + fileName, e);
        }
    }


    private String readContent(InputStream input) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private void traverseAst(Node node, List<MDNode> blocks) {
        NodeVisitor visitor = new NodeVisitor(
                // 标题
                new VisitHandler<>(com.vladsch.flexmark.ast.Heading.class,
                        n -> blocks.add(new MDNode(
                                "heading",
                                n.getLevel(),
                                extractText(n),
                                null,
                                null
                        ))),
                // 段落
                new VisitHandler<>(com.vladsch.flexmark.ast.Paragraph.class,
                        n -> blocks.add(new MDNode(
                                "paragraph",
                                null,
                                extractText(n),
                                null,
                                null
                        ))),
                // 代码块
                new VisitHandler<>(com.vladsch.flexmark.ast.FencedCodeBlock.class,
                        n -> blocks.add(new MDNode(
                                "code",
                                null,
                                n.getContentChars().toString(),
                                n.getInfo().toString().trim(),
                                null
                        ))),
                // 引用
                new VisitHandler<>(com.vladsch.flexmark.ast.BlockQuote.class,
                        n -> blocks.add(new MDNode(
                                "blockquote",
                                null,
                                extractText(n),
                                null,
                                null
                        ))),
                // 无序列表
                new VisitHandler<>(com.vladsch.flexmark.ast.BulletList.class,
                        n -> blocks.add(new MDNode(
                                "bullet_list",
                                null,
                                null,
                                null,
                                extractListItems(n)
                        ))),
                // 有序列表
                new VisitHandler<>(com.vladsch.flexmark.ast.OrderedList.class,
                        n -> blocks.add(new MDNode(
                                "ordered_list",
                                null,
                                null,
                                null,
                                extractListItems(n)
                        ))),
                // 表格
                new VisitHandler<>(com.vladsch.flexmark.ext.tables.TableBlock.class,
                        n -> blocks.add(new MDNode(
                                "table",
                                null,
                                null,
                                null,
                                extractTable(n)
                        ))),
                // 水平线
                new VisitHandler<>(com.vladsch.flexmark.ast.ThematicBreak.class,
                        n -> blocks.add(new MDNode(
                                "thematic_break",
                                null,
                                null,
                                null,
                                null
                        ))),
                // HTML 块
                new VisitHandler<>(com.vladsch.flexmark.ast.HtmlBlock.class,
                        n -> blocks.add(new MDNode(
                                "html_block",
                                null,
                                n.getContentChars().toString(),
                                null,
                                null
                        )))
        );

        visitor.visit(node);
    }

    private String extractText(Node node) {
        StringBuilder sb = new StringBuilder();
        node.getChildren().forEach(child -> {
            if (child instanceof com.vladsch.flexmark.ast.Text) {
                sb.append(child.getChars().toString());
            } else if (child instanceof com.vladsch.flexmark.ast.SoftLineBreak) {
                sb.append('\n');
            } else if (child instanceof com.vladsch.flexmark.ast.HardLineBreak) {
                sb.append('\n');
            } else if (child instanceof com.vladsch.flexmark.ast.Code) {
                sb.append('`').append(child.getChars().toString()).append('`');
            } else if (child instanceof com.vladsch.flexmark.ast.Emphasis) {
                sb.append(child.getChars().toString());
            } else if (child instanceof com.vladsch.flexmark.ast.StrongEmphasis) {
                sb.append(child.getChars().toString());
            } else if (child instanceof com.vladsch.flexmark.ast.Link) {
                com.vladsch.flexmark.ast.Link link = (com.vladsch.flexmark.ast.Link) child;
                sb.append('[').append(link.getText().toString()).append(']')
                        .append('(').append(link.getUrl().toString()).append(')');
            } else {
                // 递归子节点
                sb.append(extractText(child));
            }
        });
        return sb.toString().trim();
    }

    private List<String> extractListItems(Node listNode) {
        List<String> items = new ArrayList<>();
        listNode.getChildren().forEach(item -> {
            if (item instanceof com.vladsch.flexmark.ast.ListItem) {
                items.add(extractText(item));
            }
        });
        return items;
    }

    private List<String> extractTable(Node tableNode) {
        List<String> rows = new ArrayList<>();
        tableNode.getChildren().forEach(row -> {
            StringBuilder sb = new StringBuilder();
            row.getChildren().forEach(cell -> {
                sb.append(extractText(cell)).append(" | ");
            });
            if (sb.length() > 0) {
                rows.add(sb.substring(0, sb.length() - 3).trim());
            }
        });
        return rows;
    }

    private String buildPlainText(List<MDNode> blocks) {
        StringBuilder sb = new StringBuilder();
        for (MDNode block : blocks) {
            if (block.text != null && !block.text.isEmpty()) {
                sb.append(block.text).append("\n\n");
            } else if (block.items != null && !block.items.isEmpty()) {
                for (String item : block.items) {
                    sb.append("- ").append(item).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    // 内部 DTO
    public static class MDNode {
        public String type;
        public Integer level;          // 标题层级 1-6
        public String text;            // 文本内容
        public String lang;            // 代码块语言
        public List<String> items;     // 列表项 / 表格行

        public MDNode(String type, Integer level, String text, String lang, List<String> items) {
            this.type = type;
            this.level = level;
            this.text = text;
            this.lang = lang;
            this.items = items;
        }
    }
    @Override
    public boolean supports(FileType type) { return type==FileType.MARKDOWN;}

}
