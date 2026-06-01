# JHarness2 递归自我改进（RSI）技术方案

## 一、背景与目标

### 1.1 什么是 Agent 层的 RSI

递归自我改进（Recursive Self Improvement）在 Agent 层面的含义是：Agent 在不修改底层 LLM 权重的前提下，通过**经验积累、工具自生成、策略优化**三个维度，持续提升自身的任务执行能力，形成"能力滚雪球"效应。

### 1.2 目标

在 `jharness2-engine`（纯 Java 内核）中新增 `ext/evolution` 子包，实现三层进化能力：

| 层次 | 能力 | 价值 |
|------|------|------|
| **Level 1** | 经验记忆（Experience Memory） | Agent 从每次任务中学习，避免重复犯错 |
| **Level 2** | 工具自生成（Tool Making） | Agent 自主创造新工具，扩展能力边界 |
| **Level 3** | 策略进化（Strategy Evolution） | Agent 自动优化决策策略和工作流 |

### 1.3 设计原则

- **零 Spring 依赖**：engine 模块保持纯 Java 库定位，进化子系统仅依赖 Jackson + SLF4J
- **安全可控**：所有进化操作可审计、可回滚、受权限约束
- **渐进式**：三个 Level 独立解耦，可单独启用
- **与现有架构无缝集成**：复用 HookExecutor、ToolRegistry、EngineContext、EngineCustomizer 等现有机制

---

## 二、现有架构集成分析

### 2.1 engine 层可复用的集成点

| 现有组件 | 位置 | RSI 利用方式 |
|----------|------|-------------|
| `HookExecutor` | `ext/hook/` | SESSION_END 时触发经验提取 |
| `HookEvent.of()` | `ext/hook/` | 定义进化相关的自定义事件 |
| `ToolRegistry` | `tool/` | 动态注册自生成的工具 |
| `BaseTool<T>` | `tool/` | 自生成工具的基类 |
| `SkillRegistry` | `ext/skill/` | 将成功策略固化为 Skill |
| `EngineContext.registerExtension()` | 根包 | 注册 EvolutionEngine 实例 |
| `ToolCallDispatcher` | `tool/` | 工具执行结果采集 |

### 2.2 core 层可复用的集成点

| 现有组件 | 位置 | RSI 利用方式 |
|----------|------|-------------|
| `EngineCustomizer` | `core/engine/` | 新增 EvolutionCustomizer 初始化进化子系统 |
| `ChatInterceptor` | `core/pipeline/` | afterChat 时采集 metrics |
| `EngineMetrics` | `core/metrics/` | 新增进化相关 metrics |
| `VectorMemorySearch` SPI | `core/spi/` | 经验检索的向量化增强 |
| `EmbeddingService` SPI | `core/spi/` | 经验文本 embedding |
| `ToolAuditLogger` SPI | `core/spi/` | 工具执行记录作为经验数据源 |
| `SessionCheckpointService` | `core/checkpoint/` | 进化状态持久化 |
| `EngineConfig` | `core/` | 新增进化相关配置项 |

---

## 三、整体架构设计

### 3.1 模块结构

```
jharness2-engine/src/main/java/io/leavesfly/jharness2/engine/ext/evolution/
├── EvolutionEngine.java              // 进化引擎统一入口
├── EvolutionConfig.java              // 进化配置
├── EvolutionEvent.java               // 进化相关 HookEvent 定义
│
├── experience/                        // Level 1: 经验记忆
│   ├── Experience.java               // 经验数据模型
│   ├── ExperienceExtractor.java      // 从会话中提取经验
│   ├── ExperienceStore.java          // 经验存储接口
│   ├── FileExperienceStore.java      // 基于文件系统的默认实现
│   └── ExperienceRetriever.java      // 经验检索（关键词 + 向量）
│
├── toolmaker/                         // Level 2: 工具自生成
│   ├── ToolMaker.java                // 工具生成器
│   ├── ToolSpec.java                 // 工具规格描述
│   ├── ToolValidator.java            // 工具验证器（编译 + 沙盒测试）
│   ├── ToolPersister.java            // 工具持久化
│   ├── DynamicToolLoader.java        // 动态工具加载器
│   └── CreateToolTool.java           // 暴露给 LLM 的"造工具"内置工具
│
└── strategy/                          // Level 3: 策略进化
    ├── StrategyEvolver.java          // 策略优化器
    ├── SessionMetrics.java           // 会话执行指标
    ├── MetricsCollector.java         // 指标采集器
    ├── EvolutionDirective.java       // 进化指令（优化后的策略）
    └── DirectiveStore.java           // 指令存储
```

