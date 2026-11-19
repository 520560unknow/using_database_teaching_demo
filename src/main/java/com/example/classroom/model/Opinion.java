package com.example.classroom.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class Opinion {
    private String id;
    private String classroomId;
    private String studentId;
    private String studentName;
    private String title;
    private String content;
    private boolean anonymous;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sendTime;

    public Opinion() {
    }

    public Opinion(String id, String classroomId, String studentId, String studentName,
                   String title, String content, boolean anonymous, Date sendTime) {
        this.id = id;
        this.classroomId = classroomId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.title = title;
        this.content = content;
        this.anonymous = anonymous;
        this.sendTime = sendTime;
    }

    public String getDisplayName() {
        return anonymous ? "匿名学生" : (studentName != null ? studentName : studentId);
    }

    @Override
    public String toString() {
        return "Opinion{" +
                "id='" + id + '\'' +
                ", classroomId='" + classroomId + '\'' +
                ", studentId='" + studentId + '\'' +
                ", studentName='" + studentName + '\'' +
                ", title='" + title + '\'' +
                ", anonymous=" + anonymous +
                ", sendTime=" + sendTime +
                '}';
    }
}
