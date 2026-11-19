package com.example.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

/**
 * 正常关闭（Ctrl+C / 优雅停止）时再清一次，避免留下本次会话的记录。
 * 注意：强制结束进程（taskkill /F）不会触发。
 */
@Component
public class ShutdownDataCleanup {

    @Value("${json.storage.directory:json-storage}")
    private String jsonStorageDir;

    @Value("${ai.corpus-dir:./ai-corpus}")
    private String aiCorpusDir;

    @Value("${ai.log-dir:./ai-logs}")
    private String aiLogDir;

    @Value("${app.reset-on-start:false}")
    private boolean resetEnabled;

    @PreDestroy
    public void cleanup() {
        if (resetEnabled) {
            RuntimeDataReset.clear(jsonStorageDir, aiCorpusDir, aiLogDir);
        }
    }
}
