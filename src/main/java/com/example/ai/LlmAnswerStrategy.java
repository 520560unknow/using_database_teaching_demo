package com.example.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本地模型回答策略：开关打开且有命中资料时才调用 llama.cpp，其余情况交回下一个策略。
 */
@Component
@Order(1)
public class LlmAnswerStrategy implements AnswerStrategy {

    @Value("${ai.enabled:true}")
    private boolean enabled;

    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;

    public LlmAnswerStrategy(PromptBuilder promptBuilder, LlmClient llmClient) {
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
    }

    @Override
    public String name() {
        return "llm";
    }

    @Override
    public String answer(String question, List<RagService.Hit> hits) {
        if (!enabled || hits == null || hits.isEmpty()) {
            return null;
        }
        return llmClient.complete(
                promptBuilder.buildSystemPrompt(),
                promptBuilder.buildUserPrompt(question, hits)
        );
    }
}
