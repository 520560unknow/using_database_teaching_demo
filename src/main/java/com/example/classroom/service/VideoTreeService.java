package com.example.classroom.service;

import com.example.classroom.model.Danmaku;
import com.example.classroom.model.VideoNode;
import com.example.classroom.model.VideoTree;
import com.example.classroom.repository.VideoTreeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.*;

@Service
public class VideoTreeService {

    // 视频树存储
    private Map<String, VideoTree> videoTrees = new HashMap<>();

    // 教师-视频树映射
    private Map<String, List<String>> teacherVideoTrees = new HashMap<>();

    // 视频节点存储
    private Map<String, VideoNode> videoNodes = new HashMap<>();

    @Autowired
    private VideoTreeRepository repository;

    @Autowired
    private DanmakuAnalyzer danmakuAnalyzer;

    @Value("${app.identity.default-teacher-id:teacher_001}")
    private String defaultTeacherId;

    @PostConstruct
    public void init() {
        repository.load(videoTrees, teacherVideoTrees, videoNodes);
        indexAllTreeNodes();
    }

    /**
     * 从工作区加载视频树 JSON，服务重启后仍可使用上次的视频与树。
     */
    public synchronized void reload() {
        repository.load(videoTrees, teacherVideoTrees, videoNodes);
        indexAllTreeNodes();
    }

    /**
     * 把每棵树从根节点递归登记到 videoNodes。
     * 持久化数据里可能只存了根节点，不补齐的话智能跳转按 targetNodeId 查不到子节点。
     */
    private void indexAllTreeNodes() {
        for (VideoTree tree : videoTrees.values()) {
            indexTreeNodes(tree.getRoot(), new HashSet<>());
        }
    }

    private void indexTreeNodes(VideoNode node, Set<String> visited) {
        if (node == null || node.getId() == null || !visited.add(node.getId())) {
            return;
        }
        videoNodes.put(node.getId(), node);
        if (node.getEdges() != null) {
            for (VideoNode.VideoEdge edge : node.getEdges()) {
                indexTreeNodes(edge.getTargetNode(), visited);
            }
        }
    }

    /**
     * 清空内存视频树并写入空的 JSON，避免重启后恢复演示数据。
     */
    public synchronized void clearAll() {
        videoTrees.clear();
        teacherVideoTrees.clear();
        videoNodes.clear();
        saveToDisk();
        System.out.println("已清空所有视频树数据");
    }

    public int treeCount() {
        return videoTrees.size();
    }

    private synchronized void saveToDisk() {
        repository.save(videoTrees, teacherVideoTrees, videoNodes);
    }

    private boolean hasWorkspaceFile() {
        return repository.hasWorkspaceFile();
    }

    /**
     * 工作区视频树 JSON 文件（供数据包服务打包/统计使用）。
     */
    public File getWorkspaceFile() {
        return repository.getWorkspaceFile();
    }

    /**
     * 工作区目录（供数据包服务统计使用）。
     */
    public File getWorkspaceDir() {
        return repository.getWorkspaceDir();
    }

    // ===== 新添加的方法 =====

