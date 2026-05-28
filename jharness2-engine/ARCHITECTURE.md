# JHarness2 Engine — 三层同心圆架构

## 概述

`jharness2-engine` 是 JHarness2 的 AI Agent 核心运行时，驱动 ReAct 循环（Reasoning + Acting）。

本文档描述其内部架构的组织原则：**三层同心圆**——按"离核心循环的距离"将所有代码分为三层，实现高内聚低耦合。

---

## 设计理念

```
    ┌──────────────────────────────────────────────────────┐
    │                   ext/ (扩展层)                        │
    │  agent/ mcp/ plugin/ skill/ cron/ task/ hook/        │
    │                                                       │
    │   ┌──────────────────────────────────────────────┐   │
    │   │            policy/ (策略层)                    │   │
    │   │  access/ context/ resilience/ pipeline/       │   │
    │   │  observe/                                     │   │
    │   │                                               │   │
    │   │   ┌──────────────────────────────────────┐   │   │
    │   │   │        Kernel (内核层)                 │   │   │
    │   │   │  QueryEngine                          │   │   │
    │   │   │  llm/ message/ tool/ stream/          │   │   │
    │   │   └──────────────────────────────────────┘   │   │
    │   │                                               │   │
    │   └──────────────────────────────────────────────┘   │
    │                                                       │
    └──────────────────────────────────────────────────────┘
```

**核心原则：**

- **内核（Kernel）**：ReAct 循环**必须**依赖的最小完备集，缺一不可
- **策略（Policy）**：循环中**可插拔**的决策点，不设置也能运行
- **扩展（Extension）**：**独立于循环**的增强能力，整层删除引擎照常工作

---

## 层间依赖规则

```
依赖方向：外层 → 内层（严格单向，禁止反向引用）

ext/  ──→  policy/  ──→  kernel (根 + llm/ + message/ + tool/ + stream/)
```

