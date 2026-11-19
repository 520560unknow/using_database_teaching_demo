package com.example.classroom.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentController {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    // 学生加入课堂页面
    @GetMapping("/join")
    public String joinPage(@RequestParam(required = false) String classroomId, Model model) {
        if (classroomId != null && !classroomId.trim().isEmpty()) {
            model.addAttribute("classroomId", classroomId);
        }
        return "student/join"; // 对应 templates/student/join.html
    }

    // 学生加入课堂页面 - 带路径参数版本
    @GetMapping("/join/{classroomId}")
    public String joinClassroom(@PathVariable String classroomId, Model model) {
        model.addAttribute("classroomId", classroomId);
        return "student/join";
    }

    // 学生课堂页面
    @GetMapping("/classroom")
    public String classroomPage(@RequestParam(required = false) String classroomId,
                                @RequestParam(required = false) String studentId,
                                @RequestParam(required = false) String studentName,
                                Model model) {
        if (classroomId != null && !classroomId.trim().isEmpty()) {
            model.addAttribute("classroomId", classroomId);
        }

        // 生成或使用传入的学生ID
        if (studentId != null && !studentId.trim().isEmpty()) {
            model.addAttribute("studentId", studentId);
        } else {
            // 生成随机学生ID
            String randomStudentId = "student_" + System.currentTimeMillis();
            model.addAttribute("studentId", randomStudentId);
        }

        // 昵称仅用于界面展示
        if (studentName != null && !studentName.trim().isEmpty()) {
            model.addAttribute("studentName", studentName);
        }

        // 获取局域网访问地址
        String lanIp = getLanIpAddress();
        String normalizedContextPath = (contextPath == null || "/".equals(contextPath.trim())) ? "" : contextPath.trim();
        if (normalizedContextPath.endsWith("/")) {
            normalizedContextPath = normalizedContextPath.substring(0, normalizedContextPath.length() - 1);
        }
        String accessUrl = "http://" + lanIp + ":" + serverPort + normalizedContextPath;
        model.addAttribute("lanAccessUrl", accessUrl);

        return "student/student";
    }

    // 学生课堂页面 - 带路径参数版本
    @GetMapping("/classroom/{classroomId}")
    public String classroom(@PathVariable String classroomId, Model model) {
        model.addAttribute("classroomId", classroomId);
        return "student/student"; // 修改这里！
    }

    // 学生主页
    @GetMapping("")
    public String studentHome() {
        return "student/dashboard"; // 重定向到仪表盘
    }

    // 学生仪表盘
    @GetMapping("/dashboard")
    public String dashboardPage(@RequestParam(required = false) String studentId, Model model) {
        // 生成或使用传入的学生ID
        if (studentId != null && !studentId.trim().isEmpty()) {
            model.addAttribute("studentId", studentId);
        } else {
            // 生成随机学生ID
            String randomStudentId = "STU" + (1000 + (int)(Math.random() * 9000));
            model.addAttribute("studentId", randomStudentId);
        }
        return "student/dashboard";
    }

    // 学生课堂页面 - 修正路径
    @GetMapping("/class")
    public String classPage(@RequestParam(required = false) String classroomId,
                           @RequestParam(required = false) String studentId,
                           @RequestParam(required = false) String studentName,
                           Model model) {
        if (classroomId != null && !classroomId.trim().isEmpty()) {
            model.addAttribute("classroomId", classroomId);
        }

        // 生成或使用传入的学生ID
        if (studentId != null && !studentId.trim().isEmpty()) {
            model.addAttribute("studentId", studentId);
        } else {
            // 生成随机学生ID
            String randomStudentId = "STU" + (1000 + (int)(Math.random() * 9000));
            model.addAttribute("studentId", randomStudentId);
        }

        if (studentName != null && !studentName.trim().isEmpty()) {
            model.addAttribute("studentName", studentName);
        }

        return "student/student";
    }

    // 学生PPT课堂页面
    @GetMapping("/ppt-class")
    public String pptClassPage(@RequestParam(required = false) String classroomId,
                              @RequestParam(required = false) String studentId,
                              @RequestParam(required = false) String studentName,
                              @RequestParam(required = false) String kdocsUrl,
                              Model model) {
        if (classroomId != null && !classroomId.trim().isEmpty()) {
            model.addAttribute("classroomId", classroomId);
        }

        // 生成或使用传入的学生ID
        if (studentId != null && !studentId.trim().isEmpty()) {
            model.addAttribute("studentId", studentId);
        } else {
            // 生成随机学生ID
            String randomStudentId = "STU" + (1000 + (int)(Math.random() * 9000));
            model.addAttribute("studentId", randomStudentId);
        }

        // 添加kdocsUrl参数
        if (kdocsUrl != null && !kdocsUrl.trim().isEmpty()) {
            model.addAttribute("kdocsUrl", kdocsUrl);
        }

        if (studentName != null && !studentName.trim().isEmpty()) {
            model.addAttribute("studentName", studentName);
        }

        return "student/student";
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