package com.example.classroom.service;

import com.example.classroom.model.ClassRoom;
import com.example.classroom.repository.ClassroomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 教师课堂服务：课堂生命周期、课堂统计与搜索。
 * 课堂实体的持久化委托 {@link ClassroomRepository}，学生会话委托 {@link StudentSessionRegistry}，
 * 对外方法与返回结构保持不变。
 */
@Service
public class TeacherService {

    private static final Logger log = LoggerFactory.getLogger(TeacherService.class);

    private final ClassroomRepository classroomRepository;
    private final StudentSessionRegistry studentSessionRegistry;
    private final DanmakuService danmakuService;

    // 课堂统计（内存）
    private final Map<String, Map<String, Object>> classroomStats = new HashMap<>();

    public TeacherService(ClassroomRepository classroomRepository,
                          StudentSessionRegistry studentSessionRegistry,
                          DanmakuService danmakuService) {
        this.classroomRepository = classroomRepository;
        this.studentSessionRegistry = studentSessionRegistry;
        this.danmakuService = danmakuService;
    }

    @PostConstruct
    public void init() {
        // 为已加载的课堂补齐统计占位
        for (ClassRoom classroom : classroomRepository.findAll()) {
            ensureStats(classroom.getId());
        }
        log.info("TeacherService初始化完成，课堂数: {}", classroomStats.size());
    }

    private Map<String, Object> ensureStats(String classroomId) {
        Map<String, Object> stats = classroomStats.get(classroomId);
        if (stats == null) {
            stats = new HashMap<>();
            stats.put("studentCount", 0);
            stats.put("danmakuCount", 0);
            stats.put("avgWatchTime", 0);
            stats.put("completionRate", 0);
            stats.put("lastActivity", new Date());
            stats.put("activeStudents", 0);
            classroomStats.put(classroomId, stats);
        }
        return stats;
    }

    // 获取课堂
    public ClassRoom getClassroom(String id) {
        return classroomRepository.find(id);
    }

    // 获取教师的课堂列表
    public List<ClassRoom> getTeacherClassrooms(String teacherId) {
        return classroomRepository.findByTeacher(teacherId);
    }

    // 创建课堂
    public ClassRoom createClassroom(String teacherId, String name, String description,
                                     String videoTreeId, boolean enableDanmaku,
                                     boolean enableComments, boolean autoJump, boolean recordSession) {

        String classroomId = "classroom_" + UUID.randomUUID().toString().substring(0, 8);

        ClassRoom classroom = new ClassRoom();
        classroom.setId(classroomId);
        classroom.setName(name);
        classroom.setDescription(description);
        classroom.setTeacherId(teacherId);
        classroom.setVideoTreeId(videoTreeId);
        classroom.setCurrentVideoId(null);
        classroom.setEnableDanmaku(enableDanmaku);
        classroom.setEnableComments(enableComments);
        classroom.setAutoJump(autoJump);
        classroom.setRecordSession(recordSession);
        classroom.setStatus("created");
        classroom.setCreatedTime(new Date());
        classroom.setStartTime(null);
        classroom.setEndTime(null);

        // 保存课堂并登记教师-课堂映射
        classroomRepository.add(classroom);

        // 初始化统计
        ensureStats(classroomId);

        classroomRepository.saveToFile();
        log.info("创建课堂成功: {}, 名称: {}", classroomId, name);
        return classroom;
    }

    // 更新课堂视频树
    public boolean updateClassroomVideoTree(String classroomId, String videoTreeId) {
        ClassRoom classroom = classroomRepository.find(classroomId);
        if (classroom != null) {
            classroom.setVideoTreeId(videoTreeId);
            classroomRepository.saveToFile();
            System.out.println("更新课堂 " + classroomId + " 的视频树为: " + videoTreeId);
            return true;
        }
        return false;
    }

    // 更新当前视频
    public boolean updateCurrentVideo(String classroomId, String videoNodeId) {
        ClassRoom classroom = classroomRepository.find(classroomId);
        if (classroom != null) {
            classroom.setCurrentVideoId(videoNodeId);
            classroomRepository.saveToFile();
            System.out.println("更新课堂 " + classroomId + " 的当前视频为: " + videoNodeId);
            return true;
        }
        System.out.println("课堂不存在: " + classroomId);
        return false;
    }

    // 删除课堂
    public boolean deleteClassroom(String classroomId) {
        if (!classroomRepository.remove(classroomId)) {
            return false;
        }

        classroomStats.remove(classroomId);

        classroomRepository.saveToFile();
        log.info("删除课堂: {}", classroomId);
        return true;
    }

    // 开始课堂
    public ClassRoom startClass(String classroomId) {
        ClassRoom classroom = classroomRepository.find(classroomId);
        if (classroom != null && ("created".equals(classroom.getStatus()) || "paused".equals(classroom.getStatus()))) {
            classroom.setStatus("active");
            classroom.setStartTime(new Date());

            // 初始化一些学生参与记录（模拟数据）
            studentSessionRegistry.ensureInitialized(classroomId);

            classroomRepository.saveToFile();
            System.out.println("课堂开始: " + classroomId);
            return classroom;
        }
        throw new RuntimeException("课堂不存在或无法开始");
    }

    // 暂停课堂
    public ClassRoom pauseClass(String classroomId) {
        ClassRoom classroom = classroomRepository.find(classroomId);
        if (classroom != null && "active".equals(classroom.getStatus())) {
            classroom.setStatus("paused");
            classroomRepository.saveToFile();
            System.out.println("课堂暂停: " + classroomId);
            return classroom;
        }
        throw new RuntimeException("课堂不存在或无法暂停");
    }