```
jharness2-core/src/main/java/io/leavesfly/jharness2/core/engine/
└── EvolutionCustomizer.java           // 进化子系统初始化 Customizer

jharness2-core/src/main/java/io/leavesfly/jharness2/core/spi/
└── ExperienceSearchService.java       // 向量化经验检索 SPI（可选增强）
```

### 3.2 依赖关系

```
EvolutionEngine (engine 模块，零 Spring)
    ├── ExperienceExtractor  → 依赖 LlmClient（复用 engine 已有的）
    ├── ExperienceStore      → 依赖文件系统（workspace 目录）
    ├── ToolMaker            → 依赖 LlmClient + ToolRegistry
    ├── ToolValidator        → 依赖 javax.tools.JavaCompiler
    ├── StrategyEvolver      → 依赖 LlmClient + MetricsCollector
    └── MetricsCollector     → 依赖 HookExecutor（监听事件采集）

EvolutionCustomizer (core 模块，Spring)
    └── 组装 EvolutionEngine 并注入 EngineContext
```

### 3.3 数据流全景

```
用户发送消息
    │
    ▼
[QueryEngine ReAct 循环]
    │
    ├──── MetricsCollector 实时采集 ──→ SessionMetrics
    │     (通过 Hook: POST_TOOL_USE)
    │
    ▼
[会话结束 — Hook: SESSION_END 触发]
    │
    ├──→ ExperienceExtractor
    │       │
    │       ├── LLM self-reflection: 提取经验
    │       └── ExperienceStore.save()
    │
    ├──→ MetricsCollector.flush()
    │       │
    │       └── SessionMetrics 持久化
    │
    └──→ StrategyEvolver.evaluate() (每 N 次会话)
            │
            ├── 分析 metrics 趋势
            ├── LLM 生成优化指令
            └── DirectiveStore.save()

下次会话创建时:
    │
    ├── ExperienceRetriever.retrieve(taskDescription)
    │       └── 相关经验注入 system prompt
    │
    ├── DynamicToolLoader.loadUserTools()
    │       └── 自生成工具注册到 ToolRegistry
    │
    └── StrategyEvolver.getActiveDirective()
            └── 进化指令追加到 system prompt
```

---

## 四、Level 1 详细设计 — 经验记忆系统

### 4.1 数据模型

```java
package io.leavesfly.jharness2.engine.ext.evolution.experience;

public class Experience {
    private String id;                    // UUID
    private String userId;                // 所属用户
    private String taskPattern;           // 任务模式摘要（LLM 生成）
    private List<String> toolsUsed;       // 使用的工具列表
    private String strategy;             // 采用的策略描述
    private boolean success;             // 任务是否成功完成
    private String lesson;               // 经验教训（LLM self-reflection）
    private List<String> keywords;       // 用于检索的关键词
    private int turnsUsed;               // 消耗的轮次
    private long tokensUsed;             // 消耗的 token
    private Instant createdAt;           // 创建时间
    private float relevanceScore;        // 被引用时的相关性评分（自优化用）
}
```

### 4.2 经验提取流程

```java
public class ExperienceExtractor {

    private final LlmClient llmClient;
    private final ExperienceStore store;

    /**
     * 从一次完整的会话消息中提取经验。
     * 在 SESSION_END Hook 中异步调用。
     */
    public CompletableFuture<Experience> extract(String userId,
                                                  List<ConversationMessage> messages,
                                                  SessionMetrics metrics) {
        // 1. 构造 reflection prompt
        String reflectionPrompt = buildReflectionPrompt(messages, metrics);

        // 2. 调用 LLM 进行 self-reflection（短回复，低 token 消耗）
        // 3. 解析 LLM 返回的结构化 JSON
        // 4. 构造 Experience 对象并持久化
    }
}
```

**Reflection Prompt 模板**：

```
请分析以下对话过程，提取可复用的经验：

## 对话摘要
[最近 N 条关键消息]

## 执行指标
- 工具调用: {toolSequence}
- 轮次: {turns}/{maxTurns}
- 是否成功完成用户任务: 请判断

## 请输出 JSON 格式：
{
  "taskPattern": "用一句话描述任务模式",
  "strategy": "成功/失败的关键策略",
  "lesson": "下次遇到类似任务时应该注意什么",
  "keywords": ["关键词1", "关键词2"],
  "success": true/false
}
```

### 4.3 经验存储

