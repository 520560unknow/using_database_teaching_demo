package com.example.support;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 在 Spring 容器创建 bean 之前清理上次运行的数据，
 * 保证 TeacherService / RagService 读到的都是空初始状态。
 */
public class ResetOnStartListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        if (!Boolean.parseBoolean(env.getProperty("app.reset-on-start", "false"))) {
            return;
        }
        RuntimeDataReset.clear(
                env.getProperty("json.storage.directory", "json-storage"),
                env.getProperty("ai.corpus-dir", "./ai-corpus"),
                env.getProperty("ai.log-dir", "./ai-logs"));
    }
}