    // 继续课堂
    public ClassRoom resumeClass(String classroomId) {
        ClassRoom classroom = classroomRepository.find(classroomId);
        if (classroom != null && "paused".equals(classroom.getStatus())) {
            classroom.setStatus("active");
            classroomRepository.saveToFile();
            System.out.println("课堂继续: " + classroomId);
            return classroom;
        }
        throw new RuntimeException("课堂不存在或无法继续");
    }

    // 结束课堂
    public boolean endClass(String classroomId) {
        ClassRoom classroom = classroomRepository.find(classroomId);
        if (classroom != null && !"ended".equals(classroom.getStatus())) {
            classroom.setStatus("ended");
            classroom.setEndTime(new Date());
            classroomRepository.saveToFile();
            System.out.println("课堂结束: " + classroomId);
            return true;
        }
        return false;
    }

    // 获取课堂统计
    public Map<String, Object> getClassroomStats(String classroomId) {
        Map<String, Object> stats = ensureStats(classroomId);

        // 始终使用DanmakuService中的真实弹幕数量
        Map<String, Object> danmakuStats = danmakuService.getDanmakuStats(classroomId);
        stats.put("danmakuCount", danmakuStats.getOrDefault("totalDanmaku", 0));

        ClassRoom classroom = classroomRepository.find(classroomId);
        if (classroom != null) {
            // 根据课堂状态生成不同的模拟数据
            Random random = new Random(classroomId.hashCode()); // 使用固定种子保证相同课堂ID生成相同数据

            if ("created".equals(classroom.getStatus())) {
                stats.put("studentCount", 0);
                stats.put("avgWatchTime", 0);
                stats.put("completionRate", 0);
                stats.put("activeStudents", 0);
            } else if ("active".equals(classroom.getStatus())) {
                int baseStudents = 15 + random.nextInt(35); // 15-49个学生
                stats.put("studentCount", baseStudents);
                stats.put("avgWatchTime", 15 + random.nextInt(45)); // 15-60分钟
                stats.put("completionRate", 30 + random.nextInt(50)); // 30-80%完成率
                stats.put("activeStudents", 5 + random.nextInt(15)); // 5-20个活跃学生
            } else if ("ended".equals(classroom.getStatus())) {
                int baseStudents = 20 + random.nextInt(80); // 20-99个学生
                stats.put("studentCount", baseStudents);
                stats.put("avgWatchTime", 30 + random.nextInt(90)); // 30-120分钟
                stats.put("completionRate", 60 + random.nextInt(40)); // 60-100%完成率
                stats.put("activeStudents", 0);
            }

            // 如果有学生会话数据，使用更准确的数据
            if (studentSessionRegistry.contains(classroomId)) {
                List<Map<String, Object>> sessions = studentSessionRegistry.get(classroomId);
                if (!sessions.isEmpty()) {
                    stats.put("studentCount", sessions.size());

                    // 计算平均观看时间
                    double totalWatchTime = 0;
                    int completedCount = 0;

                    for (Map<String, Object> session : sessions) {
                        totalWatchTime += (int) session.get("watchTime");
                        if ((boolean) session.get("completed")) {
                            completedCount++;
                        }
                    }

                    stats.put("avgWatchTime", (int) (totalWatchTime / sessions.size()));
                    stats.put("completionRate", (int) ((double) completedCount / sessions.size() * 100));
                }
            }
        }

        return stats;
    }

    // 搜索教师课堂
    public List<ClassRoom> searchTeacherClassrooms(String teacherId, String keyword, String status) {
        List<ClassRoom> allClassrooms = getTeacherClassrooms(teacherId);
        List<ClassRoom> filteredClassrooms = new ArrayList<>();

        for (ClassRoom classroom : allClassrooms) {
            boolean matches = true;

            // 关键词搜索
            if (keyword != null && !keyword.trim().isEmpty()) {
                String lowerKeyword = keyword.toLowerCase();
                matches = classroom.getName().toLowerCase().contains(lowerKeyword) ||
                        (classroom.getDescription() != null &&
                                classroom.getDescription().toLowerCase().contains(lowerKeyword)) ||
                        classroom.getId().toLowerCase().contains(lowerKeyword);
            }

            // 状态过滤
            if (status != null && !status.trim().isEmpty() && !"all".equals(status)) {
                matches = matches && status.equals(classroom.getStatus());
            }

            if (matches) {
                filteredClassrooms.add(classroom);
            }
        }

        return filteredClassrooms;
    }

    // 获取学生会话记录
    public List<Map<String, Object>> getStudentSessions(String classroomId) {
        return studentSessionRegistry.get(classroomId);
    }

    // 添加学生参与记录
    public void addStudentSession(String classroomId, Map<String, Object> session) {
        studentSessionRegistry.add(classroomId, session);

        // 更新课堂统计
        Map<String, Object> stats = classroomStats.get(classroomId);
        if (stats != null) {
            stats.put("studentCount", studentSessionRegistry.get(classroomId).size());
        }
    }

    // 更新学生观看时间
    public void updateStudentWatchTime(String classroomId, String studentId, int watchTime) {
        studentSessionRegistry.updateWatchTime(classroomId, studentId, watchTime);
    }

    // 标记学生完成
    public void markStudentCompleted(String classroomId, String studentId) {
        studentSessionRegistry.markCompleted(classroomId, studentId);
    }

    // 获取所有课堂（用于调试）
    public List<ClassRoom> getAllClassrooms() {
        return classroomRepository.findAll();
    }

    // 获取所有教师课堂ID
    public List<String> getTeacherClassroomIds(String teacherId) {
        return classroomRepository.findIdsByTeacher(teacherId);
    }
}
