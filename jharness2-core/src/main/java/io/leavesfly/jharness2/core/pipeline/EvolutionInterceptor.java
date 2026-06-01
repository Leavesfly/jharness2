package io.leavesfly.jharness2.core.pipeline;

import io.leavesfly.jharness2.core.EngineInstance;
import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.UserEngineRegistry;
import io.leavesfly.jharness2.engine.EngineContext;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.ext.evolution.EvolutionEngine;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 进化经验注入拦截器 —— 在用户发送消息前，检索相关历史经验并注入到对话上下文。
 * <p>
 * 工作机制：
 * <ol>
 *   <li>从 EngineContext 中获取 EvolutionEngine</li>
 *   <li>用用户消息检索相关经验</li>
 *   <li>将经验段作为 system 消息追加到对话历史（仅首次注入）</li>
 *   <li>同时注入策略进化指令（如果有）</li>
 * </ol>
 * <p>
 * 执行顺序 50：在 Quota/RateLimit 检查之后，确保不对被拒绝的请求浪费计算。
 */
@Component
public class EvolutionInterceptor implements ChatInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(EvolutionInterceptor.class);
    private static final String EXPERIENCE_INJECTED_KEY = "__evolution_experience_injected";

    private final UserEngineRegistry engineRegistry;

    public EvolutionInterceptor(UserEngineRegistry engineRegistry) {
        this.engineRegistry = engineRegistry;
    }

    @Override
    public void beforeChat(UserContext context, String message) {
        EngineInstance instance = engineRegistry.get(context.getUserId(), context.getSessionId());
        if (instance == null || !instance.isRunning()) {
            return;
        }

        QueryEngine engine = instance.getEngine();
        EngineContext engineContext = engine.getEngineContext();
        if (engineContext == null) {
            return;
        }

        Optional<EvolutionEngine> evolutionOpt = engineContext.getExtension(EvolutionEngine.class);
        if (evolutionOpt.isEmpty()) {
            return;
        }

        EvolutionEngine evolutionEngine = evolutionOpt.get();
        List<ConversationMessage> messages = engine.getMessages();

        // 仅在会话首次用户消息时注入经验（避免每轮都重复注入）
        boolean alreadyInjected = messages.stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.Role.SYSTEM
                        && m.getContent() != null
                        && m.getContent().contains("## 相关历史经验"));

        if (alreadyInjected) {
            return;
        }

        // 检索并注入经验
        String experienceSection = evolutionEngine.getExperiencePromptSection(
                context.getUserId(), message);

        // 获取策略进化指令
        String strategySection = evolutionEngine.getStrategyDirectiveSection();

        String combined = experienceSection + strategySection;
        if (combined.isBlank()) {
            return;
        }

        // 作为补充 system 消息追加到对话历史
        messages.add(ConversationMessage.system(combined.trim()));

        logger.debug("Injected evolution context for user={}, session={}: experience={}chars, strategy={}chars",
                context.getUserId(), context.getSessionId(),
                experienceSection.length(), strategySection.length());
    }

    @Override
    public void afterChat(UserContext context, String message, Throwable error) {
        // 会话结束时的经验提取由 EvolutionEngine 通过 Hook 机制触发，此处无需处理
    }

    @Override
    public int getOrder() {
        return 50;
    }
}
