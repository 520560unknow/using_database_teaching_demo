package com.example.classroom.controller;

import com.example.classroom.model.Video;
import com.example.classroom.service.VideoAssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.annotation.PostConstruct;
import org.apache.catalina.connector.ClientAbortException;
import java.io.*;
import java.net.URLEncoder;
import java.util.Date;
import java.util.UUID;

@Controller
@RequestMapping("/video")
public class VideoController {

    @Autowired
    private VideoAssetService videoService;

    @Value("${video.upload.path}")
    private String videoUploadPath;

    @Value("${video.temporary.path}")
    private String videoTemporaryPath;

    // 初始化方法，确保路径是绝对路径
    @PostConstruct
    public void init() {
        // 处理上传路径
        File uploadDir = new File(videoUploadPath);
        if (!uploadDir.isAbsolute()) {
            // 转换为相对于项目根目录的绝对路径
            String projectRoot = System.getProperty("user.dir");
            // 检查是否在classroom-video-system子目录中运行
            if (projectRoot.endsWith("classroom-video-system")) {
                // 如果在子目录中运行，使用父目录作为项目根目录
                projectRoot = projectRoot.substring(0, projectRoot.lastIndexOf(File.separator));
            }
            videoUploadPath = projectRoot + File.separator + videoUploadPath;
            System.out.println("转换后的上传路径: " + videoUploadPath);
        }
        
        // 处理临时路径
        File tempDir = new File(videoTemporaryPath);
        if (!tempDir.isAbsolute()) {
            // 转换为相对于项目根目录的绝对路径
            String projectRoot = System.getProperty("user.dir");
            // 检查是否在classroom-video-system子目录中运行
            if (projectRoot.endsWith("classroom-video-system")) {
                // 如果在子目录中运行，使用父目录作为项目根目录
                projectRoot = projectRoot.substring(0, projectRoot.lastIndexOf(File.separator));
            }
            videoTemporaryPath = projectRoot + File.separator + videoTemporaryPath;
            System.out.println("转换后的临时路径: " + videoTemporaryPath);
        }
        
        // 确保目录存在
        File finalUploadDir = new File(videoUploadPath);
        if (!finalUploadDir.exists()) {
            boolean created = finalUploadDir.mkdirs();
            System.out.println("上传目录创建结果: " + created);
        }
        
        File finalTempDir = new File(videoTemporaryPath);
        if (!finalTempDir.exists()) {
            boolean created = finalTempDir.mkdirs();
            System.out.println("临时目录创建结果: " + created);
        }
        
        // 兼容旧版本：把根目录 video-uploads 中已有的视频迁移到工作区
        File legacyDir = new File(System.getProperty("user.dir"), "video-uploads");
        if (legacyDir.exists() && !legacyDir.getAbsolutePath().equals(finalUploadDir.getAbsolutePath())) {
            File[] legacyFiles = legacyDir.listFiles(File::isFile);
            if (legacyFiles != null) {
                for (File legacyFile : legacyFiles) {
                    File target = new File(finalUploadDir, legacyFile.getName());
                    if (!target.exists()) {
                        try {
                            java.nio.file.Files.move(legacyFile.toPath(), target.toPath());
                        } catch (Exception e) {
                            System.out.println("迁移旧视频失败: " + legacyFile.getName());
                        }
                    }
                }
            }
        }
        
        // 打印最终的目录路径
        System.out.println("最终视频上传路径: " + videoUploadPath);
        System.out.println("最终视频临时路径: " + videoTemporaryPath);
    }



