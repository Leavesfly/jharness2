# 核心引擎 - QueryEngine

## 概述

`QueryEngine` 是 JHarness2 的 AI Agent 核心运行时，驱动 **ReAct 循环**（Reasoning + Acting）：LLM 推理 → 工具调用 → 结果反馈 → 再推理，直到任务完成或达到最大轮次。

所在模块：`jharness2-engine`  
包路径：`io.leavesfly.jharness2.engine.QueryEngine`

## ReAct 循环

```
用户消息 → [加入历史]
     │
     ▼
┌─────────────────────┐
│  LLM 调用（流式）    │◄──────────────┐
│  chatStream()       │               │
└────────┬────────────┘               │
         │                            │
    ┌────┴────┐                       │
    │有工具调用?│                       │
    └────┬────┘                       │
    Yes  │  No                        │
         │   └─→ 结束（AssistantTurnComplete）
         ▼                            │
┌─────────────────────┐               │
│  权限检查            │               │
│  PermissionChecker   │               │
└────────┬────────────┘               │
         │ 允许                        │
         ▼                            │
┌─────────────────────┐               │
│  工具执行            │               │
│  ToolExecutor        │               │
└────────┬────────────┘               │
         │                            │
         ▼                            │
┌─────────────────────┐               │
│  结果加入历史        │───────────────┘
│  下一轮循环          │  (turns < maxTurns)
└─────────────────────┘
```

### 循环终止条件

1. LLM 未返回工具调用（纯文本回复）→ 正常结束
2. 达到最大轮次（`maxTurns`，默认 12）→ 强制结束
3. 用户取消（`cancel()` 调用）→ 中断退出

## 核心字段

```java
public class QueryEngine {
    private final LlmClient llmClient;           // LLM 通信客户端
    private final ToolRegistry toolRegistry;     // 工具注册表
    private final String systemPrompt;           // 系统提示词
    private final int maxTurns;                  // 最大循环轮次
    private final CostTracker costTracker;       // Token 消耗统计
    private final List<ConversationMessage> messages;  // 对话历史
    private final AtomicBoolean cancelled;       // 取消标志（线程安全）

    // 可选子系统（通过 setter 注入）
    private volatile PermissionChecker permissionChecker;
    private volatile MessageCompactionService compactionService;
    private volatile AgentOrchestrator agentOrchestrator;
    private volatile SkillRegistry skillRegistry;
    private volatile McpManager mcpManager;
    private volatile HookExecutor hookExecutor;
    private volatile BackgroundTaskManager backgroundTaskManager;
    private volatile Consumer<List<ConversationMessage>> sessionPersister;
}
```

## 消息提交

```java
public CompletableFuture<Void> submitMessage(String prompt, Consumer<StreamEvent> eventConsumer)
```

- **异步执行**：返回 `CompletableFuture<Void>`，不阻塞调用方
- **流式回调**：通过 `Consumer<StreamEvent>` 实时推送事件
- **自动压缩**：调用前检查消息历史长度，过长时自动压缩
- **自动持久化**：每轮工具执行后触发会话保存

## 流式事件体系

`StreamEvent` 是所有流式事件的基类，具体类型：

| 事件类型 | 说明 |
|----------|------|
| `AssistantTextDelta` | LLM 输出的文本增量 |
| `ToolExecutionStarted` | 工具开始执行（含工具名、参数） |
| `ToolExecutionCompleted` | 工具执行完成（含结果、是否出错） |
| `AssistantTurnComplete` | 一轮对话结束 |
| `UsageReport` | Token 用量报告 |

## Hook 事件

QueryEngine 在关键节点触发 Hook：

| Hook 事件 | 触发时机 |
|-----------|----------|
| `USER_PROMPT_SUBMIT` | 用户消息提交时 |
| `PRE_TOOL_USE` | 工具执行前 |
| `POST_TOOL_USE` | 工具执行后 |
| `STOP` | 用户取消时 |
| `SESSION_END` | 引擎关闭时 |

## LLM 客户端

`OpenAiClient` 实现了 `LlmClient` 接口，基于 OkHttp SSE 与 OpenAI 兼容 API 通信：

```java
public interface LlmClient {
    LlmResponse chatStream(List<ConversationMessage> messages,
                           List<Map<String, Object>> tools,
                           Consumer<StreamEvent> eventConsumer);
    void close();
}
```

**特性：**
- 支持所有 OpenAI Chat Completions 兼容 API（OpenAI、Ollama、vLLM 等）
- SSE 流式解析，实时推送文本增量
- 自动组装 tool_calls 参数（支持流式分片拼接）
- 可配置超时（连接/读取/写入）

## 消息模型

```java
public class ConversationMessage {
    enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    private final Role role;
    private final String content;
    private final List<ToolCall> toolCalls;    // assistant 消息携带
    private final String toolCallId;           // tool 结果消息携带
    private final String name;                 // tool 名称

    // 静态工厂方法
    static ConversationMessage system(String content);
    static ConversationMessage user(String content);
    static ConversationMessage assistant(String content);
    static ConversationMessage assistantWithToolCalls(String content, List<ToolCall> calls);
    static ConversationMessage toolResult(String toolCallId, String name, String result);
}
```

## 生命周期管理

```java
// 取消当前执行
engine.cancel();

// 关闭引擎（释放所有资源）
engine.close();
// → 触发 SESSION_END Hook
// → 关闭 McpManager
// → 关闭 AgentOrchestrator
// → 关闭 BackgroundTaskManager
// → 关闭 LlmClient
```

## 消息压缩

当对话历史过长时，`MessageCompactionService` 自动介入：

1. 判断是否需要压缩（基于消息数量阈值）
2. 保留 system prompt + 最近若干轮对话
3. 将中间部分通过 LLM 摘要为精简版本
4. 替换原始消息列表

## 设计要点

- **无框架依赖**：engine 模块不依赖 Spring，可独立于 Web 容器使用
- **线程安全**：消息列表使用 `synchronizedList`，取消标志使用 `AtomicBoolean`
- **可组合**：子系统通过 setter 可选注入，最小化依赖
- **流式优先**：所有 LLM 输出通过 StreamEvent 实时回调，支持长时间运行
