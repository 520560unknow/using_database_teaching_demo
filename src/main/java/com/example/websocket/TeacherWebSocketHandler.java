package com.example.websocket;

import com.example.classroom.model.Comment;
import com.example.classroom.model.Danmaku;
import com.example.classroom.service.CommentService;
import com.example.classroom.service.TeacherService;
import com.example.classroom.service.DanmakuService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Component
public class TeacherWebSocketHandler extends TextWebSocketHandler {

    private final TeacherService teacherService;
    private final DanmakuService danmakuService;
    private final CommentService commentService;
    private final ObjectMapper objectMapper;
    private final SessionRegistry sessionRegistry;

    @Value("${app.identity.default-teacher-id:teacher_001}")
    private String defaultTeacherId;

    @Autowired
    public TeacherWebSocketHandler(TeacherService teacherService,
                                   DanmakuService danmakuService,
                                   CommentService commentService,
                                   ObjectMapper objectMapper,
                                   SessionRegistry sessionRegistry) {
        this.teacherService = teacherService;
        this.danmakuService = danmakuService;
        this.commentService = commentService;
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String classroomId = extractClassroomId(session);
        if (classroomId != null) {
            sessionRegistry.addTeacher(classroomId, session);
            System.out.println("Teacher connected to classroom: " + classroomId);

            Map<String, Object> welcomeMsg = new HashMap<>();
            welcomeMsg.put("type", "WELCOME");
            welcomeMsg.put("message", "教师控制台连接成功");
            welcomeMsg.put("classroomId", classroomId);
            welcomeMsg.put("source", "system");
            welcomeMsg.put("timestamp", System.currentTimeMillis());

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMsg)));
            sendOnlineStudentsInfo(classroomId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String classroomId = extractClassroomId(session);

        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");
            Map<String, Object> messageData = (Map<String, Object>) data.get("data");

            switch (type) {
                case "TEACHER_JOIN":
                    handleTeacherJoin(classroomId, messageData != null ? messageData : data);
                    break;
                case "START_CLASS":
                    handleStartClass(classroomId, messageData != null ? messageData : data);
                    break;
                case "SEND_TEACHER_DANMAKU":
                    handleSendTeacherDanmaku(classroomId, messageData != null ? messageData : data);
                    break;
                case "SEND_TEACHER_COMMENT":
                    handleSendTeacherComment(classroomId, messageData != null ? messageData : data);
                    break;
                case "VIDEO_STATUS":
                    handleVideoStatus(classroomId, messageData != null ? messageData : data);
                    break;
                case "VIDEO_JUMP":
                    handleVideoJump(classroomId, messageData != null ? messageData : data);
                    break;
                case "END_CLASS":
                    handleEndClass(classroomId, messageData != null ? messageData : data);
                    break;
                case "HEARTBEAT":
                    handleTeacherHeartbeat(classroomId);
                    break;
                case "REQUEST_STUDENTS":
                    handleRequestStudents(classroomId);
                    break;
                case "DANMAKU_RECEIVED":
                    handleStudentDanmakuReceived(classroomId, messageData != null ? messageData : data);
                    break;
                case "BATCH_MESSAGES":
                    handleBatchMessages(classroomId, data);
                    break;
                case "TOGGLE_STUDENT_DANMAKU":
                case "TOGGLE_STUDENT_COMMENTS":
                case "TOGGLE_STUDENT_ALL_MESSAGES":
                case "DISABLE_STUDENT_MESSAGES":
                    handleToggleStudentMessages(classroomId, type, messageData != null ? messageData : data);
                    break;
                default:
                    Map<String, Object> errorMsg = new HashMap<>();
                    errorMsg.put("type", "ERROR");
                    errorMsg.put("message", "未知的消息类型: " + type);
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
                    break;
            }
        } catch (Exception e) {
            System.err.println("教师消息处理异常: " + e.getMessage());
            Map<String, Object> errorMsg = new HashMap<>();
            errorMsg.put("type", "ERROR");
            errorMsg.put("message", "消息处理失败");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
        }
    }

    private void handleBatchMessages(String classroomId, Map<String, Object> data) throws Exception {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) data.get("messages");
        if (messages != null && !messages.isEmpty()) {
            for (Map<String, Object> msg : messages) {
                String type = (String) msg.get("type");
                Map<String, Object> messageData = (Map<String, Object>) msg.get("data");
                switch (type) {
                    case "TEACHER_JOIN": handleTeacherJoin(classroomId, messageData != null ? messageData : msg); break;
                    case "START_CLASS": handleStartClass(classroomId, messageData != null ? messageData : msg); break;
                    case "SEND_TEACHER_DANMAKU": handleSendTeacherDanmaku(classroomId, messageData != null ? messageData : msg); break;
                    case "SEND_TEACHER_COMMENT": handleSendTeacherComment(classroomId, messageData != null ? messageData : msg); break;
                    case "VIDEO_STATUS": handleVideoStatus(classroomId, messageData != null ? messageData : msg); break;
                    case "VIDEO_JUMP": handleVideoJump(classroomId, messageData != null ? messageData : msg); break;
                    case "END_CLASS": handleEndClass(classroomId, messageData != null ? messageData : msg); break;
                    case "HEARTBEAT": handleTeacherHeartbeat(classroomId); break;
                    case "REQUEST_STUDENTS": handleRequestStudents(classroomId); break;
                    case "DANMAKU_RECEIVED": handleStudentDanmakuReceived(classroomId, messageData != null ? messageData : msg); break;
                    case "TOGGLE_STUDENT_DANMAKU": case "TOGGLE_STUDENT_COMMENTS": case "TOGGLE_STUDENT_ALL_MESSAGES": case "DISABLE_STUDENT_MESSAGES":
                        handleToggleStudentMessages(classroomId, type, messageData != null ? messageData : msg); break;
                    default: System.out.println("未知批量消息类型: " + type);
                }
            }
        }
    }

    private void handleToggleStudentMessages(String classroomId, String type, Map<String, Object> data) throws Exception {
        Map<String, Object> broadcastMsg = new HashMap<>();
        broadcastMsg.put("type", type);
        broadcastMsg.put("data", data);
        broadcastMsg.put("source", "teacher_control");
        broadcastMsg.put("timestamp", System.currentTimeMillis());

        WebSocketMessageDispatcher dispatcher = ApplicationContextHolder.getBean(WebSocketMessageDispatcher.class);
        if (dispatcher != null) {
            dispatcher.broadcastToClassroom(classroomId, broadcastMsg);
        }
    }

    private void handleSendTeacherComment(String classroomId, Map<String, Object> data) throws Exception {
        Map<String, Object> commentData = (Map<String, Object>) data.get("comment");

        if (commentData == null) {
            sendToTeacher(classroomId, "ERROR", "评论数据不能为空");
            return;
        }

        Comment comment = new Comment();
        comment.setId("comment_" + System.currentTimeMillis() + "_teacher");
        comment.setClassroomId(classroomId);
        comment.setContent((String) commentData.get("content"));
        comment.setTeacher(true);
        comment.setStudentId(null);
        comment.setStudentName(null);
        comment.setSendTime(new Date());

        Comment savedComment = commentService.addComment(comment);

        Map<String, Object> commentMap = new HashMap<>();
        commentMap.put("id", savedComment.getId());
        commentMap.put("classroomId", savedComment.getClassroomId());
        commentMap.put("content", savedComment.getContent());
        commentMap.put("isTeacher", savedComment.isTeacher());
        commentMap.put("teacher", true);
        commentMap.put("studentId", savedComment.getStudentId());
        commentMap.put("studentName", savedComment.getStudentName());
        String teacherName = (String) commentData.get("teacherName");
        commentMap.put("sender", teacherName != null ? teacherName : "教师");
        commentMap.put("sendTime", savedComment.getSendTime());

        // 统一分发：评论消息只广播一次
        Map<String, Object> broadcastMsg = new HashMap<>();
        broadcastMsg.put("type", "COMMENT_RECEIVED");
        broadcastMsg.put("data", commentMap);
        broadcastMsg.put("source", "comment");
        broadcastMsg.put("timestamp", System.currentTimeMillis());

        WebSocketMessageDispatcher dispatcher = ApplicationContextHolder.getBean(WebSocketMessageDispatcher.class);
        if (dispatcher != null) {
            dispatcher.broadcastToClassroom(classroomId, broadcastMsg);
        }

        // 确认给教师
        sendToTeacher(classroomId, "TEACHER_COMMENT_SENT", commentMap);
    }

    private void handleTeacherJoin(String classroomId, Map<String, Object> data) throws Exception {
        Map<String, Object> classroomStats = teacherService.getClassroomStats(classroomId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "CLASSROOM_STATUS");
        response.put("classroomId", classroomId);
        response.put("status", classroomStats.get("status"));
        response.put("stats", classroomStats);
        response.put("source", "system");
        response.put("timestamp", System.currentTimeMillis());

        sendToTeacher(classroomId, response);
    }

    private void handleStartClass(String classroomId, Map<String, Object> data) throws Exception {
        teacherService.startClass(classroomId);

        Map<String, Object> broadcastMsg = new HashMap<>();
        broadcastMsg.put("type", "CLASS_STARTED");
        broadcastMsg.put("classroomId", classroomId);
        broadcastMsg.put("source", "teacher_control");
        broadcastMsg.put("timestamp", System.currentTimeMillis());

        WebSocketMessageDispatcher dispatcher = ApplicationContextHolder.getBean(WebSocketMessageDispatcher.class);
        if (dispatcher != null) {
            dispatcher.broadcastToClassroom(classroomId, broadcastMsg);
        }

        sendToTeacher(classroomId, "CLASS_STARTED_CONFIRM", broadcastMsg);
    }

    private void handleSendTeacherDanmaku(String classroomId, Map<String, Object> data) throws Exception {
        Map<String, Object> danmakuData = (Map<String, Object>) data.get("danmaku");

        Danmaku danmaku = new Danmaku();
        danmaku.setClassroomId(classroomId);
        danmaku.setContent((String) danmakuData.get("content"));
        danmaku.setVideoTime(((Number) danmakuData.get("videoTime")).intValue());
        danmaku.setTeacher(true);
        danmaku.setTeacherId((String) data.getOrDefault("teacherId", defaultTeacherId));
        danmaku.setTeacherName((String) data.getOrDefault("teacherName", "教师"));
        danmaku.setSendTime(new Date());
        danmaku.setColor("#FF0000");

        danmakuService.addDanmaku(danmaku);

        Map<String, Object> broadcastMsg = new HashMap<>();
        broadcastMsg.put("type", "DANMAKU_RECEIVED");
        broadcastMsg.put("data", danmaku);
        broadcastMsg.put("source", "danmaku");
        broadcastMsg.put("timestamp", System.currentTimeMillis());

        WebSocketMessageDispatcher dispatcher = ApplicationContextHolder.getBean(WebSocketMessageDispatcher.class);
        if (dispatcher != null) {
            dispatcher.broadcastToClassroom(classroomId, broadcastMsg);
        }

        sendToTeacher(classroomId, "TEACHER_DANMAKU_SENT", danmakuData);
    }

    private void handleVideoStatus(String classroomId, Map<String, Object> data) throws Exception {
        Map<String, Object> broadcastMsg = new HashMap<>();
        broadcastMsg.put("type", "TEACHER_VIDEO_STATUS");
        broadcastMsg.put("data", data);
        broadcastMsg.put("source", "teacher_control");
        broadcastMsg.put("timestamp", System.currentTimeMillis());

        WebSocketMessageDispatcher dispatcher = ApplicationContextHolder.getBean(WebSocketMessageDispatcher.class);
        if (dispatcher != null) {
            dispatcher.broadcastToClassroom(classroomId, broadcastMsg);
        }
    }

    private void handleVideoJump(String classroomId, Map<String, Object> data) throws Exception {
        Map<String, Object> broadcastMsg = new HashMap<>();
        broadcastMsg.put("type", "TEACHER_VIDEO_JUMP");
        broadcastMsg.put("data", data);
        broadcastMsg.put("source", "teacher_control");
        broadcastMsg.put("timestamp", System.currentTimeMillis());

        WebSocketMessageDispatcher dispatcher = ApplicationContextHolder.getBean(WebSocketMessageDispatcher.class);
        if (dispatcher != null) {
            dispatcher.broadcastToClassroom(classroomId, broadcastMsg);
        }

        String videoNodeId = (String) data.get("videoNodeId");
        teacherService.updateCurrentVideo(classroomId, videoNodeId);
    }

    private void handleEndClass(String classroomId, Map<String, Object> data) throws Exception {
        teacherService.endClass(classroomId);

        Map<String, Object> broadcastMsg = new HashMap<>();
        broadcastMsg.put("type", "CLASS_ENDED");
        broadcastMsg.put("classroomId", classroomId);
        broadcastMsg.put("source", "teacher_control");
        broadcastMsg.put("timestamp", System.currentTimeMillis());

        WebSocketMessageDispatcher dispatcher = ApplicationContextHolder.getBean(WebSocketMessageDispatcher.class);
        if (dispatcher != null) {
            dispatcher.broadcastToClassroom(classroomId, broadcastMsg);
        }

        sendToTeacher(classroomId, "CLASS_ENDED_CONFIRM", broadcastMsg);
    }

    private void handleTeacherHeartbeat(String classroomId) {
        Map<String, Object> heartbeatData = new HashMap<>();
        heartbeatData.put("type", "TEACHER_HEARTBEAT");
        heartbeatData.put("classroomId", classroomId);
        heartbeatData.put("source", "system");
        heartbeatData.put("timestamp", System.currentTimeMillis());
        sendToTeacher(classroomId, heartbeatData);
    }

    private void handleRequestStudents(String classroomId) throws Exception {
        sendOnlineStudentsInfo(classroomId);
    }

    private void handleStudentDanmakuReceived(String classroomId, Map<String, Object> data) throws Exception {
        Map<String, Object> danmakuMsg = new HashMap<>();
        danmakuMsg.put("type", "STUDENT_DANMAKU");
        danmakuMsg.put("data", data);
        danmakuMsg.put("source", "danmaku");
        danmakuMsg.put("timestamp", System.currentTimeMillis());
        sendToTeacher(classroomId, danmakuMsg);
    }

    private void sendOnlineStudentsInfo(String classroomId) throws Exception {
        Map<String, WebSocketSession> students = sessionRegistry.getClassroomStudents(classroomId);

        Map<String, Object> studentInfo = new HashMap<>();
        studentInfo.put("type", "ONLINE_STUDENTS");
        studentInfo.put("classroomId", classroomId);
        studentInfo.put("onlineCount", students.size());
        studentInfo.put("studentIds", new ArrayList<>(students.keySet()));
        studentInfo.put("source", "system");
        studentInfo.put("timestamp", System.currentTimeMillis());

        WebSocketMessageDispatcher dispatcher = ApplicationContextHolder.getBean(WebSocketMessageDispatcher.class);
        if (dispatcher != null) {
            dispatcher.broadcastToClassroom(classroomId, studentInfo);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String classroomId = extractClassroomId(session);
        if (classroomId != null) {
            sessionRegistry.removeTeacher(classroomId, session);
            System.out.println("Teacher disconnected from classroom: " + classroomId);
        }
    }

    private String extractClassroomId(WebSocketSession session) {
        String uri = session.getUri().toString();
        if (uri.contains("/ws/teacher/")) {
            int startIndex = uri.indexOf("/ws/teacher/") + "/ws/teacher/".length();
            int endIndex = uri.indexOf("/", startIndex);
            if (endIndex == -1) {
                endIndex = uri.indexOf("?", startIndex);
            }
            if (endIndex == -1) {
                endIndex = uri.length();
            }
            return uri.substring(startIndex, endIndex);
        }
        return null;
    }

    public static void sendToTeacher(String classroomId, Map<String, Object> message) {
        ApplicationContextHolder.getBean(SessionRegistry.class).sendToTeacher(classroomId, message);
    }

    public static void sendToTeacher(String classroomId, String messageType, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", messageType);
        message.put("data", data);
        message.put("source", "system");
        message.put("timestamp", System.currentTimeMillis());
        sendToTeacher(classroomId, message);
    }

    public static WebSocketSession getTeacherSession(String classroomId) {
        return ApplicationContextHolder.getBean(SessionRegistry.class).getTeacherSession(classroomId);
    }
}