```java
public interface ExperienceStore {
    void save(Experience experience);
    List<Experience> search(String userId, String query, int topK);
    List<Experience> getRecent(String userId, int limit);
    void delete(String experienceId);
    void updateRelevanceScore(String experienceId, float score);
}
```

**默认实现 `FileExperienceStore`**：
- 存储路径：`{workspace}/.jharness2/evolution/experiences/`
- 每条经验一个 JSON 文件：`{id}.json`
- 索引文件：`index.json`（轻量级倒排索引 keyword → experience_id）
- 符合 engine 层零 Spring 依赖的要求

### 4.4 经验注入

在引擎创建时，将相关经验注入 system prompt：

```java
public class ExperienceRetriever {
    /**
     * 根据用户的新输入检索相关经验。
     * 优先级：最近的成功经验 > 失败教训 > 旧经验
     */
    public String buildExperienceSection(String userId, String taskDescription, int maxExperiences) {
        List<Experience> relevant = store.search(userId, taskDescription, maxExperiences);
        if (relevant.isEmpty()) return "";

        StringBuilder section = new StringBuilder("\n## 相关历史经验\n");
        for (Experience exp : relevant) {
            section.append("- [").append(exp.isSuccess() ? "✓" : "✗").append("] ")
                   .append(exp.getTaskPattern()).append("\n")
                   .append("  教训: ").append(exp.getLesson()).append("\n");
        }
        return section.toString();
    }
}
```

### 4.5 与 core 层的集成

通过 `EvolutionCustomizer` 在引擎创建时初始化：

```java
@Component
public class EvolutionCustomizer implements EngineCustomizer {

    @Override
    public void customize(QueryEngine engine, UserContext context, Path workspace) {
        EvolutionConfig config = loadEvolutionConfig(workspace);
        if (!config.isEnabled()) return;

        EvolutionEngine evolutionEngine = new EvolutionEngine(
            engine.getLlmClient(), workspace, config);

        // 注册 SESSION_END Hook 进行经验提取
        if (engine.getHookExecutor() != null) {
            engine.getHookExecutor().register(HookEvent.SESSION_END, (event, payload) -> {
                evolutionEngine.onSessionEnd(engine.getMessages(), /*metrics*/);
            });
        }

        // 注册到 EngineContext
        engine.getEngineContext().registerExtension(EvolutionEngine.class, evolutionEngine);
    }

    @Override
    public int getOrder() { return 100; } // 在其他 customizer 之后执行
}
```

---

## 五、Level 2 详细设计 — 工具自生成

### 5.1 核心流程

```
Agent 执行任务 → 发现能力缺口 → 调用 CreateToolTool
    │
    ▼
ToolMaker.create(toolSpec)
    │
    ├── 1. LLM 生成工具源码（继承 BaseTool<T>）
    ├── 2. ToolValidator 编译验证
    ├── 3. ToolValidator 沙盒测试（模拟调用）
    ├── 4. 安全审查（Guardrail 检查生成的代码）
    │
    ▼ (全部通过)
    │
    ├── 5. ToolPersister 保存到 workspace
    └── 6. ToolRegistry.register() 运行时注册
```

### 5.2 暴露给 LLM 的内置工具

```java
public class CreateToolTool extends BaseTool<CreateToolInput> {

    @Override
    public String getName() { return "create_tool"; }

    @Override
    public String getDescription() {
        return "当你发现现有工具无法满足当前任务需求时，使用此工具创建一个新的自定义工具。" +
               "你需要描述工具的功能、输入参数和预期行为，系统会自动生成、验证并注册该工具。";
    }
}

public class CreateToolInput {
    @ToolParam(description = "工具名称（snake_case）", required = true)
    private String toolName;

    @ToolParam(description = "工具功能描述")
    private String description;

    @ToolParam(description = "输入参数定义（JSON Schema 格式）")
    private String inputSchema;

    @ToolParam(description = "工具的核心逻辑描述（自然语言）")
    private String logic;

    @ToolParam(description = "测试用例（输入 → 预期输出）")
    private String testCase;
}
```

### 5.3 动态编译与安全

