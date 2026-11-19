package com.example.classroom.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Date;

@Component
public class TempFileCleanupTask {

    @Autowired
    private VideoAssetService videoService;

    @Value("${video.temporary.path}")
    private String videoTemporaryPath;

    // 每小时执行一次清理任务
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredFiles() {
        System.out.println("开始执行临时文件清理任务: " + new Date());

        try {
            // 清理过期的临时视频
            videoService.cleanupExpiredTemporaryVideos();

            // 清理临时目录中未被引用的文件
            cleanupOrphanedFiles();

            System.out.println("临时文件清理任务执行完成: " + new Date());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("临时文件清理任务执行失败: " + e.getMessage());
        }
    }

    // 清理临时目录中未被引用的文件
    private void cleanupOrphanedFiles() {
        File tempDir = new File(videoTemporaryPath);
        if (!tempDir.exists()) {
            return;
        }

        File[] files = tempDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile()) {
                // 检查文件是否被引用
                boolean isReferenced = false;
                for (com.example.classroom.model.Video video : videoService.getAllTemporaryVideos()) {
                    if (video.getFilePath() != null && video.getFilePath().equals(file.getAbsolutePath())) {
                        isReferenced = true;
                        break;
                    }
                }

                // 如果文件未被引用且超过24小时，则删除
                if (!isReferenced && file.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000) {
                    if (file.delete()) {
                        System.out.println("删除未被引用的临时文件: " + file.getName());
                    } else {
                        System.err.println("删除未被引用的临时文件失败: " + file.getName());
                    }
                }
            }
        }
    }
}
