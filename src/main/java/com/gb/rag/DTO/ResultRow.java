package com.gb.rag.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResultRow {
    int subscribe;

    Double score;

    int rankNum;

    // 新增：数据库定位字段，方便知道"到底是数据库哪一条"
    Long id;              // 表主键 id
    String docId;         // 文档标识
    Integer chunkIndex;   // 第几块
    String content;       // 命中内容（可选，方便对照）

    int page;
    int paraN;
    String fileName;

    public ResultRow(int subscribe, Double score, int rankNum) {
        this.subscribe = subscribe;
        this.score = score;
        this.rankNum = rankNum;
    }

    public ResultRow(int subscribe, Double score, int rankNum,
                     Long id, String docId, Integer chunkIndex, String content) {
        this.subscribe = subscribe;
        this.score = score;
        this.rankNum = rankNum;
        this.id = id;
        this.docId = docId;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    public ResultRow(int subscribe, Double score, int rankNum,
                     Long id, String docId, Integer chunkIndex, String content,int page,int paraN,String fileName) {
        this.subscribe = subscribe;
        this.score = score;
        this.rankNum = rankNum;
        this.id = id;
        this.docId = docId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.page = page;
        this.paraN = paraN;
        this.fileName = fileName;
    }


    @Override
    public String toString() {
        return "ResultRow{" +
                "subscribe=" + subscribe +
                ", score=" + score +
                ", rankNum=" + rankNum +
                ", id=" + id +
                ", docId='" + docId + '\'' +
                ", chunkIndex=" + chunkIndex +
                ", content='" + content + '\'' +
                ", page=" + page +
                ", paraN=" + paraN +
                ", fileName='" + fileName + '\'' +
                '}';
    }
}
