package com.example.classroom.controller;

import com.example.classroom.model.*;
import com.example.classroom.service.*;
import com.example.support.JsonStoragePath;
import com.example.support.JsonText;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 教师端 JSON 文件管理接口（上传 / 列表 / 下载 / 读取 / 删除 / 导入课堂），
 * 以及 JSON 解析测试端点。路由与返回结构保持与拆分前一致。
 */
@Controller
@RequestMapping("/teacher")
public class TeacherJsonFileController {

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private VideoTreeService videoService;

    @Autowired
    private JsonStoragePath storagePath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // JSON测试端点
    @PostMapping("/test-json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testJson(@RequestBody String jsonStr) {
        try {
            System.out.println("测试JSON数据，长度: " + jsonStr.length());

            Map<String, Object> jsonData = objectMapper.readValue(jsonStr, Map.class);

            int elementCount = 0;
            int linkCount = 0;

            if (jsonData.containsKey("elements") && jsonData.get("elements") instanceof List) {
                elementCount = ((List) jsonData.get("elements")).size();
            }

            if (jsonData.containsKey("links") && jsonData.get("links") instanceof List) {
                linkCount = ((List) jsonData.get("links")).size();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "JSON解析成功");
            response.put("elementCount", elementCount);
            response.put("linkCount", linkCount);
            response.put("jsonKeys", jsonData.keySet());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "JSON解析失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 上传JSON文件
    @PostMapping("/upload-json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadJsonFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("teacherId") String teacherId) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "文件为空");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查文件类型
            String contentType = file.getContentType();
            if (!"application/json".equals(contentType)
                    && (file.getOriginalFilename() == null || !file.getOriginalFilename().endsWith(".json"))) {
                response.put("success", false);
                response.put("message", "只支持JSON文件");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查文件大小 (5MB限制)
            if (file.getSize() > 5 * 1024 * 1024) {
                response.put("success", false);
                response.put("message", "文件大小不能超过5MB");
                return ResponseEntity.badRequest().body(response);
            }

            // 生成文件名（只取文件名部分，避免 ../ 之类的路径穿越）
            String originalFilename = file.getOriginalFilename() == null
                    ? "upload.json"
                    : file.getOriginalFilename().replaceAll("[\\\\/]", "_");
            String fileName = UUID.randomUUID().toString() + "_" + originalFilename;
            String filePath = storagePath.get() + File.separator + fileName;

            // 保存文件
            File dest = new File(filePath);
            file.transferTo(dest);

            response.put("success", true);
            response.put("message", "JSON文件上传成功");
            response.put("fileName", fileName);
            response.put("filePath", filePath);
            response.put("size", file.getSize());

            System.out.println("JSON文件上传成功: " + fileName);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 获取JSON文件列表
    @GetMapping("/json-files")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getJsonFiles(@RequestParam("teacherId") String teacherId) {
        List<Map<String, Object>> files = new ArrayList<>();

        try {
            File dir = new File(storagePath.get());
            if (dir.exists() && dir.isDirectory()) {
                File[] fileArray = dir.listFiles((d, name) -> name.endsWith(".json"));
                if (fileArray != null) {
                    for (File file : fileArray) {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("id", file.getName());
                        fileInfo.put("name", file.getName().replaceAll("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}_", ""));
                        fileInfo.put("fileName", file.getName());
                        fileInfo.put("size", file.length());
                        fileInfo.put("uploadTime", new Date(file.lastModified()));
                        files.add(fileInfo);
                    }
                }
            }
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(files);
        }
    }

    // 下载JSON文件
    @GetMapping("/download-json/{fileName}")
    public ResponseEntity<byte[]> downloadJsonFile(@PathVariable String fileName) {
        try {
            File jsonFile = storagePath.resolve(fileName);
            if (!jsonFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            byte[] content = Files.readAllBytes(jsonFile.toPath());
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=" + fileName);
            headers.add("Content-Type", "application/json");

            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 获取JSON文件内容
    @GetMapping("/get-json/{fileName}")
    @ResponseBody
    public ResponseEntity<Object> getJsonFileContent(@PathVariable String fileName) {
        try {
            // 从文件列表中找到对应的文件
            File dir = new File(storagePath.get());
            if (dir.exists() && dir.isDirectory()) {
                File[] fileArray = dir.listFiles((d, name) -> name.endsWith(".json"));
                if (fileArray != null) {
                    for (File file : fileArray) {
                        if (file.getName().equals(fileName)) {
                            String jsonContent = JsonText.readUtf8(file);
                            Object jsonObject = objectMapper.readValue(jsonContent, Object.class);
                            return ResponseEntity.ok(jsonObject);
                        }
                    }
                }
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 删除JSON文件
    @DeleteMapping("/delete-json/{fileName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteJsonFile(@PathVariable String fileName) {
        Map<String, Object> response = new HashMap<>();

        try {
            File jsonFile = storagePath.resolve(fileName);
            if (!jsonFile.exists()) {
                response.put("success", false);
                response.put("message", "文件不存在");
                return ResponseEntity.badRequest().body(response);
            }

            boolean deleted = jsonFile.delete();
            if (deleted) {
                response.put("success", true);
                response.put("message", "JSON文件删除成功");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "文件删除失败");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 上传JSON文件并创建课堂（存储到json-storage）
    @PostMapping("/upload-json-for-classroom")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadJsonFileForClassroom(
            @RequestParam("file") MultipartFile file,
            @RequestParam("teacherId") String teacherId,
            @RequestParam("classroomData") String classroomData) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 解析课堂数据
            Map<String, Object> classData = objectMapper.readValue(classroomData, Map.class);
            String className = (String) classData.get("name");
            String description = (String) classData.get("description");
            Map<String, Object> settings = (Map<String, Object>) classData.get("settings");
            // 描述为空时用课堂名，避免拼出 "null的视频树结构"
            String treeDescription = (description != null && !description.trim().isEmpty()
                    ? description : className) + "的视频树结构";

            // 上传JSON文件
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "文件为空");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查文件类型
            String contentType = file.getContentType();
            if (!"application/json".equals(contentType)
                    && (file.getOriginalFilename() == null || !file.getOriginalFilename().endsWith(".json"))) {
                response.put("success", false);
                response.put("message", "只支持JSON文件");
                return ResponseEntity.badRequest().body(response);
            }

            // 生成文件名（只取文件名部分，避免 ../ 之类的路径穿越）
            String originalFilename = file.getOriginalFilename() == null
                    ? "upload.json"
                    : file.getOriginalFilename().replaceAll("[\\\\/]", "_");
            String fileName = UUID.randomUUID().toString() + "_" + originalFilename;
            String filePath = storagePath.get() + File.separator + fileName;

            // 保存文件
            File dest = new File(filePath);
            file.transferTo(dest);

            // 读取JSON文件内容
            String jsonContent = JsonText.readUtf8(dest);

            // 从JSON创建视频树
            VideoTree videoTree = videoService.createVideoTreeFromJson(
                    jsonContent,
                    className + "的视频树",
                    treeDescription,
                    teacherId
            );

            if (videoTree == null) {
                response.put("success", false);
                response.put("message", "创建视频树失败");
                return ResponseEntity.badRequest().body(response);
            }

            // 创建课堂
            boolean enableDanmaku = settings != null && Boolean.parseBoolean(settings.getOrDefault("enableDanmaku", true).toString());
            boolean enableComments = settings != null && Boolean.parseBoolean(settings.getOrDefault("enableComments", true).toString());
            boolean autoJump = settings != null && Boolean.parseBoolean(settings.getOrDefault("autoJump", true).toString());
            boolean recordSession = settings != null && Boolean.parseBoolean(settings.getOrDefault("recordSession", true).toString());

            ClassRoom classroom = teacherService.createClassroom(
                    teacherId,
                    className,
                    description != null && !description.trim().isEmpty() ? description : className + "的课堂",
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

            System.out.println("上传JSON文件并创建课堂成功: " + className);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "操作失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
