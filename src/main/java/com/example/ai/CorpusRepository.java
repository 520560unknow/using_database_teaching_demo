package com.example.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * RAG 语料仓储：负责语料目录解析、Markdown 片段切分、资料文件读写与 FAQ 回写。
 * 不包含检索算法，检索交给 {@link Bm25Retriever}。
 */
@Component
public class CorpusRepository {

    @Value("${ai.corpus-dir:./ai-corpus}")
    private String corpusDir;

    public String getCorpusDir() {
        return Paths.get(corpusDir).toAbsolutePath().toString();
    }

    /**
     * 扫描语料目录（两层：根目录为公共资料，一级子目录名即 classroomId），切分为片段。
     */
    public List<RagService.Chunk> loadAll() {
        List<RagService.Chunk> chunks = new ArrayList<RagService.Chunk>();
        Path dir = Paths.get(corpusDir);
        if (Files.isDirectory(dir)) {
            // 扫描两层：根目录为公共资料，一级子目录名即 classroomId
            try (Stream<Path> files = Files.walk(dir, 2)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .sorted()
                        .forEach(p -> loadFile(p, dir, chunks));
            } catch (IOException e) {
                System.err.println("RAG 语料读取失败: " + e.getMessage());
            }
        } else {
            System.err.println("RAG 语料目录不存在: " + dir.toAbsolutePath());
        }
        return chunks;
    }

    private void loadFile(Path file, Path root, List<RagService.Chunk> chunks) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String fileTitle = file.getFileName().toString().replace(".md", "");
            String[] lines = text.split("\\r?\\n");
            String title = fileTitle;
            String docTitle = fileTitle;
            StringBuilder body = new StringBuilder();
            int index = 0;
            for (String line : lines) {
                if (line.matches("^#{1,3}\\s+.*")) {
                    String heading = line.replaceFirst("^#{1,3}\\s+", "").trim();
                    if (body.length() == 0 && fileTitle.equals(docTitle) && !heading.isEmpty()) {
                        docTitle = heading;
                    }
                }
                if (line.matches("^#{1,3}\\s+.*")) {
                    addChunk(chunks, fileTitle, docTitle, title, body.toString(),
                            file.getFileName().toString(), classroomOf(file, root), index++);
                    title = line.replaceFirst("^#{1,3}\\s+", "").trim();
                    body.setLength(0);
                } else {
                    body.append(line).append('\n');
                }
            }
            addChunk(chunks, fileTitle, docTitle, title, body.toString(),
                    file.getFileName().toString(), classroomOf(file, root), index);
        } catch (IOException e) {
            System.err.println("RAG 读取文件失败: " + file + " -> " + e.getMessage());
        }
    }

    private void addChunk(List<RagService.Chunk> chunks, String fileTitle, String docTitle, String title,
                          String body, String source, String classroomId, int index) {
        String content = body == null ? "" : body.trim();
        if (content.isEmpty()) {
            return;
        }
        RagService.Chunk c = new RagService.Chunk();
        c.id = (classroomId == null || classroomId.isEmpty() ? "" : classroomId + "/") + fileTitle + "#" + index;
        c.docTitle = docTitle;
        c.title = title == null || title.trim().isEmpty() ? fileTitle : title.trim();
        c.content = content;
        c.source = source;
        c.classroomId = classroomId == null ? "" : classroomId;
        chunks.add(c);
    }

    /**
     * 由文件相对语料根目录的位置推导 classroomId：根目录为 ""，一层子目录取目录名。
     */
    private String classroomOf(Path file, Path root) {
        Path relative = root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
        if (relative.getNameCount() < 2) {
            return "";
        }
        return relative.getName(0).toString();
    }

    /**
     * 课堂 ID 白名单清洗：仅保留字母、数字、下划线与中划线，避免路径穿越。
     */
    public static String safeClassroom(String classroomId) {
        if (classroomId == null) {
            return "";
        }
        String cleaned = classroomId.trim().replaceAll("[^A-Za-z0-9_-]", "");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    /**
     * 课堂对应的物理目录：公共资料落在语料根目录。
     */
    public Path classroomDir(String classroomId) {
        String safe = safeClassroom(classroomId);
        Path root = Paths.get(corpusDir);
        return safe.isEmpty() ? root : root.resolve(safe);
    }

    /**
     * 教师回复回流：写入对应课堂目录的 teacher-faq.md，公共课堂写语料根目录。
     */
    public boolean appendFaq(String question, String answer, String classroomId) {
        try {
            Path dir = classroomDir(classroomId);
            Files.createDirectories(dir);
            Path file = dir.resolve("teacher-faq.md");
            String entry = "\n## " + question.trim() + "\n" + answer.trim() + "\n";
            Files.write(file, entry.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            System.err.println("写入教师答疑语料失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 教师上传/粘贴资料：classroomId 非空时写入 ai-corpus/&lt;classroomId&gt;/，否则写根目录。
     */
    public boolean writeMaterial(String title, String content, String classroomId) {
        String safeTitle = (title == null || title.trim().isEmpty())
                ? ("资料-" + System.currentTimeMillis())
                : title.trim().replace("\n", " ").replace("\r", " ");
        try {
            Path dir = classroomDir(classroomId);
            Files.createDirectories(dir);
            String name = "upload-" + System.currentTimeMillis() + ".md";
            String text = "# " + safeTitle + "\n\n" + content.trim() + "\n";
            Files.write(dir.resolve(name), text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
            return true;
        } catch (IOException e) {
            System.err.println("写入教师资料失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 只允许删除指定课堂目录下的文件，禁止路径穿越与绝对路径。
     */
    public boolean deleteMaterial(String name, String classroomId) {
        if (name == null || !name.endsWith(".md") || name.contains("/") || name.contains("\\")
                || name.contains("..")) {
            return false;
        }
        Path dir = classroomDir(classroomId).toAbsolutePath().normalize();
        Path target = dir.resolve(name).normalize();
        if (!dir.equals(target.getParent())) {
            return false;
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            System.err.println("删除教师资料失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 只列出指定课堂目录下的资料文件（公共课堂即语料根目录）。
     */
    public List<Map<String, Object>> listMaterials(String classroomId, List<RagService.Chunk> chunks) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        Path dir = classroomDir(classroomId);
        if (!Files.isDirectory(dir)) {
            return result;
        }
        String safe = safeClassroom(classroomId);
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        Map<String, Object> m = new LinkedHashMap<String, Object>();
                        m.put("name", name);
                        m.put("classroomId", safe);
                        String displayTitle = name.replace(".md", "");
                        int count = 0;
                        for (RagService.Chunk c : chunks) {
                            if (name.equals(c.source) && safe.equals(c.classroomId)) {
                                if (count == 0 && c.docTitle != null) {
                                    displayTitle = c.docTitle;
                                }
                                count++;
                            }
                        }
                        m.put("title", displayTitle);
                        m.put("chunks", count);
                        try {
                            m.put("size", Files.size(p));
                            m.put("modified", new SimpleDateFormat("MM-dd HH:mm")
                                    .format(new Date(Files.getLastModifiedTime(p).toMillis())));
                        } catch (IOException e) {
                            m.put("size", 0);
                            m.put("modified", "");
                        }
                        result.add(m);
                    });
        } catch (IOException e) {
            System.err.println("列出教师资料失败: " + e.getMessage());
        }
        return result;
    }
}
