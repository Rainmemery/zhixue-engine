#!/bin/bash

echo "========================================="
echo "Docker 配置验证脚本"
echo "========================================="
echo ""

errors=0
warnings=0

check_file() {
    local file=$1
    local description=$2

    if [ -f "$file" ]; then
        echo "✓ $description: $file"
    else
        echo "✗ 缺失文件: $file"
        ((errors++))
    fi
}

check_directory() {
    local dir=$1
    local description=$2

    if [ -d "$dir" ]; then
        echo "✓ $description: $dir"
    else
        echo "✗ 缺失目录: $dir"
        ((errors++))
    fi
}

echo "检查必需文件..."
echo ""

check_file "docker-compose.yml" "Docker Compose配置文件"
check_file ".env" "环境变量文件"
check_file "deploy.sh" "部署脚本"
check_file "stop.sh" "停止脚本"
check_file "README.md" "部署文档"
check_file "QUICK_REFERENCE.md" "快速参考文档"

echo ""
echo "检查Dockerfile..."
echo ""

check_file "frontend/Dockerfile" "前端Dockerfile"
check_file "frontend/nginx.conf" "Nginx配置文件"
check_file "backend/Dockerfile.gateway" "网关服务Dockerfile"
check_file "backend/Dockerfile.user" "用户服务Dockerfile"
check_file "backend/Dockerfile.problem" "问题服务Dockerfile"
check_file "backend/Dockerfile.learning" "学习服务Dockerfile"
check_file "backend/Dockerfile.ai" "AI服务Dockerfile"
check_file "backend/Dockerfile.admin" "管理服务Dockerfile"

echo ""
echo "检查初始化脚本..."
echo ""

check_directory "init-scripts" "初始化脚本目录"
check_file "init-scripts/01-create-databases.sql" "数据库初始化脚本"

echo ""
echo "验证Docker Compose配置..."
echo ""

if command -v docker-compose &> /dev/null; then
    if docker-compose config > /dev/null 2>&1; then
        echo "✓ Docker Compose配置文件格式正确"
    else
        echo "✗ Docker Compose配置文件格式错误"
        docker-compose config
        ((errors++))
    fi
else
    echo "! Docker Compose未安装，跳过配置验证"
    ((warnings++))
fi

echo ""
echo "检查环境变量..."
echo ""

if [ -f ".env" ]; then
    source .env

    if [ -z "$MYSQL_ROOT_PASSWORD" ]; then
        echo "! MySQL密码未设置，将使用默认值"
        ((warnings++))
    else
        echo "✓ MySQL密码已配置"
    fi

    echo "✓ 环境变量文件存在"
else
    echo "✗ 环境变量文件不存在"
    ((errors++))
fi

echo ""
echo "检查端口占用..."
echo ""

check_port() {
    local port=$1
    local service=$2

    if netstat -tuln 2>/dev/null | grep -q ":$port " || ss -tuln 2>/dev/null | grep -q ":$port "; then
        echo "! 端口 $port ($service) 已被占用"
        ((warnings++))
    else
        echo "✓ 端口 $port ($service) 可用"
    fi
}

if command -v netstat &> /dev/null || command -v ss &> /dev/null; then
    check_port 80 "前端"
    check_port 3306 "MySQL"
    check_port 6379 "Redis"
    check_port 8080 "API网关"
    check_port 8848 "Nacos"
    check_port 19530 "Milvus"
else
    echo "! netstat/ss 未安装，跳过端口检查"
    ((warnings++))
fi

echo ""
echo "========================================="
echo "验证结果"
echo "========================================="
echo ""

if [ $errors -eq 0 ] && [ $warnings -eq 0 ]; then
    echo "✓ 所有检查通过！配置完整且正确。"
    echo ""
    echo "可以运行以下命令启动服务："
    echo "  ./deploy.sh"
    exit 0
elif [ $errors -eq 0 ]; then
    echo "✓ 配置完整，但有 $warnings 个警告"
    echo ""
    echo "建议解决警告后再部署。"
    exit 0
else
    echo "✗ 发现 $errors 个错误，$warnings 个警告"
    echo ""
    echo "请修复错误后再部署。"
    exit 1
fi
