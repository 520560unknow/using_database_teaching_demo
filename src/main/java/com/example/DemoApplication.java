package com.example;

import com.example.classroom.service.VideoTreeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.awt.Desktop;
import java.net.URI;

@EnableScheduling
@SpringBootApplication
public class DemoApplication implements CommandLineRunner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DemoApplication.class);

    @Autowired
    private VideoTreeService videoService;

    @Value("${app.auto-open-browser:false}")
    private boolean autoOpenBrowser;

    @Value("${app.auto-open-url:http://localhost:8080/teacher/dashboard}")
    private String autoOpenUrl;

    @Value("${app.demo-data:true}")
    private boolean demoData;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DemoApplication.class);
        app.addListeners(new com.example.support.ResetOnStartListener());
        app.run(args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (demoData) {
            videoService.initTestData();
            log.info("应用启动完成，已初始化测试视频树数据");
        } else {
            log.info("应用启动完成，已跳过测试视频树数据初始化");
        }

        if (autoOpenBrowser) {
            openBrowser(autoOpenUrl);
        }
    }

    private static void openBrowser(String url) {
        try {
            // 只允许 http/https 协议
            if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                log.warn("不允许打开的URL协议: {}", url);
                return;
            }

            URI uri = new URI(url);

            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(uri);
                    log.info("已打开浏览器: {}", url);
                } else {
                    log.warn("当前系统不支持 Desktop.browse，无法自动打开浏览器");
                }
            } else {
                log.warn("当前环境不支持 Desktop 操作");
            }
        } catch (Exception e) {
            log.error("打开浏览器失败: {}", e.getMessage());
        }
    }

}
