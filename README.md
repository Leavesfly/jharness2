# JHarness2 - Multi-User AI Agent Web Service

基于 JHarness 内核的多用户 AI Agent Web 服务。通过 Spring Boot 提供 RESTful API + SSE 流式输出，支持多用户并发、会话隔离、引擎池化和插件扩展。

## 特性

- **多用户并发** — Caffeine 缓存池化 Engine 实例，每用户独立 workspace + 会话隔离
- **SSE 流式输出** — 实时推送 LLM 生成文本、工具调用过程、Token 用量
- **ReAct 自主循环** — LLM 推理 → 工具调用 → 结果反馈 → 再推理，直至任务完成
- **多模型兼容** — 支持所有 OpenAI Chat Completions API（Ollama、vLLM、通义千问、GPT 等）
- **插件体系** — Tool / Skill / Plugin / MCP 四级扩展能力
- **JWT 无状态认证** — Spring Security + JJWT，三级权限控制
- **会话持久化** — JPA 自动保存对话历史 + 用户记忆系统
- **资源保护** — 引擎数量限制、空闲超时回收、危险命令黑名单

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java | 17 |
| 框架 | Spring Boot | 3.3.0 |
| 安全 | Spring Security + JJWT | 0.12.5 |
| 存储 | Spring Data JPA + H2/MySQL | - |
| 缓存 | Caffeine | 3.1.8 |
| HTTP 客户端 | OkHttp + SSE | 4.12.0 |
| 序列化 | Jackson | - |
| 构建 | Maven（多模块） | 3.8+ |

## 项目结构

```
jharness2/
├── pom.xml                              # 父 POM (Spring Boot 3.3)
│
├── jharness2-engine/                    # 🧠 AI Agent 核心引擎（无 Spring 依赖）
│   └── io.leavesfly.jharness2.core.engine/
│       ├── QueryEngine.java             #   ReAct 循环主驱动
│       ├── OpenAiClient.java            #   LLM API 通信（OkHttp SSE）
│       ├── ConversationMessage.java     #   对话消息模型
│       ├── tool/                        #   工具注册 + 执行
│       ├── skill/                       #   技能定义 + 注册
│       ├── plugin/                      #   插件加载 + 管理
│       ├── mcp/                         #   MCP 协议客户端
│       ├── hook/                        #   Hook 事件系统
│       ├── agent/                       #   多智能体协调
│       ├── permission/                  #   工具级权限检查
│       ├── compaction/                  #   消息压缩（长对话摘要）
│       ├── task/                        #   后台任务管理
│       └── stream/                      #   流式事件类型定义
│
├── jharness2-core/                      # 🔧 多用户引擎管理
│   └── io.leavesfly.jharness2.core/
│       ├── UserEngineRegistry.java      #   Caffeine 缓存池化
│       ├── DefaultEngineFactory.java    #   引擎工厂（组装 13 个子系统）
│       ├── ChatService.java             #   聊天服务门面
│       ├── ChatEventDto.java            #   统一 SSE 事件 DTO
│       ├── StreamEventAdapter.java      #   事件类型适配器
│       ├── EngineConfig.java            #   配置属性绑定
│       ├── UserContext.java             #   用户上下文
│       ├── EngineInstance.java          #   实例包装 + 生命周期
│       └── WorkspaceInitializer.java    #   工作空间目录管理
│
├── jharness2-storage/                   # 💾 数据持久化
│   └── io.leavesfly.jharness2.storage/
│       ├── entity/                      #   JPA 实体 (User/Session/Memory)
│       ├── repository/                  #   Spring Data Repository
│       ├── SessionStorageService.java   #   会话 CRUD
│       └── MemoryStorageService.java    #   记忆 CRUD + 搜索
│
└── jharness2-web/                       # 🌐 Web 应用入口
    └── io.leavesfly.jharness2.web/
        ├── JHarness2Application.java    #   Spring Boot 启动类
        ├── controller/                  #   REST 控制器
        │   ├── AuthController.java      #     注册 / 登录
        │   ├── ChatController.java      #     SSE 流式聊天
        │   ├── SessionController.java   #     会话管理
        │   └── SystemController.java    #     系统状态
        ├── dto/                         #   请求/响应 DTO
        └── security/                    #   JWT 认证过滤器
```

**依赖方向**：`engine ← core ← storage ← web`

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.8+
- Ollama（本地模型）或 OpenAI 兼容 API

### 编译 & 启动

