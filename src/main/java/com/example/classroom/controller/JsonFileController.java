package com.example.classroom.controller;

import com.example.classroom.service.JsonFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.UUID;

@Controller
@RequestMapping("/json")
public class JsonFileController {

    @Autowired
    private JsonFileService jsonFileService;

    @Value("${json.upload.path}")
    private String jsonUploadPath;

    // 初始化方法，确保路径是绝对路径
    @javax.annotation.PostConstruct
    public void init() {
        // 处理上传路径
        File uploadDir = new File(jsonUploadPath);
        if (!uploadDir.isAbsolute()) {
            // 转换为相对于项目根目录的绝对路径
            String projectRoot = System.getProperty("user.dir");
            jsonUploadPath = projectRoot + File.separator + jsonUploadPath;
            System.out.println("转换后的JSON上传路径: " + jsonUploadPath);
        }
        
        // 确保目录存在
        File finalUploadDir = new File(jsonUploadPath);
        if (!finalUploadDir.exists()) {
            boolean created = finalUploadDir.mkdirs();
            System.out.println("JSON上传目录创建结果: " + created);
        }
    }

    // 存储JSON文件
    @PostMapping("/save")
    public void saveJsonFile(@RequestParam("file") MultipartFile file, 
                              @RequestParam("name") String name, 
                              @RequestParam("teacherId") String teacherId, 
                              HttpServletResponse response) {
        try {
            if (file.isEmpty()) {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                PrintWriter out = response.getWriter();
                out.print("{\"error\": \"文件为空\"}");
                out.flush();
                return;
            }

            // 检查文件类型
            String contentType = file.getContentType();
            if (!"application/json".equals(contentType)
                    && (file.getOriginalFilename() == null || !file.getOriginalFilename().endsWith(".json"))) {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                PrintWriter out = response.getWriter();
                out.print("{\"error\": \"只支持JSON文件\"}");
                out.flush();
                return;
            }

            // 检查文件大小 (5MB限制)
            if (file.getSize() > 5 * 1024 * 1024) {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                PrintWriter out = response.getWriter();
                out.print("{\"error\": \"文件大小不能超过5MB\"}");
                out.flush();
                return;
            }

            // 确保上传目录存在
            File uploadDir = new File(jsonUploadPath);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                if (!created) {
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    PrintWriter out = response.getWriter();
                    out.print("{\"error\": \"无法创建上传目录\"}");
                    out.flush();
                    return;
                }
            }

            // 生成文件名（只取文件名部分，避免 ../ 之类的路径穿越）
            String originalFilename = file.getOriginalFilename() == null
                    ? "upload.json"
                    : file.getOriginalFilename().replaceAll("[\\\\/]", "_");
            String fileName = UUID.randomUUID().toString() + "_" + originalFilename;
            String filePath = jsonUploadPath + File.separator + fileName;

            // 保存文件
            File dest = new File(filePath);
            file.transferTo(dest);

            // 保存JSON文件信息
            String jsonFileId = jsonFileService.saveJsonFile(name, originalFilename, fileName, filePath, file.getSize(), teacherId);

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            PrintWriter out = response.getWriter();
            out.print("{\"success\": true, \"jsonFileId\": \"" + jsonFileId + "\", \"url\": \"/json/stream/" + fileName + "\"}");
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                PrintWriter out = response.getWriter();
                out.print("{\"error\": \"保存失败：" + e.getMessage() + "\"}");
                out.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // 获取所有存储的JSON文件
    @GetMapping("/list")
    public void getJsonFiles(@RequestParam("teacherId") String teacherId, HttpServletResponse response) {
        try {
            System.out.println("获取JSON文件列表，teacherId: " + teacherId);
            String result = jsonFileService.getJsonFilesByTeacherId(teacherId);
            System.out.println("JSON文件列表返回结果: " + result);
            
            // 设置响应头
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            
            // 直接写入响应流
            PrintWriter out = response.getWriter();
            out.print(result);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                PrintWriter out = response.getWriter();
                out.print("{\"error\": \"获取文件列表失败：" + e.getMessage() + "\"}");
                out.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // JSON文件流处理
    @GetMapping("/stream/{fileName}")
    public void streamJson(@PathVariable String fileName, HttpServletResponse response) {
        File jsonFile = new File(jsonUploadPath + File.separator + fileName);
        streamJsonFile(jsonFile, response);
    }

    // JSON文件流处理通用方法
    private void streamJsonFile(File jsonFile, HttpServletResponse response) {
        if (!jsonFile.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            // 设置响应头
            response.setContentType("application/json");
            response.setContentLengthLong(jsonFile.length());
            response.setHeader("Content-Disposition", "inline; filename=\"" + URLEncoder.encode(jsonFile.getName(), "UTF-8") + "\"");

            // 读取文件并写入响应流
            try (InputStream inputStream = new FileInputStream(jsonFile);
                 OutputStream outputStream = response.getOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    try {
                        outputStream.write(buffer, 0, bytesRead);
                        outputStream.flush(); // 定期刷新，避免缓冲区溢出
                    } catch (IOException e) {
                        // 客户端中止连接，这是正常情况，不需要记录错误
                        System.out.println("客户端中止JSON流连接: " + e.getMessage());
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // 其他异常，记录错误但不向客户端发送错误状态码
            // 因为响应可能已经开始发送
            System.out.println("JSON流处理异常: " + e.getMessage());
        }
    }

    // 删除JSON文件
    @PostMapping("/delete")
    public void deleteJsonFile(@RequestParam("id") String id, HttpServletResponse response) {
        try {
            boolean success = jsonFileService.deleteJsonFile(id);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            PrintWriter out = response.getWriter();
            if (success) {
                out.print("{\"success\": true, \"message\": \"JSON文件删除成功\"}");
            } else {
                out.print("{\"success\": false, \"error\": \"JSON文件不存在\"}");
            }
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                PrintWriter out = response.getWriter();
                out.print("{\"success\": false, \"error\": \"删除失败：" + e.getMessage() + "\"}");
                out.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
