package com.example.classroom.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 学生会话注册表：按课堂保存学生参与记录（内存）。
 * 原为 TeacherService 内部的 studentSessions 字段，拆出后课堂统计仍由 TeacherService 汇总。
 */
@Component
public class StudentSessionRegistry {

    private final Map<String, List<Map<String, Object>>> studentSessions = new HashMap<>();

    /** 课堂开始时为该课堂生成演示用的学生参与记录（已存在则不重复生成）。 */
    public void ensureInitialized(String classroomId) {
        if (studentSessions.containsKey(classroomId)) {
            return;
        }

        List<Map<String, Object>> sessions = new ArrayList<>();
        Random random = new Random(classroomId.hashCode());

        // 创建15-30个学生会话
        int studentCount = 15 + random.nextInt(16);
        String[] studentNames = {"张三", "李四", "王五", "赵六", "钱七", "孙八", "周九", "吴十", "郑十一", "王十二"};
        String[] studentIds = {"stu_001", "stu_002", "stu_003", "stu_004", "stu_005", "stu_006", "stu_007", "stu_008", "stu_009", "stu_010"};

        for (int i = 0; i < studentCount; i++) {
            Map<String, Object> session = new HashMap<>();
            session.put("studentId", studentIds[i % studentIds.length]);
            session.put("studentName", studentNames[i % studentNames.length] + (i > 9 ? "_" + (i + 1) : ""));
            session.put("joinTime", new Date(System.currentTimeMillis() - random.nextInt(3600000))); // 1小时内加入
            session.put("watchTime", 10 + random.nextInt(110)); // 10-120分钟
            session.put("completed", random.nextBoolean()); // 50%完成率
            session.put("lastActive", new Date());
            sessions.add(session);
        }

        studentSessions.put(classroomId, sessions);
        System.out.println("初始化了 " + sessions.size() + " 个学生会话记录");
    }

    public List<Map<String, Object>> get(String classroomId) {
        return studentSessions.getOrDefault(classroomId, new ArrayList<>());
    }

    public boolean contains(String classroomId) {
        return studentSessions.containsKey(classroomId);
    }

    public void add(String classroomId, Map<String, Object> session) {
        studentSessions.computeIfAbsent(classroomId, k -> new ArrayList<>()).add(session);
    }

    public void updateWatchTime(String classroomId, String studentId, int watchTime) {
        List<Map<String, Object>> sessions = studentSessions.get(classroomId);
        if (sessions == null) {
            return;
        }
        for (Map<String, Object> session : sessions) {
            if (studentId.equals(session.get("studentId"))) {
                session.put("watchTime", watchTime);
                session.put("lastActive", new Date());
                break;
            }
        }
    }

    public void markCompleted(String classroomId, String studentId) {
        List<Map<String, Object>> sessions = studentSessions.get(classroomId);
        if (sessions == null) {
            return;
        }
        for (Map<String, Object> session : sessions) {
            if (studentId.equals(session.get("studentId"))) {
                session.put("completed", true);
                break;
            }
        }
    }
}
