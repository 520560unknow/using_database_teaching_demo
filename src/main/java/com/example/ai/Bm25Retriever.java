package com.example.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 中文双字 BM25 检索器：不依赖向量库，CPU 可跑。
 * 作为检索实现的唯一实现保留，后续可通过 {@link Retriever} 接口替换。
 */
@Component
public class Bm25Retriever implements Retriever {

    private List<RagService.Chunk> chunks = new ArrayList<RagService.Chunk>();
    private final Map<String, Integer> df = new HashMap<String, Integer>();
    private double avgLen = 1.0;

    /**
     * 重建索引：分词、词频、文档频率与平均长度。
     */
    public synchronized void index(List<RagService.Chunk> newChunks) {
        chunks = newChunks == null ? new ArrayList<RagService.Chunk>() : newChunks;
        df.clear();
        int total = 0;
        for (RagService.Chunk c : chunks) {
            c.tokens = tokenize(c.title + "\n" + c.content);
            c.tf = new HashMap<String, Integer>();
            for (String t : c.tokens) {
                Integer old = c.tf.get(t);
                c.tf.put(t, old == null ? 1 : old + 1);
            }
            c.length = c.tokens.size();
            total += c.length;
            Set<String> unique = new HashSet<String>(c.tokens);
            for (String t : unique) {
                Integer old = df.get(t);
                df.put(t, old == null ? 1 : old + 1);
            }
        }
        avgLen = chunks.isEmpty() ? 1.0 : (double) total / chunks.size();
    }

    @Override
    public synchronized List<RagService.Hit> search(String query, int topK, String classroomFilter) {
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty() || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> unique = new LinkedHashSet<String>(queryTokens);
        List<RagService.Hit> hits = new ArrayList<RagService.Hit>();
        double k1 = 1.5;
        double bm25b = 0.75;
        int n = chunks.size();
        for (RagService.Chunk c : chunks) {
            if (classroomFilter != null && !c.classroomId.isEmpty()
                    && !c.classroomId.equals(classroomFilter)) {
                continue;
            }
            double score = 0.0;
            int matched = 0;
            for (String t : unique) {
                Integer f = c.tf.get(t);
                if (f == null) {
                    continue;
                }
                matched++;
                int dfi = df.get(t) == null ? 0 : df.get(t);
                double idf = Math.log(1.0 + (n - dfi + 0.5) / (dfi + 0.5));
                double denom = f + k1 * (1.0 - bm25b + bm25b * c.length / avgLen);
                score += idf * (f * (k1 + 1.0)) / denom;
            }
            if (score > 0) {
                hits.add(new RagService.Hit(c, score, (double) matched / unique.size()));
            }
        }
        hits.sort((a, b) -> Double.compare(b.score, a.score));
        return hits.subList(0, Math.min(topK, hits.size()));
    }

    private List<String> tokenize(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) {
            return out;
        }
        String s = text.toLowerCase(Locale.ROOT);
        StringBuilder ascii = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (isCjk(cp)) {
                flushAscii(ascii, out);
                cjk.appendCodePoint(cp);
            } else if (Character.isLetterOrDigit(cp)) {
                flushCjk(cjk, out);
                ascii.appendCodePoint(cp);
            } else {
                flushAscii(ascii, out);
                flushCjk(cjk, out);
            }
        }
        flushAscii(ascii, out);
        flushCjk(cjk, out);
        return out;
    }

    private boolean isCjk(int cp) {
        return Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN;
    }

    private void flushAscii(StringBuilder b, List<String> out) {
        if (b.length() >= 2) {
            out.add(b.toString());
        } else if (b.length() == 1 && Character.isLetter(b.charAt(0))) {
            out.add(b.toString());
        }
        b.setLength(0);
    }

    private void flushCjk(StringBuilder b, List<String> out) {
        String s = b.toString();
        if (s.length() == 1) {
            out.add(s);
        } else {
            for (int i = 0; i < s.length() - 1; i++) {
                out.add(s.substring(i, i + 2));
            }
        }
        b.setLength(0);
    }
}
