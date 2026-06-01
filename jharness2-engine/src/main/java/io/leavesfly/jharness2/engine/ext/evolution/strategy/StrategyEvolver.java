package io.leavesfly.jharness2.engine.ext.evolution.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jharness2.engine.ext.evolution.EvolutionConfig;
import io.leavesfly.jharness2.engine.llm.LlmClient;
import io.leavesfly.jharness2.engine.llm.LlmResponse;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 策略进化器 —— 分析历史会话指标，通过 LLM 生成优化指令。
 * <p>
 * 每积累 N 次会话的 metrics 后自动评估，生成新版 EvolutionDirective。
 * 支持 A/B 对比验证，效果不佳时自动回滚。
 */
public class StrategyEvolver {

    private static final Logger logger = LoggerFactory.getLogger(StrategyEvolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String EVOLUTION_PROMPT = """
            请分析以下 Agent 最近 %d 次会话的执行指标，生成优化建议。

            ## 执行统计
            - 平均轮次: %.1f / %d (最大)
            - 任务完成率: %.0f%%
            - 平均 Token 消耗: %.0f
            - 平均工具错误数: %.1f
            - 最常用工具: %s
            - 平均耗时: %.0f ms

            ## 问题分析
            %s

            ## 请输出 JSON 格式的优化指令：
            ```json
            {
              "promptAddendum": "追加到 system prompt 的策略优化建议（不超过 200 字）",
              "suggestedMaxTurns": 12,
              "avoidPatterns": ["应避免的模式1", "应避免的模式2"],
              "reasoning": "为什么这样优化的简要说明"
            }
            ```
            """;

    private final LlmClient llmClient;
    private final DirectiveStore directiveStore;
    private final EvolutionConfig config;
    private final List<SessionMetrics> metricsBuffer = Collections.synchronizedList(new ArrayList<>());

    public StrategyEvolver(LlmClient llmClient, DirectiveStore directiveStore, EvolutionConfig config) {
        this.llmClient = llmClient;
        this.directiveStore = directiveStore;
        this.config = config;
    }

    /**
     * 记录一次会话的 metrics。当积累到评估窗口大小时自动触发进化。
     */
    public void recordMetrics(SessionMetrics metrics) {
        metricsBuffer.add(metrics);

        if (metricsBuffer.size() >= config.getEvaluationWindow()) {
            evolveAsync();
        }
    }

    /**
     * 异步执行策略进化。
     */
    public CompletableFuture<Optional<EvolutionDirective>> evolveAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return evolve();
            } catch (Exception e) {
                logger.error("Strategy evolution failed: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    /**
     * 执行策略进化：分析 metrics → LLM 生成指令 → 保存。
     */
    public Optional<EvolutionDirective> evolve() {
        List<SessionMetrics> snapshot;
        synchronized (metricsBuffer) {
            if (metricsBuffer.isEmpty()) return Optional.empty();
            snapshot = new ArrayList<>(metricsBuffer);
            metricsBuffer.clear();
        }

        logger.info("Starting strategy evolution with {} session metrics", snapshot.size());

        // 统计分析
        MetricsAnalysis analysis = analyzeMetrics(snapshot);

        // LLM 生成优化指令
        EvolutionDirective directive = generateDirective(analysis, snapshot.size());
        if (directive == null) {
            logger.warn("LLM failed to generate evolution directive");
            return Optional.empty();
        }

        // 保存（版本递增）
        int newVersion = directiveStore.getLatestVersion() + 1;
        directive.setVersion(newVersion);
        directive.setPreviousId(
                directiveStore.getCurrent().map(EvolutionDirective::getId).orElse(null));
        directiveStore.save(directive);

        logger.info("Strategy evolved to v{}: '{}'",
                newVersion, truncate(directive.getPromptAddendum(), 60));
        return Optional.of(directive);
    }

    /**
     * 获取当前生效的策略指令文本（用于注入 system prompt）。
     */
    public String getActiveDirectiveSection() {
        return directiveStore.getCurrent()
                .map(directive -> {
                    StringBuilder section = new StringBuilder();
                    section.append("\n## 策略优化指令 (v").append(directive.getVersion()).append(")\n\n");
                    section.append(directive.getPromptAddendum()).append("\n");

                    if (directive.getAvoidPatterns() != null && !directive.getAvoidPatterns().isEmpty()) {
                        section.append("\n避免以下模式：\n");
                        for (String pattern : directive.getAvoidPatterns()) {
                            section.append("- ").append(pattern).append("\n");
                        }
                    }
                    return section.toString();
                })
                .orElse("");
    }

    /**
     * 回滚到上一版本。
     */
    public boolean rollback() {
        int currentVersion = directiveStore.getLatestVersion();
        if (currentVersion <= 1) {
            logger.info("Cannot rollback: already at v1 or no directive exists");
            return false;
        }
        return directiveStore.rollback(currentVersion - 1);
    }

    public DirectiveStore getDirectiveStore() { return directiveStore; }

    // --- 内部分析逻辑 ---

    private EvolutionDirective generateDirective(MetricsAnalysis analysis, int sessionCount) {
        String problems = identifyProblems(analysis);
        String topTools = analysis.toolFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(Collectors.joining(", "));

        String prompt = String.format(EVOLUTION_PROMPT,
                sessionCount, analysis.avgTurns, analysis.maxTurns,
                analysis.completionRate * 100, analysis.avgTokens,
                analysis.avgToolErrors, topTools, analysis.avgDurationMs, problems);

        List<ConversationMessage> messages = List.of(
                ConversationMessage.system("你是一个 Agent 策略优化专家。只输出 JSON。"),
                ConversationMessage.user(prompt)
        );

        LlmResponse response = llmClient.chatStream(messages, null, event -> {});
        if (response == null || response.getContent() == null) return null;

        return parseDirective(response.getContent());
    }

    private EvolutionDirective parseDirective(String llmOutput) {
        try {
            String json = extractJson(llmOutput);
            JsonNode node = MAPPER.readTree(json);

            String promptAddendum = node.path("promptAddendum").asText("");
            int suggestedMaxTurns = node.path("suggestedMaxTurns").asInt(12);

            List<String> avoidPatterns = new ArrayList<>();
            JsonNode patternsNode = node.path("avoidPatterns");
            if (patternsNode.isArray()) {
                for (JsonNode p : patternsNode) {
                    avoidPatterns.add(p.asText());
                }
            }

            // 限制 prompt 长度
            if (promptAddendum.length() > config.getMaxDirectiveLength()) {
                promptAddendum = promptAddendum.substring(0, config.getMaxDirectiveLength());
            }

            return EvolutionDirective.create(0, promptAddendum, Map.of(), suggestedMaxTurns, avoidPatterns);
        } catch (Exception e) {
            logger.warn("Failed to parse directive from LLM output: {}", e.getMessage());
            return null;
        }
    }

    private MetricsAnalysis analyzeMetrics(List<SessionMetrics> metrics) {
        MetricsAnalysis analysis = new MetricsAnalysis();
        if (metrics.isEmpty()) return analysis;

        int totalTurns = 0, totalErrors = 0, completedCount = 0;
        long totalTokens = 0, totalDuration = 0;

        for (SessionMetrics m : metrics) {
            totalTurns += m.getTurnsUsed();
            totalErrors += m.getToolErrorCount();
            totalTokens += m.getInputTokens() + m.getOutputTokens();
            totalDuration += m.getDurationMs();
            if (m.isTaskCompleted()) completedCount++;
            analysis.maxTurns = Math.max(analysis.maxTurns, m.getMaxTurns());

            for (String tool : m.getToolSequence()) {
                analysis.toolFrequency.merge(tool, 1, Integer::sum);
            }
        }

        int count = metrics.size();
        analysis.avgTurns = (double) totalTurns / count;
        analysis.avgToolErrors = (double) totalErrors / count;
        analysis.avgTokens = (double) totalTokens / count;
        analysis.avgDurationMs = (double) totalDuration / count;
        analysis.completionRate = (double) completedCount / count;

        return analysis;
    }

    private String identifyProblems(MetricsAnalysis analysis) {
        List<String> problems = new ArrayList<>();

        if (analysis.completionRate < 0.7) {
            problems.add("任务完成率低于 70%，需要改善任务规划能力");
        }
        if (analysis.avgToolErrors > 2.0) {
            problems.add("平均工具错误超过 2 次/会话，需要提高工具调用准确性");
        }
        if (analysis.avgTurns > analysis.maxTurns * 0.8) {
            problems.add("平均轮次接近上限，可能存在低效循环");
        }
        if (analysis.avgTokens > 10000) {
            problems.add("Token 消耗偏高，考虑更精简的回复策略");
        }

        return problems.isEmpty() ? "当前表现良好，尝试微调优化" : String.join("\n", problems);
    }

    private String extractJson(String text) {
        int startMarker = text.indexOf("```json");
        if (startMarker != -1) {
            int contentStart = text.indexOf('\n', startMarker) + 1;
            int endMarker = text.indexOf("```", contentStart);
            if (endMarker != -1) return text.substring(contentStart, endMarker).trim();
        }
        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart != -1 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1);
        }
        return text.trim();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    /** 内部统计分析结果 */
    private static class MetricsAnalysis {
        double avgTurns;
        int maxTurns;
        double avgToolErrors;
        double avgTokens;
        double avgDurationMs;
        double completionRate;
        Map<String, Integer> toolFrequency = new HashMap<>();
    }
}
