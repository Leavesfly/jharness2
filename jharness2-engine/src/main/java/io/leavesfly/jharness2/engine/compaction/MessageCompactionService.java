package io.leavesfly.jharness2.engine.compaction;

import io.leavesfly.jharness2.engine.ConversationMessage;
import io.leavesfly.jharness2.engine.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MessageCompactionService implements CompactionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(MessageCompactionService.class);

    private final int maxMessages;
    private final int preserveRecent;
    private final int tokenBudget;
    private int systemPromptTokens;

    public MessageCompactionService() {
        this(20, 5, 32000);
    }

    public MessageCompactionService(int maxMessages, int preserveRecent, int tokenBudget) {
        this.maxMessages = maxMessages;
        this.preserveRecent = preserveRecent;
        this.tokenBudget = tokenBudget;
    }

    public MessageCompactionService withSystemPromptTokens(int tokens) {
        this.systemPromptTokens = tokens;
        return this;
    }

    public boolean needsCompaction(List<ConversationMessage> messages) {
        if (messages.size() > maxMessages) return true;
        int estimatedTokens = estimateTokens(messages);
        return estimatedTokens + systemPromptTokens > tokenBudget;
    }

    public List<ConversationMessage> compact(List<ConversationMessage> messages, LlmClient llmClient) {
        if (!needsCompaction(messages)) return messages;

        logger.info("Compacting {} messages (token budget: {})", messages.size(), tokenBudget);

        List<ConversationMessage> result = new ArrayList<>();

        // 保留 system prompt（第一条）
        if (!messages.isEmpty() && messages.get(0).getRole() == ConversationMessage.Role.SYSTEM) {
            result.add(messages.get(0));
        }

        // 计算需要压缩的范围
        int compactEnd = messages.size() - preserveRecent;
        if (compactEnd <= 1) return messages;

        // 提取需要压缩的消息内容
        StringBuilder summaryInput = new StringBuilder();
        summaryInput.append("Please summarize the following conversation concisely, preserving key decisions and context:\n\n");
        for (int i = 1; i < compactEnd; i++) {
            ConversationMessage msg = messages.get(i);
            summaryInput.append(msg.getRole().name()).append(": ");
            if (msg.getContent() != null) {
                String content = msg.getContent();
                if (content.length() > 500) content = content.substring(0, 500) + "...";
                summaryInput.append(content);
            }
            summaryInput.append("\n");
        }

        // 用 LLM 生成摘要
        String summary = generateSummary(llmClient, summaryInput.toString());

        result.add(ConversationMessage.user("[Previous conversation summary]: " + summary));
        result.add(ConversationMessage.assistant("Understood. I have the context from our previous conversation."));

        // 保留最近的消息
        for (int i = compactEnd; i < messages.size(); i++) {
            result.add(messages.get(i));
        }

        logger.info("Compaction complete: {} -> {} messages", messages.size(), result.size());
        return result;
    }

    private String generateSummary(LlmClient llmClient, String content) {
        List<ConversationMessage> summaryMessages = List.of(
                ConversationMessage.system("You are a conversation summarizer. Be concise and preserve key information."),
                ConversationMessage.user(content)
        );
        StringBuilder summaryBuilder = new StringBuilder();
        try {
            var response = llmClient.chatStream(summaryMessages, null, event -> {
            });
            return response.getContent();
        } catch (Exception e) {
            logger.warn("Summary generation failed, using fallback", e);
            return "[Previous conversation of " + content.split("\n").length + " exchanges]";
        }
    }

    private int estimateTokens(List<ConversationMessage> messages) {
        int tokens = 0;
        for (ConversationMessage msg : messages) {
            if (msg.getContent() != null) {
                tokens += msg.getContent().length() / 3;
            }
        }
        return tokens;
    }
}
