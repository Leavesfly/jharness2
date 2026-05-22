package io.leavesfly.jharness2.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class StartupInfoPrinter {

    private static final Logger log = LoggerFactory.getLogger(StartupInfoPrinter.class);

    private final Environment environment;

    public StartupInfoPrinter(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String host = getHostAddress();
        String model = environment.getProperty("jharness2.engine.default-model", "unknown");
        String llmUrl = environment.getProperty("jharness2.engine.default-base-url", "unknown");

        String message = String.format("""
                
                ──────────────────────────────────────────────────────
                  ✅ JHarness2 启动成功！
                ──────────────────────────────────────────────────────
                  本地访问:   http://localhost:%s%s
                  网络访问:   http://%s:%s%s
                  H2 控制台:  http://localhost:%s%s/h2-console
                  健康检查:   http://localhost:%s%s/actuator/health
                ──────────────────────────────────────────────────────
                  默认模型:   %s
                  LLM 地址:   %s
                  默认账户:   admin / admin123
                ──────────────────────────────────────────────────────
                  API 文档:   POST /api/auth/login          → 登录获取 Token
                              POST /api/chat/new            → 创建会话
                              POST /api/chat/{id}/message   → 流式对话
                ──────────────────────────────────────────────────────
                """,
                port, contextPath,
                host, port, contextPath,
                port, contextPath,
                port, contextPath,
                model,
                llmUrl
        );
        log.info(message);
    }

    private String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }
}
