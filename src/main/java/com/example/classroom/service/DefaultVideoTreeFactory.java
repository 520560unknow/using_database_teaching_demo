package com.example.classroom.service;

import com.example.classroom.model.VideoNode;
import com.example.classroom.model.VideoTree;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 默认视频树工厂：把"新建课堂时没有选择视频树"这一场景的构造逻辑集中到这里，
 * 并负责把构造结果持久化，避免课堂指向一棵从未保存过的树。
 */
@Component
public class DefaultVideoTreeFactory {

    private final VideoTreeService videoTreeService;

    public DefaultVideoTreeFactory(VideoTreeService videoTreeService) {
        this.videoTreeService = videoTreeService;
    }

    /**
     * 构造并保存默认视频树，返回已持久化的树；失败返回 null。
     */
    public VideoTree create(String teacherId, String className) {
        VideoTree tree = build(teacherId, className);
        if (tree == null) {
            return null;
        }
        videoTreeService.createVideoTree(tree);
        System.out.println("创建默认视频树成功，ID: " + tree.getId());
        return tree;
    }

    private VideoTree build(String teacherId, String className) {
        try {
            VideoTree videoTree = new VideoTree();
            String treeId = "default_tree_" + UUID.randomUUID().toString().substring(0, 8);
            videoTree.setId(treeId);
            videoTree.setName(className + " - 默认视频树");
            videoTree.setDescription(className + "的系统默认视频树结构");
            videoTree.setTeacherId(teacherId);
            videoTree.setCreateTime(new Date());
            videoTree.setUpdateTime(new Date());

            VideoNode rootNode = new VideoNode();
            rootNode.setId("root_node_" + UUID.randomUUID().toString().substring(0, 8));
            rootNode.setName("欢迎视频");
            rootNode.setDescription("欢迎来到" + className);
            rootNode.setVideoUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4");
            rootNode.setX(400);
            rootNode.setY(100);
            rootNode.setWidth(180);
            rootNode.setHeight(100);

            VideoNode child1 = createSampleVideoNode("child1", "课程介绍", "介绍课程内容和目标", 250, 250);
            VideoNode child2 = createSampleVideoNode("child2", "核心概念", "讲解核心概念", 400, 250);
            VideoNode child3 = createSampleVideoNode("child3", "总结回顾", "总结课程要点", 550, 250);

            List<VideoNode.VideoEdge> rootEdges = new ArrayList<>();
            rootEdges.add(createEdge(child1, "开始学习", 1));
            rootEdges.add(createEdge(child2, "深入理解", 2));
            rootEdges.add(createEdge(child3, "回顾总结", 1));

            rootNode.setEdges(rootEdges);
            videoTree.setRoot(rootNode);

            videoTree.setNodeCount(4);
            videoTree.setMaxDepth(2);
            videoTree.setLeafCount(3);
            return videoTree;
        } catch (Exception e) {
            System.err.println("创建默认视频树失败: " + e.getMessage());
            return null;
        }
    }

    private VideoNode.VideoEdge createEdge(VideoNode target, String keyword, int weight) {
        VideoNode.VideoEdge edge = new VideoNode.VideoEdge();
        edge.setTargetNodeId(target.getId());
        edge.setTargetNode(target);
        edge.setKeyword(keyword);
        edge.setWeight(weight);
        edge.setCreateTime(new Date());
        return edge;
    }

    private VideoNode createSampleVideoNode(String prefix, String name, String description, Integer x, Integer y) {
        VideoNode node = new VideoNode();
        node.setId(prefix + "_" + UUID.randomUUID().toString().substring(0, 8));
        node.setName(name);
        node.setDescription(description);

        String[] sampleVideos = {
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
        };
        node.setVideoUrl(sampleVideos[(int) (Math.random() * sampleVideos.length)]);
        node.setX(x);
        node.setY(y);
        node.setWidth(180);
        node.setHeight(100);
        node.setEdges(new ArrayList<>());
        return node;
    }
}
