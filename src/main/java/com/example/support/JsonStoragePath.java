package com.example.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;

/**
 * JSON 文件存储目录的统一入口：负责把配置里的相对路径解析为项目根目录下的绝对路径，
 * 并确保目录存在。多个 Controller 共用同一份解析结果，避免各自重复初始化。
 */
@Component
public class JsonStoragePath {

    @Value("${json.storage.directory:json-storage}")
    private String path;

    @PostConstruct
    public void init() {
        // 处理存储路径
        File dir = new File(path);
        if (!dir.isAbsolute()) {
            // 转换为相对于项目根目录的绝对路径
            String projectRoot = System.getProperty("user.dir");
            path = projectRoot + File.separator + path;
            System.out.println("转换后的JSON存储路径: " + path);
        }

        // 确保目录存在
        File finalDir = new File(path);
        if (!finalDir.exists()) {
            boolean created = finalDir.mkdirs();
            System.out.println("JSON存储目录创建结果: " + created);
        }
    }

    /** 已解析的存储目录绝对路径。 */
    public String get() {
        return path;
    }

    /** 在存储目录下定位文件。 */
    public File resolve(String fileName) {
        return new File(path, fileName);
    }
}