```java
public class ToolValidator {

    private static final Set<String> ALLOWED_IMPORTS = Set.of(
        "java.util.*", "java.io.File", "java.nio.file.*",
        "java.net.http.*", "com.fasterxml.jackson.*"
    );

    private static final Set<String> DENIED_PATTERNS = Set.of(
        "Runtime.getRuntime", "ProcessBuilder", "System.exit",
        "Thread.sleep", "ClassLoader", "Unsafe"
    );

    /**
     * 编译验证 + 安全审查。
     */
    public ValidationResult validate(String sourceCode) {
        // 1. 静态代码审查：检查禁止的 API 调用
        // 2. 使用 javax.tools.JavaCompiler 内存编译
        // 3. 反射实例化并校验是否正确继承 BaseTool
        // 4. 用提供的 testCase 做一次沙盒执行
    }
}
```

### 5.4 持久化与自动加载

生成的工具存储在用户 workspace：

```
{workspace}/.jharness2/evolution/tools/
├── custom_csv_parser/
│   ├── tool.json          // 工具元数据
│   ├── CsvParserTool.java // 源码
│   └── CsvParserTool.class // 编译产物
└── custom_api_client/
    ├── tool.json
    ├── ApiClientTool.java
    └── ApiClientTool.class
```

`DynamicToolLoader` 在引擎创建时自动扫描并注册：

```java
public class DynamicToolLoader {
    public List<BaseTool<?>> loadUserTools(Path workspace) {
        Path toolsDir = workspace.resolve(".jharness2/evolution/tools");
        // 扫描 → 类加载 → 实例化 → 返回工具列表
    }
}
```

---

## 六、Level 3 详细设计 — 策略进化

### 6.1 指标采集

```java
public class SessionMetrics {
    private String sessionId;
    private int turnsUsed;
    private int maxTurns;
    private long inputTokens;
    private long outputTokens;
    private long durationMs;
    private boolean taskCompleted;        // LLM 自评是否完成
    private List<String> toolSequence;    // 工具调用序列
    private int toolErrorCount;           // 工具执行失败次数
    private int retryCount;              // 重试次数
    private float userSatisfaction;      // 用户满意度（通过反馈收集）
}
```

`MetricsCollector` 通过 Hook 系统被动采集：

```java
public class MetricsCollector implements HookHandler {
    // 监听 POST_TOOL_USE → 记录工具调用
    // 监听 SESSION_END → flush 本次 metrics
}
```

### 6.2 策略优化

```java
public class StrategyEvolver {

    private static final int EVALUATION_WINDOW = 10; // 每 10 次会话评估一次

    /**
     * 分析最近 N 次会话的 metrics，生成优化指令。
     */
    public Optional<EvolutionDirective> evolve(List<SessionMetrics> recentMetrics) {
        // 1. 统计分析：成功率、平均轮次、token 效率、常用工具模式
        // 2. 识别问题：哪些类型任务失败率高？哪些工具经常出错？
        // 3. LLM 生成优化建议（结构化 JSON）
        // 4. 与上一版 directive 对比，生成 diff
        // 5. 保存新版 directive
    }
}
```

### 6.3 进化指令数据模型

```java
public class EvolutionDirective {
    private String id;
    private int version;
    private Instant createdAt;
    private String promptAddendum;        // 追加到 system prompt 的优化指令
    private Map<String, Integer> toolPreference;  // 工具偏好权重
    private int suggestedMaxTurns;        // 建议的 maxTurns
    private List<String> avoidPatterns;   // 应避免的模式
    private EvolutionDirective previous;  // 上一版（用于回滚）
    private float effectivenessScore;     // 有效性评分（A/B 对比后填入）
}
```

### 6.4 A/B 验证机制

```java
public class DirectiveEvaluator {
    /**
     * 对比新旧 directive 的效果：
     * - 随机 50% 的会话使用新 directive
     * - 50% 使用旧 directive（baseline）
     * - 收集 N 次后对比 metrics
     * - 新版显著优于旧版 → 正式采用
     * - 否则 → 回滚
     */
    public EvaluationResult evaluate(EvolutionDirective candidate,
                                     EvolutionDirective baseline,
                                     List<SessionMetrics> candidateMetrics,
                                     List<SessionMetrics> baselineMetrics);
}
```

---

## 七、安全设计

### 7.1 安全约束矩阵

| 进化能力 | 风险 | 防御措施 |
|----------|------|----------|
| 经验记忆 | 中毒攻击（注入恶意经验） | 经验来源限于自身会话；写入前 Guardrail 检查 |
| 工具生成 | 恶意代码执行 | 白名单 import；禁止 Runtime/ProcessBuilder；沙盒测试 |
| 工具生成 | 资源耗尽 | 编译超时 10s；执行超时 5s；内存限制 |
| 策略进化 | Prompt 注入 | Directive 内容长度限制；不含用户原始输入 |
| 全局 | 无限递归 | 进化操作本身不触发新的进化（防循环） |

