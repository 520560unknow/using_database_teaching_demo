package com.example.classroom.controller;

import com.example.classroom.model.ClassRoom;
import com.example.classroom.service.TeacherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ClassroomController {

    private static final Logger log = LoggerFactory.getLogger(ClassroomController.class);

    @Autowired
    private TeacherService teacherService;

    @Value("${app.identity.api-teacher-id:api_teacher}")
    private String apiTeacherId;

    @GetMapping("/classrooms/{id}/exists")
    public ResponseEntity<Map<String, Object>> classroomExists(@PathVariable("id") String id) {
        Map<String, Object> response = new HashMap<>();
        ClassRoom classroom = teacherService.getClassroom(id);
        response.put("exists", classroom != null);
        if (classroom != null) {
            response.put("classroomId", classroom.getId());
            response.put("className", classroom.getName());
            response.put("status", classroom.getStatus());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/classrooms")
    public ResponseEntity<Map<String, Object>> createClassroom(
            @RequestParam("className") String className,
            @RequestParam(value = "classDescription", required = false) String classDescription,
            @RequestParam(value = "videoTreeType") String videoTreeType,
            @RequestParam(value = "videoTreeId", required = false) String videoTreeId,
            @RequestParam(value = "enableDanmaku", defaultValue = "true") boolean enableDanmaku,
            @RequestParam(value = "enableComments", defaultValue = "true") boolean enableComments,
            @RequestParam(value = "autoJump", defaultValue = "true") boolean autoJump,
            @RequestParam(value = "recordSession", defaultValue = "true") boolean recordSession,
            @RequestParam(value = "jsonFile", required = false) MultipartFile jsonFile) {

        try {
            Map<String, Object> response = new HashMap<>();

            if (className == null || className.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "课堂名称不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            // 处理视频树
            String finalVideoTreeId = null;
            if ("upload".equals(videoTreeType)) {
                if (jsonFile == null || jsonFile.isEmpty()) {
                    response.put("success", false);
                    response.put("message", "请上传JSON文件");
                    return ResponseEntity.badRequest().body(response);
                }
                // 文件已上传但暂不解析为视频树ID，使用默认值
            } else if ("existing".equals(videoTreeType)) {
                if (videoTreeId == null || videoTreeId.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "请选择视频树");
                    return ResponseEntity.badRequest().body(response);
                }
                finalVideoTreeId = videoTreeId;
            }

            String description = (classDescription != null && !classDescription.trim().isEmpty())
                    ? classDescription : className + "的课堂";
            // 使用默认教师ID（兼容旧接口）
            String teacherId = apiTeacherId;

            // 通过 TeacherService 真正保存课堂
            ClassRoom classroom = teacherService.createClassroom(
                    teacherId, className, description, finalVideoTreeId,
                    enableDanmaku, enableComments, autoJump, recordSession);

            response.put("success", true);
            response.put("message", "课堂创建成功");
            response.put("classroomId", classroom.getId());
            response.put("className", className);
            response.put("createdTime", classroom.getCreatedTime());
            response.put("pptTeacherUrl", "/kdocs/play?classroomId=" + classroom.getId());
            response.put("pptStudentUrl", "/kdocs/student?classroomId=" + classroom.getId());

            log.info("通过/api/classrooms创建课堂成功: {}", classroom.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("创建课堂失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "创建课堂失败，请稍后重试");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
