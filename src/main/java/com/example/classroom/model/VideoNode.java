package com.example.classroom.model;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class VideoNode {
    private String id;
    private String name;
    private String description;
    private String videoUrl;

    private Integer x;
    private Integer y;
    private Integer width;
    private Integer height;

    // 新增字段：关键词及其权重
    private Map<String, Integer> keywords;
    
    // 新增字段：视频标签
    private List<String> tags;
    
    // 新增字段：播放次数
    private Integer playCount;
    
    // 新增字段：相关度分数
    private Double relevanceScore;

    private List<VideoEdge> edges;

    @Data
    public static class VideoEdge {
        private String targetNodeId;
        private VideoNode targetNode;
        private String keyword;
        private Integer weight;
        private Date createTime;
        
        // 新增字段：关键词权重映射
        private Map<String, Integer> keywordWeights;
        
        // 新增字段：转换条件
        private String transitionCondition;
        
        // 新增字段：推荐置信度
        private Double confidence;

    }
}