package com.example.classroom.controller;

import com.example.classroom.model.*;
import com.example.classroom.service.*;
import com.example.support.JsonStoragePath;
import com.example.support.JsonText;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
@RequestMapping("/teacher")
public class TeacherController {

    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private VideoTreeService videoService;

    @Autowired
    private DefaultVideoTreeFactory defaultVideoTreeFactory;

    @Autowired
    private DanmakuService danmakuService;

    @Autowired
    private OpinionService opinionService;

    @Autowired
    private JsonStoragePath storagePath;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @Value("${app.identity.default-teacher-id:teacher_001}")
    private String defaultTeacherId;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============ 页面跳转方法 ============

    // 教师仪表板主页 - 修改后版本

    @GetMapping("/dashboard")
    public String dashboard(Model model,
                            @RequestParam(defaultValue = "${app.identity.default-teacher-id:teacher_001}") String teacherId,
                            HttpServletRequest request) {

        System.out.println("=== 加载教师仪表板 ===");
        System.out.println("教师ID: " + teacherId);

        // 获取教师的课堂列表
        List<ClassRoom> classrooms = teacherService.getTeacherClassrooms(teacherId);
        System.out.println("获取到课堂数量: " + classrooms.size());

        // 获取教师的视频树列表
        List<VideoTree> videoTrees = videoService.getVideoTreesByTeacher(teacherId);
        System.out.println("获取到视频树数量: " + (videoTrees != null ? videoTrees.size() : 0));

        // 检查是否有成功消息
        String successMessage = (String) request.getSession().getAttribute("successMessage");
        String errorMessage = (String) request.getSession().getAttribute("errorMessage");

        if (successMessage != null) {
            System.out.println("有成功消息: " + successMessage);
        }
        if (errorMessage != null) {
            System.out.println("有错误消息: " + errorMessage);
        }

        // 调试：打印课堂信息
        for (ClassRoom classroom : classrooms) {
            System.out.println("课堂: " + classroom.getName() +
                    ", ID: " + classroom.getId() +
                    ", 创建时间: " + classroom.getCreatedTime());
        }

        // 获取局域网访问地址
        String lanIp = getLanIpAddress();
        String normalizedContextPath = (contextPath == null || "/".equals(contextPath.trim())) ? "" : contextPath.trim();
        if (normalizedContextPath.endsWith("/")) {
            normalizedContextPath = normalizedContextPath.substring(0, normalizedContextPath.length() - 1);
        }
        String accessUrl = "http://" + lanIp + ":" + serverPort + normalizedContextPath;
        System.out.println("局域网访问地址: " + accessUrl);

        model.addAttribute("classrooms", classrooms);
        model.addAttribute("videoTrees", videoTrees);
        model.addAttribute("teacherId", teacherId);
        model.addAttribute("teacherName", "张老师");
        model.addAttribute("successMessage", successMessage);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("lanAccessUrl", accessUrl);

        // 清除session中的消息
        request.getSession().removeAttribute("successMessage");
        request.getSession().removeAttribute("errorMessage");

        return "teacher/dashboard";
    }

    // 创建课堂页面
    @GetMapping("/create-classroom")
    public String createClassroomPage(Model model, @RequestParam(defaultValue = "${app.identity.default-teacher-id:teacher_001}") String teacherId) {
        List<VideoTree> videoTrees = videoService.getVideoTreesByTeacher(teacherId);
        model.addAttribute("videoTrees", videoTrees);
        model.addAttribute("teacherId", teacherId);
        return "teacher/create-classroom";
    }



    // 视频树编辑器页面
    @GetMapping("/teacherMode")
    public String teacherModePage(Model model, @RequestParam(defaultValue = "${app.identity.default-teacher-id:teacher_001}") String teacherId) {
        System.out.println("访问视频树编辑器页面");

        List<VideoTree> videoTrees = videoService.getVideoTreesByTeacher(teacherId);

        model.addAttribute("videoTrees", videoTrees);
        model.addAttribute("teacherId", teacherId);
        model.addAttribute("teacherName", "张老师");
        model.addAttribute("title", "视频树编辑器");

        return "teacher/teacherMode";
    }

