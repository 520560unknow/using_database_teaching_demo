package com.example.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话注册表：统一管理教师端与学生端连接的生命周期与按课堂广播。
 * 消息类型、字段与广播目标保持不变，只是把原先散落在两个 Handler 里的静态会话表收敛到一处。
 */
@Component
public class SessionRegistry {

    /** 教师连接：classroomId -> 该课堂所有教师控制台会话 */
    private final Map<String, Set<WebSocketSession>> teacherSessions = new ConcurrentHashMap<>();

    /** 学生连接：classroomId -> (studentId -> session) */
    private final Map<String, Map<String, WebSocketSession>> studentSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public SessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ==================== 教师端 ====================

    public void addTeacher(String classroomId, WebSocketSession session) {
        teacherSessions.computeIfAbsent(classroomId,
                k -> Collections.newSetFromMap(new ConcurrentHashMap<WebSocketSession, Boolean>())).add(session);
    }

    public void removeTeacher(String classroomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = teacherSessions.get(classroomId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            teacherSessions.remove(classroomId, sessions);
        }
    }

    public WebSocketSession getTeacherSession(String classroomId) {
        Set<WebSocketSession> sessions = teacherSessions.get(classroomId);
        if (sessions == null) {
            return null;
        }
        for (WebSocketSession session : sessions) {
            if (session != null && session.isOpen()) {
                return session;
            }
        }
        return null;
    }

    public void sendToTeacher(String classroomId, Map<String, Object> message) {
        Set<WebSocketSession> sessions = teacherSessions.get(classroomId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            System.err.println("序列化教师消息失败，课堂ID: " + classroomId + ", 错误: " + e.getMessage());
            return;
        }
        TextMessage textMessage = new TextMessage(payload);

        for (WebSocketSession session : sessions) {
            if (session == null || !session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(textMessage);
            } catch (Exception e) {
                System.err.println("发送消息给教师失败，课堂ID: " + classroomId + ", 错误: " + e.getMessage());
                sessions.remove(session);
            }
        }

        if (sessions.isEmpty()) {
            teacherSessions.remove(classroomId, sessions);
        }
    }

    // ==================== 学生端 ====================

    public void addStudent(String classroomId, String studentId, WebSocketSession session) {
        studentSessions.computeIfAbsent(classroomId, k -> new ConcurrentHashMap<>()).put(studentId, session);
    }

    public void removeStudent(String classroomId, String studentId) {
        Map<String, WebSocketSession> sessions = studentSessions.get(classroomId);
        if (sessions == null) {
            return;
        }
        sessions.remove(studentId);
        if (sessions.isEmpty()) {
            studentSessions.remove(classroomId, sessions);
        }
    }

    public WebSocketSession getStudentSession(String classroomId, String studentId) {
        Map<String, WebSocketSession> sessions = studentSessions.get(classroomId);
        return sessions != null ? sessions.get(studentId) : null;
    }

    public Map<String, WebSocketSession> getClassroomStudents(String classroomId) {
        return studentSessions.getOrDefault(classroomId, new HashMap<>());
    }

    public int getOnlineStudentCount(String classroomId) {
        Map<String, WebSocketSession> sessions = studentSessions.get(classroomId);
        return sessions != null ? sessions.size() : 0;
    }

    public void broadcastToStudents(String classroomId, Map<String, Object> message) {
        Map<String, WebSocketSession> sessions = studentSessions.get(classroomId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            System.err.println("消息序列化失败: " + e.getMessage());
            return;
        }
        TextMessage textMessage = new TextMessage(messageJson);
        for (WebSocketSession session : sessions.values()) {
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (Exception e) {
                    System.err.println("发送学生消息失败: " + e.getMessage());
                }
            }
        }
    }
}
