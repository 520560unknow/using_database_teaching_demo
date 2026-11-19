package com.example.classroom.service;

import com.example.classroom.model.JsonFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class JsonFileService {

    // JSON文件存储
    private Map<String, JsonFile> jsonFiles = new HashMap<>();

    // 教师-JSON文件映射
    private Map<String, List<String>> teacherJsonFiles = new HashMap<>();

    private ObjectMapper objectMapper = new ObjectMapper();

    // 持久化文件路径
    private static final String PERSISTENCE_FILE = "json-files.json";

    // 初始化方法，从持久化文件加载数据
    @javax.annotation.PostConstruct
    public void init() {
        loadFromPersistence();
    }

    // 从持久化文件加载数据
    private void loadFromPersistence() {
        System.out.println("开始从持久化文件加载JSON文件数据...");
        try {
            File file = new File(PERSISTENCE_FILE);
            System.out.println("持久化文件路径: " + file.getAbsolutePath());
            System.out.println("持久化文件是否存在: " + file.exists());
            if (file.exists()) {
                System.out.println("持久化文件大小: " + file.length() + " 字节");
                try (FileInputStream fis = new FileInputStream(file);
                     ObjectInputStream ois = new ObjectInputStream(fis)) {
                    // 读取存储的数据
                    Map<String, Object> data = (Map<String, Object>) ois.readObject();
                    System.out.println("成功读取持久化文件数据");
                    
                    // 读取JSON文件
                    Map<String, JsonFile> loadedJsonFiles = (Map<String, JsonFile>) data.get("jsonFiles");
                    if (loadedJsonFiles != null) {
                        jsonFiles.putAll(loadedJsonFiles);
                        System.out.println("加载JSON文件数量: " + loadedJsonFiles.size());
                        // 打印每个JSON文件的详细信息
                        for (Map.Entry<String, JsonFile> entry : loadedJsonFiles.entrySet()) {
                            JsonFile jsonFile = entry.getValue();
                            System.out.println("加载的JSON文件: " + jsonFile.getName() + " (" + jsonFile.getId() + "), teacherId: " + jsonFile.getTeacherId());
                        }
                    } else {
                        System.out.println("未找到jsonFiles数据");
                    }
                    
                    // 读取教师-JSON文件映射
                    Map<String, List<String>> loadedTeacherJsonFiles = (Map<String, List<String>>) data.get("teacherJsonFiles");
                    if (loadedTeacherJsonFiles != null) {
                        teacherJsonFiles.putAll(loadedTeacherJsonFiles);
                        System.out.println("加载教师-JSON文件映射数量: " + loadedTeacherJsonFiles.size());
                        // 打印每个教师的文件数量
                        for (Map.Entry<String, List<String>> entry : loadedTeacherJsonFiles.entrySet()) {
                            System.out.println("教师 " + entry.getKey() + " 有 " + entry.getValue().size() + " 个JSON文件");
                            // 打印每个教师的文件ID
                            System.out.println("文件ID列表: " + entry.getValue());
                        }
                    } else {
                        System.out.println("未找到teacherJsonFiles数据");
                    }
                    
                    System.out.println("从持久化文件加载JSON文件数据成功，共" + jsonFiles.size() + "个文件");
                }
            } else {
                System.out.println("持久化文件不存在，开始使用空数据");
            }
        } catch (Exception e) {
            System.out.println("从持久化文件加载数据失败: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("加载完成后，jsonFiles大小: " + jsonFiles.size());
        System.out.println("加载完成后，teacherJsonFiles大小: " + teacherJsonFiles.size());
        // 打印teacherJsonFiles的所有键
        System.out.println("teacherJsonFiles的所有教师ID: " + teacherJsonFiles.keySet());
    }

    // 持久化数据到文件
    private void persistData() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("jsonFiles", jsonFiles);
            data.put("teacherJsonFiles", teacherJsonFiles);
            
            try (FileOutputStream fos = new FileOutputStream(PERSISTENCE_FILE);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(data);
            }
            
            System.out.println("JSON文件数据持久化成功，共" + jsonFiles.size() + "个文件");
        } catch (Exception e) {
            System.out.println("数据持久化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 保存JSON文件信息
    public String saveJsonFile(String name, String originalFilename, String fileName, String filePath, long fileSize, String teacherId) {
        // 生成唯一ID
        String id = UUID.randomUUID().toString();

        // 创建JSON文件对象
        JsonFile jsonFile = new JsonFile();
        jsonFile.setId(id);
        jsonFile.setName(name);
        jsonFile.setOriginalFilename(originalFilename);
        jsonFile.setFileName(fileName);
        jsonFile.setFilePath(filePath);
        jsonFile.setUrl("/json/stream/" + fileName);
        jsonFile.setFileSize(fileSize);
        jsonFile.setTeacherId(teacherId);

        // 保存到存储
        jsonFiles.put(id, jsonFile);

        // 更新教师-JSON文件映射
        List<String> teacherFiles = teacherJsonFiles.computeIfAbsent(teacherId, k -> new ArrayList<>());
        if (!teacherFiles.contains(id)) {
            teacherFiles.add(id);
        }

        // 持久化数据
        persistData();

        System.out.println("保存JSON文件成功: " + id + " - " + name);
        return id;
    }

    // 根据教师ID获取JSON文件列表
    public String getJsonFilesByTeacherId(String teacherId) {
        try {
            System.out.println("JsonFileService.getJsonFilesByTeacherId called with teacherId: " + teacherId);
            System.out.println("Current jsonFiles size: " + jsonFiles.size());
            System.out.println("Current teacherJsonFiles size: " + teacherJsonFiles.size());
            System.out.println("TeacherJsonFiles contains key: " + teacherJsonFiles.containsKey(teacherId));
            
            List<JsonFile> teacherFileList = new ArrayList<>();

            // 获取教师的JSON文件
            List<String> fileIds = teacherJsonFiles.get(teacherId);
            System.out.println("FileIds for teacher: " + (fileIds != null ? fileIds.size() : 0));
            
            if (fileIds != null) {
                for (String fileId : fileIds) {
                    JsonFile jsonFile = jsonFiles.get(fileId);
                    if (jsonFile != null) {
                        teacherFileList.add(jsonFile);
                        System.out.println("Found JSON file: " + jsonFile.getName() + " (" + jsonFile.getId() + ")");
                    }
                }
            }

            System.out.println("Total JSON files found: " + teacherFileList.size());
            
            // 转换为JSON
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("jsonFiles", teacherFileList);

            String result = objectMapper.writeValueAsString(response);
            System.out.println("JSON response: " + result);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"获取文件列表失败：" + e.getMessage() + "\"}";
        }
    }

    // 根据ID获取JSON文件
    public JsonFile getJsonFileById(String id) {
        return jsonFiles.get(id);
    }

    // 删除JSON文件
    public boolean deleteJsonFile(String id) {
        JsonFile jsonFile = jsonFiles.remove(id);
        if (jsonFile != null) {
            // 从教师映射中移除
            List<String> teacherFiles = teacherJsonFiles.get(jsonFile.getTeacherId());
            if (teacherFiles != null) {
                teacherFiles.remove(id);
            }

            // 删除物理文件
            java.io.File file = new java.io.File(jsonFile.getFilePath());
            if (file.exists()) {
                file.delete();
            }

            // 持久化数据
            persistData();

            System.out.println("删除JSON文件成功: " + id);
            return true;
        }
        return false;
    }

    // 更新JSON文件信息
    public JsonFile updateJsonFile(String id, JsonFile updatedFile) {
        JsonFile existingFile = jsonFiles.get(id);
        if (existingFile != null) {
            // 更新信息
            if (updatedFile.getName() != null) {
                existingFile.setName(updatedFile.getName());
            }
            if (updatedFile.getOriginalFilename() != null) {
                existingFile.setOriginalFilename(updatedFile.getOriginalFilename());
            }
            if (updatedFile.getFileName() != null) {
                existingFile.setFileName(updatedFile.getFileName());
                existingFile.setUrl("/json/stream/" + updatedFile.getFileName());
            }
            if (updatedFile.getFilePath() != null) {
                existingFile.setFilePath(updatedFile.getFilePath());
            }
            if (updatedFile.getFileSize() > 0) {
                existingFile.setFileSize(updatedFile.getFileSize());
            }
            existingFile.setUpdatedTime(new Date());

            // 保存更新
            jsonFiles.put(id, existingFile);
            System.out.println("更新JSON文件成功: " + id);
            return existingFile;
        }
        return null;
    }
}
