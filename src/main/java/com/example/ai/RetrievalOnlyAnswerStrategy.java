package com.example.ai;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 纯检索策略：不生成模型文本，交回调用方由 RagService 用命中资料组装答案。
 * 保留成独立策略是为了让"模型不可用/未命中资料"时的降级路径显式可见。
 */
@Component
@Order(2)
public class RetrievalOnlyAnswerStrategy implements AnswerStrategy {

    @Override
    public String name() {
        return "retrieval";
    }

    @Override
    public String answer(String question, List<RagService.Hit> hits) {
        return null;
    }
}
