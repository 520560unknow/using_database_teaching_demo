package com.example.classroom.controller;

import com.example.classroom.service.WorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Controller
public class WorkspaceController {

    @Autowired
    private WorkspaceService workspaceService;

    // 教师端「视频与数据包」页面
    @GetMapping("/teacher/workspace")
    public String page(Model model,
                       @RequestParam(defaultValue = "${app.identity.default-teacher-id:teacher_001}") String teacherId) {
        model.addAttribute("teacherId", teacherId);
        return "teacher/workspace";
    }

    @GetMapping("/api/workspace/info")
    @ResponseBody
    public Map<String, Object> info() {
        return workspaceService.info();
    }

    @GetMapping("/api/workspace/export")
    public void export(HttpServletResponse response) throws Exception {
        String fileName = "classroom-workspace-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".zip";
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''"
                        + URLEncoder.encode(fileName, "UTF-8"));
        workspaceService.exportZip(response.getOutputStream());
        response.flushBuffer();
    }

    @PostMapping("/api/workspace/import")
    @ResponseBody
    public Map<String, Object> importZip(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        if (file == null || file.isEmpty()) {
            result.put("success", false);
            result.put("message", "请选择要导入的 zip 压缩包");
            return result;
        }
        try {
            result.putAll(workspaceService.importZip(file));
            result.put("message", "导入完成");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "导入失败：" + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/api/workspace/clear")
    @ResponseBody
    public Map<String, Object> clear() {
        workspaceService.clearAll();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已清空视频与视频树数据");
        return result;
    }

    @DeleteMapping("/api/workspace/video")
    @ResponseBody
    public Map<String, Object> deleteVideo(@RequestParam("name") String name) {
        boolean deleted = workspaceService.deleteVideo(name);
        Map<String, Object> result = new HashMap<>();
        result.put("success", deleted);
        result.put("message", deleted ? "视频已删除" : "视频不存在或名称非法");
        return result;
    }
}
