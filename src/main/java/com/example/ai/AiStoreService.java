package com.example.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 使用日志 + 教师待答队列。全部落盘，避免重启丢失。
 */
@Service
public class AiStoreService {

    @Value("${ai.log-dir:./ai-logs}")
    private String logDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Map<String, Object>> pending = new LinkedHashMap<String, Map<String, Object>>();

    // ai-usage.jsonl 读取缓存：文件 lastModifiedTime + 大小作为失效键
    private List<Map<String, Object>> logsCache = null;
    private long logsCacheLastModified = -1L;
    private long logsCacheSize = -1L;

    @PostConstruct
    public synchronized void load() {
        try {
            Path file = Paths.get(logDir, "unanswered.json");
            if (Files.exists(file)) {
                List<?> list = mapper.readValue(Files.readAllBytes(file), List.class);
                for (Object item : list) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) item;
                        Object id = m.get("id");
                        if (id != null) {
                            pending.put(String.valueOf(id), m);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("读取待答队列失败: " + e.getMessage());
        }
    }

    public synchronized Map<String, Object> addPending(String question, String classroomId, String studentId) {
        String id = "q_" + System.currentTimeMillis();
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", id);
        item.put("question", question);
        item.put("classroomId", classroomId == null ? "" : classroomId);
        item.put("studentId", studentId == null ? "" : studentId);
        item.put("status", "pending");
        item.put("createdAt", now());
        pending.put(id, item);
        save();
        return item;
    }

    public synchronized List<Map<String, Object>> list(String classroomId, String status) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : pending.values()) {
            if (classroomId != null && !classroomId.trim().isEmpty()
                    && !classroomId.equals(String.valueOf(item.get("classroomId")))) {
                continue;
            }
            if (status != null && !status.trim().isEmpty()
                    && !status.equals(String.valueOf(item.get("status")))) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 取待答问题所属课堂，用于教师回复回流到对应课堂语料目录；缺失时返回空串。
     */
    public synchronized String classroomIdOf(String id) {
        Map<String, Object> item = pending.get(id);
        if (item == null || item.get("classroomId") == null) {
            return "";
        }
        return String.valueOf(item.get("classroomId"));
    }

    public synchronized String answer(String id, String answer, String teacherId) {
        Map<String, Object> item = pending.get(id);
        if (item == null) {
            return null;
        }
        item.put("answer", answer);
        item.put("teacherId", teacherId == null ? "" : teacherId);
        item.put("status", "answered");
        item.put("answeredAt", now());
        save();
        return String.valueOf(item.get("question"));
    }

    public synchronized Map<String, Object> stats(String classroomId) {
        int pendingCount = 0;
        int answeredCount = 0;
        for (Map<String, Object> item : pending.values()) {
            if (classroomId != null && !classroomId.trim().isEmpty()
                    && !classroomId.equals(String.valueOf(item.get("classroomId")))) {
                continue;
            }
            if ("pending".equals(String.valueOf(item.get("status")))) {
                pendingCount++;
            } else if ("answered".equals(String.valueOf(item.get("status")))) {
                answeredCount++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("pending", pendingCount);
        result.put("answered", answeredCount);
        result.put("total", pendingCount + answeredCount);
        return result;
    }

    public synchronized void logUsage(String classroomId, String studentId, String question,
                                      String answer, String mode, boolean lowConfidence,
                                      long latencyMs, List<String> sourceIds) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("ts", now());
        item.put("classroomId", classroomId);
        item.put("studentId", studentId);
        item.put("question", question);
        item.put("answer", answer);
        item.put("mode", mode);
        item.put("lowConfidence", lowConfidence);
        item.put("latencyMs", latencyMs);
        item.put("sourceIds", sourceIds);
        try {
            Path dir = Paths.get(logDir);
            Files.createDirectories(dir);
            String line = mapper.writeValueAsString(item) + System.lineSeparator();
            Files.write(dir.resolve("ai-usage.jsonl"), line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logsCache = null;
        } catch (IOException e) {
            System.err.println("写 AI 日志失败: " + e.getMessage());
        }
    }

    public synchronized List<Map<String, Object>> recentLogs(int limit) {
        List<Map<String, Object>> all = readAllLogs();
        if (all.isEmpty()) {
            return all;
        }
        int from = Math.max(0, all.size() - Math.max(1, limit));
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(all.subList(from, all.size()));
        Collections.reverse(result);
        return result;
    }

    /**
     * 读取全部问答日志，供学情统计使用（按写入顺序）。
     */
    public synchronized List<Map<String, Object>> readAllLogs() {
        Path file = Paths.get(logDir, "ai-usage.jsonl");
        if (!Files.exists(file)) {
            logsCache = null;
            logsCacheLastModified = -1L;
            logsCacheSize = -1L;
            return new ArrayList<Map<String, Object>>();
        }
        try {
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            long size = Files.size(file);
            if (logsCache != null && lastModified == logsCacheLastModified && size == logsCacheSize) {
                return copyLogs(logsCache);
            }

            List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = mapper.readValue(trimmed, Map.class);
                    result.add(item);
                } catch (Exception ignore) {
                    // 跳过损坏行
                }
            }

            logsCache = result;
            logsCacheLastModified = lastModified;
            logsCacheSize = size;
            return copyLogs(result);
        } catch (IOException e) {
            System.err.println("读取提问日志失败: " + e.getMessage());
            return new ArrayList<Map<String, Object>>();
        }
    }

    /**
     * 返回缓存副本，避免调用方修改污染缓存。
     */
    private List<Map<String, Object>> copyLogs(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<Map<String, Object>>(source.size());
        for (Map<String, Object> item : source) {
            copy.add(new LinkedHashMap<String, Object>(item));
        }
        return copy;
    }

    private void save() {
        try {
            Path dir = Paths.get(logDir);
            Files.createDirectories(dir);
            List<Map<String, Object>> list = new ArrayList<Map<String, Object>>(pending.values());
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(Paths.get(logDir, "unanswered.json").toFile(), list);
        } catch (IOException e) {
            System.err.println("保存待答队列失败: " + e.getMessage());
        }
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