    /**
     * 更新视频树
     */
    public VideoTree updateVideoTree(String id, VideoTree tree) {
        VideoTree existingTree = videoTrees.get(id);
        if (existingTree == null) {
            throw new RuntimeException("视频树不存在: " + id);
        }

        // 更新基本信息
        if (tree.getName() != null) {
            existingTree.setName(tree.getName());
        }
        if (tree.getDescription() != null) {
            existingTree.setDescription(tree.getDescription());
        }
        if (tree.getTeacherId() != null) {
            // 如果教师ID变更，需要更新映射关系
            if (!tree.getTeacherId().equals(existingTree.getTeacherId())) {
                // 从原教师列表中移除
                List<String> oldTeacherList = teacherVideoTrees.get(existingTree.getTeacherId());
                if (oldTeacherList != null) {
                    oldTeacherList.remove(id);
                }
                // 添加到新教师列表
                List<String> newTeacherList = teacherVideoTrees.computeIfAbsent(
                        tree.getTeacherId(), k -> new ArrayList<>());
                if (!newTeacherList.contains(id)) {
                    newTeacherList.add(id);
                }
                existingTree.setTeacherId(tree.getTeacherId());
            }
        }

        // 更新根节点（如果提供了新的根节点）
        if (tree.getRoot() != null) {
            // 递归更新节点信息
            updateNodeRecursively(tree.getRoot());
            existingTree.setRoot(tree.getRoot());
        }

        // 更新节点计数（如果需要）
        if (tree.getNodeCount() != null && tree.getNodeCount() > 0) {
            existingTree.setNodeCount(tree.getNodeCount());
        }

        // 更新时间戳
        existingTree.setUpdateTime(new Date());

        // 重新保存
        videoTrees.put(id, existingTree);
        saveToDisk();

        System.out.println("更新视频树成功: " + id);
        return existingTree;
    }

    /**
     * 递归更新节点
     */
    private void updateNodeRecursively(VideoNode node) {
        updateNodeRecursively(node, new HashSet<String>());
    }

    private void updateNodeRecursively(VideoNode node, Set<String> visited) {
        if (node == null || node.getId() == null || !visited.add(node.getId())) {
            return;
        }

        // 更新当前节点
        videoNodes.put(node.getId(), node);

        // 递归更新子节点
        if (node.getEdges() != null && !node.getEdges().isEmpty()) {
            for (VideoNode.VideoEdge edge : node.getEdges()) {
                if (edge == null) {
                    continue;
                }
                if (edge.getTargetNode() != null) {
                    // 同时索引嵌套在边里的目标节点
                    videoNodes.put(edge.getTargetNode().getId(), edge.getTargetNode());
                    updateNodeRecursively(edge.getTargetNode(), visited);
                } else if (edge.getTargetNodeId() != null) {
                    VideoNode childNode = getVideoNode(edge.getTargetNodeId());
                    if (childNode != null) {
                        updateNodeRecursively(childNode, visited);
                    }
                }
            }
        }
    }

    /**
     * 获取视频节点
     */
    public VideoNode getVideoNode(String id) {
        return videoNodes.get(id);
    }

    // ===== 原有方法保持 =====

    // 获取视频树
    public VideoTree getVideoTree(String id) {
        return videoTrees.get(id);
    }

    // 获取教师的视频树列表
    public List<VideoTree> getVideoTreesByTeacher(String teacherId) {
        List<String> treeIds = teacherVideoTrees.getOrDefault(teacherId, new ArrayList<>());
        List<VideoTree> result = new ArrayList<>();

        for (String treeId : treeIds) {
            VideoTree tree = videoTrees.get(treeId);
            if (tree != null) {
                result.add(tree);
            }
        }

        // 按创建时间倒序排序（兼容缺少创建时间的导入数据）
        result.sort((a, b) -> {
            Date timeA = a.getCreateTime() != null ? a.getCreateTime() : a.getUpdateTime();
            Date timeB = b.getCreateTime() != null ? b.getCreateTime() : b.getUpdateTime();
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            return timeB.compareTo(timeA);
        });
        return result;
    }

    // 从JSON创建视频树
    public VideoTree createVideoTreeFromJson(String jsonStr, String name, String description, String teacherId) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> jsonData = objectMapper.readValue(jsonStr, Map.class);

            // 检查JSON文件是否包含视频信息
            boolean hasVideoInfo = false;
            
