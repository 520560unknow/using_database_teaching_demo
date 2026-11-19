package com.example.websocket;

import com.example.classroom.model.Comment;
import com.example.classroom.model.Danmaku;
import com.example.classroom.model.Opinion;
import com.example.classroom.service.CommentService;
import com.example.classroom.service.DanmakuService;
import com.example.classroom.service.OpinionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StudentWebSocketHandler extends TextWebSocketHandler {

    private final DanmakuService danmakuService;
    private final CommentService commentService;
    private final OpinionService opinionService;
    private final ObjectMapper objectMapper;
    private final SessionRegistry sessionRegistry;

    private static final ConcurrentHashMap<String, Map<String, Boolean>> classroomMessageStatus =
            new ConcurrentHashMap<>();

    @Autowired
    public StudentWebSocketHandler(DanmakuService danmakuService,
                                   CommentService commentService,
                                   OpinionService opinionService,
                                   ObjectMapper objectMapper,
                                   SessionRegistry sessionRegistry) {
        this.danmakuService = danmakuService;
        this.commentService = commentService;
        this.opinionService = opinionService;
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String classroomId = extractClassroomId(session);
        String studentId = extractStudentId(session);

        if (classroomId != null && studentId != null) {
            sessionRegistry.addStudent(classroomId, studentId, session);

            System.out.println("Student " + studentId + " connected to classroom: " + classroomId);

            Map<String, Object> welcomeMsg = new HashMap<>();
            welcomeMsg.put("type", "WELCOME");
            welcomeMsg.put("message", "学生连接成功");
            welcomeMsg.put("classroomId", classroomId);
            welcomeMsg.put("studentId", studentId);
            welcomeMsg.put("source", "system");

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMsg)));
            notifyTeacherStudentJoined(classroomId, studentId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String classroomId = extractClassroomId(session);
        String studentId = extractStudentId(session);

        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");
            Map<String, Object> messageData = (Map<String, Object>) data.get("data");

            switch (type) {
                case "SEND_DANMAKU":
                    handleStudentSendDanmaku(classroomId, studentId, messageData != null ? messageData : data);
                    break;
                case "SEND_COMMENT":
                    handleStudentSendComment(classroomId, studentId, messageData != null ? messageData : data);
                    break;
                case "SEND_OPINION":
                    handleStudentSendOpinion(classroomId, studentId, messageData != null ? messageData : data);
                    break;
                case "VIDEO_STATUS":
                    handleStudentVideoStatus(classroomId, studentId, messageData != null ? messageData : data);
                    break;
                case "HEARTBEAT":
                    handleHeartbeat(classroomId, studentId);
                    break;
                case "STUDENT_JOIN":
                    handleStudentJoin(classroomId, studentId, session);
                    break;
                case "TEACHER_COMMENT":
                    handleTeacherComment(session, classroomId, messageData != null ? messageData : data);
                    break;
                case "COMMENT_RECEIVED":
                    handleCommentReceived(session, classroomId, messageData != null ? messageData : data);
                    break;
                case "BATCH_MESSAGES":
                    handleBatchMessages(classroomId, studentId, data);
                    break;
                default:
                    System.out.println("未知消息类型: " + type);
            }
        } catch (Exception e) {
            System.err.println("学生消息处理异常: " + e.getMessage());
            Map<String, Object> errorMsg = new HashMap<>();
            errorMsg.put("type", "ERROR");
            errorMsg.put("message", "消息处理失败");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
        }
    }

    private void handleBatchMessages(String classroomId, String studentId, Map<String, Object> data) throws Exception {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) data.get("messages");
        if (messages != null && !messages.isEmpty()) {
            for (Map<String, Object> msg : messages) {
                String type = (String) msg.get("type");
                Map<String, Object> messageData = (Map<String, Object>) msg.get("data");
                switch (type) {
                    case "SEND_DANMAKU": handleStudentSendDanmaku(classroomId, studentId, messageData != null ? messageData : msg); break;
                    case "SEND_COMMENT": handleStudentSendComment(classroomId, studentId, messageData != null ? messageData : msg); break;
                    case "VIDEO_STATUS": handleStudentVideoStatus(classroomId, studentId, messageData != null ? messageData : msg); break;
                    case "HEARTBEAT": handleHeartbeat(classroomId, studentId); break;
                    default: System.out.println("未知批量消息类型: " + type);
                }
            }
        }
    }

    private void handleTeacherComment(WebSocketSession session, String classroomId, Map<String, Object> data) throws Exception {
        Map<String, Object> commentData = (Map<String, Object>) data.get("comment");
        if (commentData == null) return;
        broadcastToClassroom(classroomId, "NEW_COMMENT", commentData, "comment");
    }

    private void handleCommentReceived(WebSocketSession session, String classroomId, Map<String, Object> data) throws Exception {
        Map<String, Object> commentData = (Map<String, Object>) data.get("comment");
        if (commentData == null) return;
        Map<String, Object> response = new HashMap<>();
        response.put("type", "COMMENT_RECEIVED");
        response.put("data", commentData);
        response.put("source", "comment");
        response.put("timestamp", System.currentTimeMillis());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleStudentSendDanmaku(String classroomId, String studentId, Map<String, Object> data) throws Exception {
        if (isMessageDisabled(classroomId, "danmaku")) {
            sendErrorToStudent(classroomId, studentId, "教师已禁止发送弹幕");
            return;
        }

        Map<String, Object> danmakuData = (Map<String, Object>) data.get("danmaku");

        Danmaku danmaku = new Danmaku();
        danmaku.setClassroomId(classroomId);
        danmaku.setContent((String) danmakuData.get("content"));
        danmaku.setVideoTime(((Number) danmakuData.get("videoTime")).intValue());
        danmaku.setColor((String) danmakuData.getOrDefault("color", "#FFFFFF"));
        danmaku.setTeacher(false);
        danmaku.setStudentId(studentId);
        danmaku.setStudentName((String) danmakuData.getOrDefault("studentName", "学生" + studentId));
        danmaku.setSendTime(new Date());

        Danmaku savedDanmaku = danmakuService.addDanmaku(danmaku);

        // 广播弹幕给所有学生
        Map<String, Object> broadcastMsg = new HashMap<>();
        broadcastMsg.put("type", "DANMAKU_RECEIVED");
        broadcastMsg.put("data", savedDanmaku);
        broadcastMsg.put("source", "danmaku");
        broadcastMsg.put("timestamp", System.currentTimeMillis());
        broadcastToClassroom(classroomId, broadcastMsg);

        // 通知教师端
        Map<String, Object> teacherMsg = new HashMap<>();
        teacherMsg.put("type", "DANMAKU_RECEIVED");
        teacherMsg.put("data", savedDanmaku);
        teacherMsg.put("source", "danmaku");
        teacherMsg.put("timestamp", System.currentTimeMillis());
        sendToTeacher(classroomId, teacherMsg);

        // 确认给学生
        sendSuccessToStudent(classroomId, studentId, "DANMAKU_SENT_SUCCESS", "弹幕发送成功");
    }

    private void handleStudentSendComment(String classroomId, String studentId, Map<String, Object> data) throws Exception {
        if (isMessageDisabled(classroomId, "comments")) {
            sendErrorToStudent(classroomId, studentId, "教师已禁止发送评论");
            return;
        }

        Map<String, Object> commentData = (Map<String, Object>) data.get("comment");

        Comment comment = new Comment();
        comment.setId("comment_" + System.currentTimeMillis() + "_" + studentId);
        comment.setClassroomId(classroomId);
        comment.setContent((String) commentData.get("content"));
        comment.setStudentId(studentId);
        comment.setStudentName((String) commentData.getOrDefault("studentName", "学生" + studentId));
        comment.setTeacher(false);
        try {
            String sendTimeStr = commentData.getOrDefault("sendTime", "").toString();
            if (!sendTimeStr.isEmpty()) {
                Date sendTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse(sendTimeStr);
                comment.setSendTime(sendTime);
            } else {
                comment.setSendTime(new Date());
            }
        } catch (ParseException e) {
            comment.setSendTime(new Date());
        }

        Comment savedComment = commentService.addComment(comment);

        // 广播评论
        Map<String, Object> broadcastMsg = new HashMap<>();
        broadcastMsg.put("type", "COMMENT_RECEIVED");
        broadcastMsg.put("data", savedComment);
        broadcastMsg.put("source", "comment");
        broadcastMsg.put("timestamp", System.currentTimeMillis());
        broadcastToClassroom(classroomId, broadcastMsg);

        // 通知教师端
        Map<String, Object> teacherMsg = new HashMap<>();
        teacherMsg.put("type", "COMMENT_RECEIVED");
        teacherMsg.put("data", savedComment);
        teacherMsg.put("source", "comment");
        teacherMsg.put("timestamp", System.currentTimeMillis());
        sendToTeacher(classroomId, teacherMsg);

        // 确认给学生
        WebSocketSession studentSession = getStudentSession(classroomId, studentId);
        if (studentSession != null && studentSession.isOpen()) {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "COMMENT_SENT_SUCCESS");
            response.put("message", "评论发送成功");
            response.put("comment", savedComment);
            response.put("source", "comment");
            studentSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }

    private void handleStudentSendOpinion(String classroomId, String studentId, Map<String, Object> data) throws Exception {
        if (isMessageDisabled(classroomId, "all")) {
            sendErrorToStudent(classroomId, studentId, "教师已禁止发送消息");
            return;
        }

        String title = (String) data.get("title");
        String content = (String) data.get("content");
        boolean anonymous = Boolean.TRUE.equals(data.get("anonymous"));

        if (content == null || content.trim().isEmpty()) {
            sendErrorToStudent(classroomId, studentId, "观点内容不能为空");
            return;
        }

        Opinion opinion = new Opinion();
        opinion.setId("opinion_" + System.currentTimeMillis() + "_" + studentId);
        opinion.setClassroomId(classroomId);
        opinion.setStudentId(studentId);
        opinion.setStudentName((String) data.getOrDefault("studentName", "学生" + studentId));
        opinion.setTitle(title != null ? title.trim() : "");
        opinion.setContent(content.trim());
        opinion.setAnonymous(anonymous);
        opinion.setSendTime(new Date());

        Opinion savedOpinion = opinionService.addOpinion(opinion);

        // 广播观点
        broadcastToClassroom(classroomId, "OPINION_RECEIVED", savedOpinion, "opinion");
        sendToTeacher(classroomId, "OPINION_RECEIVED", savedOpinion, "opinion");

        WebSocketSession studentSession = getStudentSession(classroomId, studentId);
        if (studentSession != null && studentSession.isOpen()) {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "OPINION_SENT_SUCCESS");
            response.put("message", "观点发送成功");
            response.put("opinion", savedOpinion);
            response.put("source", "opinion");
            studentSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }

    private void handleStudentVideoStatus(String classroomId, String studentId, Map<String, Object> data) {
        Map<String, Object> statusData = new HashMap<>();
        statusData.put("type", "STUDENT_VIDEO_STATUS");
        statusData.put("classroomId", classroomId);
        statusData.put("studentId", studentId);
        statusData.put("status", data.get("status"));
        statusData.put("videoTime", data.get("videoTime"));
        statusData.put("source", "system");
        statusData.put("timestamp", System.currentTimeMillis());
        sendToTeacher(classroomId, statusData);
    }

    private void handleStudentJoin(String classroomId, String studentId, WebSocketSession session) throws Exception {
        Map<String, Object> welcomeMsg = new HashMap<>();
        welcomeMsg.put("type", "WELCOME");
        welcomeMsg.put("message", "欢迎加入课堂");
        welcomeMsg.put("classroomId", classroomId);
        welcomeMsg.put("studentId", studentId);
        welcomeMsg.put("source", "system");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMsg)));
        syncMessageStatusToStudent(classroomId, session);
    }

    private void syncMessageStatusToStudent(String classroomId, WebSocketSession session) throws Exception {
        Map<String, Boolean> statusMap = classroomMessageStatus.get(classroomId);
        if (statusMap != null) {
            if (Boolean.TRUE.equals(statusMap.get("all"))) {
                sendToggleMessage(session, "TOGGLE_STUDENT_ALL_MESSAGES", true);
            } else {
                if (Boolean.TRUE.equals(statusMap.get("danmaku"))) {
                    sendToggleMessage(session, "TOGGLE_STUDENT_DANMAKU", true);
                }
                if (Boolean.TRUE.equals(statusMap.get("comments"))) {
                    sendToggleMessage(session, "TOGGLE_STUDENT_COMMENTS", true);
                }
            }
        }
    }

    private void sendToggleMessage(WebSocketSession session, String type, boolean disabled) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        Map<String, Object> data = new HashMap<>();
        data.put("disabled", disabled);
        message.put("data", data);
        message.put("source", "teacher_control");
        message.put("timestamp", System.currentTimeMillis());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }

    private void handleHeartbeat(String classroomId, String studentId) {
        Map<String, Object> heartbeatData = new HashMap<>();
        heartbeatData.put("type", "STUDENT_HEARTBEAT");
        heartbeatData.put("classroomId", classroomId);
        heartbeatData.put("studentId", studentId);
        heartbeatData.put("source", "system");
        heartbeatData.put("timestamp", System.currentTimeMillis());
        sendToTeacher(classroomId, heartbeatData);
    }

    private void notifyTeacherStudentJoined(String classroomId, String studentId) {
        Map<String, Object> joinData = new HashMap<>();
        joinData.put("type", "STUDENT_JOINED");
        joinData.put("classroomId", classroomId);
        joinData.put("studentId", studentId);
        joinData.put("source", "system");
        joinData.put("timestamp", System.currentTimeMillis());
        sendToTeacher(classroomId, joinData);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String classroomId = extractClassroomId(session);
        String studentId = extractStudentId(session);

        if (classroomId != null && studentId != null) {
            sessionRegistry.removeStudent(classroomId, studentId);
            System.out.println("Student " + studentId + " disconnected from classroom: " + classroomId);
            notifyTeacherStudentLeft(classroomId, studentId);
        }
    }

    private void notifyTeacherStudentLeft(String classroomId, String studentId) {
        Map<String, Object> leaveData = new HashMap<>();
        leaveData.put("type", "STUDENT_LEFT");
        leaveData.put("classroomId", classroomId);
        leaveData.put("studentId", studentId);
        leaveData.put("source", "system");
        leaveData.put("timestamp", System.currentTimeMillis());
        sendToTeacher(classroomId, leaveData);
    }

    private String extractClassroomId(WebSocketSession session) {
        String uri = session.getUri().toString();
        if (uri.contains("/ws/student/")) {
            int startIndex = uri.indexOf("/ws/student/") + "/ws/student/".length();
            int endIndex = uri.indexOf("/", startIndex);
            if (endIndex == -1) endIndex = uri.indexOf("?", startIndex);
            if (endIndex == -1) endIndex = uri.length();
            return uri.substring(startIndex, endIndex);
        }
        return null;
    }

    private String extractStudentId(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.contains("studentId=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("studentId=")) {
                    return param.substring("studentId=".length());
                }
            }
        }
        return "student_" + session.getId().substring(0, 8);
    }

    public void broadcastToClassroom(String classroomId, Map<String, Object> message) {
        String messageType = (String) message.get("type");
        if (messageType != null) {
            switch (messageType) {
                case "TOGGLE_STUDENT_DANMAKU": updateMessageStatus(classroomId, "danmaku", message); break;
                case "TOGGLE_STUDENT_COMMENTS": updateMessageStatus(classroomId, "comments", message); break;
                case "TOGGLE_STUDENT_ALL_MESSAGES": updateMessageStatus(classroomId, "all", message); break;
            }
        }

        sessionRegistry.broadcastToStudents(classroomId, message);
    }

    private void updateMessageStatus(String classroomId, String type, Map<String, Object> message) {
        Map<String, Object> data = (Map<String, Object>) message.get("data");
        if (data != null) {
            Boolean disabled = (Boolean) data.get("disabled");
            if (disabled != null) {
                Map<String, Boolean> statusMap = classroomMessageStatus.computeIfAbsent(classroomId, k -> new HashMap<>());
                if (type.equals("all")) {
                    statusMap.put("danmaku", disabled);
                    statusMap.put("comments", disabled);
                    statusMap.put("all", disabled);
                } else {
                    statusMap.put(type, disabled);
                }
            }
        }
    }

    private boolean isMessageDisabled(String classroomId, String type) {
        Map<String, Boolean> statusMap = classroomMessageStatus.get(classroomId);
        if (statusMap != null) {
            if (Boolean.TRUE.equals(statusMap.get("all"))) return true;
            return Boolean.TRUE.equals(statusMap.get(type));
        }
        return false;
    }

    public void broadcastToClassroom(String classroomId, String messageType, Object data, String source) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", messageType);
        message.put("data", data);
        message.put("source", source);
        message.put("timestamp", System.currentTimeMillis());
        broadcastToClassroom(classroomId, message);
    }

    public void broadcastToClassroom(String classroomId, String messageType, Object data) {
        broadcastToClassroom(classroomId, messageType, data, "system");
    }

    public void sendToTeacher(String classroomId, Map<String, Object> message) {
        TeacherWebSocketHandler.sendToTeacher(classroomId, message);
    }

    public void sendToTeacher(String classroomId, String messageType, Object data, String source) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", messageType);
        message.put("data", data);
        message.put("source", source);
        message.put("timestamp", System.currentTimeMillis());
        TeacherWebSocketHandler.sendToTeacher(classroomId, message);
    }

    private void sendErrorToStudent(String classroomId, String studentId, String errorMsg) throws Exception {
        WebSocketSession studentSession = getStudentSession(classroomId, studentId);
        if (studentSession != null && studentSession.isOpen()) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "ERROR");
            msg.put("message", errorMsg);
            studentSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        }
    }

    private void sendSuccessToStudent(String classroomId, String studentId, String type, String msg) throws Exception {
        WebSocketSession studentSession = getStudentSession(classroomId, studentId);
        if (studentSession != null && studentSession.isOpen()) {
            Map<String, Object> response = new HashMap<>();
            response.put("type", type);
            response.put("message", msg);
            response.put("source", "system");
            studentSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }

    public WebSocketSession getStudentSession(String classroomId, String studentId) {
        return sessionRegistry.getStudentSession(classroomId, studentId);
    }

    public Map<String, WebSocketSession> getClassroomStudents(String classroomId) {
        return sessionRegistry.getClassroomStudents(classroomId);
    }

    public int getOnlineStudentCount(String classroomId) {
        return sessionRegistry.getOnlineStudentCount(classroomId);
    }

    public void broadcastMessage(String classroomId, String messageType, Object data) {
        broadcastToClassroom(classroomId, messageType, data);
    }
}