    // 视频流处理
    @GetMapping("/stream/{fileName}")
    public void streamVideo(@PathVariable String fileName, HttpServletRequest request,
                            HttpServletResponse response) {
        // 打印视频文件路径信息
        System.out.println("=== 视频流请求 ===");
        System.out.println("请求的文件名: " + fileName);
        System.out.println("上传路径: " + videoUploadPath);

        if (!isSafeFileName(fileName)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        File videoFile = new File(videoUploadPath + File.separator + fileName);
        System.out.println("完整视频文件路径: " + videoFile.getAbsolutePath());
        System.out.println("文件是否存在: " + videoFile.exists());
        System.out.println("文件是否可读: " + videoFile.canRead());
        System.out.println("文件大小: " + (videoFile.exists() ? videoFile.length() : 0) + " bytes");
        
        // 如果文件不存在，尝试在父目录的video-uploads中查找
        if (!videoFile.exists()) {
            String parentPath = new File(videoUploadPath).getParent();
            File parentVideoFile = new File(parentPath + File.separator + "video-uploads" + File.separator + fileName);
            System.out.println("尝试在父目录查找: " + parentVideoFile.getAbsolutePath());
            System.out.println("父目录文件是否存在: " + parentVideoFile.exists());
            if (parentVideoFile.exists()) {
                videoFile = parentVideoFile;
                System.out.println("使用父目录中的视频文件");
            }
        }
        
        streamVideoFile(videoFile, request, response);
    }

    // 临时视频流处理
    @GetMapping("/stream/temporary/{fileName}")
    public void streamTemporaryVideo(@PathVariable String fileName, HttpServletRequest request,
                                     HttpServletResponse response) {
        if (!isSafeFileName(fileName)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        File videoFile = new File(videoTemporaryPath + File.separator + fileName);
        streamVideoFile(videoFile, request, response);
    }

    // 视频流处理通用方法
    private void streamVideoFile(File videoFile, HttpServletRequest request, HttpServletResponse response) {
        if (!videoFile.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            // 设置响应头
            response.setContentType("video/mp4");
            response.setHeader("Content-Disposition", "inline; filename=\"" + URLEncoder.encode(videoFile.getName(), "UTF-8") + "\"");
            response.setHeader("Accept-Ranges", "bytes");

            long fileLength = videoFile.length();
            long start = 0;
            long end = fileLength - 1;

            // 处理范围请求（Range 来自请求头，而不是响应头）
            String range = request.getHeader("Range");
            if (range != null && range.startsWith("bytes=") && !range.contains(",")) {
                System.out.println("处理范围请求: " + range);
                String spec = range.substring("bytes=".length()).trim();
                String[] parts = spec.split("-", -1);
                try {
                    long suffixedEnd = fileLength - 1;
                    if (parts[0].isEmpty() && parts.length > 1 && !parts[1].isEmpty()) {
                        // "bytes=-500" 表示最后 500 字节
                        long suffixLength = Long.parseLong(parts[1]);
                        start = Math.max(0, fileLength - suffixLength);
                        end = suffixedEnd;
                    } else {
                        start = Long.parseLong(parts[0]);
                        if (parts.length > 1 && !parts[1].isEmpty()) {
                            end = Math.min(suffixedEnd, Long.parseLong(parts[1]));
                        } else {
                            end = suffixedEnd;
                        }
                    }
                    if (start < 0 || start > end || start >= fileLength) {
                        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                        response.setHeader("Content-Range", "bytes */" + fileLength);
                        return;
                    }
                    // 设置部分内容响应状态码
                    response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                    // 设置Content-Range响应头
                    response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                    // 设置Content-Length为实际发送的字节数
                    response.setContentLengthLong(end - start + 1);
                } catch (NumberFormatException e) {
                    System.out.println("范围请求格式错误: " + e.getMessage());
                    // 忽略错误，使用完整范围
                    start = 0;
                    end = fileLength - 1;
                    response.setContentLengthLong(fileLength);
                }
            } else {
                // 完整请求
                response.setContentLengthLong(fileLength);
            }

            // 读取文件并写入响应流
            try (RandomAccessFile raf = new RandomAccessFile(videoFile, "r")) {
                // 定位到起始位置
                raf.seek(start);
                // 创建缓冲区
                byte[] buffer = new byte[4096];
                long bytesToRead = end - start + 1;
                int bytesRead;
                
                while (bytesToRead > 0 && (bytesRead = raf.read(buffer, 0, (int) Math.min(buffer.length, bytesToRead))) != -1) {
                    try {
                        response.getOutputStream().write(buffer, 0, bytesRead);
                        response.getOutputStream().flush(); // 定期刷新，避免缓冲区溢出
                        bytesToRead -= bytesRead;
                    } catch (IOException e) {
                        // 客户端中止连接，这是正常情况，不需要记录错误
                        System.out.println("客户端中止视频流连接: " + e.getMessage());
                        return;
                    }
                }
            }
        } catch (ClientAbortException e) {
            // 客户端中止连接，这是正常情况，不需要记录错误
            System.out.println("客户端中止视频流连接: " + e.getMessage());
        } catch (Exception e) {
            // 其他异常，记录错误但不向客户端发送错误状态码
            // 因为响应可能已经开始发送
            System.out.println("视频流处理异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 增加播放量（登录用户）
    @PostMapping("/play/user/{id}")
    @ResponseBody
    public String incrementViewsForUser(@PathVariable Integer id, @RequestParam String userId) {
        boolean success = videoService.incrementViewsForUser(id, userId);
        return "{\"success\": " + success + "}";
    }

    // 增加播放量（游客）
    @PostMapping("/play/visitor/{id}")
    @ResponseBody
    public String incrementViewsForVisitor(@PathVariable Integer id) {
        boolean success = videoService.incrementViewsForVisitor(id);
        return "{\"success\": " + success + "}";
    }

    // 增加点赞数
    @PostMapping("/like/{id}")
    @ResponseBody
    public String incrementLikes(@PathVariable Integer id) {
        boolean success = videoService.incrementLikes(id);
        return "{\"success\": " + success + "}";
    }

    // 增加收藏数
    @PostMapping("/favorite/{id}")
    @ResponseBody
    public String incrementFavorites(@PathVariable Integer id) {
        boolean success = videoService.incrementFavorites(id);
        return "{\"success\": " + success + "}";
    }

    // 获取视频信息
    @GetMapping("/info/{id}")
    @ResponseBody
    public Video getVideoInfo(@PathVariable Integer id) {
        Video video = videoService.getVideo(id);
        if (video == null) {
            // 尝试从临时视频中查找
            for (Video tempVideo : videoService.getAllTemporaryVideos()) {
                if (tempVideo.getId().equals(id)) {
                    video = tempVideo;
                    break;
                }
            }
        }
        return video;
    }

    // 获取所有视频
    @GetMapping("/list")
    @ResponseBody
    public java.util.List<Video> getAllVideos() {
        java.util.List<Video> allVideos = new java.util.ArrayList<>();
        
        // 从内存中获取视频
        allVideos.addAll(videoService.getAllVideos());
        allVideos.addAll(videoService.getAllTemporaryVideos());
        
        // 扫描实际的视频文件目录，确保所有视频都被包含
        File uploadDir = new File(videoUploadPath);
        if (uploadDir.exists() && uploadDir.isDirectory()) {
            File[] videoFiles = uploadDir.listFiles((dir, name) -> {
                String lowerName = name.toLowerCase();
                return lowerName.endsWith(".mp4") || lowerName.endsWith(".avi") || 
                       lowerName.endsWith(".mov") || lowerName.endsWith(".wmv") || 
                       lowerName.endsWith(".mkv");
            });
            
            if (videoFiles != null) {
                for (File videoFile : videoFiles) {
                    // 检查是否已经在内存列表中
                    boolean exists = false;
                    for (Video existingVideo : allVideos) {
                        if (existingVideo.getFileName() != null && 
                            existingVideo.getFileName().equals(videoFile.getName())) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (!exists) {
                        // 创建新的视频对象
                        Video video = new Video();
                        video.setTitle(videoFile.getName().substring(0, videoFile.getName().lastIndexOf('.')));
                        video.setDescription("");
                        video.setFileName(videoFile.getName());
                        video.setFileSize(videoFile.length());
                        video.setFilePath(videoFile.getAbsolutePath());
                        video.setUrl("/video/stream/" + videoFile.getName());
                        video.setUploader("anonymous");
                        video.setUploadTime(new Date(videoFile.lastModified()));
                        allVideos.add(video);
                    }
                }
            }
        }
        
        return allVideos;
    }

    // HTTP分块上传 - 初始化上传
    @PostMapping("/upload/chunk/init")
    @ResponseBody
    public String initChunkUpload(@RequestParam("fileName") String fileName, @RequestParam("fileSize") long fileSize) {
        try {
            String uploadId = UUID.randomUUID().toString();
            
            // 创建临时目录存储分块
            String chunkDir = videoTemporaryPath + File.separator + uploadId;
            File dir = new File(chunkDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            return "{\"uploadId\": \"" + uploadId + "\", \"chunkSize\": 2097152}"; // 2MB chunks
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"初始化上传失败\"}";
        }
    }

    // HTTP分块上传 - 上传分块
    @PostMapping("/upload/chunk")
    @ResponseBody
    public String uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("file") MultipartFile file) {
        try {
            if (!isValidUploadId(uploadId)) {
                return "{\"error\": \"无效的上传ID\"}";
            }

            String chunkDir = videoTemporaryPath + File.separator + uploadId;
            File dir = new File(chunkDir);
            if (!dir.exists()) {
                return "{\"error\": \"上传ID不存在\"}";
            }
            
            // 保存分块
            String chunkFileName = chunkIndex + ".part";
            File chunkFile = new File(chunkDir, chunkFileName);
            file.transferTo(chunkFile);
            
            return "{\"success\": true}";
        } catch (Exception e) {
            e.printStackTrace();
            // 上传分块失败时，清理临时目录
            try {
                String chunkDir = videoTemporaryPath + File.separator + uploadId;
                File dir = new File(chunkDir);
                if (dir.exists()) {
                    deleteDirectory(dir);
                }
            } catch (Exception cleanupError) {
                cleanupError.printStackTrace();
            }
            return "{\"error\": \"上传分块失败\"}";
        }
    }
    
    // 删除目录及其所有内容
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    // HTTP分块上传 - 完成上传
    @PostMapping("/upload/chunk/complete")
    @ResponseBody
    public String completeChunkUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("fileName") String fileName,
            @RequestParam("fileSize") long fileSize,
            @RequestParam("totalChunks") int totalChunks) {
        try {
            if (!isValidUploadId(uploadId)) {
                return "{\"error\": \"无效的上传ID\"}";
            }

            String chunkDir = videoTemporaryPath + File.separator + uploadId;
            File dir = new File(chunkDir);
            if (!dir.exists()) {
                return "{\"error\": \"上传ID不存在\"}";
            }
            
            // 先检查分块目录是否存在
            File[] chunks = dir.listFiles((d, name) -> name.endsWith(".part"));
            if (chunks == null || chunks.length == 0) {
                return "{\"error\": \"没有找到分块文件\"}";
            }
            
            // 使用前端传递的totalChunks参数，确保与前端计算一致
            int expectedChunks = totalChunks;
            boolean[] chunkExists = new boolean[expectedChunks];
            for (int i = 0; i < expectedChunks; i++) {
                File chunkFile = new File(dir, i + ".part");
                chunkExists[i] = chunkFile.exists();
                if (!chunkExists[i]) {
                    System.out.println("缺少分块文件: " + i + ".part");
                }
            }
            
            // 检查是否有缺失的分块
            boolean allChunksExist = true;
            StringBuilder missingChunks = new StringBuilder();
            for (int i = 0; i < expectedChunks; i++) {
                if (!chunkExists[i]) {
                    allChunksExist = false;
                    if (missingChunks.length() > 0) {
                        missingChunks.append(", ");
                    }
                    missingChunks.append(i);
                }
            }
            
            if (!allChunksExist) {
                return "{\"error\": \"缺少分块文件：" + missingChunks.toString() + "\"}";
            }
            
            // 只有当所有分块都存在时，才创建最终文件
            // 使用用户指定的文件名，避免使用UUID前缀
            String finalFileName = fileName;
            String finalFilePath = videoUploadPath + File.separator + finalFileName;
            File finalFile = new File(finalFilePath);
            
            // 检查文件名是否冲突，如果冲突则添加时间戳后缀
            int counter = 1;
            while (finalFile.exists()) {
                String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
                String fileExt = fileName.substring(fileName.lastIndexOf('.'));
                String timestamp = String.valueOf(System.currentTimeMillis());
                finalFileName = fileNameWithoutExt + "_" + timestamp + fileExt;
                finalFilePath = videoUploadPath + File.separator + finalFileName;
                finalFile = new File(finalFilePath);
                counter++;
                if (counter > 10) break; // 防止无限循环
            }
            
            try (FileOutputStream fos = new FileOutputStream(finalFile)) {
                // 按索引顺序合并（直接按索引顺序读取，不依赖listFiles的顺序）
                for (int i = 0; i < expectedChunks; i++) {
                    File chunk = new File(dir, i + ".part");
                    
                    if (!chunk.exists()) {
                        return "{\"error\": \"分块文件不存在：" + chunk.getName() + "\"}";
                    }
                    
                    // 添加重试机制，处理文件被占用的情况
                    int retryCount = 0;
                    boolean readSuccess = false;
                    
                    while (retryCount < 3 && !readSuccess) {
                        try {
                            try (FileInputStream fis = new FileInputStream(chunk)) {
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = fis.read(buffer)) != -1) {
                                    fos.write(buffer, 0, bytesRead);
                                }
                            }
                            readSuccess = true;
                        } catch (java.io.IOException e) {
                            if (e.getMessage().contains("另一个程序正在使用此文件")) {
                                retryCount++;
                                Thread.sleep(1000); // 等待1秒后重试
                            } else {
                                throw e;
                            }
                        }
                    }
                    
                    if (!readSuccess) {
                        return "{\"error\": \"无法读取分块文件：" + chunk.getName() + "\"}";
                    }
                    
                    // 删除分块
                    chunk.delete();
                }
            }
            
            // 删除临时目录
            deleteDirectory(dir);
            
            // 创建视频对象
            Video video = new Video();
            video.setTitle(fileName.substring(0, fileName.lastIndexOf('.')));
            video.setDescription("");
            video.setFileName(fileName);
            video.setFileSize(finalFile.length());
            video.setFilePath(finalFilePath);
            video.setUrl("/video/stream/" + finalFileName);
            video.setUploader("anonymous");
            
            // 保存视频信息
            videoService.createVideo(video);
            
            return "{\"success\": true, \"fileName\": \"" + fileName + "\", \"url\": \"/video/stream/" + finalFileName + "\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"合并分块失败\"}";
        }
    }

    // 查找视频文件
    @PostMapping("/find")
    @ResponseBody
    public String findVideos(@RequestBody java.util.Map<String, Object> request) {
        try {
            String folder = (String) request.get("folder");
            java.util.Map<String, Object> treeData = (java.util.Map<String, Object>) request.get("treeData");
            
            System.out.println("=== 开始查找视频文件 ===");
            System.out.println("文件夹: " + folder);
            System.out.println("树数据是否为空: " + (treeData == null));
            if (treeData != null) {
                System.out.println("树数据包含键: " + treeData.keySet());
            }
            
            java.util.List<java.util.Map<String, Object>> foundVideos = new java.util.ArrayList<>();
            java.util.List<java.util.Map<String, Object>> missingVideos = new java.util.ArrayList<>();
            
            // 检查上传目录中的视频文件
            File uploadDir = new File(videoUploadPath);
            System.out.println("上传目录路径: " + uploadDir.getAbsolutePath());
            System.out.println("上传目录是否存在: " + uploadDir.exists());
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
                System.out.println("已创建上传目录");
            }
            
            // 获取上传目录中的视频文件
            File[] tempVideos = uploadDir.listFiles((dir, name) -> {
                String lowerName = name.toLowerCase();
                return lowerName.endsWith(".mp4") || lowerName.endsWith(".avi") || lowerName.endsWith(".mov") || lowerName.endsWith(".wmv") || lowerName.endsWith(".mkv");
            });
            
            // 如果上传目录中没有视频文件，尝试在父目录的video-uploads中查找
            if (tempVideos == null || tempVideos.length == 0) {
                String parentPath = uploadDir.getParent();
                File parentUploadDir = new File(parentPath + File.separator + "video-uploads");
                System.out.println("尝试在父目录查找视频文件: " + parentUploadDir.getAbsolutePath());
                System.out.println("父目录是否存在: " + parentUploadDir.exists());
                if (parentUploadDir.exists()) {
                    tempVideos = parentUploadDir.listFiles((dir, name) -> {
                        String lowerName = name.toLowerCase();
                        return lowerName.endsWith(".mp4") || lowerName.endsWith(".avi") || lowerName.endsWith(".mov") || lowerName.endsWith(".wmv") || lowerName.endsWith(".mkv");
                    });
                    System.out.println("父目录中找到的视频文件数量: " + (tempVideos != null ? tempVideos.length : 0));
                }
            }
            
            // 使变量成为 effectively final
            final File[] existingVideos = tempVideos;
            
            System.out.println("找到的现有视频文件数量: " + (existingVideos != null ? existingVideos.length : 0));
            if (existingVideos != null) {
                for (File videoFile : existingVideos) {
                    System.out.println("现有视频文件: " + videoFile.getName() + " (" + videoFile.length() + " bytes)");
                }
            }
            
            // 处理树数据
            if (treeData != null) {
                // 检查是否是标准格式（elements 数组）
                if (treeData.containsKey("elements")) {
                    java.util.List<java.util.Map<String, Object>> elements = (java.util.List<java.util.Map<String, Object>>) treeData.get("elements");
                    System.out.println("处理元素数量: " + elements.size());
                    
                    elements.forEach(element -> {
                        String nodeName = (String) element.get("name");
                        String fileName = (String) element.get("fileName");
                        
                        System.out.println("处理节点: " + nodeName + ", 文件名: " + fileName);
                        
                        // 如果没有fileName，尝试根据nodeName生成
                        if (fileName == null || fileName.isEmpty()) {
                            fileName = nodeName + ".mp4"; // 默认使用.mp4扩展名
                            System.out.println("自动生成文件名: " + fileName);
                        }
                        
                        // 查找视频文件
                        boolean found = false;
                        if (existingVideos != null) {
                            for (File videoFile : existingVideos) {
                                if (videoFile.getName().equals(fileName)) {
                                    // 找到视频文件
                                    java.util.Map<String, Object> foundVideo = new java.util.HashMap<>();
                                    foundVideo.put("nodeName", nodeName);
                                    foundVideo.put("fileName", videoFile.getName());
                                    foundVideo.put("url", "/video/stream/" + videoFile.getName());
                                    foundVideos.add(foundVideo);
                                    found = true;
                                    System.out.println("找到视频: " + videoFile.getName());
                                    break;
                                }
                            }
                        }
                        
                        if (!found) {
                            // 视频文件不存在
                            java.util.Map<String, Object> missingVideo = new java.util.HashMap<>();
                            missingVideo.put("nodeName", nodeName);
                            missingVideo.put("fileName", fileName);
                            missingVideos.add(missingVideo);
                            System.out.println("缺失视频: " + fileName);
                        }
                    });
                } 
                // 检查是否是JSON文件导入格式（metadata + nodes）
                else if (treeData.containsKey("metadata") && treeData.containsKey("nodes")) {
                    java.util.List<java.util.Map<String, Object>> nodes = (java.util.List<java.util.Map<String, Object>>) treeData.get("nodes");
                    System.out.println("处理节点数量: " + nodes.size());
                    
                    nodes.forEach(node -> {
                        String nodeName = (String) node.get("name");
                        String videoUrl = (String) node.get("videoUrl");
                        String fileName = null;
                        
                        // 从videoUrl中提取文件名
                        if (videoUrl != null) {
                            fileName = videoUrl.split("/")[videoUrl.split("/").length - 1];
                        }
                        
                        System.out.println("处理节点: " + nodeName + ", 视频URL: " + videoUrl + ", 提取的文件名: " + fileName);
                        
                        // 如果没有fileName，尝试根据nodeName生成
                        if (fileName == null || fileName.isEmpty()) {
                            fileName = nodeName + ".mp4"; // 默认使用.mp4扩展名
                            System.out.println("自动生成文件名: " + fileName);
                        }
                        
                        // 查找视频文件
                        boolean found = false;
                        if (existingVideos != null) {
                            for (File videoFile : existingVideos) {
                                if (videoFile.getName().equals(fileName)) {
                                    // 找到视频文件
                                    java.util.Map<String, Object> foundVideo = new java.util.HashMap<>();
                                    foundVideo.put("nodeName", nodeName);
                                    foundVideo.put("fileName", videoFile.getName());
                                    foundVideo.put("url", "/video/stream/" + videoFile.getName());
                                    foundVideos.add(foundVideo);
                                    found = true;
                                    System.out.println("找到视频: " + videoFile.getName());
                                    break;
                                }
                            }
                        }
                        
                        if (!found) {
                            // 视频文件不存在
                            java.util.Map<String, Object> missingVideo = new java.util.HashMap<>();
                            missingVideo.put("nodeName", nodeName);
                            missingVideo.put("fileName", fileName);
                            missingVideos.add(missingVideo);
                            System.out.println("缺失视频: " + fileName);
                        }
                    });
                } 
                // 检查是否是树形结构格式（root 节点）
                else if (treeData.containsKey("root")) {
                    System.out.println("处理树形结构视频树");
                    java.util.Map<String, Object> rootNode = (java.util.Map<String, Object>) treeData.get("root");
                    processTreeNode(rootNode, existingVideos, foundVideos, missingVideos);
                } else {
                    System.out.println("树数据格式未知，无法处理");
                }
            } else {
                System.out.println("树数据为null，无法处理");
            }
            
            // 构建响应
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", true);
            response.put("foundVideos", foundVideos);
            response.put("missingVideos", missingVideos);
            
            System.out.println("=== 查找完成 ===");
            System.out.println("找到视频数量: " + foundVideos.size());
            System.out.println("缺失视频数量: " + missingVideos.size());
            
            // 使用Jackson库将响应转换为JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"success\": false, \"error\": \"查找视频失败: " + e.getMessage() + "\"}";
        }
    }
    
    // 递归处理树形结构的视频节点
    private void processTreeNode(java.util.Map<String, Object> node, File[] existingVideos, 
                                java.util.List<java.util.Map<String, Object>> foundVideos, 
                                java.util.List<java.util.Map<String, Object>> missingVideos) {
        if (node == null) return;
        
        String nodeName = (String) node.get("name");
        String videoUrl = (String) node.get("videoUrl");
        String fileUrl = (String) node.get("fileUrl");
        String fileName = (String) node.get("fileName");
        
        // 确定视频URL
        String finalVideoUrl = videoUrl != null ? videoUrl : fileUrl;
        
        // 提取文件名
        if (fileName == null || fileName.isEmpty() && finalVideoUrl != null) {
            fileName = finalVideoUrl.split("/")[finalVideoUrl.split("/").length - 1];
        }
        
        System.out.println("处理节点: " + nodeName + ", 视频URL: " + finalVideoUrl + ", 文件名: " + fileName);
        
        // 如果有视频URL或文件名，查找视频文件
        if ((finalVideoUrl != null || fileName != null) && !fileName.isEmpty()) {
            // 如果没有fileName，尝试根据nodeName生成
            if (fileName == null || fileName.isEmpty()) {
                fileName = nodeName + ".mp4"; // 默认使用.mp4扩展名
                System.out.println("自动生成文件名: " + fileName);
            }
            
            // 查找视频文件
            boolean found = false;
            if (existingVideos != null) {
                for (File videoFile : existingVideos) {
                    if (videoFile.getName().equals(fileName)) {
                        // 找到视频文件
                        java.util.Map<String, Object> foundVideo = new java.util.HashMap<>();
                        foundVideo.put("nodeName", nodeName);
                        foundVideo.put("fileName", videoFile.getName());
                        foundVideo.put("url", "/video/stream/" + videoFile.getName());
                        foundVideos.add(foundVideo);
                        found = true;
                        System.out.println("找到视频: " + videoFile.getName());
                        break;
                    }
                }
            }
            
            if (!found) {
                // 视频文件不存在
                java.util.Map<String, Object> missingVideo = new java.util.HashMap<>();
                missingVideo.put("nodeName", nodeName);
                missingVideo.put("fileName", fileName);
                missingVideos.add(missingVideo);
                System.out.println("缺失视频: " + fileName);
            }
        }
        
        // 递归处理子节点
        if (node.containsKey("edges")) {
            java.util.List<java.util.Map<String, Object>> edges = (java.util.List<java.util.Map<String, Object>>) node.get("edges");
            if (edges != null) {
                for (java.util.Map<String, Object> edge : edges) {
                    if (edge.containsKey("targetNode")) {
                        java.util.Map<String, Object> targetNode = (java.util.Map<String, Object>) edge.get("targetNode");
                        processTreeNode(targetNode, existingVideos, foundVideos, missingVideos);
                    }
                }
            }
        } else if (node.containsKey("children")) {
            java.util.List<java.util.Map<String, Object>> children = (java.util.List<java.util.Map<String, Object>>) node.get("children");
            if (children != null) {
                for (java.util.Map<String, Object> child : children) {
                    processTreeNode(child, existingVideos, foundVideos, missingVideos);
                }
            }
        }
    }

    // 删除视频文件
    @DeleteMapping("/delete/{fileName}")
    @ResponseBody
    public java.util.Map<String, Object> deleteVideo(@PathVariable String fileName) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        
        try {
            if (!isSafeFileName(fileName)) {
                response.put("success", false);
                response.put("error", "非法的文件名");
                return response;
            }

            // 尝试在上传目录中查找视频文件
            File videoFile = new File(videoUploadPath + File.separator + fileName);
            
            // 如果文件不存在，尝试在父目录的video-uploads中查找
            if (!videoFile.exists()) {
                String parentPath = new File(videoUploadPath).getParent();
                File parentVideoFile = new File(parentPath + File.separator + "video-uploads" + File.separator + fileName);
                if (parentVideoFile.exists()) {
                    videoFile = parentVideoFile;
                }
            }
            
            if (!videoFile.exists()) {
                response.put("success", false);
                response.put("error", "视频文件不存在");
                return response;
            }
            
            boolean deleted = videoFile.delete();
            if (deleted) {
                response.put("success", true);
                response.put("message", "视频文件删除成功");
                System.out.println("删除视频文件成功: " + fileName);
            } else {
                response.put("success", false);
                response.put("error", "视频文件删除失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "删除失败：" + e.getMessage());
        }
        
        return response;
    }

    /** 分块上传 ID 由服务端以 UUID 生成，入参必须符合该形态，防止被拼进路径做目录穿越。 */
    private static boolean isValidUploadId(String uploadId) {
        return uploadId != null && uploadId.matches("[0-9a-fA-F-]{36}");
    }

    /** 只接受纯文件名（不含路径分隔符与上级目录），避免 ../ 之类穿越出上传目录。 */
    private static boolean isSafeFileName(String fileName) {
        return fileName != null
                && !fileName.isEmpty()
                && !fileName.contains("..")
                && !fileName.contains("/")
                && !fileName.contains("\\");
    }

}