# JHarness2 — 多用户 AI Agent Web 服务

将单用户 CLI 形态的 JHarness 引擎封装为支持多用户并发的 RESTful + SSE 流式 Web 服务。

## 核心特性

- **多用户隔离** — Caffeine 缓存池化 Engine 实例，每用户独立 workspace + 会话
- **SSE 流式输出** — 实时推送 LLM 文本、工具调用过程、Token 用量
- **ReAct 自主循环** — LLM 推理 → 工具调用 → 结果反馈，最多 12 轮迭代
- **多模型兼容** — OpenAI Chat Completions API 协议（Ollama / vLLM / 通义千问 / GPT）
- **四级插件体系** — Tool / Skill / Plugin / MCP
- **JWT 认证** — Spring Security + JJWT，三级权限模式
- **会话持久化** — JPA 自动保存对话历史 + 用户记忆系统
- **资源保护** — 引擎数量限制、空闲超时回收、危险命令黑名单

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java | 17 |
| 框架 | Spring Boot | 3.3.0 |
| 安全 | Spring Security + JJWT | 0.12.5 |
| 存储 | Spring Data JPA + H2/MySQL | — |
| 缓存 | Caffeine | 3.1.8 |
| HTTP 客户端 | OkHttp + SSE | 4.12.0 |
| 序列化 | Jackson | — |
| 构建 | Maven 多模块 | 3.8+ |

---

## 模块架构与依赖关系

### 分层总览

```
┌─────────────────────────────────────────────────────────────────┐
│  jharness2-web          (Web 入口层 — 唯一可执行 JAR)           │
│  依赖: core + storage + Spring Web/Security/Validation          │
├─────────────────────────────────────────────────────────────────┤
│  jharness2-storage      (持久化层)                              │
│  依赖: core + Spring Data JPA                                   │
├─────────────────────────────────────────────────────────────────┤
│  jharness2-core         (多用户管理层)                          │
│  依赖: engine + Spring Context + Caffeine                       │
├─────────────────────────────────────────────────────────────────┤
│  jharness2-engine       (AI Agent 内核 — 零 Spring 依赖)        │
│  依赖: OkHttp + Jackson (纯 Java 库，可独立使用)                │
└─────────────────────────────────────────────────────────────────┘
```

### 依赖方向

```
engine ← core ← storage ← web
  ↑                          │
  └──────────────────────────┘ (web 也直接依赖 core)
```

**严格单向依赖，无循环**。上层可访问下层 API，下层不感知上层。

### 模块详解

#### `jharness2-engine` — AI Agent 内核

**定位**：纯 Java 库，无 Spring / 无框架绑定，可独立在任意 Java 项目中使用。

| 子包 | 职责 |
|------|------|
| `QueryEngine` | ReAct 循环主驱动 |
| `OpenAiClient` | LLM API 通信（OkHttp SSE 流式） |
| `tool/` | 工具注册表 + 内置工具（Bash/File/Glob/Grep/Cron） |
| `skill/` | 技能定义与动态加载 |
| `plugin/` | 插件清单加载与管理 |
| `mcp/` | MCP (Model Context Protocol) 客户端 |
| `hook/` | Hook 事件系统 (PRE/POST_TOOL_USE, SESSION_END 等) |
| `agent/` | Sub-Agent 多智能体编排 |
| `permission/` | 工具级权限检查（PERMISSIVE / DEFAULT / STRICT） |
| `compaction/` | 长对话消息压缩（LLM 摘要） |
| `task/` | 后台异步任务管理 |
| `stream/` | 流式事件类型定义 |
| `heartbeat/` | 心跳监控 |
| `cron/` | Cron 定时任务调度器 |

**外部依赖**：OkHttp 4.12 · Jackson · SLF4J

---

#### `jharness2-core` — 多用户引擎管理

**定位**：将单用户 Engine 封装为多用户可并发的 Spring Service 层。

| 类 | 职责 |
|----|------|
| `UserEngineRegistry` | Caffeine 缓存池化（200 上限 / 30min 空闲回收 / 单用户 5 个） |
| `DefaultEngineFactory` | 引擎工厂，组装 13 个子系统创建完整 Engine 实例 |
| `ChatService` | 聊天服务门面，协调引擎获取 + 消息提交 |
| `StreamEventAdapter` | 引擎 StreamEvent → Web 层 ChatEventDto 类型转换 |
| `UserContext` | 用户会话上下文（userId / sessionId / workspace / model / apiKey） |
| `EngineInstance` | 引擎实例包装 + 生命周期元数据 |
| `WorkspaceInitializer` | 用户工作空间目录创建与隔离 |
| `EngineConfig` | Spring @ConfigurationProperties 配置绑定 |
| `spi/SessionPersistenceService` | SPI 接口，由 storage 模块实现 |

**外部依赖**：jharness2-engine · Spring Context · Caffeine 3.1.8

---

#### `jharness2-storage` — 数据持久化

**定位**：基于 Spring Data JPA 的会话和记忆存储，实现 core 模块的 SPI 接口。

| 类 | 职责 |
|----|------|
| `SessionStorageService` | 会话 CRUD（实现 `SessionPersistenceService` SPI） |
| `MemoryStorageService` | 用户记忆 CRUD + 关键词搜索 |
| `entity/` | JPA 实体（UserEntity / SessionEntity / MemoryEntity） |
| `repository/` | Spring Data Repository 接口 |

**外部依赖**：jharness2-core · Spring Data JPA · H2（dev）· MySQL（prod）

---

#### `jharness2-web` — Web 应用入口

