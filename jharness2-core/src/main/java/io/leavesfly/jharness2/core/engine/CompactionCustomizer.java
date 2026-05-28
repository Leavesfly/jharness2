package io.leavesfly.jharness2.core.engine;

import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.policy.context.MessageCompactionService;

import java.nio.file.Path;

/**
 * 消息压缩策略定制器。
 */
public class CompactionCustomizer implements EngineCustomizer {

    @Override
    public void customize(QueryEngine engine, UserContext context, Path workspace) {
        MessageCompactionService compactionService = new MessageCompactionService();
        // 基于 system prompt 长度估算 token 数（粗略估算：1 token ≈ 3 chars）
        String systemPrompt = engine.getMessages().isEmpty() ? "" :
                engine.getMessages().get(0).getContent();
        if (systemPrompt != null) {
            compactionService.withSystemPromptTokens(systemPrompt.length() / 3);
        }
        engine.setCompactionStrategy(compactionService);
    }

    @Override
    public int getOrder() { return 200; }
}