    // 课堂控制台页面
    @GetMapping("/classroom/{id}")
    public String classroomConsole(@PathVariable String id, Model model) {
        System.out.println("=== 进入课堂控制台，课堂ID: " + id + " ===");

        try {
            ClassRoom classroom = teacherService.getClassroom(id);
            if (classroom == null) {
                System.err.println("课堂不存在: " + id);
                return "redirect:/teacher/dashboard";
            }

            System.out.println("课堂信息: 名称=" + classroom.getName() + ", 视频树ID=" + classroom.getVideoTreeId());

            VideoTree videoTree = null;
            String videoTreeId = classroom.getVideoTreeId();
            
            // 只有当视频树ID不是JSON文件类型时，才尝试获取视频树
            if (videoTreeId != null && !videoTreeId.trim().isEmpty() && !videoTreeId.startsWith("json_")) {
                videoTree = videoService.getVideoTree(videoTreeId);
                System.out.println("查找视频树结果: " + (videoTree != null ? "找到" : "未找到"));
            } else if (videoTreeId != null && videoTreeId.startsWith("json_")) {
                System.out.println("视频树ID是JSON文件类型，跳过获取视频树");
            }

            // 只有当视频树ID不是JSON文件类型且视频树不存在时，才创建默认视频树
            if (videoTree == null && videoTreeId != null && !videoTreeId.trim().isEmpty() && !videoTreeId.startsWith("json_")) {
                System.err.println("视频树不存在或为空，ID: " + videoTreeId);
                videoTree = defaultVideoTreeFactory.create(classroom.getTeacherId(), classroom.getName());
                if (videoTree != null) {
                    // 更新课堂的视频树ID（工厂内部已保存视频树）
                    teacherService.updateClassroomVideoTree(id, videoTree.getId());
                    System.out.println("已创建并设置默认视频树: " + videoTree.getId());
                } else {
                    System.err.println("创建默认视频树失败");
                }
            }

            List<Danmaku> recentDanmaku = danmakuService.getRecentDanmaku(id, 50);
            Map<String, Object> stats = teacherService.getClassroomStats(id);

            model.addAttribute("classroom", classroom);
            model.addAttribute("videoTree", videoTree);
            model.addAttribute("recentDanmaku", recentDanmaku);
            model.addAttribute("stats", stats);
            model.addAttribute("teacherId", classroom.getTeacherId());
            model.addAttribute("teacherName", "张老师");

            return "teacher/classroom";
        } catch (Exception e) {
            log.error("加载课堂控制台失败, 课堂ID: {}", id, e);
            model.addAttribute("error", "加载课堂失败，请稍后重试");
            return "teacher/dashboard";
        }
    }



