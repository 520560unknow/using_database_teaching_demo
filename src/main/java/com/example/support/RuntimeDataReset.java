package com.example.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * 清理运行时产生的数据，让每次启动都是干净的初始状态。
 * 只删运行期写入的内容，保留 ai-corpus 下的内置示例资料。
 */
public final class RuntimeDataReset {

    private static final Logger log = LoggerFactory.getLogger(RuntimeDataReset.class);

    private RuntimeDataReset() {
    }

    public static void clear(String jsonStorageDir, String aiCorpusDir, String aiLogDir) {
        deleteFilesIn(Paths.get(jsonStorageDir));
        deleteIfExists(Paths.get("json-files.json"));
        deleteDirectoryContents(Paths.get("json-upload"));
        deleteDirectoryContents(Paths.get(aiLogDir));
        clearGeneratedCorpus(Paths.get(aiCorpusDir));
        log.info("已清理上次运行产生的课堂、资料、问答与日志记录");
    }

    /** 删除目录下的所有文件和子目录，保留目录本身 */
    private static void deleteDirectoryContents(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                deleteRecursively(entry);
            }
        } catch (IOException e) {
            log.warn("清理目录失败: {} ({})", dir, e.getMessage());
        }
    }

    /** json-storage 只清文件，目录由服务自己维护 */
    private static void deleteFilesIn(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    deleteRecursively(entry);
                }
            }
        } catch (IOException e) {
            log.warn("清理存储目录失败: {} ({})", dir, e.getMessage());
        }
    }

    /** ai-corpus 保留内置示例，删掉教师上传和课堂专属目录 */
    private static void clearGeneratedCorpus(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteRecursively(entry);
                    continue;
                }
                String name = entry.getFileName().toString();
                if (name.startsWith("upload-") || "teacher-faq.md".equals(name)) {
                    deleteRecursively(entry);
                }
            }
        } catch (IOException e) {
            log.warn("清理知识库目录失败: {} ({})", dir, e.getMessage());
        }
    }

    private static void deleteIfExists(Path path) {
        if (Files.exists(path)) {
            deleteRecursively(path);
        }
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    for (Path child : stream) {
                        deleteRecursively(child);
                    }
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除失败: {} ({})", path, e.getMessage());
        }
    }
}
