# API 接口文档

## 概述

JHarness2 提供 RESTful API + SSE 流式接口。所有 API 以 `/api` 为前缀，除认证接口外均需 JWT Token。

**Base URL**: `http://localhost:8080`  
**认证方式**: `Authorization: Bearer <token>`  
**Content-Type**: `application/json`

## 认证接口

### POST /api/auth/register

注册新用户。

**请求体：**
```json
{
  "username": "alice",
  "password": "password123"
}
```

**成功响应（200）：**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "alice"
}
```

**错误响应（409）：**
```json
{
  "error": "Username already exists"
}
```

### POST /api/auth/login

用户登录，获取 JWT Token。

**请求体：**
```json
{
  "username": "alice",
  "password": "password123"
}
```

**成功响应（200）：**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "alice"
}
```

**错误响应（401）：**
```json
{
  "error": "Invalid credentials"
}
```

## 聊天接口

### POST /api/chat/new

创建新的聊天会话。

**响应（200）：**
```json
{
  "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

### POST /api/chat/{sessionId}/message

发送消息并获取 SSE 流式响应。

**路径参数：**
- `sessionId` - 会话 ID

**请求体：**
```json
{
  "message": "帮我写一个快速排序算法"
}
```

**响应：** `Content-Type: text/event-stream`

SSE 事件流格式：
```
data: {"type":"text","content":"好的"}

data: {"type":"text","content":"，我来"}

data: {"type":"tool_start","toolName":"write_file","toolId":"call_123","toolInput":"{...}"}

data: {"type":"tool_end","toolName":"write_file","toolId":"call_123","toolResult":"File written","toolError":false}

data: {"type":"usage","inputTokens":150,"outputTokens":320,"costUsd":0.002}

data: {"type":"done","done":true}

```

**事件类型说明：**

| type | 字段 | 说明 |
|------|------|------|
| `text` | content | LLM 输出的文本增量 |
| `tool_start` | toolName, toolId, toolInput | 工具开始执行 |
| `tool_end` | toolName, toolId, toolResult, toolError | 工具执行完成 |
| `usage` | inputTokens, outputTokens, costUsd | Token 用量统计 |
| `done` | done=true | 对话轮次结束 |
| `error` | error | 执行错误信息 |

### POST /api/chat/{sessionId}/cancel

取消正在进行的消息生成。

**响应（200）：**
```json
{
  "cancelled": true
}
```

## 会话管理接口

### GET /api/sessions

列出当前用户的所有会话（按更新时间倒序）。

**响应（200）：**
```json
[
  {
    "sessionId": "a1b2c3d4...",
    "model": "qwen3.5:4b",
    "title": "快速排序实现",
    "messageCount": 6,
    "inputTokens": 1200,
    "outputTokens": 3500,
    "createdAt": "2026-05-22T10:00:00Z",
    "updatedAt": "2026-05-22T10:05:30Z"
  }
]
```

### GET /api/sessions/{sessionId}

获取指定会话详情（含完整消息历史）。

**响应（200）：**
```json
{
  "sessionId": "a1b2c3d4...",
  "model": "qwen3.5:4b",
  "title": "快速排序实现",
  "messageCount": 6,
  "messagesJson": "[...]",
  "inputTokens": 1200,
  "outputTokens": 3500,
  "createdAt": "2026-05-22T10:00:00Z",
  "updatedAt": "2026-05-22T10:05:30Z"
}
```

### DELETE /api/sessions/{sessionId}

删除指定会话。

**响应（204）：** 无内容

## 系统接口

### GET /api/system/status

获取系统运行状态（需认证）。

**响应（200）：**
```json
{
  "status": "running",
  "activeEngines": 12,
  "maxEngines": 200,
  "uptime": "2h 30m"
}
```

### GET /api/system/config

获取系统公开配置（需认证）。

**响应（200）：**
```json
{
  "defaultModel": "qwen3.5:4b",
  "maxEnginesPerUser": 5,
  "maxTotalEngines": 200,
  "engineIdleTimeoutMinutes": 30
}
```

## 错误处理

所有错误响应统一格式：

```json
{
  "error": "错误描述信息",
  "status": 400
}
```

**常见错误码：**

| HTTP 状态码 | 场景 |
|-------------|------|
| 400 | 请求参数校验失败 |
| 401 | 未认证 / Token 无效 |
| 404 | 会话不存在 |
| 409 | 用户名已存在 |
| 429 | 引擎数量超限 |
| 500 | 服务器内部错误 |

## 公开端点

以下端点无需认证：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /actuator/health`
