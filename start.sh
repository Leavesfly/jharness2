#!/bin/bash
# JHarness2 一键启动脚本
# 自动检测环境、编译并启动服务

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

JAR_FILE="jharness2-web/target/jharness2-web-0.1.0-SNAPSHOT.jar"

# ─── 环境检测 ───────────────────────────────────────────
info "检测运行环境..."

# Java 检测
if ! command -v java &> /dev/null; then
    error "未找到 Java，请安装 JDK 17+: https://adoptium.net/"
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ] 2>/dev/null; then
    error "需要 Java 17+，当前版本: $(java -version 2>&1 | head -1)"
fi
info "Java 版本: $(java -version 2>&1 | head -1 | cut -d'"' -f2)"

# Maven 检测
if command -v mvn &> /dev/null; then
    MVN="mvn"
elif [ -x "./mvnw" ]; then
    MVN="./mvnw"
else
    error "未找到 Maven。请安装 Maven 3.8+ 或使用 ./mvnw"
fi
info "Maven: $MVN"

# ─── 编译 ───────────────────────────────────────────────
if [ ! -f "$JAR_FILE" ] || [ "$1" = "--build" ] || [ "$1" = "-b" ]; then
    info "编译项目..."
    $MVN clean package -DskipTests -q
    info "编译完成"
else
    info "已存在编译产物，跳过编译（使用 --build 强制重新编译）"
fi

# ─── LLM 服务检测 ────────────────────────────────────────
LLM_URL="${JHARNESS2_ENGINE_DEFAULT_BASE_URL:-http://localhost:11434/v1}"
if echo "$LLM_URL" | grep -q "localhost:11434"; then
    if command -v ollama &> /dev/null; then
        if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
            warn "Ollama 未运行，尝试启动..."
            ollama serve &> /dev/null &
            sleep 2
        fi
        MODEL="${JHARNESS2_ENGINE_DEFAULT_MODEL:-qwen3.5:4b}"
        if ! ollama list 2>/dev/null | grep -q "$MODEL"; then
            warn "模型 $MODEL 未下载，正在拉取（首次可能需要几分钟）..."
            ollama pull "$MODEL"
        fi
        info "Ollama 就绪，模型: $MODEL"
    else
        warn "未检测到 Ollama，聊天功能可能不可用"
        warn "安装 Ollama: https://ollama.com/download"
        warn "或设置环境变量指向其他 LLM API:"
        warn "  export JHARNESS2_ENGINE_DEFAULT_BASE_URL=https://api.openai.com/v1"
        warn "  export JHARNESS2_ENGINE_DEFAULT_API_KEY=sk-xxx"
        warn "  export JHARNESS2_ENGINE_DEFAULT_MODEL=gpt-4o"
    fi
fi

# ─── 启动 ───────────────────────────────────────────────
info "启动 JHarness2..."
echo ""

exec java -jar "$JAR_FILE" "$@"
