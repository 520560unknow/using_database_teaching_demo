package com.example.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

/**
 * /ws/danmu WebSocket 端点。
 * 负责接收和广播弹幕来源的消息。
 * 广播到自身所有 /ws/danmu 连接；跨端分发由 WebSocketMessageDispatcher 统一处理。
 */
@Component
public class DanmuWebSocketServer extends TextWebSocketHandler {

    private ObjectMapper objectMapper = new ObjectMapper();

    // 课堂ID -> 连接列表
    private Map<String, CopyOnWriteArrayList<WebSocketSession>> classroomSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String uri = session.getUri().toString();
        String classroomId = extractClassroomId(uri);

        if (classroomId != null) {
            classroomSessions.computeIfAbsent(classroomId, k -> new CopyOnWriteArrayList<>()).add(session);
            System.out.println("DanmuWebSocket连接建立: 课堂ID=" + classroomId + ", 会话ID=" + session.getId());
        } else {
            System.out.println("DanmuWebSocket连接建立，但未找到课堂ID，会话ID=" + session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String classroomId = (String) data.get("classroomId");

            if (classroomId == null) {
                String uri = session.getUri().toString();
                classroomId = extractClassroomId(uri);
            }

            if (classroomId != null) {
                // 确保 session 在课堂列表中
                boolean isSessionInClassroom = false;
                for (CopyOnWriteArrayList<WebSocketSession> sessions : classroomSessions.values()) {
                    if (sessions.contains(session)) {
                        isSessionInClassroom = true;
                        break;
                    }
                }
                if (!isSessionInClassroom) {
                    classroomSessions.computeIfAbsent(classroomId, k -> new CopyOnWriteArrayList<>()).add(session);
                }

                // 广播到 /ws/danmu 的所有连接（同一课堂内的弹幕来源端）
                broadcastToAll(classroomId, data);
            } else {
                session.sendMessage(new TextMessage("{\"error\": \"未找到课堂ID\"}"));
            }
        } catch (Exception e) {
            System.err.println("Danmaku消息处理异常: " + e.getMessage());
            session.sendMessage(new TextMessage("{\"error\": \"消息格式错误\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        for (Map.Entry<String, CopyOnWriteArrayList<WebSocketSession>> entry : classroomSessions.entrySet()) {
            entry.getValue().remove(session);
            if (entry.getValue().isEmpty()) {
                classroomSessions.remove(entry.getKey());
            }
        }
        System.out.println("DanmuWebSocket连接关闭: 会话ID=" + session.getId());
    }

    /**
     * 广播消息给 /ws/danmu 的同一课堂所有连接。
     */
    public void broadcastToAll(String classroomId, Map<String, Object> message) {
        CopyOnWriteArrayList<WebSocketSession> sessions = classroomSessions.get(classroomId);
        if (sessions != null) {
            try {
                TextMessage textMessage = new TextMessage(objectMapper.writeValueAsString(message));
                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                }
            } catch (Exception e) {
                System.err.println("Danmaku广播异常: " + e.getMessage());
            }
        }
    }

    private String extractClassroomId(String uri) {
        try {
            URI url = new URI(uri);
            String query = url.getQuery();
            if (query != null) {
                Map<String, String> params = parseQueryString(query);
                return params.get("classroomId");
            }
        } catch (URISyntaxException e) {
            System.err.println("URI解析异常: " + e.getMessage());
        }
        return null;
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                params.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return params;
    }
}
