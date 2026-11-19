package com.example.classroom.service;

import com.example.classroom.model.VideoNode;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DanmakuAnalysisService {
    
    // 存储视频的弹幕信息
    private Map<String, List<String>> videoDanmakus;
    
    // 存储视频的关键词分析结果
    private Map<String, Map<String, Integer>> videoKeywordAnalysis;
    
    // 存储正在分析的视频
    private Set<String> analyzingVideos;

    public DanmakuAnalysisService() {
        this.videoDanmakus = new ConcurrentHashMap<>();
        this.videoKeywordAnalysis = new ConcurrentHashMap<>();
        this.analyzingVideos = ConcurrentHashMap.newKeySet();
    }

    /**
     * 收集视频弹幕
     * @param videoId 视频ID
     * @param danmaku 弹幕内容
     */
    public void collectDanmaku(String videoId, String danmaku) {
        if (videoId == null || danmaku == null || danmaku.trim().isEmpty()) {
            return;
        }
        
        videoDanmakus.computeIfAbsent(videoId, k -> new ArrayList<>()).add(danmaku.trim());
    }

    /**
     * 批量收集视频弹幕
     * @param videoId 视频ID
     * @param danmakus 弹幕列表
     */
    public void collectDanmakus(String videoId, List<String> danmakus) {
        if (videoId == null || danmakus == null || danmakus.isEmpty()) {
            return;
        }
        
        List<String> existingDanmakus = videoDanmakus.computeIfAbsent(videoId, k -> new ArrayList<>());
        existingDanmakus.addAll(danmakus.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * 分析视频弹幕，提取关键词
     * @param videoId 视频ID
     * @return 关键词及其权重
     */
    public Map<String, Integer> analyzeDanmakus(String videoId) {
        if (videoId == null || !videoDanmakus.containsKey(videoId)) {
            return Collections.emptyMap();
        }
        
        // 检查是否正在分析
        if (analyzingVideos.contains(videoId)) {
            return videoKeywordAnalysis.getOrDefault(videoId, Collections.emptyMap());
        }
        
        analyzingVideos.add(videoId);
        
        try {
            List<String> danmakus = videoDanmakus.get(videoId);
            Map<String, Integer> keywordWeights = new HashMap<>();
            
            // 分析每个弹幕
            for (String danmaku : danmakus) {
                List<String> keywords = extractKeywords(danmaku);
                for (String keyword : keywords) {
                    keywordWeights.put(keyword, keywordWeights.getOrDefault(keyword, 0) + 1);
                }
            }
            
            // 过滤低频关键词
            Map<String, Integer> filteredKeywords = new HashMap<>();
            for (Map.Entry<String, Integer> entry : keywordWeights.entrySet()) {
                if (entry.getValue() >= 2) { // 至少出现2次
                    filteredKeywords.put(entry.getKey(), entry.getValue());
                }
            }
            
            // 保存分析结果
            videoKeywordAnalysis.put(videoId, filteredKeywords);
            return filteredKeywords;
        } finally {
            analyzingVideos.remove(videoId);
        }
    }

    /**
     * 从文本中提取关键词
     * @param text 文本内容
     * @return 关键词列表
     */
    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        
        // 简单的关键词提取逻辑
        // 实际应用中可以使用更复杂的分词库
        String[] words = text.split(" ");
        
        for (String word : words) {
            word = word.trim();
            if (word.length() >= 2 && !isStopWord(word)) {
                keywords.add(word);
            }
        }
        
        return keywords;
    }

    /**
     * 检查是否为停用词
     * @param word 词语
     * @return 是否为停用词
     */
    private boolean isStopWord(String word) {
        Set<String> stopWords = new HashSet<>(Arrays.asList(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这"
        ));
        return stopWords.contains(word);
    }

    /**
     * 获取视频的弹幕数量
     * @param videoId 视频ID
     * @return 弹幕数量
     */
    public int getDanmakuCount(String videoId) {
        if (videoId == null || !videoDanmakus.containsKey(videoId)) {
            return 0;
        }
        return videoDanmakus.get(videoId).size();
    }

    /**
     * 清除视频的弹幕信息
     * @param videoId 视频ID
     */
    public void clearDanmakus(String videoId) {
        if (videoId == null) {
            return;
        }
        videoDanmakus.remove(videoId);
        videoKeywordAnalysis.remove(videoId);
        analyzingVideos.remove(videoId);
    }

    /**
     * 清除所有弹幕信息
     */
    public void clearAllDanmakus() {
        videoDanmakus.clear();
        videoKeywordAnalysis.clear();
        analyzingVideos.clear();
    }

    /**
     * 获取视频的关键词分析结果
     * @param videoId 视频ID
     * @return 关键词及其权重
     */
    public Map<String, Integer> getKeywordAnalysis(String videoId) {
        return videoKeywordAnalysis.getOrDefault(videoId, Collections.emptyMap());
    }

    /**
     * 分析视频节点的弹幕，为智能推荐做准备
     * @param node 视频节点
     * @return 关键词及其权重
     */
    public Map<String, Integer> analyzeNodeDanmakus(VideoNode node) {
        if (node == null || node.getId() == null) {
            return Collections.emptyMap();
        }
        
        return analyzeDanmakus(node.getId());
    }
}