### 7.2 权限控制

```java
public class EvolutionConfig {
    private boolean enabled = false;                    // 总开关
    private boolean experienceEnabled = true;           // Level 1 开关
    private boolean toolMakerEnabled = false;           // Level 2 开关（默认关闭）
    private boolean strategyEvolutionEnabled = false;   // Level 3 开关
    private int maxExperiencesPerUser = 100;            // 经验上限
    private int maxCustomToolsPerUser = 10;             // 自定义工具上限
    private int maxDirectiveLength = 500;               // 指令最大字符数
    private List<String> deniedToolImports = List.of(); // 工具代码禁止的 import
}
```

### 7.3 审计

所有进化操作通过 `ToolAuditLogger` SPI 记录：

```java
// 经验提取事件
ToolAuditEntry.builder()
    .type("evolution_experience_extracted")
    .userId(userId)
    .input(sessionSummary)
    .output(experience.toJson())
    .build();

// 工具生成事件
ToolAuditEntry.builder()
    .type("evolution_tool_created")
    .userId(userId)
    .input(toolSpec.toJson())
    .output(validationResult.toJson())
    .build();
```

---

## 八、配置设计

### 8.1 application.yml 新增配置

```yaml
jharness2:
  engine:
    evolution:
      enabled: true
      experience:
        enabled: true
        max-per-user: 100
        extraction-model: null          # null 表示复用引擎默认模型
        auto-extract-on-session-end: true
        min-turns-to-extract: 3         # 少于 3 轮的会话不提取经验
      tool-maker:
        enabled: false                  # 默认关闭，需显式开启
        max-tools-per-user: 10
        compile-timeout-seconds: 10
        execute-timeout-seconds: 5
        denied-imports:
          - java.lang.reflect.*
          - sun.misc.*
      strategy:
        enabled: false
        evaluation-window: 10           # 每 10 次会话评估一次
        ab-test-sessions: 20            # A/B 测试样本数
        max-directive-length: 500
```

### 8.2 Engine 模块配置（无 Spring，纯 Java）

```java
public class EvolutionConfig {
    // 从 workspace/.jharness2/evolution/config.json 加载
    // 或通过 EvolutionCustomizer 从 Spring 配置注入
    public static EvolutionConfig load(Path workspace) { ... }
}
```

---

## 九、Metrics 与可观测性

### 9.1 新增 Metrics 指标

| 指标名 | 类型 | 描述 |
|--------|------|------|
| `jharness2.evolution.experience.extracted.total` | Counter | 经验提取总数 |
| `jharness2.evolution.experience.retrieved.total` | Counter | 经验检索命中总数 |
| `jharness2.evolution.tool.created.total` | Counter | 工具生成总数 |
| `jharness2.evolution.tool.creation.failed.total` | Counter | 工具生成失败总数 |
| `jharness2.evolution.directive.updated.total` | Counter | 策略更新总数 |
| `jharness2.evolution.directive.rollback.total` | Counter | 策略回滚总数 |
| `jharness2.evolution.experience.store.size` | Gauge | 当前经验库大小 |

### 9.2 Tracer 集成

所有进化操作通过现有 `Tracer` 系统记录 Span：

```java
Span extractSpan = tracer.startSpan("evolution-experience-extract", Map.of(
    "userId", userId,
    "sessionId", sessionId,
    "messageCount", messages.size()
));
```

---

## 十、存储结构

### 10.1 文件系统布局（engine 层）

```
{workspace}/.jharness2/evolution/
├── config.json                        // 进化配置
├── experiences/
│   ├── index.json                     // 倒排索引
│   ├── {id-1}.json                    // 经验记录
│   ├── {id-2}.json
│   └── ...
├── tools/
│   ├── {tool-name-1}/
│   │   ├── tool.json                  // 工具元数据
│   │   ├── ToolImpl.java             // 源码
│   │   └── ToolImpl.class            // 编译产物
│   └── {tool-name-2}/
│       └── ...
├── strategy/
│   ├── metrics/
│   │   ├── {session-id-1}.json       // 会话指标
│   │   └── ...
│   ├── directives/
│   │   ├── v1.json                    // 历史指令
│   │   ├── v2.json
│   │   └── current.json → v2.json    // 当前生效指令（符号链接）
│   └── evaluation/
│       └── ab-test-{id}.json         // A/B 测试结果
└── audit.log                          // 本地审计日志（补充）
```

### 10.2 数据库存储（core 层，可选增强）

