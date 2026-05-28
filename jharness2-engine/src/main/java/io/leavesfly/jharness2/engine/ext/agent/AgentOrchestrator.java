package io.leavesfly.jharness2.engine.ext.agent;

import io.leavesfly.jharness2.engine.llm.LlmClient;
import io.leavesfly.jharness2.engine.llm.LlmResponse;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class AgentOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final LlmClient llmClient;
    private final ExecutorService executor;

    public AgentOrchestrator(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.executor = new ThreadPoolExecutor(
                4, Runtime.getRuntime().availableProcessors() * 2,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "jharness2-agent-" + (++count));
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public List<AgentResult> executeParallel(List<AgentTask> tasks) {
        List<Future<AgentResult>> futures = new ArrayList<>();
        for (AgentTask task : tasks) {
            futures.add(executor.submit(() -> executeSingle(task)));
        }

        List<AgentResult> results = new ArrayList<>();
        for (Future<AgentResult> future : futures) {
            try {
                results.add(future.get(5, TimeUnit.MINUTES));
            } catch (TimeoutException e) {
                results.add(new AgentResult("unknown", "Timeout", false, 300000));
            } catch (Exception e) {
                results.add(new AgentResult("unknown", "Error: " + e.getMessage(), false, 0));
            }
        }
        return results;
    }

    public List<AgentResult> executeSequential(List<AgentTask> tasks) {
        List<AgentResult> results = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (AgentTask task : tasks) {
            String enrichedPrompt = task.getPrompt();
            if (!context.isEmpty()) {
                enrichedPrompt = "Previous results:\n" + context + "\n\nCurrent task: " + task.getPrompt();
            }
            AgentTask enriched = new AgentTask(task.getDescription(), enrichedPrompt, task.getRole());
            AgentResult result = executeSingle(enriched);
            results.add(result);
            context.append(task.getRole().getName()).append(": ").append(result.getOutput()).append("\n");
        }
        return results;
    }

    public AgentResult executeSingle(AgentTask task) {
        long start = System.currentTimeMillis();
        AgentRole role = task.getRole();
        String agentName = role != null ? role.getName() : "default";

        try {
            List<ConversationMessage> messages = new ArrayList<>();
            if (role != null && role.getSystemPrompt() != null) {
                messages.add(ConversationMessage.system(role.getSystemPrompt()));
            }
            messages.add(ConversationMessage.user(task.getPrompt()));

            LlmResponse response = llmClient.chatStream(messages, null, event -> {});
            long duration = System.currentTimeMillis() - start;

            logger.info("Agent '{}' completed in {}ms", agentName, duration);
            return new AgentResult(agentName, response.getContent(), true, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            logger.error("Agent '{}' failed: {}", agentName, e.getMessage(), e);
            return new AgentResult(agentName, "Error: " + e.getMessage(), false, duration);
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
