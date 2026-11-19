package com.example.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiAskController {

    @Autowired
    private RagService ragService;

    @Autowired
    private AiService aiService;

    @Autowired
    private AiStoreService storeService;

    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Map<String, String> request) {
        String question = trim(request.get("question"), 500);
        String classroomId = trim(request.get("classroomId"), 100);
        String studentId = trim(request.get("studentId"), 100);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (question.isEmpty()) {
            result.put("error", "问题不能为空");
            return result;
        }

        List<RagService.Hit> hits = ragService.search(question, 3, classroomId);
        boolean lowConfidence = hits.isEmpty() || hits.get(0).score < 1.0 || hits.get(0).coverage < 0.34;
        long start = System.currentTimeMillis();

        String answer = lowConfidence ? null : aiService.answer(question, hits);
        String mode = answer != null ? "llm" : "retrieval";

        if (answer == null) {
            if (lowConfidence) {
                answer = "资料中没有找到直接答案。建议点击“转人工教师”，老师会收到你的问题并在课后回复。";
            } else {
                answer = "根据资料：" + excerpt(hits.get(0).chunk.content, 160)
                        + "\n\n（本地 AI 未启动，当前为资料检索结果）";
            }
        }

        long latency = System.currentTimeMillis() - start;
        List<Map<String, Object>> sources = new ArrayList<Map<String, Object>>();
        List<String> sourceIds = new ArrayList<String>();
        for (int i = 0; i < hits.size() && !lowConfidence; i++) {
            RagService.Chunk chunk = hits.get(i).chunk;
            String sid = "S" + (i + 1);
            sourceIds.add(sid);
            Map<String, Object> source = new LinkedHashMap<String, Object>();
            source.put("id", sid);
            source.put("title", chunk.title);
            source.put("excerpt", excerpt(chunk.content, 180));
            source.put("source", chunk.source);
            source.put("score", Math.round(hits.get(i).score * 100.0) / 100.0);
            source.put("match", Math.round(hits.get(i).coverage * 100));
            sources.add(source);
        }

        storeService.logUsage(classroomId, studentId, question, answer, mode, lowConfidence, latency, sourceIds);

        result.put("answer", answer);
        result.put("mode", mode);
        result.put("lowConfidence", lowConfidence);
        result.put("latencyMs", latency);
        result.put("sources", sources);
        return result;
    }

    @PostMapping("/transfer")
    public Map<String, Object> transfer(@RequestBody Map<String, String> request) {
        String question = trim(request.get("question"), 500);
        String classroomId = trim(request.get("classroomId"), 100);
        String studentId = trim(request.get("studentId"), 100);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (question.isEmpty()) {
            result.put("error", "问题不能为空");
            return result;
        }
        Map<String, Object> item = storeService.addPending(question, classroomId, studentId);
        result.put("ok", true);
        result.put("item", item);
        return result;
    }

    @GetMapping("/unanswered")
    public Map<String, Object> unanswered(@RequestParam(value = "classroomId", required = false) String classroomId,
                                          @RequestParam(value = "status", required = false) String status) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", storeService.list(classroomId, status));
        result.put("stats", storeService.stats(classroomId));
        return result;
    }

    @PostMapping("/answer")
    public Map<String, Object> answer(@RequestBody Map<String, String> request) {
        String id = trim(request.get("id"), 100);
        String answer = trim(request.get("answer"), 1000);
        String teacherId = trim(request.get("teacherId"), 100);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (id.isEmpty() || answer.isEmpty()) {
            result.put("error", "id 和 answer 不能为空");
            return result;
        }
        String classroomId = storeService.classroomIdOf(id);
        String question = storeService.answer(id, answer, teacherId);
        if (question == null) {
            result.put("error", "未找到该问题");
            return result;
        }
        ragService.addEntry(question, answer, classroomId);
        result.put("ok", true);
        result.put("message", "已回复，并自动加入答疑知识库");
        return result;
    }

    private String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private String excerpt(String text, int max) {
        if (text == null) {
            return "";
        }
        String clean = text.replace("\n", " ").replace("\r", " ").trim();
        return clean.length() > max ? clean.substring(0, max) + "..." : clean;
    }
}
