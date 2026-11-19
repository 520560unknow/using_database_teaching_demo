package com.example.ai;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AiPageController {

    @GetMapping("/student/ai")
    public String studentAi() {
        return "student/ai-preview";
    }

    @GetMapping("/teacher/ai")
    public String teacherAi() {
        return "teacher/ai";
    }

    @GetMapping("/teacher/analytics")
    public String teacherAnalytics() {
        return "teacher/analytics";
    }
}
