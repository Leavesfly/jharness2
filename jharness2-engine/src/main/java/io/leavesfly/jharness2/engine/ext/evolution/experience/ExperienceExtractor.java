package io.leavesfly.jharness2.engine.ext.evolution.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jharness2.engine.ext.evolution.strategy.SessionMetrics;
import io.leavesfly.jharness2.engine.llm.LlmClient;
import io.leavesfly.jharness2.engine.llm.LlmResponse;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 经验提取器 —— 从一次完整的会话中提取可复用的经验。
 * <p>
 * 在 SESSION_END Hook 中异步调用，通过 LLM self-reflection 生成结构化经验。
 */
public class ExperienceExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ExperienceExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String REFLECTION_PROMPT = """
            请分析以下对话过程，提取可复用的经验教训。

            ## 对话摘要
            %s

            ## 执行指标
            - 工具调用序列: %s
            - 使用轮次: %d
            - 消耗 Token: %d

            ## 请严格按以下 JSON 格式输出（不要输出其他内容）：
            ```json
            {
              "taskPattern": "用一句话描述这次任务的模式（例如：文件批量重命名、API 接口开发）",
              "strategy": "本次采用的关键策略或方法",
              "lesson": "下次遇到类似任务时应该注意什么（成功经验或失败教训）",
              "keywords": ["关键词1", "关键词2", "关键词3"],
              "success": true
            }
            ```
            """;

    private final LlmClient llmClient;
    private final ExperienceStore store;
    private final int minTurnsToExtract;

    public ExperienceExtractor(LlmClient llmClient, ExperienceStore store, int minTurnsToExtract) {
        this.llmClient = llmClient;
        this.store = store;
        this.minTurnsToExtract = minTurnsToExtract;
    }

    /**
     * 异步从会话中提取经验。
     *
     * @param userId   用户 ID
     * @param messages 完整会话消息
     * @param metrics  会话执行指标
     * @return 提取的经验（异步）
     */
    public CompletableFuture<Experience> extract(String userId,
                                                 List<ConversationMessage> messages,
                                                 SessionMetrics metrics) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doExtract(userId, messages, metrics);
            } catch (Exception e) {
                logger.warn("Experience extraction failed for user={}: {}", userId, e.getMessage());
                return null;
            }
        });
    }

    private Experience doExtract(String userId, List<ConversationMessage> messages,
                                 SessionMetrics metrics) {
        // 会话太短不提取
        if (metrics.getTurnsUsed() < minTurnsToExtract) {
            logger.debug("Skipping extraction: turns={} < minTurns={}",
                    metrics.getTurnsUsed(), minTurnsToExtract);
            return null;
        }

        String conversationSummary = buildConversationSummary(messages);
        String toolSequence = String.join(" → ", metrics.getToolSequence());
        long totalTokens = metrics.getInputTokens() + metrics.getOutputTokens();

        String prompt = String.format(REFLECTION_PROMPT,
                conversationSummary, toolSequence, metrics.getTurnsUsed(), totalTokens);

        // 调用 LLM 进行 self-reflection
        List<ConversationMessage> reflectionMessages = List.of(
                ConversationMessage.system("你是一个经验提取助手，从对话记录中总结可复用的经验。只输出 JSON。"),
                ConversationMessage.user(prompt)
        );

        LlmResponse response = llmClient.chatStream(reflectionMessages, null, event -> {});

        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            logger.warn("LLM returned empty response for experience extraction");
            return null;
        }

        return parseAndSave(userId, response.getContent(), metrics);
    }

    private Experience parseAndSave(String userId, String llmOutput, SessionMetrics metrics) {
        try {
            // 提取 JSON 部分（LLM 可能包裹在 markdown 代码块中）
            String json = extractJson(llmOutput);
            JsonNode node = MAPPER.readTree(json);

            String taskPattern = node.path("taskPattern").asText("");
            String strategy = node.path("strategy").asText("");
            String lesson = node.path("lesson").asText("");
            boolean success = node.path("success").asBoolean(true);

            List<String> keywords = new ArrayList<>();
            JsonNode keywordsNode = node.path("keywords");
            if (keywordsNode.isArray()) {
                for (JsonNode kw : keywordsNode) {
                    keywords.add(kw.asText());
                }
            }

            Experience experience = Experience.create(
                    userId, taskPattern, metrics.getToolSequence(),
                    strategy, success, lesson, keywords,
                    metrics.getTurnsUsed(),
                    metrics.getInputTokens() + metrics.getOutputTokens()
            );

            store.save(experience);
            logger.info("Extracted experience: id={}, task={}, success={}",
                    experience.getId(), taskPattern, success);
            return experience;

        } catch (Exception e) {
            logger.warn("Failed to parse experience from LLM output: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建对话摘要 —— 取最近的 user/assistant 消息对（最多 5 轮）。
     */
    private String buildConversationSummary(List<ConversationMessage> messages) {
        StringBuilder summary = new StringBuilder();
        int pairCount = 0;
        int maxPairs = 5;

        // 从后往前取最近的消息对
        for (int i = messages.size() - 1; i >= 0 && pairCount < maxPairs; i--) {
            ConversationMessage msg = messages.get(i);
            if (msg.getRole() == ConversationMessage.Role.USER
                    || msg.getRole() == ConversationMessage.Role.ASSISTANT) {
                String content = msg.getContent();
                if (content != null && !content.isBlank()) {
                    // 截断过长的内容
                    if (content.length() > 200) {
                        content = content.substring(0, 200) + "...";
                    }
                    summary.insert(0, "[" + msg.getRole().name() + "] " + content + "\n");
                    if (msg.getRole() == ConversationMessage.Role.USER) {
                        pairCount++;
                    }
                }
            }
        }
        return summary.toString().trim();
    }

    /**
     * 从 LLM 输出中提取 JSON 内容（处理可能的 markdown 代码块包裹）。
     */
    private String extractJson(String text) {
        // 尝试匹配 ```json ... ``` 或 ``` ... ```
        int startMarker = text.indexOf("```json");
        if (startMarker != -1) {
            int contentStart = text.indexOf('\n', startMarker) + 1;
            int endMarker = text.indexOf("```", contentStart);
            if (endMarker != -1) {
                return text.substring(contentStart, endMarker).trim();
            }
        }

        startMarker = text.indexOf("```");
        if (startMarker != -1) {
            int contentStart = text.indexOf('\n', startMarker) + 1;
            int endMarker = text.indexOf("```", contentStart);
            if (endMarker != -1) {
                return text.substring(contentStart, endMarker).trim();
            }
        }

        // 尝试找到 JSON 对象边界
        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart != -1 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1);
        }

        return text.trim();
    }
}
