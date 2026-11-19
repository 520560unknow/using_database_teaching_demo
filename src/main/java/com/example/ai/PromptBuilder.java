package com.example.ai;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 提示词构建：把检索命中的资料片段拼成固定格式的 system / user 提示词。
 * 文案与拼接顺序保持与拆分前完全一致。
 */
@Component
public class PromptBuilder {

    public String buildSystemPrompt() {
        return "你是“课堂智答”的课堂答疑助手，服务对象是学生。\n"
                + "规则：\n"
                + "1. 只能依据【资料】回答，不得编造。\n"
                + "2. 资料不足时，直接回答“资料中没有找到”，并建议转人工教师。\n"
                + "3. 回答不超过120字，先给结论，再给一条依据编号，如[S1]。\n"
                + "4. 不要输出思考过程。";
    }

    public String buildUserPrompt(String question, List<RagService.Hit> hits) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            RagService.Chunk c = hits.get(i).chunk;
            context.append("[S").append(i + 1).append("] ").append(c.title).append('\n');
            context.append(c.content).append("\n\n");
        }
        return "【资料】\n" + context.toString() + "【学生问题】" + question;
    }
}
