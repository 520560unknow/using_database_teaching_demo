package com.example.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 llama.cpp server 的 OpenAI 兼容接口客户端：只负责 HTTP 调用、超时与响应解析。
 * 失败（含未启动模型）统一返回 null，由上层保留纯检索降级行为。
 */
@Component
public class LlmClient {

    @Value("${ai.base-url:http://127.0.0.1:8081}")
    private String baseUrl;

    @Value("${ai.model:D:/app/lamma/mode/Ling-3.0-tiny-Q5_K_M.gguf}")
    private String model;

    private final RestTemplate restTemplate;

    public LlmClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 发送一次非流式对话请求，返回助手回复文本；任何异常都返回 null。
     */
    public String complete(String systemPrompt, String userPrompt) {
        try {
            List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
            messages.add(message("system", systemPrompt));
            messages.add(message("user", userPrompt));

            Map<String, Object> body = new HashMap<String, Object>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", 220);
            body.put("temperature", 0.3);
            body.put("reasoning_effort", "none");
            body.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8));
            HttpEntity<Map<String, Object>> entity = new HttpEntity<Map<String, Object>>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/v1/chat/completions", entity, Map.class);
            if (response == null) {
                return null;
            }
            Object choicesObj = response.get("choices");
            if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) {
                return null;
            }
            Object first = ((List<?>) choicesObj).get(0);
            if (!(first instanceof Map)) {
                return null;
            }
            Object messageObj = ((Map<?, ?>) first).get("message");
            if (!(messageObj instanceof Map)) {
                return null;
            }
            Object content = ((Map<?, ?>) messageObj).get("content");
            if (content == null) {
                return null;
            }
            String text = String.valueOf(content).trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            System.err.println("本地 AI 调用失败: " + e.getMessage());
            return null;
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> m = new HashMap<String, String>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
