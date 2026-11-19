package com.example.ai;

import java.util.List;

/**
 * 回答策略（Strategy）：按 {@link org.springframework.core.annotation.Order} 顺序尝试，
 * 返回 null 表示"本策略不接管，交给下一个策略"，全部不接管时由调用方走纯检索兜底。
 */
public interface AnswerStrategy {

    /** 策略名称，用于日志与排障。 */
    String name();

    /** 返回回答文本，或 null 表示不接管。 */
    String answer(String question, List<RagService.Hit> hits);
}
