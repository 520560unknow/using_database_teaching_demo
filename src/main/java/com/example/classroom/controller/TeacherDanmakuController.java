package com.example.classroom.controller;

import com.example.classroom.model.*;
import com.example.classroom.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 教师端课堂弹幕接口（发送 / 最近列表 / 统计 / 智能跳转决策）。
 * 路由与返回结构保持与拆分前一致。
 */
@Controller
@RequestMapping("/teacher")
public class TeacherDanmakuController {

    private static final Logger log = LoggerFactory.getLogger(TeacherDanmakuController.class);

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private VideoTreeService videoService;

    @Autowired
    private DanmakuService danmakuService;

    @Value("${app.identity.default-teacher-id:teacher_001}")
    private String defaultTeacherId;

    // 发送教师弹幕
    @PostMapping("/classroom/{id}/send-danmaku")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendTeacherDanmaku(
            @PathVariable String id,
            @RequestBody Danmaku danmaku) {
        try {
            danmaku.setClassroomId(id);
            danmaku.setTeacher(true);
            danmaku.setSendTime(new Date());
            danmaku.setTeacherId(defaultTeacherId);
            danmaku.setTeacherName("张老师");

            Danmaku savedDanmaku = danmakuService.addDanmaku(danmaku);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "弹幕发送成功");
            response.put("danmakuId", savedDanmaku.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("发送弹幕失败, 课堂ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "弹幕发送失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 发送教师弹幕（简化版）
    @PostMapping("/classroom/{id}/send-teacher-danmaku")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendTeacherDanmakuSimple(
            @PathVariable String id,
            @RequestParam String content,
            @RequestParam(defaultValue = "0") int videoTime) {
        try {
            Danmaku danmaku = new Danmaku();
            // 不再手动设置id，由service层自动生成
            danmaku.setClassroomId(id);
            danmaku.setContent(content);
            danmaku.setVideoTime(videoTime);
            danmaku.setTeacher(true);
            danmaku.setTeacherId(defaultTeacherId);
            danmaku.setTeacherName("张老师");
            danmaku.setSendTime(new Date());
            danmaku.setColor("#FF0000");

            Danmaku savedDanmaku = danmakuService.addDanmaku(danmaku);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "教师弹幕发送成功");
            response.put("danmaku", savedDanmaku);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("发送教师弹幕失败, 课堂ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "弹幕发送失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 发送教师弹幕（JSON请求体）
    @PostMapping("/classroom/{id}/send-teacher-danmaku-json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendTeacherDanmakuSimpleJson(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            String content = (String) body.get("content");
            int videoTime = body.containsKey("videoTime") ? ((Number) body.get("videoTime")).intValue() : 0;

            Danmaku danmaku = new Danmaku();
            danmaku.setClassroomId(id);
            danmaku.setContent(content);
            danmaku.setVideoTime(videoTime);
            danmaku.setTeacher(true);
            danmaku.setTeacherId((String) body.getOrDefault("teacherId", defaultTeacherId));
            danmaku.setTeacherName((String) body.getOrDefault("teacherName", "教师"));
            danmaku.setSendTime(new Date());
            danmaku.setColor("#FF0000");

            Danmaku savedDanmaku = danmakuService.addDanmaku(danmaku);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "教师弹幕发送成功");
            response.put("danmaku", savedDanmaku);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("发送教师弹幕失败(JSON), 课堂ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "弹幕发送失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }
    @GetMapping("/classroom/{id}/danmaku/recent")
    @ResponseBody
    public ResponseEntity<List<Danmaku>> getRecentDanmaku(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int limit) {
        List<Danmaku> danmakuList = danmakuService.getRecentDanmaku(id, limit);
        return ResponseEntity.ok(danmakuList);
    }

    // 获取弹幕统计
    @GetMapping("/classroom/{id}/danmaku/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDanmakuStats(@PathVariable String id) {
        Map<String, Object> stats = danmakuService.getDanmakuStats(id);
        return ResponseEntity.ok(stats);
    }

    // 智能跳转决策
    @PostMapping("/classroom/{id}/decide-next")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> decideNextVideo(@PathVariable String id) {
        try {
            ClassRoom classroom = teacherService.getClassroom(id);
            if (classroom == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "课堂不存在");
                return ResponseEntity.badRequest().body(response);
            }

            List<Danmaku> recentDanmaku = danmakuService.getRecentDanmaku(id, 100);
            Map<String, Integer> keywordWeights = videoService.analyzeDanmaku(recentDanmaku);

            VideoNode nextNode = videoService.decideNextNode(
                    classroom.getCurrentVideoId(),
                    keywordWeights
            );

            if (nextNode != null) {
                teacherService.updateCurrentVideo(id, nextNode.getId());

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("nextNode", nextNode);
                response.put("keywordWeights", keywordWeights);
                response.put("message", "跳转决策完成");
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("nextNode", null);
                response.put("message", "没有可跳转的节点");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.error("智能跳转决策失败, 课堂ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "跳转决策失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }
}
