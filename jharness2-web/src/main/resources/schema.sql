CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    display_name VARCHAR(128),
    email VARCHAR(256),
    role VARCHAR(32),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    api_key VARCHAR(512),
    base_url VARCHAR(256),
    preferred_model VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    model VARCHAR(128),
    title VARCHAR(512),
    messages_json CLOB,
    message_count INT DEFAULT 0,
    input_tokens BIGINT DEFAULT 0,
    output_tokens BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_session_user_session ON chat_sessions(user_id, session_id);
CREATE INDEX IF NOT EXISTS idx_session_user ON chat_sessions(user_id);

CREATE TABLE IF NOT EXISTS user_memories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    project VARCHAR(128) NOT NULL,
    title VARCHAR(256) NOT NULL,
    content CLOB,
    category VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_memory_user_project ON user_memories(user_id, project);

-- 用量记录表
CREATE TABLE IF NOT EXISTS usage_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    recorded_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_usage_user_date ON usage_records(user_id, recorded_at);
CREATE INDEX IF NOT EXISTS idx_usage_model ON usage_records(model, recorded_at);

-- 工具执行审计日志表
CREATE TABLE IF NOT EXISTS tool_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    arguments CLOB,
    result CLOB,
    status VARCHAR(16) NOT NULL,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
    duration_ms BIGINT NOT NULL DEFAULT 0,
    executed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_user_time ON tool_audit_logs(user_id, executed_at);
CREATE INDEX IF NOT EXISTS idx_audit_session ON tool_audit_logs(user_id, session_id);
CREATE INDEX IF NOT EXISTS idx_audit_tool ON tool_audit_logs(tool_name, executed_at);

-- 后台任务表
CREATE TABLE IF NOT EXISTS agent_tasks (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    parent_task_id VARCHAR(36),
    type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    input CLOB,
    output CLOB,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    timeout_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_task_user_status ON agent_tasks(user_id, status);
CREATE INDEX IF NOT EXISTS idx_task_timeout ON agent_tasks(status, timeout_at);

-- 聊天消息表（会话消息结构化拆分）
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    seq_no INT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content CLOB NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_msg_session_seq ON chat_messages(user_id, session_id, seq_no);
CREATE INDEX IF NOT EXISTS idx_msg_role ON chat_messages(user_id, session_id, role);

-- 工作区工件表
CREATE TABLE IF NOT EXISTS workspace_artifacts (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    file_path VARCHAR(1024) NOT NULL,
    mime_type VARCHAR(128),
    size_bytes BIGINT NOT NULL DEFAULT 0,
    checksum VARCHAR(128),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_artifact_user_session ON workspace_artifacts(user_id, session_id);
CREATE INDEX IF NOT EXISTS idx_artifact_user ON workspace_artifacts(user_id, created_at);

-- 记忆向量索引表（语义检索）
CREATE TABLE IF NOT EXISTS memory_embeddings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    memory_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    project VARCHAR(128) NOT NULL,
    content_snippet VARCHAR(512),
    embedding CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_embedding_memory ON memory_embeddings(memory_id);
CREATE INDEX IF NOT EXISTS idx_embedding_user_project ON memory_embeddings(user_id, project);
