package com.example.classroom.model;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class Student {
    private String id;
    private String name;
    private String classroomId;
    private StudentStatus status;
    private LocalDateTime joinTime;
    private LocalDateTime lastActivityTime; // 改为 LocalDateTime

    public Student() {
    }

    // getter 和 setter

    public enum StudentStatus {
        ONLINE, OFFLINE, AWAY
    }
}