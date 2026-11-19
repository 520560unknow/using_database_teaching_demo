package com.example.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class VideoUploadWebSocketHandler extends BinaryWebSocketHandler {

    @Value("${video.upload.path}")
    private String videoUploadPath;

    @Value("${video.temporary.path}")
    private String videoTemporaryPath;

    // 存储每个会话的上传状态
    private final Map<String, UploadState> uploadStates = new HashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 初始化上传状态
        UploadState state = new UploadState();
        uploadStates.put(session.getId(), state);
        System.out.println("视频上传WebSocket连接建立: " + session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        UploadState state = uploadStates.get(session.getId());
        if (state == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        ByteBuffer buffer = message.getPayload();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        // 如果是第一个消息，解析文件信息
        if (!state.isInitialized()) {
            try {
                // 解析文件信息（JSON格式）
                String infoStr = new String(data);
                VideoFileInfo info = VideoFileInfo.fromJson(infoStr);
                state.initialize(info);
                
                // 发送初始化成功响应
                session.sendMessage(new TextMessage("{\"status\": \"initialized\", \"fileId\": \"" + state.getFileId() + "\"}"));
            } catch (Exception e) {
                e.printStackTrace();
                session.sendMessage(new TextMessage("{\"error\": \"文件信息解析失败\"}"));
                session.close(CloseStatus.BAD_DATA);
            }
        } else {
            // 写入文件数据
            state.writeData(data);
            
            // 发送进度更新
            double progress = state.getProgress();
            session.sendMessage(new TextMessage("{\"status\": \"uploading\", \"progress\": " + progress + "}"));
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        UploadState state = uploadStates.get(session.getId());
        if (state == null) {
            return;
        }

        try {
            String payload = message.getPayload();
            
            // 检查是否是文件信息（JSON格式）
            if (payload.startsWith("{") && payload.endsWith("}") && !state.isInitialized()) {
                // 解析文件信息
                VideoFileInfo info = VideoFileInfo.fromJson(payload);
                state.initialize(info);
                
                // 发送初始化成功响应
                session.sendMessage(new TextMessage("{\"status\": \"initialized\", \"fileId\": \"" + state.getFileId() + "\"}"));
            } else if ("complete".equals(payload)) {
                // 完成上传
                state.completeUpload();
                
                // 构建视频访问URL
                String videoUrl = "/video/stream/" + state.getFileName();
                
                // 发送完成响应
                session.sendMessage(new TextMessage("{\"status\": \"completed\", \"fileId\": \"" + state.getFileId() + "\", \"fileName\": \"" + state.getOriginalFileName() + "\", \"url\": \"" + videoUrl + "\"}"));
            } else if ("cancel".equals(payload)) {
                // 取消上传
                state.cancelUpload();
                session.sendMessage(new TextMessage("{\"status\": \"cancelled\"}"));
                session.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                session.sendMessage(new TextMessage("{\"error\": \"处理文本消息失败\"}"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UploadState state = uploadStates.remove(session.getId());
        if (state != null && !state.isCompleted()) {
            // 清理未完成的上传
            try {
                state.cancelUpload();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("视频上传WebSocket连接关闭: " + session.getId());
    }

    // 上传状态类
    private class UploadState {
        private String fileId;
        private String originalFileName;
        private String fileName;
        private long fileSize;
        private long uploadedSize;
        private FileOutputStream outputStream;
        private boolean initialized;
        private boolean completed;

        public void initialize(VideoFileInfo info) throws IOException {
            this.fileId = UUID.randomUUID().toString();
            this.originalFileName = info.getFileName();
            this.fileName = fileId + "_" + originalFileName;
            this.fileSize = info.getFileSize();
            this.uploadedSize = 0;

            // 确保上传目录存在
            File uploadDir = new File(videoUploadPath);
            if (!uploadDir.isAbsolute()) {
                String projectRoot = System.getProperty("user.dir");
                videoUploadPath = projectRoot + File.separator + videoUploadPath;
            }

            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            // 创建文件输出流
            String filePath = videoUploadPath + File.separator + fileName;
            this.outputStream = new FileOutputStream(filePath);
            this.initialized = true;
            this.completed = false;

            System.out.println("开始上传视频: " + originalFileName + " (" + fileSize + " bytes)");
        }

        public void writeData(byte[] data) throws IOException {
            if (!initialized || completed) {
                return;
            }

            outputStream.write(data);
            uploadedSize += data.length;
        }

        public void completeUpload() throws IOException {
            if (!initialized || completed) {
                return;
            }

            outputStream.close();
            completed = true;
            System.out.println("视频上传完成: " + originalFileName);
        }

        public void cancelUpload() {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // 删除未完成的文件
            if (fileName != null) {
                File file = new File(videoUploadPath + File.separator + fileName);
                if (file.exists()) {
                    file.delete();
                }
            }

            completed = true;
            System.out.println("视频上传取消: " + originalFileName);
        }

        public double getProgress() {
            if (fileSize == 0) {
                return 0;
            }
            return (double) uploadedSize / fileSize * 100;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public boolean isCompleted() {
            return completed;
        }

        public String getFileId() {
            return fileId;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }

        public String getFileName() {
            return fileName;
        }
    }

    // 视频文件信息类
    private static class VideoFileInfo {
        private String fileName;
        private long fileSize;

        public String getFileName() {
            return fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public static VideoFileInfo fromJson(String json) {
            VideoFileInfo info = new VideoFileInfo();
            try {
                // 移除首尾的大括号和引号
                json = json.trim();
                if (json.startsWith("{") && json.endsWith("}")) {
                    json = json.substring(1, json.length() - 1);
                }
                
                // 分割键值对
                String[] pairs = json.split(",");
                for (String pair : pairs) {
                    String[] keyValue = pair.split(":");
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().replaceAll("\"", "");
                        String value = keyValue[1].trim().replaceAll("\"", "");
                        
                        System.out.println("解析键值对: " + key + " = " + value); // 添加日志
                        
                        if ("fileName".equals(key)) {
                            info.fileName = value;
                        } else if ("fileSize".equals(key)) {
                            try {
                                info.fileSize = Long.parseLong(value);
                                System.out.println("文件大小解析成功: " + info.fileSize); // 添加日志
                            } catch (NumberFormatException e) {
                                System.out.println("文件大小解析失败: " + value); // 添加日志
                                info.fileSize = 0;
                            }
                        }
                    }
                }
                
                System.out.println("解析结果 - 文件名: " + info.fileName + ", 文件大小: " + info.fileSize); // 添加日志
                
            } catch (Exception e) {
                System.out.println("文件信息解析异常: " + e.getMessage()); // 添加日志
                e.printStackTrace();
            }
            return info;
        }
    }
}