            // 首先尝试 "elements" 字段（新格式）
            List<Map<String, Object>> nodes = null;
            if (jsonData.containsKey("elements") && jsonData.get("elements") instanceof List) {
                nodes = (List<Map<String, Object>>) jsonData.get("elements");
                for (Map<String, Object> nodeData : nodes) {
                    if (nodeData.containsKey("fileUrl") || nodeData.containsKey("videoUrl") || nodeData.containsKey("url")) {
                        hasVideoInfo = true;
                        break;
                    }
                }
            }
            // 然后尝试 "nodes" 字段（旧格式）
            else if (jsonData.containsKey("nodes") && jsonData.get("nodes") instanceof List) {
                nodes = (List<Map<String, Object>>) jsonData.get("nodes");
                for (Map<String, Object> nodeData : nodes) {
                    if (nodeData.containsKey("fileUrl") || nodeData.containsKey("videoUrl") || nodeData.containsKey("url")) {
                        hasVideoInfo = true;
                        break;
                    }
                }
            }
            // 最后尝试 "root" 字段（树形结构）
            else if (jsonData.containsKey("root") && jsonData.get("root") instanceof Map) {
                Map<String, Object> rootData = (Map<String, Object>) jsonData.get("root");
                if (rootData.containsKey("fileUrl") || rootData.containsKey("videoUrl") || rootData.containsKey("url")) {
                    hasVideoInfo = true;
                }
            }

            if (!hasVideoInfo) {
                throw new RuntimeException("JSON文件中不存在视频信息，请确保每个节点都包含fileUrl、videoUrl或url字段");
            }

            // 创建视频树
            VideoTree videoTree = new VideoTree();
            String treeId = "tree_" + UUID.randomUUID().toString().substring(0, 8);
            videoTree.setId(treeId);
            videoTree.setName(name);
            videoTree.setDescription(description);
            videoTree.setTeacherId(teacherId);
            videoTree.setCreateTime(new Date());
            videoTree.setUpdateTime(new Date());
            videoTree.setNodeCount(0);

            if (nodes != null && !nodes.isEmpty()) {
                // 使用第一个节点作为根节点
                Map<String, Object> firstNodeData = nodes.get(0);
                VideoNode rootNode = createNodeFromJson(firstNodeData);
                if (rootNode != null) {
                    videoNodes.put(rootNode.getId(), rootNode);
                    videoTree.setRoot(rootNode);
                    videoTree.setNodeCount(videoTree.getNodeCount() + 1);

                    // 处理剩余节点作为子节点
                    List<VideoNode.VideoEdge> edges = new ArrayList<>();
                    for (int i = 1; i < nodes.size(); i++) {
                        Map<String, Object> nodeData = nodes.get(i);
                        VideoNode node = createNodeFromJson(nodeData);
                        if (node != null) {
                            videoNodes.put(node.getId(), node);

                            VideoNode.VideoEdge edge = new VideoNode.VideoEdge();
                            edge.setTargetNodeId(node.getId());
                            edge.setTargetNode(node);
                            edge.setKeyword((String) nodeData.getOrDefault("keyword", "default"));
                            edge.setWeight((Integer) nodeData.getOrDefault("weight", 1));
                            edge.setCreateTime(new Date());
                            edges.add(edge);

                            videoTree.setNodeCount(videoTree.getNodeCount() + 1);
                        }
                    }

                    rootNode.setEdges(edges);
                }
            } else if (jsonData.containsKey("root") && jsonData.get("root") instanceof Map) {
                // 处理树形结构
                Map<String, Object> rootData = (Map<String, Object>) jsonData.get("root");
                VideoNode rootNode = createNodeFromJson(rootData);
                if (rootNode != null) {
                    videoNodes.put(rootNode.getId(), rootNode);
                    videoTree.setRoot(rootNode);
                    videoTree.setNodeCount(1);
                }
            } else {
                throw new RuntimeException("JSON文件格式错误，无法提取视频树结构");
            }

            // 保存视频树
            createVideoTree(videoTree);

