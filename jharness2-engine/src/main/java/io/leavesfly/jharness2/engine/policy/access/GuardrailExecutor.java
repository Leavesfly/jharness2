package io.leavesfly.jharness2.engine.policy.access;

import io.leavesfly.jharness2.engine.message.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Guardrail 执行器 — 管理和执行注册的 Guardrail 列表。
 * <p>
 * 按注册顺序依次执行同阶段的 Guardrail，遇到 tripwire 立即返回。
 */
public class GuardrailExecutor {

    private static final Logger logger = LoggerFactory.getLogger(GuardrailExecutor.class);

    private final List<Guardrail> guardrails = new CopyOnWriteArrayList<>();

    public void register(Guardrail guardrail) {
        guardrails.add(guardrail);
    }

    public void unregister(Guardrail guardrail) {
        guardrails.remove(guardrail);
    }

    /**
     * 执行指定阶段的所有 Guardrail。
     *
     * @param phase    阶段
     * @param messages 对话历史
     * @param content  待检查内容
     * @return 聚合结果（取最严格的结果）
     */
    public GuardrailResult execute(Guardrail.Phase phase, List<ConversationMessage> messages, String content) {
        List<Guardrail> phaseGuardrails = guardrails.stream()
                .filter(g -> g.getPhase() == phase)
                .toList();

        if (phaseGuardrails.isEmpty()) {
            return GuardrailResult.pass();
        }

        String currentContent = content;
        List<String> warnings = new ArrayList<>();

        for (Guardrail guardrail : phaseGuardrails) {
            try {
                GuardrailResult result = guardrail.check(messages, currentContent);

                if (result.tripwire()) {
                    logger.warn("Guardrail '{}' triggered tripwire: {}", guardrail.getName(), result.reason());
                    return result;
                }

                if (!result.passed()) {
                    warnings.add(guardrail.getName() + ": " + result.reason());
                    // 如果提供了修正内容，用修正内容继续后续检查
                    if (result.correctedContent() != null) {
                        currentContent = result.correctedContent();
                    }
                }
            } catch (Exception e) {
                logger.error("Guardrail '{}' execution failed: {}", guardrail.getName(), e.getMessage(), e);
                // Guardrail 自身异常不应阻断主流程
            }
        }

        if (!warnings.isEmpty()) {
            String combinedReason = String.join("; ", warnings);
            if (!currentContent.equals(content)) {
                return GuardrailResult.correct(combinedReason, currentContent);
            }
            return GuardrailResult.warn(combinedReason);
        }

        return GuardrailResult.pass();
    }

    public int size() {
        return guardrails.size();
    }

    public int size(Guardrail.Phase phase) {
        return (int) guardrails.stream().filter(g -> g.getPhase() == phase).count();
    }
}
