package com.example.ai;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对外保留的回答入口：内部委托 {@link AnswerOrchestrator}，
 * 方法签名与降级语义（未启用/无命中返回 null）保持不变。
 */
@Service
public class AiService {

    private final AnswerOrchestrator answerOrchestrator;

    public AiService(AnswerOrchestrator answerOrchestrator) {
        this.answerOrchestrator = answerOrchestrator;
    }

    public String answer(String question, List<RagService.Hit> hits) {
        return answerOrchestrator.answer(question, hits);
    }
}
