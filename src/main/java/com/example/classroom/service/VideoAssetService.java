package com.example.classroom.service;

import com.example.classroom.model.Video;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class VideoAssetService {

    // 视频存储
    private ConcurrentHashMap<Integer, Video> videos = new ConcurrentHashMap<>();
    private AtomicInteger videoIdGenerator = new AtomicInteger(1);

    // 播放记录存储（用于防刷）
    private ConcurrentHashMap<String, Long> playRecords = new ConcurrentHashMap<>();

    // 临时文件存储
    private ConcurrentHashMap<String, Video> temporaryVideos = new ConcurrentHashMap<>();

    // 创建视频
    public Video createVideo(Video video) {
        int id = videoIdGenerator.getAndIncrement();
        video.setId(id);
        video.setUploadTime(new Date());
        video.setLastPlayTime(new Date());
        video.setViews(0);
        video.setLikes(0);
        video.setComments(0);
        video.setFavorites(0);
        videos.put(id, video);
        return video;
    }

    // 创建临时视频
    public Video createTemporaryVideo(Video video) {
        String fileName = video.getFileName();
        video.setId(videoIdGenerator.getAndIncrement());
        video.setUploadTime(new Date());
        video.setLastPlayTime(new Date());
        video.setViews(0);
        video.setLikes(0);
        video.setComments(0);
        video.setFavorites(0);
        video.setTemporary(true);
        video.setExpireTime(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)); // 24小时过期
        temporaryVideos.put(fileName, video);
        return video;
    }

    // 获取视频
    public Video getVideo(Integer id) {
        return videos.get(id);
    }

    // 获取临时视频
    public Video getTemporaryVideo(String fileName) {
        return temporaryVideos.get(fileName);
    }

    // 获取所有视频
    public List<Video> getAllVideos() {
        return new ArrayList<>(videos.values());
    }

    // 获取所有临时视频
    public List<Video> getAllTemporaryVideos() {
        return new ArrayList<>(temporaryVideos.values());
    }

    // 更新视频（通过ID）
    public Video updateVideo(Integer id, Video video) {
        Video existingVideo = videos.get(id);
        if (existingVideo != null) {
            if (video.getTitle() != null) existingVideo.setTitle(video.getTitle());
            if (video.getDescription() != null) existingVideo.setDescription(video.getDescription());
            if (video.getUrl() != null) existingVideo.setUrl(video.getUrl());
            if (video.getDuration() > 0) existingVideo.setDuration(video.getDuration());
            if (video.getCoverUrl() != null) existingVideo.setCoverUrl(video.getCoverUrl());
            videos.put(id, existingVideo);
        }
        return existingVideo;
    }
    
    // 更新视频（直接传入Video对象）
    public Video updateVideo(Video video) {
        if (video.isTemporary()) {
            // 更新临时视频
            temporaryVideos.put(video.getFileName(), video);
        } else {
            // 更新正式视频
            videos.put(video.getId(), video);
        }
        return video;
    }

    // 删除视频
    public boolean deleteVideo(Integer id) {
        Video video = videos.remove(id);
        if (video != null && video.getFilePath() != null) {
            File file = new File(video.getFilePath());
            if (file.exists()) {
                file.delete();
            }
            return true;
        }
        return false;
    }

    // 删除临时视频
    public boolean deleteTemporaryVideo(String fileName) {
        Video video = temporaryVideos.remove(fileName);
        if (video != null && video.getFilePath() != null) {
            File file = new File(video.getFilePath());
            if (file.exists()) {
                file.delete();
            }
            return true;
        }
        return false;
    }

    // 清理过期的临时视频
    public void cleanupExpiredTemporaryVideos() {
        long now = System.currentTimeMillis();
        List<String> expiredFiles = new ArrayList<>();

        for (Video video : temporaryVideos.values()) {
            if (video.getExpireTime() != null && video.getExpireTime().getTime() < now) {
                expiredFiles.add(video.getFileName());
            }
        }

        for (String fileName : expiredFiles) {
            deleteTemporaryVideo(fileName);
        }
    }

    // 增加播放量（登录用户）
    public boolean incrementViewsForUser(Integer videoId, String userId) {
        Video video = videos.get(videoId);
        if (video == null) {
            video = getTemporaryVideoByVideoId(videoId);
            if (video == null) return false;
        }

        String key = userId + "_" + videoId;
        long now = System.currentTimeMillis();
        Long lastPlayTime = playRecords.get(key);

        // 30秒内防刷
        if (lastPlayTime == null || now - lastPlayTime > 30 * 1000) {
            video.setViews(video.getViews() + 1);
            video.setLastPlayTime(new Date());
            playRecords.put(key, now);
            if (video.isTemporary()) {
                temporaryVideos.put(video.getFileName(), video);
            } else {
                videos.put(videoId, video);
            }
            return true;
        }

        return false;
    }

    // 增加播放量（游客）
    public boolean incrementViewsForVisitor(Integer videoId) {
        Video video = videos.get(videoId);
        if (video == null) {
            video = getTemporaryVideoByVideoId(videoId);
            if (video == null) return false;
        }

        video.setViews(video.getViews() + 1);
        video.setLastPlayTime(new Date());
        if (video.isTemporary()) {
            temporaryVideos.put(video.getFileName(), video);
        } else {
            videos.put(videoId, video);
        }
        return true;
    }

    // 增加点赞数
    public boolean incrementLikes(Integer videoId) {
        Video video = videos.get(videoId);
        if (video == null) {
            video = getTemporaryVideoByVideoId(videoId);
            if (video == null) return false;
        }

        video.setLikes(video.getLikes() + 1);
        if (video.isTemporary()) {
            temporaryVideos.put(video.getFileName(), video);
        } else {
            videos.put(videoId, video);
        }
        return true;
    }

    // 增加评论数
    public boolean incrementComments(Integer videoId) {
        Video video = videos.get(videoId);
        if (video == null) {
            video = getTemporaryVideoByVideoId(videoId);
            if (video == null) return false;
        }

        video.setComments(video.getComments() + 1);
        if (video.isTemporary()) {
            temporaryVideos.put(video.getFileName(), video);
        } else {
            videos.put(videoId, video);
        }
        return true;
    }

    // 增加收藏数
    public boolean incrementFavorites(Integer videoId) {
        Video video = videos.get(videoId);
        if (video == null) {
            video = getTemporaryVideoByVideoId(videoId);
            if (video == null) return false;
        }

        video.setFavorites(video.getFavorites() + 1);
        if (video.isTemporary()) {
            temporaryVideos.put(video.getFileName(), video);
        } else {
            videos.put(videoId, video);
        }
        return true;
    }

    // 根据视频ID获取临时视频
    private Video getTemporaryVideoByVideoId(Integer videoId) {
        for (Video video : temporaryVideos.values()) {
            if (video.getId().equals(videoId)) {
                return video;
            }
        }
        return null;
    }
}
