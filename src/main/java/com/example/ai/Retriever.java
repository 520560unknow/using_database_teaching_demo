package com.example.ai;

import java.util.List;

/**
 * 检索器抽象：当前唯一实现为 {@link Bm25Retriever}。
 * classroomFilter 为 null 表示不做课堂过滤。
 */
public interface Retriever {

    /**
     * 重建索引。
     */
    void index(List<RagService.Chunk> chunks);

    List<RagService.Hit> search(String query, int topK, String classroomFilter);
}
