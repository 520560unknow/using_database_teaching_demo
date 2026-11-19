package com.example.classroom.service;

import com.example.classroom.model.VideoNode;
import com.example.classroom.model.VideoTree;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IntelligentRecommendationService {
    
    private final DanmakuAnalysisService danmakuAnalysisService;
    
    // 存储视频树结构
    private Map<String, VideoTree> videoTrees;
    
    // 存储当前推荐结果
    private Map<String, VideoNode> currentRecommendations;

    @Autowired
    public IntelligentRecommendationService(DanmakuAnalysisService danmakuAnalysisService) {
        this.danmakuAnalysisService = danmakuAnalysisService;
        this.videoTrees = new ConcurrentHashMap<>();
        this.currentRecommendations = new ConcurrentHashMap<>();
    }

    /**
     * 注册视频树
     * @param treeId 树ID
     * @param videoTree 视频树
     */
    public void registerVideoTree(String treeId, VideoTree videoTree) {
        if (treeId == null || videoTree == null) {
            return;
        }
        videoTrees.put(treeId, videoTree);
    }

    /**
     * 智能推荐下一个视频节点
     * @param treeId 树ID
     * @param currentNode 当前视频节点
     * @return 推荐的下一个视频节点
     */
    public VideoNode recommendNextVideo(String treeId, VideoNode currentNode) {
        if (treeId == null || currentNode == null) {
            return null;
        }
        
        VideoTree videoTree = videoTrees.get(treeId);
        if (videoTree == null) {
            return null;
        }
        
        // 分析当前节点的弹幕
        Map<String, Integer> danmakuKeywords = danmakuAnalysisService.analyzeNodeDanmakus(currentNode);
        
        // 使用视频树进行智能推荐
        VideoNode recommendedNode = videoTree.recommendNextNode(currentNode, danmakuKeywords);
        
        // 保存当前推荐结果
        if (recommendedNode != null) {
            currentRecommendations.put(currentNode.getId(), recommendedNode);
        }
        
        return recommendedNode;
    }

    /**
     * 基于弹幕关键词推荐下一个视频
     * @param treeId 树ID
     * @param currentNodeId 当前节点ID
     * @param danmakuKeywords 弹幕关键词及其权重
     * @return 推荐的下一个视频节点
     */
    public VideoNode recommendByKeywords(String treeId, String currentNodeId, Map<String, Integer> danmakuKeywords) {
        if (treeId == null || currentNodeId == null) {
            return null;
        }
        
        VideoTree videoTree = videoTrees.get(treeId);
        if (videoTree == null) {
            return null;
        }
        
        // 查找当前节点
        VideoNode currentNode = findNodeById(videoTree, currentNodeId);
        if (currentNode == null) {
            return null;
        }
        
        // 使用视频树进行智能推荐
        VideoNode recommendedNode = videoTree.recommendNextNode(currentNode, danmakuKeywords);
        
        // 保存当前推荐结果
        if (recommendedNode != null) {
            currentRecommendations.put(currentNodeId, recommendedNode);
        }
        
        return recommendedNode;
    }

    /**
     * 根据ID查找节点
     * @param videoTree 视频树
     * @param nodeId 节点ID
     * @return 视频节点
     */
    private VideoNode findNodeById(VideoTree videoTree, String nodeId) {
        if (videoTree == null || nodeId == null) {
            return null;
        }
        
        return findNodeRecursive(videoTree.getRoot(), nodeId);
    }

    /**
     * 递归查找节点
     * @param node 当前节点
     * @param nodeId 目标节点ID
     * @return 找到的节点
     */
    private VideoNode findNodeRecursive(VideoNode node, String nodeId) {
        if (node == null) {
            return null;
        }
        
        if (nodeId.equals(node.getId())) {
            return node;
        }
        
        if (node.getEdges() != null) {
            for (VideoNode.VideoEdge edge : node.getEdges()) {
                VideoNode foundNode = findNodeRecursive(edge.getTargetNode(), nodeId);
                if (foundNode != null) {
                    return foundNode;
                }
            }
        }
        
        return null;
    }

    /**
     * 获取当前推荐结果
     * @param currentNodeId 当前节点ID
     * @return 推荐的下一个视频节点
     */
    public VideoNode getCurrentRecommendation(String currentNodeId) {
        return currentRecommendations.get(currentNodeId);
    }

    /**
     * 清除推荐结果
     * @param currentNodeId 当前节点ID
     */
    public void clearRecommendation(String currentNodeId) {
        currentRecommendations.remove(currentNodeId);
    }

    /**
     * 清除所有推荐结果
     */
    public void clearAllRecommendations() {
        currentRecommendations.clear();
    }

    /**
     * 更新视频节点的关键词权重
     * @param treeId 树ID
     * @param nodeId 节点ID
     * @param keywords 关键词及其权重
     */
    public void updateNodeKeywords(String treeId, String nodeId, Map<String, Integer> keywords) {
        if (treeId == null || nodeId == null || keywords == null || keywords.isEmpty()) {
            return;
        }
        
        VideoTree videoTree = videoTrees.get(treeId);
        if (videoTree == null) {
            return;
        }
        
        VideoNode node = findNodeById(videoTree, nodeId);
        if (node != null) {
            videoTree.updateNodeKeywords(node, keywords);
        }
    }

    /**
     * 增加节点播放次数
     * @param treeId 树ID
     * @param nodeId 节点ID
     */
    public void incrementNodePlayCount(String treeId, String nodeId) {
        if (treeId == null || nodeId == null) {
            return;
        }
        
        VideoTree videoTree = videoTrees.get(treeId);
        if (videoTree == null) {
            return;
        }
        
        VideoNode node = findNodeById(videoTree, nodeId);
        if (node != null) {
            videoTree.incrementPlayCount(node);
        }
    }

    /**
     * 获取视频树
     * @param treeId 树ID
     * @return 视频树
     */
    public VideoTree getVideoTree(String treeId) {
        return videoTrees.get(treeId);
    }

    /**
     * 移除视频树
     * @param treeId 树ID
     */
    public void removeVideoTree(String treeId) {
        videoTrees.remove(treeId);
    }

    /**
     * 清除所有视频树
     */
    public void clearAllVideoTrees() {
        videoTrees.clear();
        currentRecommendations.clear();
    }

    /**
     * 预分析视频节点，提高推荐速度
     * @param treeId 树ID
     * @param nodeId 节点ID
     */
    public void preAnalyzeNode(String treeId, String nodeId) {
        if (treeId == null || nodeId == null) {
            return;
        }
        
        VideoTree videoTree = videoTrees.get(treeId);
        if (videoTree == null) {
            return;
        }
        
        VideoNode node = findNodeById(videoTree, nodeId);
        if (node != null) {
            // 预分析弹幕
            danmakuAnalysisService.analyzeNodeDanmakus(node);
        }
    }
}
