package com.example.classroom.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;

@Controller
@RequestMapping("/json-storage")
public class JsonStorageController {

    @Value("${json.storage.enabled}")
    private boolean storageEnabled;

    @Value("${json.storage.directory}")
    private String storageDirectory;

    // 初始化方法，确保存储目录存在
    @javax.annotation.PostConstruct
    public void init() {
        if (storageEnabled) {
            // 处理存储路径
            File dir = new File(storageDirectory);
            if (!dir.isAbsolute()) {
                // 转换为相对于项目根目录的绝对路径
                String projectRoot = System.getProperty("user.dir");
                storageDirectory = projectRoot + File.separator + storageDirectory;
                System.out.println("转换后的JSON存储路径: " + storageDirectory);
            }
            
            // 确保目录存在
            File finalDir = new File(storageDirectory);
            if (!finalDir.exists()) {
                boolean created = finalDir.mkdirs();
                System.out.println("JSON存储目录创建结果: " + created);
            }
        }
    }

    // 存储JSON文件
    @PostMapping("/upload")
    @ResponseBody
    public Map<String, Object> uploadJsonFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("teacherId") String teacherId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!storageEnabled) {
                response.put("success", false);
                response.put("error", "JSON存储功能未启用");
                return response;
            }

            if (file.isEmpty()) {
                response.put("success", false);
                response.put("error", "文件为空");
                return response;
            }

            // 检查文件类型
            String contentType = file.getContentType();
            if (!"application/json".equals(contentType)
                    && (file.getOriginalFilename() == null || !file.getOriginalFilename().endsWith(".json"))) {
                response.put("success", false);
                response.put("error", "只支持JSON文件");
                return response;
            }

            // 检查文件大小 (5MB限制)
            if (file.getSize() > 5 * 1024 * 1024) {
                response.put("success", false);
                response.put("error", "文件大小不能超过5MB");
                return response;
            }

            // 生成文件名（只取文件名部分，避免 ../ 之类的路径穿越）
            String originalFilename = file.getOriginalFilename() == null
                    ? "upload.json"
                    : file.getOriginalFilename().replaceAll("[\\\\/]", "_");
            String fileName = UUID.randomUUID().toString() + "_" + originalFilename;
            String filePath = storageDirectory + File.separator + fileName;

            // 保存文件
            File dest = new File(filePath);
            file.transferTo(dest);

            // 构建返回结果
            response.put("success", true);
            response.put("fileId", UUID.randomUUID().toString());
            response.put("fileName", fileName);
            response.put("originalFilename", originalFilename);
            response.put("name", name);
            response.put("teacherId", teacherId);
            response.put("url", "/json-storage/get/" + fileName);
            response.put("path", filePath);
            response.put("size", file.getSize());
            response.put("timestamp", new Date().getTime());

            System.out.println("JSON文件上传成功: " + name + " - " + fileName);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "上传失败：" + e.getMessage());
        }

        return response;
    }

    // 获取存储的JSON文件列表
    @GetMapping("/list")
    @ResponseBody
    public Map<String, Object> getJsonFiles(@RequestParam("teacherId") String teacherId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!storageEnabled) {
                response.put("success", false);
                response.put("error", "JSON存储功能未启用");
                return response;
            }

            List<Map<String, Object>> files = new ArrayList<>();
            File dir = new File(storageDirectory);
            
            if (dir.exists() && dir.isDirectory()) {
                File[] fileArray = dir.listFiles((d, name) -> name.endsWith(".json"));
                if (fileArray != null) {
                    for (File file : fileArray) {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("id", UUID.randomUUID().toString());
                        fileInfo.put("name", file.getName().replaceAll("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}_", ""));
                        fileInfo.put("fileName", file.getName());
                        fileInfo.put("path", file.getAbsolutePath());
                        fileInfo.put("size", file.length());
                        fileInfo.put("url", "/json-storage/get/" + file.getName());
                        fileInfo.put("timestamp", file.lastModified());
                        files.add(fileInfo);
                    }
                }
            }

            response.put("success", true);
            response.put("files", files);
            response.put("total", files.size());
            response.put("teacherId", teacherId);

            System.out.println("获取JSON文件列表成功，共" + files.size() + "个文件");
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "获取文件列表失败：" + e.getMessage());
        }

        return response;
    }

    // 获取指定的JSON文件
    @GetMapping("/get/{fileName}")
    public void getJsonFile(@PathVariable String fileName, HttpServletResponse response) {
        try {
            if (!storageEnabled) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.getWriter().write("{\"error\": \"JSON存储功能未启用\"}");
                return;
            }

            File jsonFile = new File(storageDirectory + File.separator + fileName);
            if (!jsonFile.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\": \"文件不存在\"}");
                return;
            }

            // 设置响应头
            response.setContentType("application/json");
            response.setContentLengthLong(jsonFile.length());
            response.setHeader("Content-Disposition", "inline; filename=\"" + URLEncoder.encode(fileName, "UTF-8") + "\"");

            // 读取文件并写入响应流
            try (InputStream inputStream = new FileInputStream(jsonFile);
                 OutputStream outputStream = response.getOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
            }

            System.out.println("获取JSON文件成功: " + fileName);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\": \"获取文件失败：" + e.getMessage() + "\"}");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // 删除JSON文件
    @PostMapping("/delete")
    @ResponseBody
    public Map<String, Object> deleteJsonFile(@RequestParam("fileName") String fileName) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!storageEnabled) {
                response.put("success", false);
                response.put("error", "JSON存储功能未启用");
                return response;
            }

            File jsonFile = new File(storageDirectory + File.separator + fileName);
            if (!jsonFile.exists()) {
                response.put("success", false);
                response.put("error", "文件不存在");
                return response;
            }

            boolean deleted = jsonFile.delete();
            if (deleted) {
                response.put("success", true);
                response.put("message", "文件删除成功");
                System.out.println("删除JSON文件成功: " + fileName);
            } else {
                response.put("success", false);
                response.put("error", "文件删除失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "删除失败：" + e.getMessage());
        }

        return response;
    }
}