            System.out.println("从JSON创建视频树成功: " + treeId + ", 包含 " + videoTree.getNodeCount() + " 个节点");
            return videoTree;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("从JSON创建视频树失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("创建视频树失败: " + e.getMessage());
        }
    }

    // 从JSON节点数据创建视频节点
    private VideoNode createNodeFromJson(Map<String, Object> nodeData) {
        try {
            VideoNode node = new VideoNode();
            
            // 设置节点ID
            if (nodeData.containsKey("id")) {
                node.setId((String) nodeData.get("id"));
            } else {
                node.setId("node_" + UUID.randomUUID().toString().substring(0, 8));
            }
            
            // 设置节点名称
            node.setName((String) nodeData.getOrDefault("name", "未命名节点"));
            
            // 设置节点描述
            node.setDescription((String) nodeData.getOrDefault("description", "节点描述"));
            
            // 设置视频URL，优先使用fileUrl，然后是videoUrl
            String videoUrl = null;
            if (nodeData.containsKey("fileUrl")) {
                videoUrl = (String) nodeData.get("fileUrl");
            } else if (nodeData.containsKey("videoUrl")) {
                videoUrl = (String) nodeData.get("videoUrl");
            } else if (nodeData.containsKey("url")) {
                videoUrl = (String) nodeData.get("url");
            }
            
            // 检查视频URL是否有效
            if (videoUrl == null || videoUrl.trim().isEmpty()) {
                throw new RuntimeException("节点缺少有效的视频URL，请确保每个节点都包含fileUrl、videoUrl或url字段");
            }
            
            // 使用JSON文件中的视频URL
            node.setVideoUrl(videoUrl);
            System.out.println("使用JSON文件中的视频URL: " + videoUrl);

            // 设置位置和大小
            if (nodeData.containsKey("position")) {
                Map<String, Object> position = (Map<String, Object>) nodeData.get("position");
                if (position.containsKey("x")) {
                    node.setX((Integer) position.get("x"));
                } else {
                    node.setX(200);
                }
                if (position.containsKey("y")) {
                    node.setY((Integer) position.get("y"));
                } else {
                    node.setY(200);
                }
            } else {
                node.setX((Integer) nodeData.getOrDefault("x", 200));
                node.setY((Integer) nodeData.getOrDefault("y", 200));
            }
            
            if (nodeData.containsKey("size")) {
                Map<String, Object> size = (Map<String, Object>) nodeData.get("size");
                if (size.containsKey("width")) {
                    node.setWidth((Integer) size.get("width"));
                } else {
                    node.setWidth(180);
                }
                if (size.containsKey("height")) {
                    node.setHeight((Integer) size.get("height"));
                } else {
                    node.setHeight(100);
                }
            } else {
                node.setWidth((Integer) nodeData.getOrDefault("width", 180));
                node.setHeight((Integer) nodeData.getOrDefault("height", 100));
            }

            // 处理关键词
            if (nodeData.containsKey("keywords")) {
                Object keywordsObj = nodeData.get("keywords");
                if (keywordsObj instanceof List) {
                    List<String> keywords = new ArrayList<>();
                    for (Object keyword : (List<?>) keywordsObj) {
                        if (keyword instanceof String) {
                            keywords.add((String) keyword);
                        }
                    }
                    // 将关键词转换为Map<String, Integer>格式
                    Map<String, Integer> keywordMap = new HashMap<>();
                    for (String keyword : keywords) {
                        keywordMap.put(keyword, 1); // 默认为权重1
                    }
                    node.setKeywords(keywordMap);
                }
            }

            // 如果有子节点连接，这里可以进一步处理
            node.setEdges(new ArrayList<>());

            return node;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("创建节点失败: " + e.getMessage());
            throw new RuntimeException("创建节点失败: " + e.getMessage());
        }
    }

    // 创建视频树
    public VideoTree createVideoTree(VideoTree videoTree) {
        // 生成ID如果为空
        if (videoTree.getId() == null || videoTree.getId().isEmpty()) {
            videoTree.setId("tree_" + UUID.randomUUID().toString().substring(0, 8));
        }

        // 设置创建时间
        if (videoTree.getCreateTime() == null) {
            videoTree.setCreateTime(new Date());
        }

        // 设置更新时间
        videoTree.setUpdateTime(new Date());

        // 保存根节点及其子节点（智能跳转需要按 targetNodeId 查节点）
        if (videoTree.getRoot() != null) {
            indexTreeNodes(videoTree.getRoot(), new HashSet<>());
        }

        // 保存视频树
        videoTrees.put(videoTree.getId(), videoTree);

        // 更新教师-视频树映射
        List<String> teacherTreeList = teacherVideoTrees.computeIfAbsent(videoTree.getTeacherId(), k -> new ArrayList<>());
        if (!teacherTreeList.contains(videoTree.getId())) {
            teacherTreeList.add(videoTree.getId());
        }

        saveToDisk();

        System.out.println("创建视频树成功: " + videoTree.getId() + ", 教师: " + videoTree.getTeacherId());
        return videoTree;
    }

    // 删除视频树
    public boolean deleteVideoTree(String treeId) {
        VideoTree videoTree = videoTrees.get(treeId);
        if (videoTree != null) {
            String teacherId = videoTree.getTeacherId();

            // 从教师-视频树映射中移除
            List<String> teacherTreeList = teacherVideoTrees.get(teacherId);
            if (teacherTreeList != null) {
                teacherTreeList.remove(treeId);
            }

            // 从存储中移除
            videoTrees.remove(treeId);
            saveToDisk();

            System.out.println("删除视频树: " + treeId);
            return true;
        }
        return false;
    }

    // 决定下一个节点（智能跳转）
    public VideoNode decideNextNode(String currentVideoId, Map<String, Integer> keywordWeights) {
        return danmakuAnalyzer.decideNextNode(videoNodes, currentVideoId, keywordWeights);
    }

    // 分析弹幕关键词权重
    public Map<String, Integer> analyzeDanmaku(List<Danmaku> danmakuList) {
        return danmakuAnalyzer.analyzeDanmaku(danmakuList);
    }

    // 初始化测试数据
    public void initTestData() {
        String teacherId = defaultTeacherId;

        // 只在从未落盘过视频树时初始化，避免清空后重启又出现演示数据
        if (videoTrees.isEmpty() && !hasWorkspaceFile()) {
            // 创建一个简单的测试视频树
            VideoTree videoTree = new VideoTree();
            String treeId = "tree_1";
            videoTree.setId(treeId);
            videoTree.setName("默认视频树");
            videoTree.setDescription("系统默认视频树，包含基础的课程结构");
            videoTree.setTeacherId(teacherId);
            videoTree.setCreateTime(new Date());
            videoTree.setUpdateTime(new Date());

            // 创建根节点
            VideoNode rootNode = new VideoNode();
            rootNode.setId("root_1");
            rootNode.setName("课程介绍");
            rootNode.setDescription("欢迎学习本课程，本课程将带你入门相关领域");
            rootNode.setVideoUrl("https://example.com/intro.mp4");
            rootNode.setX(400);
            rootNode.setY(100);
            rootNode.setWidth(180);
            rootNode.setHeight(100);

            // 创建子节点
            List<VideoNode.VideoEdge> edges = new ArrayList<>();
            String[][] chapterData = {
                    {"基础概念", "介绍基本概念和原理", "https://example.com/chapter1.mp4"},
                    {"核心知识点", "深入讲解核心知识点", "https://example.com/chapter2.mp4"},
                    {"总结回顾", "课程总结与回顾", "https://example.com/summary.mp4"}
            };

            for (int j = 0; j < chapterData.length; j++) {
                VideoNode chapterNode = new VideoNode();
                String nodeId = "node_1_" + j;
                chapterNode.setId(nodeId);
                chapterNode.setName(chapterData[j][0]);
                chapterNode.setDescription(chapterData[j][1]);
                chapterNode.setVideoUrl(chapterData[j][2]);
                chapterNode.setX(200 + j * 200);
                chapterNode.setY(250);
                chapterNode.setWidth(180);
                chapterNode.setHeight(100);
                chapterNode.setEdges(new ArrayList<>());

                // 保存节点
                videoNodes.put(nodeId, chapterNode);

                // 创建连接
                VideoNode.VideoEdge edge = new VideoNode.VideoEdge();
                edge.setTargetNodeId(nodeId);
                edge.setKeyword(chapterData[j][0]);
                edge.setWeight(1 + j);
                edge.setCreateTime(new Date());
                edges.add(edge);
            }

            rootNode.setEdges(edges);
            videoTree.setRoot(rootNode);
            videoTree.setNodeCount(1 + chapterData.length);

            // 保存根节点
            videoNodes.put(rootNode.getId(), rootNode);

            // 保存视频树
            createVideoTree(videoTree);

            System.out.println("初始化了 " + videoTrees.size() + " 个测试视频树");
            System.out.println("教师 " + teacherId + " 的视频树数量: " +
                    teacherVideoTrees.getOrDefault(teacherId, new ArrayList<>()).size());
        } else {
            System.out.println("已有视频树数据，跳过初始化");
        }
    }

    // 获取所有视频树（用于调试）
    public List<VideoTree> getAllVideoTrees() {
        return new ArrayList<>(videoTrees.values());
    }

    // 获取所有视频节点（用于调试）
    public List<VideoNode> getAllVideoNodes() {
        return new ArrayList<>(videoNodes.values());
    }

    // 创建简单视频树（用于课堂没有视频树时的默认值）
    public VideoTree createSimpleVideoTree(String teacherId) {
        VideoTree videoTree = new VideoTree();
        String treeId = "simple_tree_" + UUID.randomUUID().toString().substring(0, 8);
        videoTree.setId(treeId);
        videoTree.setName("默认视频树");
        videoTree.setDescription("系统创建的默认视频树结构");
        videoTree.setTeacherId(teacherId);
        videoTree.setCreateTime(new Date());
        videoTree.setUpdateTime(new Date());

        // 创建根节点
        VideoNode rootNode = new VideoNode();
        rootNode.setId("simple_root");
        rootNode.setName("欢迎视频");
        rootNode.setDescription("欢迎来到课堂，请开始学习");
        rootNode.setVideoUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4");
        rootNode.setX(400);
        rootNode.setY(100);
        rootNode.setWidth(180);
        rootNode.setHeight(100);

        // 创建两个子节点
        List<VideoNode.VideoEdge> edges = new ArrayList<>();

        // 节点1
        VideoNode node1 = new VideoNode();
        node1.setId("simple_node_1");
        node1.setName("课程介绍");
        node1.setDescription("课程内容介绍和教学目标");
        node1.setVideoUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4");
        node1.setX(250);
        node1.setY(250);
        node1.setWidth(180);
        node1.setHeight(100);
        node1.setEdges(new ArrayList<>());
        videoNodes.put(node1.getId(), node1);

        VideoNode.VideoEdge edge1 = new VideoNode.VideoEdge();
        edge1.setTargetNodeId(node1.getId());
        edge1.setKeyword("介绍");
        edge1.setWeight(1);
        edge1.setCreateTime(new Date());
        edges.add(edge1);

        // 节点2
        VideoNode node2 = new VideoNode();
        node2.setId("simple_node_2");
        node2.setName("课程大纲");
        node2.setDescription("详细课程大纲和学习路径");
        node2.setVideoUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4");
        node2.setX(550);
        node2.setY(250);
        node2.setWidth(180);
        node2.setHeight(100);
        node2.setEdges(new ArrayList<>());
        videoNodes.put(node2.getId(), node2);

        VideoNode.VideoEdge edge2 = new VideoNode.VideoEdge();
        edge2.setTargetNodeId(node2.getId());
        edge2.setKeyword("大纲");
        edge2.setWeight(1);
        edge2.setCreateTime(new Date());
        edges.add(edge2);

        rootNode.setEdges(edges);
        videoTree.setRoot(rootNode);
        videoTree.setNodeCount(3);

        videoNodes.put(rootNode.getId(), rootNode);

        // 保存视频树
        createVideoTree(videoTree);

        return videoTree;
    }
}