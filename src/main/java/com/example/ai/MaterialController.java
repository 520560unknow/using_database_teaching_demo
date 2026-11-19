package com.example.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class MaterialController {

    @Autowired
    private RagService ragService;

    @Autowired
    private AiStoreService storeService;

    @PostMapping("/material")
    public Map<String, Object> addMaterial(@RequestBody Map<String, String> request) {
        String title = trim(request.get("title"), 80);
        String content = trim(request.get("content"), 20000);
        String classroomId = trim(request.get("classroomId"), 100);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (content.isEmpty()) {
            result.put("error", "资料内容不能为空");
            return result;
        }
        boolean ok = ragService.addMaterial(title, content, classroomId);
        result.put("ok", ok);
        if (!ok) {
            result.put("error", "资料保存失败");
        } else {
            result.put("message", "资料已入库，学生可立即检索");
        }
        return result;
    }

    @PostMapping("/material/upload")
    public Map<String, Object> uploadMaterial(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "classroomId", required = false) String classroomId) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (file == null || file.isEmpty()) {
            result.put("error", "请选择文件");
            return result;
        }
        String original = file.getOriginalFilename() == null ? "资料" : file.getOriginalFilename();
        String lower = original.toLowerCase();
        if (!lower.endsWith(".md") && !lower.endsWith(".txt")) {
            result.put("error", "当前支持 .txt / .md 文本资料，Word/PDF 请先转成文本");
            return result;
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            String title = original.replaceAll("(?i)\\.(md|txt)$", "");
            boolean ok = ragService.addMaterial(title, content, trim(classroomId, 100));
            result.put("ok", ok);
            if (ok) {
                result.put("message", "已导入 " + original + "，学生可立即检索");
            } else {
                result.put("error", "文件为空或解析失败");
            }
        } catch (Exception e) {
            result.put("error", "读取文件失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/materials")
    public Map<String, Object> materials(@RequestParam(value = "classroomId", required = false) String classroomId) {
        List<Map<String, Object>> items = ragService.listMaterials(trim(classroomId, 100));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        int chunks = 0;
        for (Map<String, Object> item : items) {
            Object c = item.get("chunks");
            if (c instanceof Number) {
                chunks += ((Number) c).intValue();
            }
        }
        result.put("items", items);
        result.put("fileCount", items.size());
        result.put("chunkCount", chunks);
        return result;
    }

    @DeleteMapping("/material/delete")
    public Map<String, Object> deleteMaterial(@RequestParam(value = "name", required = false) String name,
                                              @RequestParam(value = "classroomId", required = false) String classroomId) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        boolean ok = ragService.removeMaterial(trim(name, 200), trim(classroomId, 100));
        result.put("ok", ok);
        if (!ok) {
            result.put("error", "删除失败");
        }
        return result;
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", storeService.recentLogs(Math.min(Math.max(limit, 1), 200)));
        return result;
    }

    private String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