当 `VectorMemorySearch` SPI 有实现时，经验检索自动升级为语义搜索：

```sql
-- 新增表（由 storage 模块实现）
CREATE TABLE evolution_experience (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    task_pattern TEXT,
    strategy TEXT,
    lesson TEXT,
    success BOOLEAN,
    keywords JSON,
    tools_used JSON,
    turns_used INT,
    tokens_used BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at)
);

CREATE TABLE evolution_directive (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    prompt_addendum TEXT,
    effectiveness_score FLOAT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_version (user_id, version)
);
```

---

## 十一、实施计划

### Phase 1：经验记忆系统（预计 3 天）

| 步骤 | 任务 | 产出 |
|------|------|------|
| 1.1 | 实现 `Experience` + `ExperienceStore` + `FileExperienceStore` | engine 层数据模型和存储 |
| 1.2 | 实现 `ExperienceExtractor`（LLM reflection） | 经验提取逻辑 |
| 1.3 | 实现 `ExperienceRetriever`（关键词匹配） | 经验检索 |
| 1.4 | 实现 `EvolutionEngine` 统一入口 | 进化引擎门面 |
| 1.5 | 实现 `EvolutionCustomizer` | core 层集成 |
| 1.6 | 在 `DefaultEngineFactory.buildSystemPrompt()` 中注入经验 | 经验生效路径 |
| 1.7 | 单元测试 + 集成测试 | 质量保障 |

### Phase 2：工具自生成（预计 5 天）

| 步骤 | 任务 | 产出 |
|------|------|------|
| 2.1 | 实现 `CreateToolTool`（暴露给 LLM 的工具） | Agent 入口 |
| 2.2 | 实现 `ToolMaker`（LLM 生成代码） | 代码生成 |
| 2.3 | 实现 `ToolValidator`（编译 + 安全审查 + 沙盒） | 安全验证 |
| 2.4 | 实现 `DynamicToolLoader`（加载 + 注册） | 运行时集成 |
| 2.5 | 实现 `ToolPersister`（持久化） | 跨会话保留 |
| 2.6 | 安全测试（恶意代码防御） | 安全保障 |

### Phase 3：策略进化（预计 4 天）

| 步骤 | 任务 | 产出 |
|------|------|------|
| 3.1 | 实现 `MetricsCollector`（通过 Hook 采集） | 数据基础 |
| 3.2 | 实现 `StrategyEvolver`（LLM 分析 + 优化） | 策略生成 |
| 3.3 | 实现 `DirectiveEvaluator`（A/B 测试） | 效果验证 |
| 3.4 | 实现 directive 注入 system prompt | 策略生效 |
| 3.5 | Metrics 埋点 + 可观测性 | 运维保障 |

---

## 十二、风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| 经验提取消耗额外 token | 高 | 低 | 使用轻量模型；仅对有意义的会话提取 |
| 自生成工具有 bug 导致用户任务失败 | 中 | 中 | 工具默认标记为 experimental；失败 2 次自动禁用 |
| 策略进化方向偏移（越优化越差） | 低 | 高 | A/B 验证 + 自动回滚机制 |
| 经验库膨胀影响检索效率 | 中 | 低 | LRU 淘汰 + 定期归并相似经验 |
| 动态类加载的安全性 | 中 | 高 | 独立 ClassLoader + SecurityManager 沙盒 |

---

## 十三、扩展演进方向

1. **跨用户经验共享**：管理员可将高质量经验标记为"全局经验"，共享给所有用户
2. **经验向量化**：接入 `EmbeddingService` SPI，实现语义级经验检索
3. **工具市场**：用户生成的工具可发布到共享市场，其他用户一键安装
4. **多 Agent 协同进化**：Sub-Agent 的执行经验汇总到主 Agent 的经验库
5. **自动 Skill 生成**：将稳定的策略+工具组合自动固化为 `SkillDefinition`

---

## 十四、总结

本方案在保持 jharness2-engine 零框架依赖的纯净性的前提下，通过三层递进式设计为 Agent 赋予递归自我改进能力：

- **Level 1（经验记忆）**：成本最低、收益最高，建议优先实现
- **Level 2（工具自生成）**：显著扩展 Agent 能力边界，但需要严格的安全控制
- **Level 3（策略进化）**：长期价值最大，需要足够的数据积累后才能发挥作用

三个层次共同构成 Agent 的"进化闭环"：**做任务 → 提取经验 → 造新工具 → 优化策略 → 做得更好 → ...**
