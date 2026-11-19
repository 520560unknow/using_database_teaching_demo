package com.example.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师学情看板：只读取问答日志与待答队列，不参与问答与检索逻辑。
 */
@RestController
@RequestMapping("/api/ai")
public class AnalyticsController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private AiStoreService storeService;

    @GetMapping("/analytics")
    public Map<String, Object> analytics(
            @RequestParam(value = "classroomId", required = false) String classroomId,
            @RequestParam(value = "days", defaultValue = "7") int days) {
        String cid = trim(classroomId, 100);
        int range = Math.min(Math.max(days, 1), 90);
        LocalDateTime from = LocalDate.now().minusDays(range - 1).atStartOfDay();

        List<Map<String, Object>> logs = storeService.readAllLogs();

        int total = 0;
        int hit = 0;
        int miss = 0;
        int llmCount = 0;
        int retrievalCount = 0;
        long latencySum = 0L;

        Map<String, Integer> questionCounts = new LinkedHashMap<String, Integer>();
        Map<String, String> questionSamples = new HashMap<String, String>();
        Map<String, Integer> missedCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> dayCounts = new LinkedHashMap<String, Integer>();

        for (Map<String, Object> log : logs) {
            if (!cid.isEmpty() && !cid.equals(String.valueOf(log.get("classroomId")))) {
                continue;
            }
            String ts = log.get("ts") == null ? "" : String.valueOf(log.get("ts"));
            LocalDateTime time = parseTime(ts);
            if (time == null || time.isBefore(from)) {
                continue;
            }

            total++;
            boolean lowConfidence = Boolean.TRUE.equals(log.get("lowConfidence"));
            if (lowConfidence) {
                miss++;
            } else {
                hit++;
            }
            if ("llm".equals(String.valueOf(log.get("mode")))) {
                llmCount++;
            } else {
                retrievalCount++;
            }
            Object latency = log.get("latencyMs");
            if (latency instanceof Number) {
                latencySum += ((Number) latency).longValue();
            }

            String question = log.get("question") == null ? "" : String.valueOf(log.get("question")).trim();
            String key = normalize(question);
            if (!key.isEmpty()) {
                questionCounts.merge(key, 1, Integer::sum);
                questionSamples.putIfAbsent(key, question);
                if (lowConfidence) {
                    missedCounts.merge(key, 1, Integer::sum);
                }
            }

            if (ts.length() >= 10) {
                dayCounts.merge(ts.substring(0, 10), 1, Integer::sum);
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("classroomId", cid);
        result.put("days", range);
        result.put("total", total);
        result.put("hit", hit);
        result.put("miss", miss);
        result.put("hitRate", total == 0 ? 0.0 : Math.round(hit * 1000.0 / total) / 10.0);
        result.put("llmCount", llmCount);
        result.put("retrievalCount", retrievalCount);
        result.put("avgLatencyMs", total == 0 ? 0 : Math.round(latencySum / (double) total));
        result.put("byDay", toTrend(dayCounts, range));
        result.put("topQuestions", toTopList(questionCounts, questionSamples, 8));
        result.put("missedQuestions", toTopList(missedCounts, questionSamples, 8));
        result.put("desk", storeService.stats(cid));
        return result;
    }

    private List<Map<String, Object>> toTopList(Map<String, Integer> counts, Map<String, String> samples, int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < entries.size() && i < limit; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("question", samples.getOrDefault(entry.getKey(), entry.getKey()));
            item.put("count", entry.getValue());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> toTrend(Map<String, Integer> dayCounts, int range) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        LocalDate start = LocalDate.now().minusDays(range - 1);
        for (int i = 0; i < range; i++) {
            String day = start.plusDays(i).format(DAY_FORMAT);
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("date", day);
            item.put("count", dayCounts.getOrDefault(day, 0));
            result.add(item);
        }
        return result;
    }

    private LocalDateTime parseTime(String ts) {
        try {
            return LocalDateTime.parse(ts, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 归并同一问题的不同写法：去掉空白与常见中英文标点后比较。
     */
    private String normalize(String question) {
        if (question == null) {
            return "";
        }
        return question.toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。？！；：、“”‘’、（）《》【】…—·]+", "");
    }

    private String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
