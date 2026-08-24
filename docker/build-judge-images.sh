#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "========================================="
echo "  智学引擎 - 判题Docker镜像构建脚本"
echo "========================================="

echo ""
echo "[1/2] 构建 Java 判题镜像 (judge-java:latest)..."
docker build -t judge-java:latest -f "$SCRIPT_DIR/judge/Dockerfile.judge-java" "$SCRIPT_DIR/judge/"

echo ""
echo "[2/2] 构建 C/C++ 判题镜像 (judge-cpp:latest)..."
docker build -t judge-cpp:latest -f "$SCRIPT_DIR/judge/Dockerfile.judge-cpp" "$SCRIPT_DIR/judge/"

echo ""
echo "========================================="
echo "  镜像构建完成！"
echo "========================================="
echo ""
echo "已构建镜像:"
docker images | grep -E "judge-java|judge-cpp" | head -5
echo ""
echo "使用方式:"
echo "  1. 确保 Docker 服务正在运行"
echo "  2. 启动 zhixue-problem 服务 (judge.docker.enabled=true)"
echo "  3. 容器池将自动初始化并开始服务"
echo ""
echo "验证镜像:"
echo "  docker run --rm judge-java:latest javac -version"
echo "  docker run --rm judge-cpp:latest g++ --version"
