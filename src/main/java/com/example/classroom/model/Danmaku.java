package com.example.classroom.model;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class Danmaku {
    private Integer id;
    private Integer vid;
    private Integer uid;
    private String content;
    private Integer fontsize;
    private Integer mode;
    private String color;
    private Double timePoint;
    private Integer state;
    private Date createDate;
    
    // 兼容旧的课堂弹幕
    private String classroomId;
    private boolean teacher;
    private String teacherId;
    private String teacherName;
    private String studentId;
    private String studentName;
    private Date sendTime;
    private int videoTime;
    private List<String> keywords;
}