package com.example.classroom.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
@Data
public class Comment {
    private String id;
    private String classroomId;
    private String content;
    private String studentId;
    private String studentName;
    private boolean isTeacher;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sendTime;

    // 构造方法
    public Comment() {
    }

    public Comment(String id, String classroomId, String content, String studentId,
                   String studentName, boolean isTeacher, Date sendTime) {
        this.id = id;
        this.classroomId = classroomId;
        this.content = content;
        this.studentId = studentId;
        this.studentName = studentName;
        this.isTeacher = isTeacher;
        this.sendTime = sendTime;
    }



    public boolean isTeacher() {
        return isTeacher;
    }

    public void setTeacher(boolean teacher) {
        isTeacher = teacher;
    }



    @Override
    public String toString() {
        return "Comment{" +
                "id='" + id + '\'' +
                ", classroomId='" + classroomId + '\'' +
                ", content='" + content + '\'' +
                ", studentId='" + studentId + '\'' +
                ", studentName='" + studentName + '\'' +
                ", isTeacher=" + isTeacher +
                ", sendTime=" + sendTime +
                '}';
    }
}