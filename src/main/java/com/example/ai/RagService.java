package com.example.ai;

import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 轻量 RAG 检索入口：对外 API 保持不变，内部委托 {@link CorpusRepository}（语料/资料读写）
 * 与 {@link Retriever}（BM25 检索）。
 */
@Service
public class RagService {

    public static class Chunk {
        public String id;
        public String title;
        public String docTitle;
        public String content;
        public String source;
        /** 所属课堂：空串表示公共/内置资料，对全部课堂可见 */
        public String classroomId;
        public List<String> tokens;
        public Map<String, Integer> tf;
        public int length;
    }

    public static class Hit {
        public Chunk chunk;
        public double score;
        public double coverage;

        public Hit(Chunk chunk, double score, double coverage) {
            this.chunk = chunk;
            this.score = score;
            this.coverage = coverage;
        }
    }

    private final CorpusRepository corpusRepository;
    private final Retriever retriever;

    private List<Chunk> chunks = new ArrayList<Chunk>();

    public RagService(CorpusRepository corpusRepository, Retriever retriever) {
        this.corpusRepository = corpusRepository;
        this.retriever = retriever;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        chunks = corpusRepository.loadAll();
        retriever.index(chunks);
        System.out.println("RAG 已加载语料片段: " + chunks.size() + "，目录: " + corpusRepository.getCorpusDir());
    }

    /**
     * 全局检索，不按课堂过滤，保留给非课堂场景使用。
     */
    public synchronized List<Hit> search(String query, int topK) {
        return retriever.search(query, topK, null);
    }

    /**
     * 按课堂检索：只保留公共资料（classroomId 为空）与本课堂资料。
     */
    public synchronized List<Hit> search(String query, int topK, String classroomId) {
        return retriever.search(query, topK, CorpusRepository.safeClassroom(classroomId));
    }

    public synchronized void addEntry(String question, String answer) {
        addEntry(question, answer, "");
    }

    /**
     * 教师回复回流：写入对应课堂目录的 teacher-faq.md，公共课堂写语料根目录。
     */
    public synchronized void addEntry(String question, String answer, String classroomId) {
        if (question == null || question.trim().isEmpty() || answer == null || answer.trim().isEmpty()) {
            return;
        }
        if (corpusRepository.appendFaq(question, answer, classroomId)) {
            reload();
        }
    }

    /**
     * 教师上传/粘贴资料，写入语料目录后立即重建索引。
     */
    public synchronized boolean addMaterial(String title, String content) {
        return addMaterial(title, content, "");
    }

    /**
     * 教师上传/粘贴资料：classroomId 非空时写入 ai-corpus/&lt;classroomId&gt;/，否则写根目录。
     */
    public synchronized boolean addMaterial(String title, String content, String classroomId) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        boolean ok = corpusRepository.writeMaterial(title, content, classroomId);
        if (ok) {
            reload();
        }
        return ok;
    }

    public synchronized boolean removeMaterial(String name) {
        return removeMaterial(name, "");
    }

    /**
     * 只允许删除指定课堂目录下的文件，禁止路径穿越与绝对路径。
     */
    public synchronized boolean removeMaterial(String name, String classroomId) {
        boolean deleted = corpusRepository.deleteMaterial(name, classroomId);
        if (deleted) {
            reload();
        }
        return deleted;
    }

    public synchronized List<Map<String, Object>> listMaterials() {
        return listMaterials("");
    }

    /**
     * 只列出指定课堂目录下的资料文件（公共课堂即语料根目录）。
     */
    public synchronized List<Map<String, Object>> listMaterials(String classroomId) {
        return corpusRepository.listMaterials(classroomId, chunks);
    }
}
