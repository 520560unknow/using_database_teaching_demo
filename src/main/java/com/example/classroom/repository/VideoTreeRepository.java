package com.example.classroom.repository;

import com.example.classroom.model.VideoNode;
import com.example.classroom.model.VideoTree;
import com.example.support.JsonStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 视频树工作区 JSON 持久化：负责工作区目录解析与读写 video-trees.json。
 * 内存中的视频树、教师映射与节点由调用方持有，本类只负责读写。
 */
@Repository
public class VideoTreeRepository {

    // 工作区目录（视频与视频树 JSON 统一存放）
    @Value("${video.workspace.dir:./workspace}")
    private String workspaceDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private File workspaceDirFile;

    @PostConstruct
    public void init() {
        File dir = new File(workspaceDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), workspaceDir);
        }
        try {
            dir = dir.getCanonicalFile();
        } catch (IOException e) {
            dir = dir.getAbsoluteFile();
        }
        workspaceDirFile = dir;
        if (!dir.exists() && dir.mkdirs()) {
            System.out.println("已创建工作区目录: " + dir.getAbsolutePath());
        }
    }

    /**
     * 从工作区加载视频树 JSON，并填充到调用方传入的内存 Map。
     * 镜像原 VideoTreeService.loadFromDisk() 的行为。
     */
    public void load(Map<String, VideoTree> videoTrees,
                     Map<String, List<String>> teacherVideoTrees,
                     Map<String, VideoNode> videoNodes) {
        File file = workspaceFile();
        videoTrees.clear();
        teacherVideoTrees.clear();
        videoNodes.clear();
        if (file == null || !file.exists() || file.length() == 0) {
            System.out.println("未找到视频树数据文件，使用空数据");
            return;
        }
        try {
            WorkspaceData data = JsonStore.read(file.toPath(), WorkspaceData.class, objectMapper);
            if (data.videoTrees != null) {
                videoTrees.putAll(data.videoTrees);
            }
            if (data.teacherVideoTrees != null) {
                teacherVideoTrees.putAll(data.teacherVideoTrees);
            }
            if (data.videoNodes != null) {
                videoNodes.putAll(data.videoNodes);
            }
            // 修复缺失或不完整的教师映射（兼容只带 videoTrees 的导入包）
            for (VideoTree tree : videoTrees.values()) {
                if (tree.getTeacherId() == null || tree.getId() == null) {
                    continue;
                }
                List<String> treeIds = teacherVideoTrees.computeIfAbsent(
                        tree.getTeacherId(), k -> new ArrayList<>());
                if (!treeIds.contains(tree.getId())) {
                    treeIds.add(tree.getId());
                }
            }
            System.out.println("已加载视频树数据: " + videoTrees.size() + " 棵树, "
                    + videoNodes.size() + " 个节点");
        } catch (Exception e) {
            System.err.println("加载视频树数据失败: " + file + " (" + e.getMessage() + ")");
        }
    }

    /**
     * 将内存中的视频树、教师映射与节点写入工作区 JSON。
     * 镜像原 VideoTreeService.saveToDisk() 的行为。
     */
    public synchronized void save(Map<String, VideoTree> videoTrees,
                                  Map<String, List<String>> teacherVideoTrees,
                                  Map<String, VideoNode> videoNodes) {
        File file = workspaceFile();
        if (file == null) {
            return;
        }
        try {
            WorkspaceData data = new WorkspaceData();
            data.videoTrees = videoTrees;
            data.teacherVideoTrees = teacherVideoTrees;
            data.videoNodes = videoNodes;
            JsonStore.write(file.toPath(), data, objectMapper);
        } catch (Exception e) {
            System.err.println("保存视频树数据失败: " + file + " (" + e.getMessage() + ")");
        }
    }

    private File workspaceFile() {
        if (workspaceDirFile == null) {
            return null;
        }
        if (!workspaceDirFile.exists()) {
            workspaceDirFile.mkdirs();
        }
        return new File(workspaceDirFile, "video-trees.json");
    }

    public boolean hasWorkspaceFile() {
        File file = workspaceFile();
        return file != null && file.exists();
    }

    /**
     * 工作区视频树 JSON 文件（供数据包服务打包/统计使用）。
     */
    public File getWorkspaceFile() {
        return workspaceFile();
    }

    /**
     * 工作区目录（供数据包服务统计使用）。
     */
    public File getWorkspaceDir() {
        return workspaceDirFile;
    }

    /**
     * 持久化结构：视频树 + 教师映射 + 节点。
     */
    public static class WorkspaceData {
        public Map<String, VideoTree> videoTrees;
        public Map<String, List<String>> teacherVideoTrees;
        public Map<String, VideoNode> videoNodes;
    }
}
