package com.example.classroom.repository;

import com.example.classroom.model.ClassRoom;
import com.example.support.JsonStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 课堂仓储：内存 Map + classrooms.json 持久化。
 * 只负责课堂实体与「教师-课堂」映射的读写，文件字段 classrooms / teacherClassrooms 保持不变；
 * 课堂统计与学生会话分别由 TeacherService、StudentSessionRegistry 负责。
 */
@Component
public class ClassroomRepository {

    private static final Logger log = LoggerFactory.getLogger(ClassroomRepository.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${json.storage.directory:json-storage}")
    private String storageDir;

    // 课堂存储（内存）
    private final Map<String, ClassRoom> classrooms = new ConcurrentHashMap<>();

    // 教师-课堂映射（内存）
    private final Map<String, List<String>> teacherClassrooms = new ConcurrentHashMap<>();

    // 持久化文件路径
    private String persistenceFile;

    @PostConstruct
    public void init() {
        try {
            File dir = new File(storageDir);
            if (!dir.isAbsolute()) {
                dir = new File(System.getProperty("user.dir"), storageDir);
            }
            dir.mkdirs();
            persistenceFile = new File(dir, "classrooms.json").getAbsolutePath();
            loadFromFile();
            log.info("ClassroomRepository初始化完成，持久化文件: {}", persistenceFile);
        } catch (Exception e) {
            log.warn("课堂持久化文件初始化失败，使用纯内存模式: {}", e.getMessage());
        }
    }

    public ClassRoom find(String classroomId) {
        return classrooms.get(classroomId);
    }

    /** 按教师取课堂，按创建时间倒序。 */
    public List<ClassRoom> findByTeacher(String teacherId) {
        List<String> classroomIds = teacherClassrooms.getOrDefault(teacherId, new ArrayList<>());
        List<ClassRoom> result = new ArrayList<>();

        for (String id : classroomIds) {
            ClassRoom classroom = classrooms.get(id);
            if (classroom != null) {
                result.add(classroom);
            }
        }

        result.sort((a, b) -> b.getCreatedTime().compareTo(a.getCreatedTime()));
        return result;
    }

    /** 新增课堂并登记到教师的课堂列表（不落盘，由调用方决定何时 saveToFile）。 */
    public void add(ClassRoom classroom) {
        classrooms.put(classroom.getId(), classroom);
        teacherClassrooms
                .computeIfAbsent(classroom.getTeacherId(), k -> new CopyOnWriteArrayList<>())
                .add(classroom.getId());
    }

    public boolean remove(String classroomId) {
        ClassRoom classroom = classrooms.get(classroomId);
        if (classroom == null) {
            return false;
        }

        List<String> teacherClassroomList = teacherClassrooms.get(classroom.getTeacherId());
        if (teacherClassroomList != null) {
            teacherClassroomList.remove(classroomId);
        }
        classrooms.remove(classroomId);
        return true;
    }

    public List<ClassRoom> findAll() {
        return new ArrayList<>(classrooms.values());
    }

    public List<String> findIdsByTeacher(String teacherId) {
        return new ArrayList<>(teacherClassrooms.getOrDefault(teacherId, new ArrayList<>()));
    }

    public synchronized void saveToFile() {
        try {
            if (persistenceFile == null) {
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("classrooms", classrooms);
            data.put("teacherClassrooms", teacherClassrooms);
            JsonStore.write(new File(persistenceFile).toPath(), data, objectMapper);
        } catch (Exception e) {
            log.error("保存课堂数据到文件失败: {}", persistenceFile, e);
        }
    }

    private void loadFromFile() {
        try {
            if (persistenceFile == null) {
                return;
            }
            File file = new File(persistenceFile);
            if (!file.exists()) {
                log.info("持久化文件不存在，从空数据启动");
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = JsonStore.read(file.toPath(), Map.class, objectMapper);

            // 恢复 classrooms
            if (data.containsKey("classrooms")) {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> rawClassrooms = (Map<String, Map<String, Object>>) data.get("classrooms");
                for (Map.Entry<String, Map<String, Object>> entry : rawClassrooms.entrySet()) {
                    ClassRoom cr = objectMapper.convertValue(entry.getValue(), ClassRoom.class);
                    classrooms.put(entry.getKey(), cr);
                }
            }

            // 恢复 teacherClassrooms
            if (data.containsKey("teacherClassrooms")) {
                @SuppressWarnings("unchecked")
                Map<String, List<String>> rawMapping = (Map<String, List<String>>) data.get("teacherClassrooms");
                teacherClassrooms.putAll(rawMapping);
            }

            log.info("从文件恢复了 {} 个课堂数据", classrooms.size());
        } catch (Exception e) {
            log.error("从文件加载课堂数据失败: {}", persistenceFile, e);
        }
    }
}