    // 搜索课堂页面
    @GetMapping("/search")
    public String searchClassrooms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "${app.identity.default-teacher-id:teacher_001}") String teacherId,
            Model model) {

        List<ClassRoom> allClassrooms = teacherService.getTeacherClassrooms(teacherId);
        List<ClassRoom> filteredClassrooms = new ArrayList<>();

        for (ClassRoom classroom : allClassrooms) {
            boolean matches = true;

            if (keyword != null && !keyword.trim().isEmpty()) {
                String lowerKeyword = keyword.toLowerCase();
                matches = classroom.getName().toLowerCase().contains(lowerKeyword) ||
                        (classroom.getDescription() != null &&
                                classroom.getDescription().toLowerCase().contains(lowerKeyword)) ||
                        classroom.getId().toLowerCase().contains(lowerKeyword);
            }

            if (status != null && !status.trim().isEmpty() && !"all".equals(status)) {
                matches = matches && status.equals(classroom.getStatus());
            }

            if (matches) {
                filteredClassrooms.add(classroom);
            }
        }

        model.addAttribute("classrooms", filteredClassrooms);
        model.addAttribute("videoTrees", videoService.getVideoTreesByTeacher(teacherId));
        model.addAttribute("teacherId", teacherId);
        model.addAttribute("teacherName", "张老师");
        model.addAttribute("searchKeyword", keyword);
        model.addAttribute("searchStatus", status);
        model.addAttribute("searchResultsCount", filteredClassrooms.size());

        return "teacher/dashboard";
    }

    // ============ API 方法 ============

    // 创建课堂 - 使用JSON接收（这是主要的创建课堂方法）- 修改后版本
    @PostMapping("/create-classroom")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createClassroom(@RequestBody Map<String, Object> requestData,
                                                               HttpServletRequest request) {
        System.out.println("=== 接收到创建课堂请求 ===");
        System.out.println("请求数据: " + requestData);

        Map<String, Object> response = new HashMap<>();

        try {
            // 提取参数
            String teacherId = (String) requestData.get("teacherId");
            String name = (String) requestData.get("name");
            String description = (String) requestData.get("description");
            String videoTreeId = (String) requestData.get("videoTreeId");
            String videoTreeJson = (String) requestData.get("videoTreeJson");

            // 布尔参数处理
            boolean enableDanmaku = true;
            boolean enableComments = true;
            boolean autoJump = true;
            boolean recordSession = true;

            // 设置参数支持两种结构：顶层字段（旧页面）或 settings 对象（工作台弹窗）
            Map<String, Object> settings = null;
            if (requestData.get("settings") instanceof Map) {
                settings = (Map<String, Object>) requestData.get("settings");
            }
            Map<String, Object> settingSource = settings != null ? settings : requestData;
            if (settingSource.containsKey("enableDanmaku")) {
                enableDanmaku = Boolean.parseBoolean(settingSource.get("enableDanmaku").toString());
            }
            if (settingSource.containsKey("enableComments")) {
                enableComments = Boolean.parseBoolean(settingSource.get("enableComments").toString());
            }
            if (settingSource.containsKey("autoJump")) {
                autoJump = Boolean.parseBoolean(settingSource.get("autoJump").toString());
            }
            if (settingSource.containsKey("recordSession")) {
                recordSession = Boolean.parseBoolean(settingSource.get("recordSession").toString());
            }

            System.out.println("教师ID: " + teacherId);
            System.out.println("课堂名称: " + name);
            System.out.println("描述: " + description);
            System.out.println("视频树ID: " + videoTreeId);
            System.out.println("JSON数据: " + (videoTreeJson != null ? "有，长度=" + videoTreeJson.length() : "无"));

            String finalVideoTreeId;
            String finalDescription = description != null && !description.trim().isEmpty() ? description : name + "的课堂";

            // 处理视频树
            if (videoTreeJson != null && !videoTreeJson.trim().isEmpty()) {
                System.out.println("检测到JSON数据，开始创建视频树...");

                // 验证JSON格式
                try {
                    Map<String, Object> jsonData = objectMapper.readValue(videoTreeJson, Map.class);
                    System.out.println("JSON验证通过，包含键: " + jsonData.keySet());
                } catch (Exception e) {
                    response.put("success", false);
                    response.put("message", "JSON格式错误: " + e.getMessage());
                    return ResponseEntity.badRequest().body(response);
                }

                // 创建视频树
                VideoTree newTree = videoService.createVideoTreeFromJson(
                        videoTreeJson,
                        name + "的视频树",
                        finalDescription + "的视频树结构",
                        teacherId
                );

                if (newTree == null) {
                    response.put("success", false);
                    response.put("message", "创建视频树失败");
                    return ResponseEntity.badRequest().body(response);
                }

                finalVideoTreeId = newTree.getId();
                System.out.println("创建新视频树成功，ID: " + finalVideoTreeId);
            }
            // 使用已有视频树
            else if (videoTreeId != null && !videoTreeId.trim().isEmpty()) {
                finalVideoTreeId = videoTreeId;
                System.out.println("使用已有视频树，ID: " + finalVideoTreeId);

                // 处理JSON文件类型的视频树
                if (finalVideoTreeId.startsWith("json_")) {
                    System.out.println("处理JSON文件类型的视频树...");
                    try {
                        // 提取文件名
                        String fileName = finalVideoTreeId.substring(5); // 去掉"json_"前缀
                        System.out.println("JSON文件名: " + fileName);

                        // 读取JSON文件
                        System.out.println("JSON存储路径: " + storagePath.get());
                        File jsonFile = storagePath.resolve(fileName);
                        System.out.println("完整文件路径: " + jsonFile.getAbsolutePath());
                        if (!jsonFile.exists()) {
                            response.put("success", false);
                            response.put("message", "选择的JSON文件不存在，ID: " + finalVideoTreeId);
                            return ResponseEntity.badRequest().body(response);
                        }

                        // 检查文件可读性
                        if (!jsonFile.canRead()) {
                            response.put("success", false);
                            response.put("message", "无法读取JSON文件，可能是权限问题: " + finalVideoTreeId);
                            return ResponseEntity.badRequest().body(response);
                        }

                        // 读取文件内容
                        String jsonContent = JsonText.readUtf8(jsonFile);
                        System.out.println("JSON文件内容长度: " + jsonContent.length());
                        // 输出文件内容的前50个字符，用于验证
                        if (jsonContent.length() > 50) {
                            System.out.println("JSON文件内容前50个字符: " + jsonContent.substring(0, 50) + "...");
                        } else {
                            System.out.println("JSON文件内容: " + jsonContent);
                        }

                        // 从JSON创建视频树
                        VideoTree jsonTree = videoService.createVideoTreeFromJson(jsonContent, "JSON视频树", "从JSON文件导入", teacherId);
                        if (jsonTree == null) {
                            response.put("success", false);
                            response.put("message", "从JSON文件创建视频树失败");
                            return ResponseEntity.badRequest().body(response);
                        }

                        // 使用新创建的视频树ID
                        finalVideoTreeId = jsonTree.getId();
                        System.out.println("从JSON文件创建视频树成功，ID: " + finalVideoTreeId);
                        // 输出视频树的节点数
                        System.out.println("视频树节点数: " + (jsonTree.getRoot() != null ? countVideoTreeNodes(jsonTree.getRoot()) : 0));
                    } catch (Exception e) {
                        e.printStackTrace();
                        response.put("success", false);
                        response.put("message", "处理JSON文件失败: " + e.getMessage());
                        return ResponseEntity.badRequest().body(response);
                    }
                }
                // 处理普通视频树
                else {
                    // 验证视频树是否存在
                    VideoTree existingTree = videoService.getVideoTree(finalVideoTreeId);
                    if (existingTree == null) {
                        response.put("success", false);
                        response.put("message", "选择的视频树不存在，ID: " + finalVideoTreeId);
                        return ResponseEntity.badRequest().body(response);
                    }
                }
            }
            // 都没有提供，创建默认视频树
            else {
                System.out.println("没有提供视频树，创建默认视频树...");
                VideoTree defaultTree = defaultVideoTreeFactory.create(teacherId, name);
                if (defaultTree == null) {
                    response.put("success", false);
                    response.put("message", "创建默认视频树失败");
                    return ResponseEntity.badRequest().body(response);
                }
                finalVideoTreeId = defaultTree.getId();
                System.out.println("创建默认视频树成功，ID: " + finalVideoTreeId);
            }

            // 创建课堂
            ClassRoom classroom = teacherService.createClassroom(
                    teacherId,
                    name,
                    finalDescription,
                    finalVideoTreeId,
                    enableDanmaku,
                    enableComments,
                    autoJump,
                    recordSession
            );

            System.out.println("课堂创建成功，ID: " + classroom.getId());

            // 在session中设置成功消息
            request.getSession().setAttribute("successMessage", "课堂创建成功！课堂ID: " + classroom.getId());

            response.put("success", true);
            response.put("message", "课堂创建成功！");
            response.put("classroomId", classroom.getId());
            response.put("classroomName", classroom.getName());
            response.put("videoTreeId", finalVideoTreeId);
            response.put("redirect", true); // 添加重定向标志
            response.put("redirectUrl", "/teacher/dashboard?teacherId=" + teacherId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建课堂失败", e);

            response.put("success", false);
            response.put("message", "创建失败，请稍后重试");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 删除视频树
    @PostMapping("/video-tree/delete/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteVideoTree(@PathVariable String id) {
        try {
            boolean success = videoService.deleteVideoTree(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "视频树删除成功" : "视频树删除失败");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除视频树失败, ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除失败，请稍后重试");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 导出课堂数据
    @GetMapping("/classroom/{id}/export")
    @ResponseBody
    public ResponseEntity<String> exportClassroomData(@PathVariable String id) {
        try {
            ClassRoom classroom = teacherService.getClassroom(id);
            if (classroom == null) {
                return ResponseEntity.badRequest().body("课堂不存在");
            }

            Map<String, Object> stats = teacherService.getClassroomStats(id);
            List<Danmaku> allDanmaku = danmakuService.getAllDanmaku(id);

            // 创建JSON数据
            Map<String, Object> exportData = new HashMap<>();
            exportData.put("classroom", classroom);
            exportData.put("stats", stats);
            exportData.put("danmakuCount", allDanmaku.size());
            exportData.put("danmakuList", allDanmaku);
            exportData.put("exportTime", new Date());

            String jsonData = objectMapper.writeValueAsString(exportData);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            headers.add("Content-Disposition", "attachment; filename=classroom_" + id + "_data.json");

            return new ResponseEntity<>(jsonData, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("导出失败: " + e.getMessage());
        }
    }

    @GetMapping("/classroom/{id}/opinions")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClassroomOpinions(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Opinion> opinions = opinionService.getOpinionsByClassroomId(id);
            response.put("success", true);
            response.put("opinions", opinions);
            response.put("count", opinions.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取观点失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/classroom/{id}/opinions/export/txt")
    @ResponseBody
    public ResponseEntity<byte[]> exportOpinionsAsTxt(
            @PathVariable String id,
            @RequestBody(required = false) List<String> opinionIds) {
        try {
            List<Opinion> opinions;
            if (opinionIds != null && !opinionIds.isEmpty()) {
                opinions = opinionService.getOpinionsByIds(opinionIds);
            } else {
                opinions = opinionService.getOpinionsByClassroomId(id);
            }

            String txtContent = opinionService.exportOpinionsAsTxt(opinions);
            byte[] txtBytes = txtContent.getBytes(StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=opinions_" + id + "_" + System.currentTimeMillis() + ".txt");

            return new ResponseEntity<>(txtBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 获取课堂视频树信息
    @GetMapping("/classroom/{id}/video-tree")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClassroomVideoTree(@PathVariable String id) {
        try {
            System.out.println("=== 获取课堂视频树数据，课堂ID: " + id + " ===");
            
            ClassRoom classroom = teacherService.getClassroom(id);
            if (classroom == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "课堂不存在");
                System.out.println("课堂不存在");
                return ResponseEntity.badRequest().body(response);
            }
            
            String videoTreeId = classroom.getVideoTreeId();
            if (videoTreeId == null || videoTreeId.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "课堂未设置视频树");
                System.out.println("课堂未设置视频树");
                return ResponseEntity.badRequest().body(response);
            }
            
            System.out.println("视频树ID: " + videoTreeId);
            
            // 处理JSON文件类型的视频树
            if (videoTreeId.startsWith("json_")) {
                System.out.println("处理JSON文件类型的视频树...");
                try {
                    // 提取文件名
                    String fileName = videoTreeId.substring(5); // 去掉"json_"前缀
                    System.out.println("JSON文件名: " + fileName);
                    
                    // 构建文件路径
                    File jsonFile = storagePath.resolve(fileName);
                    System.out.println("JSON存储路径: " + storagePath.get());
                    System.out.println("完整文件路径: " + jsonFile.getAbsolutePath());
                    
                    if (!jsonFile.exists()) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "JSON文件不存在: " + fileName);
                        System.out.println("JSON文件不存在: " + fileName);
                        return ResponseEntity.badRequest().body(response);
                    }
                    
                    // 读取并解析JSON文件
                    String jsonContent = JsonText.readUtf8(jsonFile);
                    System.out.println("JSON文件内容长度: " + jsonContent.length());
                    System.out.println("JSON文件内容前50个字符: " + jsonContent.substring(0, Math.min(50, jsonContent.length())));
                    
                    try {
                        Map<String, Object> videoTree = objectMapper.readValue(jsonContent, Map.class);
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("videoTree", videoTree);
                        System.out.println("成功返回JSON文件类型的视频树数据");
                        System.out.println("视频树数据结构: " + videoTree.keySet());
                        if (videoTree.containsKey("elements")) {
                            System.out.println("视频树元素数量: " + ((List<?>) videoTree.get("elements")).size());
                        }
                        return ResponseEntity.ok(response);
                    } catch (Exception parseError) {
                        System.err.println("解析JSON文件失败: " + parseError.getMessage());
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "解析JSON文件失败: " + parseError.getMessage());
                        return ResponseEntity.badRequest().body(response);
                    }
                } catch (Exception e) {
                    System.err.println("处理JSON文件失败: " + e.getMessage());
                    e.printStackTrace();
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "处理JSON文件失败: " + e.getMessage());
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            // 处理普通视频树
            VideoTree videoTree = videoService.getVideoTree(videoTreeId);
            if (videoTree == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "视频树不存在");
                System.out.println("视频树不存在: " + videoTreeId);
                return ResponseEntity.badRequest().body(response);
            }
            
            // 转换视频树为前端需要的格式
            Map<String, Object> videoTreeData = convertVideoTreeToFrontendFormat(videoTree);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("videoTree", videoTreeData);
            System.out.println("成功返回普通视频树数据");
            System.out.println("视频树数据结构: " + videoTreeData.keySet());
            if (videoTreeData.containsKey("elements")) {
                System.out.println("视频树元素数量: " + ((List<?>) videoTreeData.get("elements")).size());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("获取视频树数据失败: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取视频树数据失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 将视频树转换为前端需要的格式
    private Map<String, Object> convertVideoTreeToFrontendFormat(VideoTree videoTree) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> elements = new ArrayList<>();
        List<Map<String, Object>> links = new ArrayList<>();
        
        // 处理根节点
        if (videoTree.getRoot() != null) {
            processVideoNode(videoTree.getRoot(), elements, links, null);
        }
        
        result.put("elements", elements);
        result.put("links", links);
        return result;
    }

    // 递归处理视频节点
    private void processVideoNode(VideoNode node, List<Map<String, Object>> elements, List<Map<String, Object>> links, String parentId) {
        // 创建节点元素
        Map<String, Object> element = new HashMap<>();
        element.put("id", node.getId());
        element.put("name", node.getName());
        element.put("description", node.getDescription());
        element.put("fileName", node.getVideoUrl() != null ? node.getVideoUrl().split("/")[node.getVideoUrl().split("/").length - 1] : null);
        element.put("videoUrl", node.getVideoUrl());
        element.put("keywords", node.getKeywords() != null ? node.getKeywords() : new ArrayList<>());
        element.put("x", node.getX());
        element.put("y", node.getY());
        element.put("width", node.getWidth());
        element.put("height", node.getHeight());
        elements.add(element);
        
        // 处理边
        if (parentId != null) {
            Map<String, Object> link = new HashMap<>();
            link.put("source", parentId);
            link.put("target", node.getId());
            links.add(link);
        }
        
        // 递归处理子节点
        if (node.getEdges() != null) {
            for (VideoNode.VideoEdge edge : node.getEdges()) {
                if (edge.getTargetNode() != null) {
                    processVideoNode(edge.getTargetNode(), elements, links, node.getId());
                } else if (edge.getTargetNodeId() != null) {
                    // 根据targetNodeId查找对应的节点
                    VideoNode targetNode = videoService.getVideoNode(edge.getTargetNodeId());
                    if (targetNode != null) {
                        processVideoNode(targetNode, elements, links, node.getId());
                    }
                }
            }
        }
    }

    // ============ 视频树统计 ============
    // 计算视频树的节点数
    private int countVideoTreeNodes(VideoNode node) {
        int count = 1; // 包含当前节点
        if (node.getEdges() != null) {
            for (VideoNode.VideoEdge edge : node.getEdges()) {
                if (edge.getTargetNode() != null) {
                    count += countVideoTreeNodes(edge.getTargetNode());
                }
            }
        }
        return count;
    }

    // 传输JSON信息并创建课堂（不存储到json-storage）
    @PostMapping("/create-classroom-with-json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createClassroomWithJson(@RequestBody Map<String, Object> requestData) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 提取参数
            String teacherId = (String) requestData.get("teacherId");
            String name = (String) requestData.get("name");
            String description = (String) requestData.get("description");
            Map<String, Object> videoTreeJson = (Map<String, Object>) requestData.get("videoTreeJson");
            Map<String, Object> settings = (Map<String, Object>) requestData.get("settings");

            // 验证参数
            if (teacherId == null || name == null || videoTreeJson == null) {
                response.put("success", false);
                response.put("message", "缺少必要参数");
                return ResponseEntity.badRequest().body(response);
            }

            // 转换JSON对象为字符串
            String jsonContent = objectMapper.writeValueAsString(videoTreeJson);
            String finalDescription = description != null && !description.trim().isEmpty() ? description : name + "的课堂";

            // 从JSON创建视频树
            VideoTree videoTree = videoService.createVideoTreeFromJson(
                    jsonContent,
                    name + "的视频树",
                    finalDescription + "的视频树结构",
                    teacherId
            );

            if (videoTree == null) {
                response.put("success", false);
                response.put("message", "创建视频树失败");
                return ResponseEntity.badRequest().body(response);
            }

            // 创建课堂
            boolean enableDanmaku = settings == null || Boolean.parseBoolean(settings.getOrDefault("enableDanmaku", true).toString());
            boolean enableComments = settings == null || Boolean.parseBoolean(settings.getOrDefault("enableComments", true).toString());
            boolean autoJump = settings == null || Boolean.parseBoolean(settings.getOrDefault("autoJump", true).toString());
            boolean recordSession = settings == null || Boolean.parseBoolean(settings.getOrDefault("recordSession", true).toString());

            ClassRoom classroom = teacherService.createClassroom(
                    teacherId,
                    name,
                    finalDescription,
                    videoTree.getId(),
                    enableDanmaku,
                    enableComments,
                    autoJump,
                    recordSession
            );

            response.put("success", true);
            response.put("message", "课堂创建成功");
            response.put("classroomId", classroom.getId());
            response.put("videoTreeId", videoTree.getId());

            System.out.println("传输JSON信息并创建课堂成功: " + name);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "操作失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 更新课堂状态
    @PutMapping("/update-classroom-status/{classroomId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateClassroomStatus(
            @PathVariable String classroomId,
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String status = request.get("status");
            if (status == null || status.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "状态不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            ClassRoom classroom = null;
            switch (status) {
                case "active":
                    classroom = teacherService.startClass(classroomId);
                    break;
                case "paused":
                    classroom = teacherService.pauseClass(classroomId);
                    break;
                case "ended":
                    boolean success = teacherService.endClass(classroomId);
                    if (success) {
                        response.put("success", true);
                        response.put("message", "课堂状态更新成功");
                        return ResponseEntity.ok(response);
                    } else {
                        response.put("success", false);
                        response.put("message", "更新课堂状态失败");
                        return ResponseEntity.badRequest().body(response);
                    }
                default:
                    response.put("success", false);
                    response.put("message", "无效的状态");
                    return ResponseEntity.badRequest().body(response);
            }

            if (classroom != null) {
                response.put("success", true);
                response.put("message", "课堂状态更新成功");
                response.put("classroom", classroom);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "更新课堂状态失败");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 获取本机局域网IP地址
     * @return 局域网IP地址，如果找不到则返回127.0.0.1
     */
    private String getLanIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            
            // 优先查找常见的局域网网段
            List<String> lanIps = new ArrayList<>();
            
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                
                // 跳过回环接口和未激活的接口
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    // 只处理IPv4地址
                    if (address instanceof Inet4Address) {
                        String ip = address.getHostAddress();
                        
                        // 优先选择常见局域网网段
                        if (ip.startsWith("192.168.") || 
                            ip.startsWith("10.") || 
                            (ip.startsWith("172.") && isPrivate172Range(ip))) {
                            lanIps.add(ip);
                        }
                    }
                }
            }
            
            // 如果找到局域网IP，返回第一个
            if (!lanIps.isEmpty()) {
                return lanIps.get(0);
            }
            
        } catch (Exception e) {
            System.err.println("获取局域网IP失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 如果找不到局域网IP，返回本地回环地址
        return "127.0.0.1";
    }
    
    /**
     * 判断是否是172.16-31.x.x的私有地址范围
     */
    private boolean isPrivate172Range(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                int secondOctet = Integer.parseInt(parts[1]);
                return secondOctet >= 16 && secondOctet <= 31;
            }
        } catch (NumberFormatException e) {
            // 忽略解析错误
        }
        return false;
    }
}
