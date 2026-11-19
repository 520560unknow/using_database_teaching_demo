package com.example.classroom.controller;

import com.example.classroom.model.*;
import com.example.classroom.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 教师端课堂运行控制接口（批量操作、当前视频、实时状态、开始/暂停/继续/结束、统计）。
 * 路由与返回结构保持与拆分前一致。
 */
@Controller
@RequestMapping("/teacher")
public class TeacherClassroomRuntimeController {

    private static final Logger log = LoggerFactory.getLogger(TeacherClassroomRuntimeController.class);

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private DanmakuService danmakuService;

    // 批量操作课堂
    @PostMapping("/classrooms/batch-operation")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> batchOperation(
            @RequestParam List<String> classroomIds,
            @RequestParam String operation) {
        try {
            int successCount = 0;
            int failCount = 0;
            List<String> results = new ArrayList<>();

            for (String classroomId : classroomIds) {
                try {
                    switch (operation) {
                        case "start":
                            teacherService.startClass(classroomId);
                            results.add("课堂 " + classroomId + " 已开始");
                            successCount++;
                            break;
                        case "pause":
                            teacherService.pauseClass(classroomId);
                            results.add("课堂 " + classroomId + " 已暂停");
                            successCount++;
                            break;
                        case "end":
                            teacherService.endClass(classroomId);
                            results.add("课堂 " + classroomId + " 已结束");
                            successCount++;
                            break;
                        default:
                            results.add("课堂 " + classroomId + ": 未知操作");
                            failCount++;
                    }
                } catch (Exception e) {
                    results.add("课堂 " + classroomId + " 操作失败");
                    failCount++;
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("successCount", successCount);
            response.put("failCount", failCount);
            response.put("results", results);
            response.put("message", "批量操作完成");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("批量操作失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "批量操作失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 设置当前播放视频
    @PostMapping("/classroom/{id}/set-current-video")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setCurrentVideo(
            @PathVariable String id,
            @RequestParam String videoNodeId) {
        try {
            boolean success = teacherService.updateCurrentVideo(id, videoNodeId);

            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("success", true);
                response.put("message", "当前视频已更新");
            } else {
                response.put("success", false);
                response.put("message", "更新失败");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("设置当前视频失败, 课堂ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "更新失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 获取课堂实时状态
    @GetMapping("/classroom/{id}/realtime-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRealtimeStatus(@PathVariable String id) {
        try {
            ClassRoom classroom = teacherService.getClassroom(id);
            if (classroom == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "课堂不存在");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> stats = teacherService.getClassroomStats(id);
            List<Danmaku> recentDanmaku = danmakuService.getRecentDanmaku(id, 20);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("classroom", classroom);
            response.put("stats", stats);
            response.put("recentDanmaku", recentDanmaku);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取实时状态失败, 课堂ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取状态失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 开始课堂
    @PostMapping("/classroom/{id}/start")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startClass(@PathVariable String id) {
        try {
            ClassRoom classroom = teacherService.startClass(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("classroom", classroom);
            response.put("message", "课堂已开始");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("开始课堂失败, ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "开始课堂失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 暂停课堂
    @PostMapping("/classroom/{id}/pause")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> pauseClass(@PathVariable String id) {
        try {
            ClassRoom classroom = teacherService.pauseClass(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("classroom", classroom);
            response.put("message", "课堂已暂停");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("暂停课堂失败, ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "暂停课堂失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 继续课堂
    @PostMapping("/classroom/{id}/resume")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resumeClass(@PathVariable String id) {
        try {
            ClassRoom classroom = teacherService.resumeClass(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("classroom", classroom);
            response.put("message", "课堂已继续");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("继续课堂失败, ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "继续课堂失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 结束课堂
    @PostMapping("/classroom/{id}/end")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> endClass(@PathVariable String id) {
        try {
            boolean success = teacherService.endClass(id);
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("success", true);
                response.put("message", "课堂已结束");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "结束课堂失败");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("结束课堂失败, ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "结束课堂失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 获取课堂统计
    @GetMapping("/classroom/{id}/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClassroomStats(@PathVariable String id) {
        try {
            Map<String, Object> stats = teacherService.getClassroomStats(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stats", stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取课堂统计失败, ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取统计失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }
}
