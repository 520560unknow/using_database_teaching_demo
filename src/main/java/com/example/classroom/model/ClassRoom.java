package com.example.classroom.model;

import lombok.Data;

import java.util.Date;
@Data
public class ClassRoom {
    private String id;
    private String name;
    private String description;
    private String teacherId;
    private String videoTreeId;
    private String currentVideoId;

    private boolean enableDanmaku;
    private boolean enableComments;
    private boolean autoJump;
    private boolean recordSession;

    private String status; // created, active, paused, ended

    private Date createdTime;
    private Date startTime;
    private Date endTime;

}