| 层 | 可依赖 | 不可依赖 |
|----|--------|----------|
| **Kernel** (根, llm/, message/, tool/, stream/) | 无（最底层） | policy/, ext/ |
| **Policy** (policy/**) | Kernel 层所有包 | ext/ |
| **Extension** (ext/**) | Kernel 层 + Policy 层 | 无限制 |

---

## 第一层：内核（Kernel）

> ReAct 循环的最小完备集。删除任何一个子包都无法运行。

### 根包

| 类 | 职责 |
|----|------|
| `QueryEngine` | 循环驱动器 — 唯一入口，编排 LLM 调用与工具执行 |
| `EngineContext` | 扩展子系统注册表 — 承载 ext/ 层的可选组件 |
| `SessionPersister` | 会话持久化接口 |

### llm/ — LLM 通信（循环的"大脑"）

| 类 | 职责 |
|----|------|
| `LlmClient` | 接口：流式调用 LLM Chat Completion |
| `LlmResponse` | LLM 单轮响应（content + toolCalls + finishReason + refusal） |
| `ResponseFormat` | 输出格式约束（text / json_object / json_schema） |
| `OpenAiClient` | OpenAI 兼容 API 实现（OkHttp SSE，支持 OpenAI/Ollama/vLLM） |
| `CostTracker` | Token 消耗统计（input/output/total/cost） |

**内聚标准：** "如何与 LLM 通信"。换 LLM 供应商只需改此包。

### message/ — 消息模型（循环的"血液"）

| 类 | 职责 |
|----|------|
| `ConversationMessage` | 对话消息（role + content + toolCalls + toolCallId） |
| `ContentBlock` | sealed 接口：多模态内容块 |
| `TextBlock` | 文本内容块 |
| `ImageBlock` | 图片内容块（URL / Base64） |
| `FileBlock` | 文件内容块 |
| `ContentBlocks` | 工具类：构建和转换 ContentBlock 列表 |
| `ToolUseBlock` | 工具调用请求（id + name + input JSON） |
| `ToolResultBlock` | 工具执行结果（id + output + isError） |

**内聚标准：** "消息长什么样"。纯数据模型，零业务逻辑。被所有层引用。

### tool/ — 工具框架（循环的"手脚"）

| 类 | 职责 |
|----|------|
| `BaseTool<T>` | 工具抽象基类（name + description + inputClass + execute） |
| `ToolParam` | @ToolParam 注解：为字段声明 description/enum/required |
| `ToolResult` | 工具返回值（output + isError） |
| `ToolRegistry` | 工具注册表（name → BaseTool 映射 + toApiSchemas()） |
| `ToolCallDispatcher` | 工具调度器（权限检查 → 执行 → 错误恢复） |
| `ToolExecutionContext` | 执行上下文（cwd + permissionChecker） |
| `builtin/` | 内置工具实现（file/, shell/, meta/） |

**内聚标准：** "工具如何定义和执行"。加新工具只需继承 BaseTool。

### stream/ — 流式事件（循环的"嘴巴"）

| 类 | 职责 |
|----|------|
| `StreamEvent` | 事件抽象基类 |
| `AssistantTextDelta` | LLM 输出文本增量 |
| `AssistantTurnComplete` | 单轮对话结束 |
| `ToolExecutionStarted` | 工具开始执行 |
| `ToolExecutionCompleted` | 工具执行完成 |
| `UsageReport` | Token 用量报告 |

**内聚标准：** "循环产生什么事件"。所有实时回调的载体。

---

## 第二层：策略（Policy）

> 循环行为的可插拔决策点。全部通过 setter 注入 QueryEngine，不注入时使用默认行为（通常是"不做任何事"）。

### policy/access/ — 访问控制策略

> "决定什么**能不能做**"

| 类 | 职责 |
|----|------|
| `PermissionChecker` | 统一权限判断（工具黑白名单 + 路径规则 + 命令拦截） |
| `PermissionMode` | 权限模式枚举：PERMISSIVE / NORMAL / STRICT |
| `Guardrail` | LLM 输入/输出校验接口（INPUT 阶段 + OUTPUT 阶段） |
| `GuardrailResult` | 校验结果 record（pass / warn / tripwire / correct） |
| `GuardrailExecutor` | Guardrail 链式执行器 |

**典型变更场景：** 加新的安全规则、调整权限模式。

### policy/context/ — 上下文管理策略

> "决定**上下文如何管理**"

| 类 | 职责 |
|----|------|
| `CompactionStrategy` | 消息压缩策略接口 |
| `MessageCompactionService` | 默认实现：LLM 摘要压缩 |
| `TokenCounter` | Token 计数器接口 |
| `EstimateTokenCounter` | 估算实现（~3 字符/token） |
| `ContextBudget` | 上下文窗口预算分配（maxTokens / reservedForOutput / threshold） |

**典型变更场景：** 换模型（不同窗口大小）、接入精确 tokenizer、优化压缩算法。

### policy/resilience/ — 容错策略

> "决定**出错后怎么办**"

| 类 | 职责 |
|----|------|
| `ErrorRecoveryStrategy` | 工具错误恢复策略接口 |
| `RecoveryAction` | 恢复动作枚举（RETRY / FALLBACK / ESCALATE / SKIP / ABORT） |
| `RecoveryDecision` | 恢复决策 record |
| `DefaultErrorRecoveryStrategy` | 默认实现（超时重试，其他升级给 LLM） |
| `ToolExecutionPolicy` | 单工具超时/重试/沙箱策略配置 |

**典型变更场景：** 调整超时、加重试策略、配置高危工具沙箱。

### policy/pipeline/ — 请求管道策略

> "决定 **LLM 请求/响应如何变换**"

| 类 | 职责 |
|----|------|
| `EngineMiddleware` | 中间件接口（洋葱模型） |
| `MiddlewareChain` | 链传递接口 |
| `MiddlewarePipeline` | 管道执行器（包装 LlmClient 调用） |

**典型变更场景：** 加 prompt caching 标注、加 rate limiting、加请求日志。

### policy/observe/ — 可观测性策略

> "决定**记录什么**"

| 类 | 职责 |
|----|------|
| `Tracer` | 追踪器接口（创建 Span） |
| `Span` | Span 接口（属性、状态、子 Span） |
| `SpanData` | Span 不可变快照（导出/序列化用） |
| `SpanStatus` | Span 状态枚举（UNSET / OK / ERROR） |
| `DefaultSpan` | 内存 Span 实现 |
| `DefaultTracer` | 内存 Tracer 实现（调试/测试/评估） |
| `NoopTracer` | 零开销空实现 |

**典型变更场景：** 对接 OpenTelemetry、换 trace 存储、接入 LangSmith。

---

## 第三层：扩展（Extension）

> 独立于循环的增强能力。整个 `ext/` 目录删除后引擎核心循环不受影响。  
> 通过 `EngineContext` 懒注册，按需启用。

### ext/agent/ — Sub-Agent 协作

| 类 | 职责 |
|----|------|
| `AgentRole` | 角色定义（name + systemPrompt + model） |
| `AgentTask` | 任务定义（description + prompt + role） |
| `AgentResult` | 执行结果（output + success + duration） |
| `AgentOrchestrator` | 简单编排器（并行/串行纯 LLM 调用） |
| `Handoff` | Handoff 定义（目标 Agent + InputFilter） |
| `HandoffResult` | Handoff 结果（output + turnsUsed + agentMessages） |
| `HandoffExecutor` | Handoff 执行器（Sub-Agent 拥有完整 ReAct 循环） |

### ext/mcp/ — MCP 协议

| 类 | 职责 |
|----|------|
| `McpTransport` | 传输层接口（connect / send / onMessage / close） |
| `StdioMcpTransport` | Stdio 实现（本地子进程 stdin/stdout） |
| `HttpSseMcpTransport` | HTTP+SSE 实现（远程 MCP 服务器） |
| `McpClient` | MCP 客户端（JSON-RPC 2.0 协议） |
| `McpManager` | MCP 服务器生命周期管理 |
| `McpTool` | MCP 工具适配器（MCP tool → BaseTool） |

### ext/plugin/ — 插件系统

| 类 | 职责 |
|----|------|
| `PluginManifest` | 插件清单（name + version + tools + commands） |
| `PluginLoader` | 插件加载器（从目录扫描 manifest） |
| `PluginRegistry` | 插件注册表 |
| `LoadedPlugin` | 已加载插件实例 |

### ext/skill/ — 技能系统

| 类 | 职责 |
|----|------|
| `SkillDefinition` | 技能定义（name + description + prompt template） |
| `SkillLoader` | 技能加载器（从 .md 文件） |
| `SkillRegistry` | 技能注册表 |

### ext/cron/ — 定时任务

| 类 | 职责 |
|----|------|
| `CronExpression` | Cron 表达式解析 |
| `CronJob` | 定时任务定义 |
| `CronScheduler` | 调度器 |
| `CronTaskHandler` | 任务处理器接口 |
| `CronTriggerContext` | 触发上下文 |

### ext/task/ — 后台任务

| 类 | 职责 |
|----|------|
| `BackgroundTask` | 任务定义 |
| `BackgroundTaskManager` | 任务管理器（线程池 + 生命周期） |

### ext/hook/ — 事件钩子

| 类 | 职责 |
|----|------|
| `HookEvent` | 事件定义（开放式字符串标识，支持自定义） |
| `HookHandler` | 处理器接口 |
| `HookExecutor` | 事件分发器 |
| `ShellHookHandler` | Shell 命令处理器实现 |

**Hook 放在 ext/ 而非 policy/ 的理由：**
- Hook 消费者是外部系统（shell 脚本、通知、审计），不影响循环正确性
- Fire-and-forget 语义，不改变循环行为
- 删除 Hook 后循环照常运行

---

## QueryEngine 对各层的引用方式

```java
public class QueryEngine {

    // ═══ 内核层：构造时必须传入 ═══
    private final LlmClient llmClient;                          // llm/
    private final ToolRegistry toolRegistry;                    // tool/
    private final ToolCallDispatcher dispatcher;                // tool/
    private final String systemPrompt;
    private final int maxTurns;
    private final List<ConversationMessage> messages;           // message/

    // ═══ 策略层：setter 可选注入 ═══
    private volatile PermissionChecker permissionChecker;       // policy/access/
    private volatile GuardrailExecutor guardrailExecutor;       // policy/access/
    private volatile CompactionStrategy compactionStrategy;     // policy/context/
    private volatile TokenCounter tokenCounter;                 // policy/context/
    private volatile ContextBudget contextBudget;               // policy/context/
    private volatile ErrorRecoveryStrategy recoveryStrategy;    // policy/resilience/
    private volatile MiddlewarePipeline middlewarePipeline;     // policy/pipeline/
    private volatile Tracer tracer = NoopTracer.INSTANCE;       // policy/observe/
    private volatile SessionPersister sessionPersister;

    // ═══ 扩展层：通过 EngineContext 间接持有 ═══
    private volatile EngineContext engineContext;
    // → engineContext.getAgentOrchestrator()        ext/agent/
    // → engineContext.getHandoffExecutor()          ext/agent/
    // → engineContext.getMcpManager()               ext/mcp/
    // → engineContext.getSkillRegistry()            ext/skill/
    // → engineContext.getCronScheduler()            ext/cron/
    // → engineContext.getBackgroundTaskManager()    ext/task/
    // → engineContext.getHookExecutor()             ext/hook/
}
```

---

## "删除测试" — 验证内聚性

| 操作 | 预期结果 |
|------|----------|
| 删除 `ext/` | ✅ 引擎编译通过，ReAct 循环正常运行（无 Sub-Agent、MCP、插件） |
| 删除 `ext/` + `policy/` | ✅ 引擎编译通过（无权限检查、无压缩、无 trace，但循环照跑） |
| 删除 Kernel 中任一子包 | ❌ 编译失败 — 证明内核是最小完备集 |
| 删除 `policy/observe/` | ✅ 引擎正常（NoopTracer 作为默认值） |
| 删除 `policy/resilience/` | ✅ 引擎正常（工具错误直接返回给 LLM） |

---

## ReAct 循环与三层的交互

```
用户消息
    │
    ▼
┌─ policy/access/ ─┐    INPUT Guardrail 检查
│  GuardrailExecutor │    （tripwire → 中止）
└────────┬──────────┘
         │ 通过
         ▼
┌─ policy/context/ ─┐    上下文压缩判断
│  TokenCounter      │    （超预算 → CompactionStrategy 压缩）
│  ContextBudget     │
│  CompactionStrategy│
└────────┬──────────┘
         │
         ▼
┌─ policy/pipeline/ ─┐   Middleware 变换
│  MiddlewarePipeline │   （注入上下文 / cache / rate limit）
└────────┬───────────┘
         │
         ▼
┌─── Kernel: llm/ ───┐   LLM 流式调用
│  LlmClient.chatStream │
└────────┬───────────┘
         │
         ▼
┌─ policy/access/ ─┐    OUTPUT Guardrail 检查
│  GuardrailExecutor │    （tripwire → 中止 / correct → 修正）
└────────┬──────────┘
         │
    ┌────┴────────┐
    │ 有 tool_calls? │
    └────┬────────┘
    No   │  Yes
    │    ▼
    │  ┌─ policy/access/ ─┐     权限检查
    │  │  PermissionChecker │
    │  └────────┬──────────┘
    │           │
    │           ▼
    │  ┌── Kernel: tool/ ──┐     工具执行
    │  │  ToolCallDispatcher │
    │  └────────┬──────────┘
    │           │ 失败?
    │           ▼
    │  ┌─ policy/resilience/ ┐   错误恢复
    │  │  ErrorRecoveryStrategy│  （RETRY → 重试 / ESCALATE → 返回 LLM）
    │  └────────┬────────────┘
    │           │
    │           ▼
    │    结果加入历史 → 下一轮循环
    │
    ▼
  结束（AssistantTurnComplete）

  ──── 横切：policy/observe/ 的 Tracer 在每个阶段创建 Span ────
  ──── 横切：ext/hook/ 的 HookExecutor 在关键节点 fire 事件 ────
```

---

## 包的统计

| 层 | 包数 | 类数 | 占比 |
|----|------|------|------|
| Kernel | 4 (+根) | ~30 | 33% |
| Policy | 5 子包 | ~25 | 28% |
| Extension | 7 子包 | ~35 | 39% |
| **合计** | **16 包** | **~90** | 100% |

顶层认知单元仅 **3 个**（Kernel / Policy / Extension），新人理解成本最低。

---

## 设计决策记录

### 为什么不按"功能域"平级排列？

平级排列（如 llm/ tool/ permission/ guardrail/ compaction/ mcp/ agent/ ...）导致：
- 17+ 个顶层包，认知负担高
- 无法快速判断哪些是必须的、哪些可以删
- 依赖方向不明确，容易产生循环引用

### 为什么 stream/ 不在 policy/observe/ 里？

`StreamEvent` 是引擎输出的唯一通道，ReAct 循环没有它无法向外部传递任何信息。它是 Kernel 级的，不是可选的观测策略。

### 为什么 tool/builtin/ 不拆到 ext/ 里？

内置工具（file_read, bash 等）是引擎作为 Coding Agent 的基本能力。没有它们引擎虽然能跑循环，但等于一个"无手无脚"的 LLM 聊天框，违背了 harness 运行时的定位。

### 为什么 ToolExecutionPolicy 在 policy/resilience/ 而非 tool/ 里？

`ToolExecutionPolicy` 是"决策"（每个工具超时多久、重试几次），不是"执行"。它的变化原因是运维/安全需求，而非工具功能本身。`BaseTool` 不需要知道它的存在。

---

## 迁移路径（从当前结构）

当前结构 → 三层同心圆的迁移可按以下顺序执行：

1. **创建 message/ 包**：将 `ConversationMessage` + `model/` + `content/` 合并
2. **创建 llm/ 包**：移入 `LlmClient`、`LlmResponse`、`OpenAiClient`、`ResponseFormat`、`CostTracker`
3. **创建 policy/ 层**：将现有 `permission/`、`guardrail/`、`compaction/`、`recovery/`、`middleware/`、`token/`、`trace/` 移入 policy/ 对应子包
4. **创建 ext/ 层**：将 `agent/`、`mcp/`、`plugin/`、`skill/`、`cron/`、`task/`、`hook/` 移入
5. **调整 QueryEngine 引用**：更新 import 路径
6. **验证删除测试**：依次删除 ext/ 和 policy/ 确认编译通过

每步独立可提交，不影响其他模块（`jharness2-core` 等只依赖 engine 的公共 API）。
