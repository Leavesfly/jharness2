package io.leavesfly.jharness2.core.engine.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shell 命令型 Hook 处理器。
 * 将插件声明的 shell 命令封装为 HookHandler，在事件触发时执行对应命令。
 * 命令的 env 中会注入事件名和 payload 中的键值对。
 */
public class ShellHookHandler implements HookHandler {

    private static final Logger logger = LoggerFactory.getLogger(ShellHookHandler.class);
    private static final int TIMEOUT_SECONDS = 30;

    private final String command;
    private final String pluginName;

    public ShellHookHandler(String command, String pluginName) {
        this.command = command;
        this.pluginName = pluginName;
    }

    @Override
    public void handle(HookEvent event, Map<String, Object> payload) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);
        processBuilder.redirectErrorStream(true);

        Map<String, String> env = processBuilder.environment();
        env.put("JHARNESS_HOOK_EVENT", event.getValue());
        env.put("JHARNESS_PLUGIN", pluginName);
        if (payload != null) {
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                env.put("JHARNESS_" + entry.getKey().toUpperCase(), String.valueOf(entry.getValue()));
            }
        }

        Process process = processBuilder.start();
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            logger.warn("Hook command timed out ({}s) for plugin '{}': {}", TIMEOUT_SECONDS, pluginName, command);
            return;
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String output = reader.lines().reduce("", (a, b) -> a + "\n" + b).trim();
                logger.warn("Hook command failed (exit={}) for plugin '{}': {} | output: {}",
                        exitCode, pluginName, command, output);
            }
        } else {
            logger.debug("Hook command succeeded for plugin '{}' on event '{}': {}",
                    pluginName, event.getValue(), command);
        }
    }

    public String getCommand() { return command; }
    public String getPluginName() { return pluginName; }
}
