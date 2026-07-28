package io.leavesfly.jharness2.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 引擎线程池持有者 —— 集中管理 Agent 循环与工具执行的有界线程池。
 * <p>
 * 两个池相互独立:Agent 池线程会在等待工具结果时阻塞(join),
 * 若与工具共用一个池,饱和时会互相等待造成死锁。
 */
public class EngineExecutors implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(EngineExecutors.class);

    private final ExecutorService agentExecutor;
    private final ExecutorService toolExecutor;

    public EngineExecutors(int agentPoolSize, int agentQueueCapacity,
                           int toolPoolSize, int toolQueueCapacity) {
        this.agentExecutor = newBoundedPool("jh2-agent-", agentPoolSize, agentQueueCapacity);
        this.toolExecutor = newBoundedPool("jh2-tool-", toolPoolSize, toolQueueCapacity);
        logger.info("EngineExecutors initialized: agent={}(queue={}), tool={}(queue={})",
                agentPoolSize, agentQueueCapacity, toolPoolSize, toolQueueCapacity);
    }

    private static ExecutorService newBoundedPool(String namePrefix, int poolSize, int queueCapacity) {
        AtomicInteger seq = new AtomicInteger(0);
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, namePrefix + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        // 饱和后 AbortPolicy 快速失败,由上层向用户返回"系统繁忙",避免请求无限堆积
        return new ThreadPoolExecutor(poolSize, poolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    public ExecutorService getAgentExecutor() {
        return agentExecutor;
    }

    public ExecutorService getToolExecutor() {
        return toolExecutor;
    }

    @Override
    public void close() {
        logger.info("Shutting down EngineExecutors");
        agentExecutor.shutdown();
        toolExecutor.shutdown();
        try {
            if (!agentExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                agentExecutor.shutdownNow();
            }
            if (!toolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                toolExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            agentExecutor.shutdownNow();
            toolExecutor.shutdownNow();
        }
    }
}
