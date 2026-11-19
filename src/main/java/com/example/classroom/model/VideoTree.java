package com.example.classroom.model;

import lombok.Data;

import java.util.Date;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class VideoTree {
    private String id;
    private String name;
    private String description;
    private String teacherId;
    private VideoNode root;
    private Date createdTime;
    private Date createTime;
    private Date updateTime;

    private Integer nodeCount;
    private Integer maxDepth;
    private Integer leafCount;

    /**
     * 根据弹幕关键词推荐下一个视频节点
     * @param currentNode 当前播放节点
     * @param danmakuKeywords 弹幕关键词及其权重
     * @return 推荐的下一个视频节点
     */
    public VideoNode recommendNextNode(VideoNode currentNode, Map<String, Integer> danmakuKeywords) {
        if (currentNode == null || currentNode.getEdges() == null || currentNode.getEdges().isEmpty()) {
            return null;
        }

        // 计算每个边的匹配度分数
        List<VideoNode.VideoEdge> edgesWithScores = currentNode.getEdges().stream()
                .map(edge -> {
                    double score = calculateMatchScore(edge, danmakuKeywords);
                    edge.setConfidence(score);
                    return edge;
                })
                .collect(Collectors.toList());

        // 按匹配度分数排序
        edgesWithScores.sort(Comparator.comparingDouble(VideoNode.VideoEdge::getConfidence).reversed());

        // 返回匹配度最高的节点
        return edgesWithScores.isEmpty() ? null : edgesWithScores.get(0).getTargetNode();
    }

    /**
     * 计算边的匹配度分数
     * @param edge 视频边
     * @param danmakuKeywords 弹幕关键词及其权重
     * @return 匹配度分数
     */
    private double calculateMatchScore(VideoNode.VideoEdge edge, Map<String, Integer> danmakuKeywords) {
        if (danmakuKeywords == null || danmakuKeywords.isEmpty()) {
            return edge.getWeight() != null ? edge.getWeight() : 0;
        }

        double score = 0.0;
        VideoNode targetNode = edge.getTargetNode();

        // 计算边的关键词匹配度
        if (edge.getKeywordWeights() != null) {
            for (Map.Entry<String, Integer> entry : danmakuKeywords.entrySet()) {
                String keyword = entry.getKey();
                int danmakuWeight = entry.getValue();
                
                if (edge.getKeywordWeights().containsKey(keyword)) {
                    int edgeWeight = edge.getKeywordWeights().get(keyword);
                    score += danmakuWeight * edgeWeight;
                }
            }
        }

        // 计算目标节点的关键词匹配度
        if (targetNode != null && targetNode.getKeywords() != null) {
            for (Map.Entry<String, Integer> entry : danmakuKeywords.entrySet()) {
                String keyword = entry.getKey();
                int danmakuWeight = entry.getValue();
                
                if (targetNode.getKeywords().containsKey(keyword)) {
                    int nodeWeight = targetNode.getKeywords().get(keyword);
                    score += danmakuWeight * nodeWeight * 0.5; // 节点关键词权重减半
                }
            }
        }

        // 添加基础权重
        if (edge.getWeight() != null) {
            score += edge.getWeight();
        }

        return score;
    }

    /**
     *  更新节点的关键词权重
     * @param node 视频节点
     * @param keywords 新的关键词及其权重
     */
    public void updateNodeKeywords(VideoNode node, Map<String, Integer> keywords) {
        if (node == null) return;
        
        Map<String, Integer> currentKeywords = node.getKeywords();
        if (currentKeywords == null) {
            node.setKeywords(keywords);
        } else {
            // 合并关键词权重
            keywords.forEach((key, weight) -> {
                currentKeywords.put(key, currentKeywords.getOrDefault(key, 0) + weight);
            });
        }
    }

    /**
     * 增加节点播放次数
     * @param node 视频节点
     */
    public void incrementPlayCount(VideoNode node) {
        if (node == null) return;
        Integer count = node.getPlayCount();
        node.setPlayCount(count == null ? 1 : count + 1);
    }
}
