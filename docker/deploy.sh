#!/bin/bash

set -e

echo "========================================="
echo "智学平台 Docker 部署脚本"
echo "========================================="
echo ""

check_docker() {
    if ! command -v docker &> /dev/null; then
        echo "错误: Docker 未安装"
        echo "请访问 https://docs.docker.com/get-docker/ 安装 Docker"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        echo "错误: Docker Compose 未安装"
        echo "请访问 https://docs.docker.com/compose/install/ 安装 Docker Compose"
        exit 1
    fi

    echo "✓ Docker 已安装: $(docker --version)"
    echo "✓ Docker Compose 已安装: $(docker-compose --version)"
    echo ""
}

check_env_file() {
    if [ ! -f ".env" ]; then
        echo "创建 .env 文件..."
        cat > .env << EOF
MYSQL_ROOT_PASSWORD=123456

GATEWAY_JAVA_OPTS=-Xms256m -Xmx512m
USER_JAVA_OPTS=-Xms256m -Xmx512m
PROBLEM_JAVA_OPTS=-Xms256m -Xmx512m
LEARNING_JAVA_OPTS=-Xms256m -Xmx512m
AI_JAVA_OPTS=-Xms512m -Xmx1024m
ADMIN_JAVA_OPTS=-Xms256m -Xmx512m
EOF
        echo "✓ .env 文件已创建"
    else
        echo "✓ .env 文件已存在"
    fi
    echo ""
}

build_images() {
    echo "构建 Docker 镜像..."
    echo "这可能需要几分钟时间..."
    docker-compose build
    echo "✓ 镜像构建完成"
    echo ""
}

start_services() {
    echo "启动服务..."
    docker-compose up -d
    echo "✓ 服务启动完成"
    echo ""
}

wait_for_services() {
    echo "等待服务就绪..."
    echo ""

    services=(
        "zhixue-mysql:MySQL"
        "zhixue-redis:Redis"
        "zhixue-nacos:Nacos"
        "zhixue-milvus:Milvus"
        "zhixue-ollama:Ollama"
        "zhixue-gateway:API网关"
        "zhixue-user:用户服务"
        "zhixue-problem:问题服务"
        "zhixue-learning:学习服务"
        "zhixue-ai:AI服务"
        "zhixue-admin:管理服务"
        "zhixue-frontend:前端应用"
    )

    for service_info in "${services[@]}"; do
        IFS=':' read -r service name <<< "$service_info"
        echo -n "等待 $name 启动... "

        max_attempts=30
        attempt=1
        while [ $attempt -le $max_attempts ]; do
            status=$(docker inspect --format='{{.State.Health.Status}}' "$service" 2>/dev/null || echo "not_found")

            if [ "$status" = "healthy" ]; then
                echo "✓"
                break
            elif [ "$status" = "unhealthy" ]; then
                echo "✗ (不健康)"
                echo "查看日志: docker-compose logs $service"
                break
            fi

            if [ $attempt -eq $max_attempts ]; then
                echo "✗ (超时)"
                echo "查看日志: docker-compose logs $service"
            fi

            sleep 5
            attempt=$((attempt + 1))
        done
    done
    echo ""
}

show_status() {
    echo "服务状态:"
    docker-compose ps
    echo ""
}

show_access_info() {
    echo "========================================="
    echo "部署完成！"
    echo "========================================="
    echo ""
    echo "访问地址:"
    echo "  前端应用:     http://localhost"
    echo "  API网关:      http://localhost:8080"
    echo "  Nacos控制台:  http://localhost:8848/nacos (nacos/nacos)"
    echo "  MinIO控制台:  http://localhost:9001 (minioadmin/minioadmin)"
    echo ""
    echo "数据库信息:"
    echo "  MySQL:        localhost:3306 (root/123456)"
    echo "  Redis:        localhost:6379"
    echo "  Milvus:       localhost:19530"
    echo "  Ollama:       localhost:11434"
    echo ""
    echo "常用命令:"
    echo "  查看日志:     docker-compose logs -f"
    echo "  停止服务:     docker-compose down"
    echo "  重启服务:     docker-compose restart"
    echo "  查看状态:     docker-compose ps"
    echo ""
}

main() {
    check_docker
    check_env_file

    read -p "是否构建镜像? (y/n) [y]: " build_choice
    build_choice=${build_choice:-y}

    if [ "$build_choice" = "y" ] || [ "$build_choice" = "Y" ]; then
        build_images
    fi

    start_services
    wait_for_services
    show_status
    show_access_info
}

main "$@"