```bash
# 编译
cd jharness2
mvn clean package -DskipTests

# 启动（默认 H2 + 本地 Ollama）
java -jar jharness2-web/target/jharness2-web-0.1.0-SNAPSHOT.jar

# 或指定云端 API
java -jar jharness2-web/target/jharness2-web-0.1.0-SNAPSHOT.jar \
  --jharness2.engine.default-base-url=https://api.openai.com/v1 \
  --jharness2.engine.default-model=gpt-4o
```

服务启动后监听 `http://localhost:8080`。

### 快速验证

```bash
# 注册并获取 Token
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
| POST | `/api/chat/{sessionId}/message` | 发送消息（SSE 流式响应） | ✅ |
| POST | `/api/chat/{sessionId}/cancel` | 取消正在进行的生成 | ✅ |

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
| GET | `/api/system/config` | 系统公开配置 | ✅ |
| GET | `/actuator/health` | 健康检查 | 无 |

### SSE 事件格式

聊天接口返回 `text/event-stream`，每个事件格式为 `data: {json}\n\n`：

```jsonc
// LLM 文本增量
{"type": "text", "content": "好的，我来..."}

// 工具开始执行
{"type": "tool_start", "toolName": "write_file", "toolId": "call_123", "toolInput": "{...}"}

// 工具执行完成
{"type": "tool_end", "toolName": "write_file", "toolId": "call_123", "toolResult": "...", "toolError": false}

// Token 用量
{"type": "usage", "inputTokens": 150, "outputTokens": 320}

// 对话结束
{"type": "done", "done": true}
```

## 配置

### 核心配置项

```yaml
jharness2:
  engine:
    default-model: qwen3.5:4b                    # 默认模型
    default-base-url: http://localhost:11434/v1   # LLM API 地址
    max-tokens: 4096                             # 单次最大输出 Token
    max-turns: 12                                # ReAct 最大循环轮次
    max-engines-per-user: 5                      # 单用户最大引擎数
    max-total-engines: 200                       # 全局最大引擎数
    engine-idle-timeout-minutes: 30              # 空闲超时回收（分钟）
    denied-command-patterns:                     # 危险命令黑名单
      - "rm -rf /*"
      - "sudo *"
      - "shutdown*"
  workspace:
    root: ./data/workspaces                      # 用户工作空间根目录
  security:
    jwt:
      secret: your-256-bit-secret                # JWT 签名密钥（生产环境必改）
      expiration-ms: 86400000                    # Token 有效期（24h）
```

### 数据库切换

默认使用 H2 嵌入式数据库（零配置开箱即用），生产环境切换为 MySQL：

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

### 环境变量覆盖

```bash
export JHARNESS2_ENGINE_DEFAULT_MODEL=gpt-4o
export JHARNESS2_ENGINE_DEFAULT_BASE_URL=https://api.openai.com/v1
export JHARNESS2_SECURITY_JWT_SECRET=your-production-secret
```

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                        Client                               │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTP / SSE
┌────────────────────────────▼────────────────────────────────┐
│  jharness2-web                                              │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────────┐ │
│  │ Security │→ │  Controller  │→ │  DTO (Req/Resp/Event) │ │
│  └──────────┘  └──────┬───────┘  └───────────────────────┘ │
├────────────────────────┼────────────────────────────────────┤
│  jharness2-core        │                                    │
│  ┌─────────────────────▼─────────────────────────────────┐  │
│  │  ChatService → UserEngineRegistry → EngineFactory     │  │
│  │                    (Caffeine Cache)                    │  │
│  └───────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  jharness2-engine                                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  QueryEngine (ReAct Loop)                             │  │
│  │  ├─ OpenAiClient (LLM SSE)                           │  │
│  │  ├─ ToolRegistry + ToolExecutor                       │  │
│  │  ├─ PermissionChecker                                 │  │
│  │  ├─ HookExecutor                                      │  │
│  │  ├─ McpManager                                        │  │
│  │  ├─ AgentOrchestrator                                 │  │
│  │  └─ MessageCompactionService                          │  │
│  └───────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  jharness2-storage                                          │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  SessionStorageService / MemoryStorageService          │  │
│  │  JPA Entity (User / Session / Memory)                 │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 数据目录

启动后自动创建：

```
./data/
├── jharness2-db.mv.db       # H2 数据库文件
└── workspaces/              # 用户工作空间
    └── <userId>/            # 各用户独立目录
```

## 详细文档

完整技术文档位于 [wiki/](wiki/README.md) 目录，包含：

- 整体架构设计
- QueryEngine ReAct 循环详解
- 多用户引擎池化机制
- 安全认证与权限系统
- 存储模型与持久化
- 工具 / 插件 / MCP 扩展开发
- 全量配置参考

## License

MIT
