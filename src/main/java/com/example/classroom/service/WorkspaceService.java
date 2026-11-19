package com.example.classroom.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 工作区数据包管理：视频与视频树 JSON 统一放在同一个文件夹，支持整包导出、导入与清理。
 */
@Service
public class WorkspaceService {

    @Autowired
    private VideoTreeService videoService;

    @Value("${video.workspace.dir:./workspace}")
    private String workspaceDir;

    @Value("${video.upload.path:./workspace/videos}")
    private String videoUploadPath;

    @Value("${video.temporary.path:./workspace/temp}")
    private String videoTemporaryPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private File workspaceDirFile;

    @PostConstruct
    public void init() {
        workspaceDirFile = resolve(workspaceDir);
        if (!workspaceDirFile.exists()) {
            workspaceDirFile.mkdirs();
        }
        videosDir().mkdirs();
        tempDir().mkdirs();
    }

    /**
     * 工作区概览：路径、体积、视频数量、视频树数量与视频列表。
     */
    public Map<String, Object> info() {
        File videos = videosDir();
        File json = videoService.getWorkspaceFile();

        List<Map<String, Object>> videoList = new ArrayList<>();
        long totalBytes = 0L;
        File[] files = videos.listFiles(File::isFile);
        if (files != null) {
            for (File file : files) {
                totalBytes += file.length();
                Map<String, Object> item = new HashMap<>();
                item.put("name", file.getName());
                item.put("size", file.length());
                item.put("modified", file.lastModified());
                item.put("url", "/video/stream/" + file.getName());
                videoList.add(item);
            }
        }
        videoList.sort((a, b) -> Long.compare((Long) b.get("modified"), (Long) a.get("modified")));

        Map<String, Object> result = new HashMap<>();
        result.put("workspaceDir", workspaceDirFile.getAbsolutePath());
        result.put("jsonFile", json == null ? "" : json.getAbsolutePath());
        result.put("jsonExists", json != null && json.exists());
        result.put("jsonSize", json != null && json.exists() ? json.length() : 0L);
        result.put("videoCount", videoList.size());
        result.put("videoBytes", totalBytes);
        result.put("treeCount", videoService.treeCount());
        result.put("videos", videoList);
        return result;
    }

    /**
     * 把视频与视频树 JSON 打包为 zip 输出。
     */
    public void exportZip(OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            File json = videoService.getWorkspaceFile();
            if (json != null && json.exists()) {
                addFile(zip, json, "video-trees.json");
            }
            File[] files = videosDir().listFiles(File::isFile);
            if (files != null) {
                for (File file : files) {
                    addFile(zip, file, "videos/" + file.getName());
                }
            }
        }
    }

    /**
     * 从 zip 恢复工作区：video-trees.json 放回工作区根目录，videos/ 下的文件放回视频目录。
     */
    public Map<String, Object> importZip(MultipartFile file) throws IOException {
        File json = videoService.getWorkspaceFile();
        File jsonTmp = new File(workspaceDirFile, "video-trees.json.import");
        File videos = videosDir();
        videos.mkdirs();

        int videoCount = 0;
        boolean hasJson = false;
        List<File> copiedVideos = new ArrayList<>();

        try {
            try (InputStream in = file.getInputStream();
                 ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String path = entry.getName().replace('\\', '/');
                    if (path.contains("..") || path.startsWith("/") || path.contains(":")) {
                        throw new IOException("压缩包包含非法路径: " + entry.getName());
                    }
                    String baseName = path.substring(path.lastIndexOf('/') + 1);
                    if (baseName.isEmpty()) {
                        continue;
                    }
                    if (path.startsWith("videos/")) {
                        File target = new File(videos, baseName);
                        Files.copy(zip, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        copiedVideos.add(target);
                        videoCount++;
                    } else if ("video-trees.json".equals(baseName)) {
                        Files.copy(zip, jsonTmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        hasJson = true;
                    }
                }
            }

            if (hasJson) {
                validateTreeJson(jsonTmp);
                if (json != null) {
                    Files.move(jsonTmp.toPath(), json.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException | RuntimeException e) {
            // 导入失败时清理已落盘的视频与临时 JSON，避免留下半套数据
            for (File copied : copiedVideos) {
                copied.delete();
            }
            jsonTmp.delete();
            throw e;
        }
        videoService.reload();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("videoCount", videoCount);
        result.put("treeCount", videoService.treeCount());
        result.put("hasJson", hasJson);
        return result;
    }

    /**
     * 删除所有视频文件与视频树 JSON。
     */
    public void clearAll() {
        deleteContents(videosDir());
        deleteContents(tempDir());
        videoService.clearAll();
    }

    /**
     * 删除单个视频文件。
     */
    public boolean deleteVideo(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return false;
        }
        File target = new File(videosDir(), name);
        return target.exists() && target.isFile() && target.delete();
    }

    private void validateTreeJson(File file) throws IOException {
        JsonNode node = objectMapper.readTree(file);
        if (node == null || !node.has("videoTrees")) {
            throw new IOException("video-trees.json 格式不正确，缺少 videoTrees 字段");
        }
    }

    private void addFile(ZipOutputStream zip, File file, String entryName) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(file.toPath(), zip);
        zip.closeEntry();
    }

    private File videosDir() {
        return resolve(videoUploadPath);
    }

    private File tempDir() {
        return resolve(videoTemporaryPath);
    }

    private File resolve(String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(System.getProperty("user.dir"), path);
        }
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    private void deleteContents(File dir) {
        if (!dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                deleteContents(file);
            }
            file.delete();
        }
    }
}