**定位**：Spring Boot 可执行应用，暴露 REST API + SSE 流式接口。

| 包/类 | 职责 |
|--------|------|
| `JHarness2Application` | Spring Boot 启动类 |
| `controller/AuthController` | 注册 / 登录 |
| `controller/ChatController` | SSE 流式聊天 |
| `controller/SessionController` | 会话管理 |
| `controller/SystemController` | 系统状态 / 配置查询 |
| `controller/GlobalExceptionHandler` | 统一异常处理 |
| `security/` | JWT 认证过滤器 + Spring Security 配置 |
| `dto/` | 请求 / 响应 DTO |

**外部依赖**：jharness2-core · jharness2-storage · Spring Web · Spring Security · JJWT · Actuator

---

### 模块间 SPI 解耦

```
jharness2-core 定义:
  └── spi/SessionPersistenceService (接口)

jharness2-storage 实现:
  └── SessionStorageService implements SessionPersistenceService
```

core 层通过 SPI 接口声明持久化能力，storage 层提供实现，两者仅通过接口耦合。

---

## 请求处理流程

```
Client (HTTP/SSE)
    │
    ▼
[SecurityFilter] → JWT 验证 → 提取 userId
    │
    ▼
[ChatController] → 构建 UserContext
    │
    ▼
[ChatService] → UserEngineRegistry.getOrCreate(ctx)
    │                     │
    │   ┌─────────────────┴─────────────────┐
    │   │ 缓存命中?                          │
    │   │  Yes → 直接复用 EngineInstance     │
    │   │  No  → EngineFactory.create(ctx)   │
    │   │        → 组装 13 个子系统          │
    │   └───────────────────────────────────┘
    │
    ▼
[QueryEngine] → ReAct Loop
    │   ├── LLM 调用 (SSE 流式)
    │   ├── 工具调用 (权限检查 → 执行 → 结果)
    │   └── 循环直到: 纯文本回复 / maxTurns / cancel
    │
    ▼
[StreamEventAdapter] → ChatEventDto → SSE 推送 Client
```

---

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.8+
- Ollama（本地模型）或 OpenAI 兼容 API

### 编译 & 启动

```bash
cd jharness2
mvn clean package -DskipTests

# 默认 H2 + 本地 Ollama
java -jar jharness2-web/target/jharness2-web-0.1.0-SNAPSHOT.jar

# 指定云端 API
java -jar jharness2-web/target/jharness2-web-0.1.0-SNAPSHOT.jar \
  --jharness2.engine.default-base-url=https://api.openai.com/v1 \
  --jharness2.engine.default-model=gpt-4o
```

服务监听 `http://localhost:8080`。

### 快速验证

```bash
# 注册
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}' | jq -r '.token')

# 创建会话
SID=$(curl -s -X POST http://localhost:8080/api/chat/new \
  -H "Authorization: Bearer $TOKEN" | jq -r '.sessionId')

# 流式对话
curl -N -X POST "http://localhost:8080/api/chat/$SID/message" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"用 Java 写一个快速排序"}'
```

---

## API 接口

### 认证

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/register` | 注册用户 | 无 |
| POST | `/api/auth/login` | 登录获取 JWT | 无 |

### 聊天

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/chat/new` | 创建新会话 | ✅ |
| POST | `/api/chat/{sessionId}/message` | 发送消息（SSE） | ✅ |
| POST | `/api/chat/{sessionId}/cancel` | 取消生成 | ✅ |

### 会话管理

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/sessions` | 列出所有会话 | ✅ |
| GET | `/api/sessions/{sessionId}` | 获取会话详情 | ✅ |
| DELETE | `/api/sessions/{sessionId}` | 删除会话 | ✅ |

### 系统

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/system/status` | 系统运行状态 | ✅ |
| GET | `/api/system/config` | 公开配置 | ✅ |
| GET | `/actuator/health` | 健康检查 | 无 |

### SSE 事件格式

```jsonc
{"type": "text", "content": "好的，我来..."}           // LLM 文本增量
{"type": "tool_start", "toolName": "write_file", ...}  // 工具开始
{"type": "tool_end", "toolName": "write_file", ...}    // 工具完成
{"type": "usage", "inputTokens": 150, "outputTokens": 320}  // Token 用量
{"type": "done", "done": true}                         // 对话结束
```

---

## 配置参考

```yaml
jharness2:
  engine:
    default-model: qwen3.5:4b
    default-base-url: http://localhost:11434/v1
    max-tokens: 4096
    max-turns: 12
    max-engines-per-user: 5
    max-total-engines: 200
    engine-idle-timeout-minutes: 30
    denied-command-patterns:
      - "rm -rf /*"
      - "sudo *"
      - "shutdown*"
  workspace:
    root: ./data/workspaces
  security:
    jwt:
      secret: your-256-bit-secret
      expiration-ms: 86400000
```

### 数据库切换（MySQL）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/jharness2?useSSL=false&serverTimezone=UTC
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: your-password
  jpa:
    hibernate:
      ddl-auto: validate
```

---

## 数据目录

```
./data/
├── jharness2-db.mv.db       # H2 数据库文件
└── workspaces/              # 用户工作空间
    └── <username>/          # 各用户独立目录
```

## 详细文档

完整技术文档位于 [wiki/](wiki/README.md)：

- 整体架构设计 · QueryEngine ReAct 循环详解
- 多用户引擎池化机制 · 安全认证与权限系统
- 存储模型与持久化 · 工具 / 插件 / MCP 扩展开发
- 全量配置参考

## License

MIT
