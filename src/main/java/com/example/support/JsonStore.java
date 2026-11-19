package com.example.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * JSON 文件读写工具：写入走临时文件 + 原子替换，并在替换前保留 .bak 备份。
 * 不依赖 Spring，供各持久化服务复用。
 */
public final class JsonStore {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

    /** 串行化写入，避免同一时刻两个请求各写一个临时文件后互相覆盖。 */
    private static final Object WRITE_LOCK = new Object();

    private JsonStore() {
    }

    public static void write(Path path, Object value) throws IOException {
        write(path, value, DEFAULT_MAPPER);
    }

    /**
     * 先写 path + ".tmp"，再备份现有文件为 path + ".bak"，最后原子替换目标文件。
     */
    public static void write(Path path, Object value, ObjectMapper mapper) throws IOException {
        if (path == null) {
            throw new IOException("JSON 写入路径为空");
        }
        synchronized (WRITE_LOCK) {
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp." + UUID.randomUUID());
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), value);

                if (Files.exists(path)) {
                    Path bak = path.resolveSibling(path.getFileName() + ".bak");
                    Files.copy(path, bak, StandardCopyOption.REPLACE_EXISTING);
                }

                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
    }

    public static <T> T read(Path path, Class<T> type) throws IOException {
        return read(path, type, DEFAULT_MAPPER);
    }

    /**
     * 文件不存在返回 null；解析失败抛出带路径信息的 IOException。
     */
    public static <T> T read(Path path, Class<T> type, ObjectMapper mapper) throws IOException {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return mapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            // 主文件损坏时尝试 .bak（写入前会自动备份上一版），避免整个数据被当作空文件加载
            Path bak = path.resolveSibling(path.getFileName() + ".bak");
            if (Files.exists(bak)) {
                try {
                    return mapper.readValue(bak.toFile(), type);
                } catch (IOException ignored) {
                    e.addSuppressed(ignored);
                }
            }
            throw new IOException("读取 JSON 失败: " + path + " (" + e.getMessage() + ")", e);
        }
    }
}
