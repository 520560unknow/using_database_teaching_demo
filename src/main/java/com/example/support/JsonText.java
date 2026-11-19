package com.example.support;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * JSON 文本读取：统一按 UTF-8 解码，并去掉文件开头可能存在的 BOM。
 * Windows 记事本等工具保存的 UTF-8 JSON 常带 BOM，直接交给 Jackson 会报
 * "Unexpected character (code 65279)"。
 */
public final class JsonText {

    private JsonText() {
    }

    public static String readUtf8(File file) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
