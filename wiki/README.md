# JHarness2 技术文档

> 基于 JHarness 内核的多用户 AI Agent Web 服务 — 完整技术文档

## 项目简介

JHarness2 将单用户 CLI 形态的 JHarness AI Agent 引擎封装为支持多用户并发的 Web 服务。通过 Spring Boot 提供 RESTful API 和 SSE（Server-Sent Events）流式输出，适用于团队内部私有化部署的 AI 编程助手平台。

### 核心能力一览

| 能力 | 实现方式 |
|------|----------|
| 多用户并发 | Caffeine 缓存池化 Engine 实例，每用户独立隔离 |
| 流式对话 | SSE 实时推送 LLM 生成内容 + 工具执行过程 |
| 自主决策 | ReAct 循环：LLM 推理 → 工具调用 → 结果反馈 → 再推理 |
| 工具系统 | 内置工具 + 插件动态加载 + MCP 协议外接 |
| 多模型支持 | 兼容所有 OpenAI Chat Completions API（Ollama、vLLM、云端 API） |
| 安全认证 | JWT 无状态认证 + 工具级权限三级控制 |
| 数据持久化 | JPA 会话存储 + 用户记忆系统 + 自动保存 |
| 消息压缩 | 长对话自动摘要，控制 Token 消耗 |
| 多智能体 | Sub-Agent 协调器，支持任务分发与并行执行 |

### 技术栈

```
Runtime:     Java 17 + Spring Boot 3.3.0
Web:         Spring Web + SSE
Security:    Spring Security + JWT (jjwt 0.12.5)
Storage:     Spring Data JPA + H2 / MySQL
Cache:       Caffeine 3.1.8
HTTP:        OkHttp 4.12 + SSE (LLM API 通信)
Serialization: Jackson
Build:       Maven (多模块)
```

### 模块结构

```
jharness2/                         # 父 POM (Spring Boot 3.3)
├── jharness2-engine/              # AI Agent 核心引擎（无 Spring 依赖）
│   └── QueryEngine, LLM Client, Tool/Skill/Plugin/MCP/Hook/Agent
├── jharness2-core/                # 多用户引擎管理 + SPI 抽象
│   └── EngineRegistry, ChatService, Factory, Config
├── jharness2-storage/             # 数据库持久化
│   └── JPA Entity, Repository, StorageService
└── jharness2-web/                 # Spring Boot Web 应用入口
    └── Controller, DTO, Security, Application
```

依赖方向：**engine ← core ← storage ← web**（下层不感知上层）

---

## 文档索引

### 基础篇

| 编号 | 文档 | 内容概要 |
|------|------|----------|
| 01 | [项目概述](01-项目概述.md) | 项目定位、核心特性矩阵、技术栈、适用场景 |
| 02 | [快速开始](02-快速开始.md) | 环境要求、编译构建、启动服务、首次 API 调用示例 |
| 03 | [整体架构](03-整体架构.md) | 四层模块设计、组件职责、请求处理完整流程图 |

### 核心篇

| 编号 | 文档 | 内容概要 |
|------|------|----------|
| 04 | [核心引擎 QueryEngine](04-核心引擎-QueryEngine.md) | ReAct 循环机制、LLM 调用、流式事件体系、消息模型、生命周期 |
| 05 | [多用户引擎管理](05-多用户引擎管理.md) | Caffeine 池化设计、引擎工厂、资源限制、工作空间隔离、事件适配 |
| 06 | [API 接口文档](06-API接口文档.md) | 全量 RESTful API 说明、SSE 事件协议、请求/响应示例 |

### 系统篇

| 编号 | 文档 | 内容概要 |
|------|------|----------|
| 07 | [安全认证系统](07-安全认证系统.md) | JWT 认证流程、Spring Security 配置、权限三级模式、安全最佳实践 |
| 08 | [存储与持久化](08-存储与持久化.md) | ER 数据模型、会话/记忆存储服务、数据库切换指南 |
| 09 | [工具与插件系统](09-工具与插件系统.md) | Tool 注册、Skill 封装、Plugin 加载、MCP 协议、Hook 系统、多智能体 |
| 10 | [配置参考](10-配置参考.md) | 全量配置项表格、环境切换示例、性能调优建议 |

---

## 建议阅读路径

根据你的目标选择最适合的阅读路径：

### 🚀 快速上手（15 分钟）

> 目标：跑起来、调通 API

```
01-项目概述 → 02-快速开始 → 06-API接口文档
```

### 🏗️ 理解架构（30 分钟）

> 目标：理解系统设计、模块职责、数据流

```
01-项目概述 → 03-整体架构 → 04-核心引擎 → 05-多用户引擎管理
```

### 🔧 二次开发

> 目标：扩展工具、开发插件、自定义 Agent

```
03-整体架构 → 09-工具与插件系统 → 04-核心引擎 → 10-配置参考
```

### 🔒 安全与运维

> 目标：生产部署、安全加固、数据库配置

```
02-快速开始 → 10-配置参考 → 07-安全认证系统 → 08-存储与持久化
```

---

## 快速体验

```bash
# 编译
cd jharness2 && mvn clean package -DskipTests

# 启动（默认 Ollama 本地模型）
java -jar jharness2-web/target/jharness2-web-0.1.0-SNAPSHOT.jar

# 注册 + 登录
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

## 贡献与反馈

- 文档源码位于 `jharness2/wiki/` 目录
- 如发现文档与代码不一致，以代码为准并欢迎提交修正
- 技术问题请通过 Issue 反馈
