package com.example.classroom.controller;

import com.example.classroom.model.VideoNode;
import com.example.classroom.model.VideoTree;
import com.example.classroom.service.VideoTreeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/video-tree")
public class VideoTreeController {

    @Autowired
    private VideoTreeService videoService;

    // 视频树编辑器页面
    @GetMapping("/editor")
    public String editor(Model model, @RequestParam(defaultValue = "${app.identity.default-teacher-id:teacher_001}") String teacherId) {
        List<VideoTree> videoTrees = videoService.getVideoTreesByTeacher(teacherId);
        model.addAttribute("videoTrees", videoTrees);
        model.addAttribute("teacherId", teacherId);
        return "video-tree/editor";
    }

    // 创建视频树
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createVideoTree(@RequestBody VideoTree tree) {
        try {
            VideoTree createdTree = videoService.createVideoTree(tree);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tree", createdTree);
            response.put("message", "视频树创建成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 更新视频树
    @PostMapping("/update/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateVideoTree(
            @PathVariable String id,
            @RequestBody VideoTree tree) {
        try {
            tree.setId(id);
            VideoTree updatedTree = videoService.updateVideoTree(id, tree);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tree", updatedTree);
            response.put("message", "视频树更新成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 获取视频树
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<VideoTree> getVideoTree(@PathVariable String id) {
        VideoTree tree = videoService.getVideoTree(id);
        if (tree != null) {
            return ResponseEntity.ok(tree);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 获取教师的所有视频树
    @GetMapping("/teacher/{teacherId}")
    @ResponseBody
    public ResponseEntity<List<VideoTree>> getVideoTreesByTeacher(@PathVariable String teacherId) {
        List<VideoTree> trees = videoService.getVideoTreesByTeacher(teacherId);
        return ResponseEntity.ok(trees);
    }

    // 删除视频树
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteVideoTree(@PathVariable String id) {
        try {
            boolean success = videoService.deleteVideoTree(id);
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("success", true);
                response.put("message", "视频树删除成功");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "视频树不存在");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 获取视频节点
    @GetMapping("/node/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getVideoNode(@PathVariable String id) {
        VideoNode node = videoService.getVideoNode(id);
        Map<String, Object> response = new HashMap<>();
        if (node != null) {
            response.put("success", true);
            response.put("node", node);
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "节点不存在");
            return ResponseEntity.badRequest().body(response);
        }
    }
}