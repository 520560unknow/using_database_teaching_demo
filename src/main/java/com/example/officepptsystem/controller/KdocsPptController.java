package com.example.officepptsystem.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/kdocs")
public class KdocsPptController {

    @GetMapping("")
    public String kdocsDashboard(Model model,
                               @RequestParam(defaultValue = "${app.identity.default-teacher-id:teacher_001}") String teacherId) {
        model.addAttribute("teacherId", teacherId);
        model.addAttribute("teacherName", "教师");
        return "kdocs/dashboard";
    }

    @GetMapping("/play")
    public String playPpt(Model model,
                         @RequestParam(defaultValue = "${app.identity.default-teacher-id:teacher_001}") String teacherId,
                         @RequestParam(required = false) String classroomId,
                         @RequestParam(required = false) String kdocsUrl) {
        model.addAttribute("teacherId", teacherId);
        model.addAttribute("classroomId", classroomId);
        // 如果有传入的kdocsUrl，则使用传入的，否则使用默认值
        if (kdocsUrl != null && !kdocsUrl.trim().isEmpty()) {
            model.addAttribute("kdocsUrl", kdocsUrl);
        } else {
            model.addAttribute("kdocsUrl", "https://www.kdocs.cn/l/crxBk4VXdbN9");
        }
        return "kdocs/play";
    }

    @GetMapping("/student")
    public String studentView(Model model,
                             @RequestParam(defaultValue = "${app.identity.default-student-id:student_001}") String studentId,
                             @RequestParam(required = false) String nickname,
                             @RequestParam(defaultValue = "${app.identity.default-teacher-id:teacher_001}") String teacherId,
                             @RequestParam(required = false) String classroomId,
                             @RequestParam(required = false) String kdocsUrl) {
        // 重定向到视频学生端页面
        if (classroomId != null) {
            return "redirect:/student/class?classroomId=" + classroomId + "&studentId=" + studentId;
        } else {
            return "redirect:/student/join";
        }
    }


}