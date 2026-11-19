package com.example.classroom.model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "video")
public class Video {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "title")
    private String title;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "file_path")
    private String filePath;
    
    @Column(name = "file_name")
    private String fileName;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "duration")
    private Integer duration;
    
    @Column(name = "upload_time")
    private Date uploadTime;
    
    @Column(name = "is_temporary")
    private Boolean isTemporary;
    

    
    // 新增字段：过期时间（临时视频使用）
    @Column(name = "expire_time")
    private Date expireTime;
    
    // 新增字段：播放统计
    @Column(name = "views")
    private Integer views = 0;
    
    @Column(name = "likes")
    private Integer likes = 0;
    
    @Column(name = "comments")
    private Integer comments = 0;
    
    @Column(name = "favorites")
    private Integer favorites = 0;
    
    @Column(name = "last_play_time")
    private Date lastPlayTime;
    
    @Column(name = "url")
    private String url;
    
    @Column(name = "cover_url")
    private String coverUrl;
    
    @Column(name = "uploader")
    private String uploader;
    
    // 构造方法
    public Video() {
        this.uploadTime = new Date();
        this.isTemporary = false;
        this.views = 0;
        this.likes = 0;
        this.comments = 0;
        this.favorites = 0;
    }
    
    // Getter和Setter方法
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public Integer getDuration() {
        return duration;
    }
    
    public void setDuration(Integer duration) {
        this.duration = duration;
    }
    
    public Date getUploadTime() {
        return uploadTime;
    }
    
    public void setUploadTime(Date uploadTime) {
        this.uploadTime = uploadTime;
    }
    
    public Boolean getIsTemporary() {
        return isTemporary;
    }
    
    public void setIsTemporary(Boolean temporary) {
        isTemporary = temporary;
    }
    

    
    public Date getExpireTime() {
        return expireTime;
    }
    
    public void setExpireTime(Date expireTime) {
        this.expireTime = expireTime;
    }
    
    public Integer getViews() {
        return views;
    }
    
    public void setViews(Integer views) {
        this.views = views;
    }
    
    public Integer getLikes() {
        return likes;
    }
    
    public void setLikes(Integer likes) {
        this.likes = likes;
    }
    
    public Integer getComments() {
        return comments;
    }
    
    public void setComments(Integer comments) {
        this.comments = comments;
    }
    
    public Integer getFavorites() {
        return favorites;
    }
    
    public void setFavorites(Integer favorites) {
        this.favorites = favorites;
    }
    
    public Date getLastPlayTime() {
        return lastPlayTime;
    }
    
    public void setLastPlayTime(Date lastPlayTime) {
        this.lastPlayTime = lastPlayTime;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getCoverUrl() {
        return coverUrl;
    }
    
    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }
    
    public String getUploader() {
        return uploader;
    }
    
    public void setUploader(String uploader) {
        this.uploader = uploader;
    }
    
    // 兼容性方法
    public Boolean isTemporary() {
        return isTemporary;
    }
    
    public void setTemporary(Boolean temporary) {
        isTemporary = temporary;
    }
    

}