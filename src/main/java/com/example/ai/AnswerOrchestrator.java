package com.example.ai;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 回答编排（策略容器）：按顺序挑选第一个愿意接管回答的策略。
 * 全部策略返回 null 时保持原有行为——由 RagService 输出纯检索结果或兜底文案。
 */
@Service
public class AnswerOrchestrator {

    private final List<AnswerStrategy> strategies;

    public AnswerOrchestrator(List<AnswerStrategy> strategies) {
        this.strategies = strategies;
    }

    public String answer(String question, List<RagService.Hit> hits) {
        for (AnswerStrategy strategy : strategies) {
            String result = strategy.answer(question, hits);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
