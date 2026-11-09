package com.example;

import java.util.List;

public class TestCase {
    private String query;
    private List<Document> documents;

    // getter / setter（必须要有，Jackson 靠 setter 或字段可见性注入）
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public List<Document> getDocuments() { return documents; }
    public void setDocuments(List<Document> documents) { this.documents = documents; }
}