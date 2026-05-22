# JHarness2 用户使用指南

从部署到使用的完整手册，覆盖运维部署和日常使用两大场景。

---

## 目录

- [环境要求](#环境要求)
- [快速部署](#快速部署)
- [配置说明](#配置说明)
- [用户使用](#用户使用)
- [API 使用详解](#api-使用详解)
- [工具与插件扩展](#工具与插件扩展)
- [运维管理](#运维管理)
- [常见问题](#常见问题)

---

## 环境要求

| 依赖 | 最低版本 | 说明 |
|------|----------|------|
| JDK | 17+ | 推荐 Eclipse Temurin |
| Maven | 3.8+ | 或使用项目自带的 `./mvnw` |
| LLM 服务 | — | Ollama（本地）或任何 OpenAI 兼容 API |

**可选：**
- Docker + Docker Compose（容器化部署）
- MySQL 8.0+（生产环境替代 H2）

---

## 快速部署

### 方式一：一键脚本（推荐）

```bash
git clone <repo-url> && cd jharness2
./start.sh
```

脚本会自动：
1. 检测 Java 版本
2. 查找 Maven 或使用内置 `mvnw`
3. 编译项目
4. 检测并启动 Ollama + 拉取默认模型
5. 启动服务

### 方式二：Docker Compose

```bash
docker compose up -d
```

自动拉起 JHarness2 + Ollama 两个容器，开箱即用。

首次启动需要等待 Ollama 拉取模型，可通过以下命令查看进度：

```bash
docker compose exec ollama ollama pull qwen3.5:4b
```

### 方式三：手动编译运行

```bash
# 编译
mvn clean package -DskipTests

# 启动
java -jar jharness2-web/target/jharness2-web-0.1.0-SNAPSHOT.jar
```

### 启动成功标志

启动后控制台会打印：

```
──────────────────────────────────────────────────────
  ✅ JHarness2 启动成功！
──────────────────────────────────────────────────────
  本地访问:   http://localhost:8080
  默认账户:   admin / admin123
──────────────────────────────────────────────────────
```

---

## 配置说明

### 切换 LLM 模型

**使用 Ollama（默认）：**

```bash
# 安装 Ollama: https://ollama.com/download
ollama pull qwen3.5:4b
# 启动后默认连接 http://localhost:11434/v1
```

**使用 OpenAI：**

```bash
export JHARNESS2_ENGINE_DEFAULT_BASE_URL=https://api.openai.com/v1
export JHARNESS2_ENGINE_DEFAULT_API_KEY=sk-your-key
export JHARNESS2_ENGINE_DEFAULT_MODEL=gpt-4o
```

**使用通义千问：**

```bash
export JHARNESS2_ENGINE_DEFAULT_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export JHARNESS2_ENGINE_DEFAULT_API_KEY=sk-your-key
export JHARNESS2_ENGINE_DEFAULT_MODEL=qwen-max
```

### 切换数据库（生产环境）

默认使用 H2 嵌入式数据库（零配置），生产环境建议切换 MySQL：

```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/jharness2?useSSL=false&serverTimezone=UTC
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=your-password
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

### JWT 密钥（生产环境必改）

```bash
export JHARNESS2_SECURITY_JWT_SECRET=your-random-256bit-secret
```

### 完整配置参考

详见 [wiki/10-配置参考.md](wiki/10-配置参考.md)。

---

## 用户使用

### 第一步：获取 Token

系统预置管理员账户 `admin / admin123`，也可注册新用户：

```bash
# 注册新用户
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice123"}' | jq .

# 登录已有用户
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq .
```

返回示例：
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin"
}
```

保存 Token 供后续使用：
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."
```

### 第二步：创建会话

```bash
curl -s -X POST http://localhost:8080/api/chat/new \
  -H "Authorization: Bearer $TOKEN" | jq .
```

返回：
```json
{
  "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

### 第三步：流式对话

```bash
SID="a1b2c3d4-e5f6-7890-abcd-ef1234567890"

curl -N -X POST "http://localhost:8080/api/chat/$SID/message" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"用 Java 写一个快速排序，保存到 QuickSort.java"}'
```

响应是 SSE 事件流，实时推送：

```
data: {"type":"text","content":"好的，"}
data: {"type":"text","content":"我来帮你实现"}
data: {"type":"tool_start","toolName":"write_file","toolId":"call_1","toolInput":"{...}"}
data: {"type":"tool_end","toolName":"write_file","toolId":"call_1","toolResult":"File written","toolError":false}
data: {"type":"text","content":"文件已创建。"}
data: {"type":"usage","inputTokens":150,"outputTokens":320}
data: {"type":"done","done":true}
```

### 第四步：管理会话

```bash
# 查看所有会话
curl -s http://localhost:8080/api/sessions \
  -H "Authorization: Bearer $TOKEN" | jq .

# 查看某个会话详情（含历史消息）
curl -s http://localhost:8080/api/sessions/$SID \
  -H "Authorization: Bearer $TOKEN" | jq .

# 删除会话
curl -s -X DELETE http://localhost:8080/api/sessions/$SID \
  -H "Authorization: Bearer $TOKEN"
```

### 取消生成

当 Agent 正在执行长时间任务时，可随时取消：

```bash
curl -s -X POST "http://localhost:8080/api/chat/$SID/cancel" \
  -H "Authorization: Bearer $TOKEN"
```

---

## API 使用详解

### SSE 事件类型

| type | 含义 | 关键字段 |
|------|------|----------|
| `text` | LLM 文本增量 | `content` |
| `tool_start` | 工具开始执行 | `toolName`, `toolId`, `toolInput` |
| `tool_end` | 工具执行完成 | `toolName`, `toolId`, `toolResult`, `toolError` |
| `usage` | Token 用量 | `inputTokens`, `outputTokens` |
| `done` | 对话轮次结束 | `done=true` |
| `error` | 执行错误 | `error` |

### 错误码

| HTTP 状态码 | 场景 | 处理建议 |
|-------------|------|----------|
| 400 | 参数校验失败 | 检查请求体格式 |
| 401 | Token 无效/过期 | 重新登录获取新 Token |
| 404 | 会话不存在 | 创建新会话 |
| 409 | 用户名已存在 | 更换用户名或直接登录 |
| 429 | 引擎数量超限 | 等待空闲引擎释放或关闭旧会话 |
| 500 | 服务器内部错误 | 检查日志 |

### 前端集成示例

**JavaScript EventSource：**

```javascript
const token = "your-jwt-token";
const sessionId = "your-session-id";

const response = await fetch(`/api/chat/${sessionId}/message`, {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ message: "你好" })
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  
  const lines = decoder.decode(value).split('\n');
  for (const line of lines) {
    if (line.startsWith('data: ')) {
      const event = JSON.parse(line.slice(6));
      switch (event.type) {
        case 'text':
          appendToChat(event.content);
          break;
        case 'tool_start':
          showToolExecution(event.toolName);
          break;
        case 'done':
          markComplete();
          break;
      }
    }
  }
}
```

---

## 工具与插件扩展

### 内置工具

Agent 开箱即用的工具能力：

| 工具 | 功能 | 示例场景 |
|------|------|----------|
| `file_read` | 读取文件内容 | "帮我看看 pom.xml" |
| `file_write` | 创建/修改文件 | "写一个 Hello World" |
| `bash` | 执行 Shell 命令 | "列出当前目录文件" |
| `grep` | 文本搜索 | "搜索包含 TODO 的文件" |
| `glob` | 文件模式匹配 | "找出所有 .java 文件" |

### 技能系统（Skill）

技能是对工具的高层语义封装，Agent 会自动识别并调用：

**技能目录加载顺序：**
1. 内置技能（引擎自带）
2. 用户技能（`~/.jharness/skills/`）
3. 项目技能（`<workspace>/.jharness/skills/`）

### 插件系统（Plugin）

插件可携带 Skills、Tools、Agents、Hooks 和 MCP 配置。

**插件目录：** `<workspace>/.jharness/plugins/<plugin-name>/`

**插件清单文件** `manifest.json`：
```json
{
  "name": "my-plugin",
  "version": "1.0.0",
  "description": "自定义插件",
  "skills": ["skill1.md"],
  "commands": ["/my-command"],
  "hooks": ["audit-hook.sh"]
}
```

### MCP 协议扩展

支持连接外部 MCP 服务器，动态扩展 Agent 能力：

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/dir"]
    }
  }
}
```

---

## 运维管理

### 健康检查

```bash
curl http://localhost:8080/actuator/health
```

### 系统状态

```bash
curl -s http://localhost:8080/api/system/status \
  -H "Authorization: Bearer $TOKEN" | jq .
```

返回当前活跃引擎数、最大容量、运行时长等信息。

### 引擎资源管理

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 单用户最大引擎数 | 5 | 超限返回 HTTP 429 |
| 全局最大引擎数 | 200 | 每引擎约 50-100MB 内存 |
| 空闲超时 | 30 分钟 | 超时自动回收 |

**调优建议：**
- 高并发场景：增大 `max-total-engines`，需对应增加 JVM 堆内存
- 内存受限：减小 `max-engines-per-user` 和 `engine-idle-timeout-minutes`
- 长对话场景：增大 `max-turns`（默认 12，最大建议 20）

### 日志

```bash
# 调整日志级别
java -jar app.jar --logging.level.io.leavesfly.jharness2=DEBUG
```

### 数据备份

```bash
# H2 数据库文件
cp ./data/jharness2-db.mv.db backup/

# 用户工作空间
cp -r ./data/workspaces/ backup/workspaces/
```

---

## 常见问题

### Q: 启动后对话报错 "Connection refused"

LLM 服务未启动。检查 Ollama 是否运行：
```bash
curl http://localhost:11434/api/tags
# 如未运行：
ollama serve
```

### Q: 对话无响应或很慢

1. 确认模型已下载完成：`ollama list`
2. 首次加载模型需要时间（需载入显存/内存）
3. 尝试更小的模型：`--jharness2.engine.default-model=qwen3.5:1b`

### Q: Token 过期怎么办？

Token 默认 24 小时有效，过期后重新调用 `/api/auth/login` 获取新 Token。

### Q: 如何限制用户的文件访问范围？

Agent 的文件操作被限制在用户 workspace 目录内（`./data/workspaces/<username>/`），无法访问外部文件。

### Q: 如何增加自定义工具？

参考 [wiki/09-工具与插件系统.md](wiki/09-工具与插件系统.md) 中的扩展开发部分。

### Q: 429 Too Many Engines 怎么处理？

单用户最多 5 个并发引擎。解决方案：
1. 删除不需要的会话释放引擎
2. 等待 30 分钟空闲超时自动回收
3. 修改配置 `max-engines-per-user` 增大限制

---

## 更多资料

- [项目 README](README.md) — 项目概览与架构
- [wiki/](wiki/README.md) — 完整技术文档
  - 整体架构设计
  - QueryEngine ReAct 循环详解
  - 安全认证与权限系统
  - 全量配置参考
