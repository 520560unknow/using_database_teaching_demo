package com.example.classroom.service;

import com.example.classroom.model.Danmaku;
import com.example.classroom.model.VideoNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 弹幕分析器：把弹幕内容汇总成关键词权重，并据此决定下一跳节点。
 */
@Component
public class DanmakuAnalyzer {

    /**
     * 分析弹幕关键词权重
     */
    public Map<String, Integer> analyzeDanmaku(List<Danmaku> danmakuList) {
        Map<String, Integer> keywordWeights = new HashMap<>();

        // 常见关键词（教育相关）
        String[] educationKeywords = {"问题", "疑问", "不懂", "明白", "理解", "知识点", "重点", "难点",
                "例子", "示例", "练习", "作业", "考试", "复习", "预习", "总结"};

        // 初始化教育关键词
        for (String keyword : educationKeywords) {
            keywordWeights.put(keyword, 5); // 基础权重
        }

        // 分析弹幕内容
        for (Danmaku danmaku : danmakuList) {
            String content = danmaku.getContent();
            if (content != null && !content.trim().isEmpty()) {
                String lowerContent = content.toLowerCase();

                // 检查是否包含教育关键词
                for (String keyword : educationKeywords) {
                    if (lowerContent.contains(keyword.toLowerCase())) {
                        keywordWeights.put(keyword, keywordWeights.get(keyword) + 1);
                    }
                }

                // 如果是教师弹幕，权重加倍
                if (danmaku.isTeacher()) {
                    for (String keyword : educationKeywords) {
                        if (lowerContent.contains(keyword.toLowerCase())) {
                            keywordWeights.put(keyword, keywordWeights.get(keyword) * 2);
                        }
                    }
                }

                // 简单的分词（按空格和标点分割）
                String[] words = lowerContent.split("[\\s\\p{Punct}]+");
                for (String word : words) {
                    if (word.length() > 1) { // 忽略单个字符
                        keywordWeights.put(word, keywordWeights.getOrDefault(word, 0) + 1);
                    }
                }
            }
        }

        return keywordWeights;
    }

    /**
     * 决定下一个节点（智能跳转）
     */
    public VideoNode decideNextNode(Map<String, VideoNode> videoNodes,
                                    String currentVideoId,
                                    Map<String, Integer> keywordWeights) {
        // 如果当前节点不存在，返回null
        if (currentVideoId == null) {
            return null;
        }

        VideoNode currentNode = videoNodes.get(currentVideoId);
        if (currentNode == null || currentNode.getEdges() == null || currentNode.getEdges().isEmpty()) {
            return null;
        }

        // 找到权重最高的连接
        VideoNode.VideoEdge bestEdge = null;
        int maxScore = -1;

        for (VideoNode.VideoEdge edge : currentNode.getEdges()) {
            int score = keywordWeights.getOrDefault(edge.getKeyword(), 0) * edge.getWeight();
            if (score > maxScore) {
                maxScore = score;
                bestEdge = edge;
            }
        }

        if (bestEdge != null && maxScore > 0) {
            return videoNodes.get(bestEdge.getTargetNodeId());
        }

        return null;
    }
}
