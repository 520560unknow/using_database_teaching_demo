package com.example.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一的 WebSocket 消息分发器。
 * 所有需要广播到课堂的消息都应通过此分发器，避免重复发送。
 *
 * 消息来源（source）：
 *   - danmaku:          弹幕（来自 /ws/danmu 或学生端 SEND_DANMAKU）
 *   - comment:          评论
 *   - opinion:          观点
 *   - teacher_control:  教师端控制消息（视频跳转、开关弹幕等）
 *   - system:           系统消息（学生加入/离开等）
 */
@Component
public class WebSocketMessageDispatcher {

    @Autowired
    private DanmuWebSocketServer danmuWebSocketServer;

    @Autowired
    private StudentWebSocketHandler studentWebSocketHandler;

    /**
     * 广播消息到课堂的教师端和学生端（不经过 /ws/danmu 端口，避免重复）。
     * DanmuWebSocketServer 的 /ws/danmu 端口是弹幕来源，不在这里转发。
     */
    public void broadcastToClassroom(String classroomId, Map<String, Object> message) {
        // 确保有 source 字段
        if (!message.containsKey("source")) {
            message.put("source", inferSource((String) message.get("type")));
        }

        // 发送给教师端
        TeacherWebSocketHandler.sendToTeacher(classroomId, message);

        // 广播给学生端
        if (studentWebSocketHandler != null) {
            studentWebSocketHandler.broadcastToClassroom(classroomId, message);
        }
    }

    /**
     * 仅发送给教师端
     */
    public void sendToTeacher(String classroomId, Map<String, Object> message) {
        if (!message.containsKey("source")) {
            message.put("source", inferSource((String) message.get("type")));
        }
        TeacherWebSocketHandler.sendToTeacher(classroomId, message);
    }

    /**
     * 广播到所有已连接的 WebSocket（包括 /ws/danmu、教师端、学生端）。
     * 仅用于需要通知所有渠道的场景（如来自 /ws/danmu 的原始弹幕）。
     */
    public void broadcastToAll(String classroomId, Map<String, Object> message) {
        if (!message.containsKey("source")) {
            message.put("source", inferSource((String) message.get("type")));
        }

        // 发送到 /ws/danmu 的所有连接
        danmuWebSocketServer.broadcastToAll(classroomId, message);

        // 发送到教师端
        TeacherWebSocketHandler.sendToTeacher(classroomId, message);

        // 发送到学生端
        if (studentWebSocketHandler != null) {
            studentWebSocketHandler.broadcastToClassroom(classroomId, message);
        }
    }

    private String inferSource(String messageType) {
        if (messageType == null) return "system";
        if (messageType.contains("DANMAKU")) return "danmaku";
        if (messageType.contains("COMMENT")) return "comment";
        if (messageType.contains("OPINION")) return "opinion";
        if (messageType.contains("VIDEO") || messageType.contains("CLASS_") || messageType.contains("TOGGLE")) return "teacher_control";
        return "system";
    }
}
