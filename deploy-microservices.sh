#!/bin/bash

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

SSH_USER="${SSH_USER:-root}"
SSH_PASS="${SSH_PASS:-changeme}"
SSH_OPTS="-o StrictHostKeyChecking=no"

# 请根据实际服务器IP修改以下配置
MAIN_SERVER="${MAIN_SERVER:-127.0.0.1}"
FRONTEND_SERVER="${FRONTEND_SERVER:-127.0.0.1}"
PROBLEM_SERVER_1="${PROBLEM_SERVER_1:-127.0.0.1}"
PROBLEM_SERVER_2="${PROBLEM_SERVER_2:-127.0.0.1}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

ssh_exec() {
    local ip="$1"
    shift
    sshpass -p "$SSH_PASS" ssh $SSH_OPTS ${SSH_USER}@${ip} "$@"
}

scp_file() {
    local ip="$1"
    local src="$2"
    local dst="$3"
    sshpass -p "$SSH_PASS" scp $SSH_OPTS "$src" "${SSH_USER}@${ip}:${dst}"
}

scp_dir() {
    local ip="$1"
    local src="$2"
    local dst="$3"
    sshpass -p "$SSH_PASS" scp -r $SSH_OPTS "$src" "${SSH_USER}@${ip}:${dst}"
}

deploy_backend_service() {
    local ip="$1"
    local service_name="$2"
    local jar_path="$3"
    local jvm_opts="$4"
    local env_vars="${5:-}"

    log_info "部署 ${service_name} 到 ${ip}..."

    ssh_exec "$ip" "mkdir -p /opt/zhixue/${service_name}"

    log_info "  传输 JAR 包..."
    scp_file "$ip" "$jar_path" "/opt/zhixue/${service_name}/app.jar"

    local env_line=""
    if [ -n "$env_vars" ]; then
        env_line="Environment=${env_vars}"
    fi

    log_info "  创建 systemd 服务单元..."
    local service_unit="[Unit]
Description=Zhixue ${service_name} Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/zhixue/${service_name}
ExecStart=/usr/bin/java ${jvm_opts} -jar /opt/zhixue/${service_name}/app.jar
Restart=always
RestartSec=5
${env_line}

[Install]
WantedBy=multi-user.target"

    ssh_exec "$ip" "cat > /etc/systemd/system/${service_name}.service << 'SERVICEEOF'
${service_unit}
SERVICEEOF"

    log_info "  停止旧服务(如存在)..."
    ssh_exec "$ip" "systemctl stop ${service_name} 2>/dev/null || true"

    log_info "  启用并启动服务..."
    ssh_exec "$ip" "systemctl daemon-reload && systemctl enable ${service_name} && systemctl start ${service_name}"

    log_info "${service_name} 部署完成 ✓"
}

deploy_frontend() {
    local ip="$1"

    log_info "部署前端到 ${ip}..."

    log_info "  准备前端部署目录..."
    ssh_exec "$ip" "rm -rf /opt/zhixue/frontend && mkdir -p /opt/zhixue/frontend"

    log_info "  打包前端 standalone 构建..."
    cd "${PROJECT_DIR}/frontend"
    tar czf /tmp/zhixue-frontend.tar.gz -C .next/standalone . 2>/dev/null

    log_info "  传输前端 standalone 包..."
    scp_file "$ip" "/tmp/zhixue-frontend.tar.gz" "/opt/zhixue/frontend/frontend.tar.gz"

    log_info "  解压前端包..."
    ssh_exec "$ip" "cd /opt/zhixue/frontend && tar xzf frontend.tar.gz && rm frontend.tar.gz"

    log_info "  复制 static 文件到 standalone 目录..."
    cd "${PROJECT_DIR}/frontend"
    tar czf /tmp/zhixue-frontend-static.tar.gz -C .next/static . 2>/dev/null
    scp_file "$ip" "/tmp/zhixue-frontend-static.tar.gz" "/tmp/zhixue-frontend-static.tar.gz"
    ssh_exec "$ip" "mkdir -p /opt/zhixue/frontend/.next/static && cd /opt/zhixue/frontend/.next/static && tar xzf /tmp/zhixue-frontend-static.tar.gz && rm /tmp/zhixue-frontend-static.tar.gz"

    log_info "  复制 public 文件..."
    if [ -d "${PROJECT_DIR}/frontend/public" ]; then
        scp_dir "$ip" "${PROJECT_DIR}/frontend/public" "/opt/zhixue/frontend/public"
    fi

    log_info "  创建前端 systemd 服务..."
    local frontend_service="[Unit]
Description=Zhixue Frontend Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/zhixue/frontend
ExecStart=/usr/bin/node /opt/zhixue/frontend/server.js
Restart=always
RestartSec=5
Environment=HOSTNAME=0.0.0.0
Environment=PORT=3000

[Install]
WantedBy=multi-user.target"

    ssh_exec "$ip" "cat > /etc/systemd/system/zhixue-frontend.service << 'SERVICEEOF'
${frontend_service}
SERVICEEOF"

    log_info "  配置 Nginx..."
    scp_file "$ip" "${PROJECT_DIR}/docker/frontend/nginx-prod.conf" "/etc/nginx/sites-available/zhixue-frontend"
    ssh_exec "$ip" "rm -f /etc/nginx/sites-enabled/default 2>/dev/null || true"
    ssh_exec "$ip" "ln -sf /etc/nginx/sites-available/zhixue-frontend /etc/nginx/sites-enabled/zhixue-frontend 2>/dev/null || true"

    ssh_exec "$ip" "if [ ! -f /etc/nginx/sites-enabled/zhixue-frontend ]; then \
        grep -q 'include /etc/nginx/conf.d/nginx-prod.conf' /etc/nginx/nginx.conf || \
        echo 'include /etc/nginx/conf.d/nginx-prod.conf;' >> /etc/nginx/nginx.conf; \
    fi"

    log_info "  启动前端服务..."
    ssh_exec "$ip" "systemctl stop zhixue-frontend 2>/dev/null || true"
    ssh_exec "$ip" "systemctl daemon-reload && systemctl enable zhixue-frontend && systemctl start zhixue-frontend"

    log_info "  测试并重启 Nginx..."
    ssh_exec "$ip" "nginx -t && systemctl enable nginx && systemctl restart nginx"

    log_info "前端部署完成 ✓"
}

health_check() {
    local ip="$1"
    local port="$2"
    local name="$3"
    local url="${4:-}"

    if [ -n "$url" ]; then
        if curl -sf --connect-timeout 5 --max-time 10 "$url" > /dev/null 2>&1; then
            log_info "健康检查通过: ${name} (${url})"
        else
            log_warn "健康检查失败: ${name} (${url})"
        fi
    else
        if curl -sf --connect-timeout 5 --max-time 10 "http://${ip}:${port}/actuator/health" > /dev/null 2>&1; then
            log_info "健康检查通过: ${name} (http://${ip}:${port}/actuator/health)"
        else
            log_warn "健康检查失败: ${name} (http://${ip}:${port}/actuator/health)"
        fi
    fi
}

echo "=========================================="
echo "   智学微服务自动化部署脚本"
echo "=========================================="
echo ""

log_info "开始部署微服务..."
echo ""

log_info "===== 部署主服务器 (${MAIN_SERVER}) 服务 ====="
deploy_backend_service "$MAIN_SERVER" \
    "zhixue-gateway" \
    "${PROJECT_DIR}/zhixue-getaway/target/zhixue-getaway-0.0.1-SNAPSHOT.jar" \
    "-Xms256m -Xmx512m"
echo ""

deploy_backend_service "$MAIN_SERVER" \
    "zhixue-user" \
    "${PROJECT_DIR}/zhixue-user/target/zhixue-user-0.0.1-SNAPSHOT.jar" \
    "-Xms256m -Xmx512m"
echo ""

deploy_backend_service "$MAIN_SERVER" \
    "zhixue-ai" \
    "${PROJECT_DIR}/zhixue-ai/target/zhixue-ai-0.0.1-SNAPSHOT.jar" \
    "-Xms512m -Xmx1024m"
echo ""

deploy_backend_service "$MAIN_SERVER" \
    "zhixue-admin" \
    "${PROJECT_DIR}/zhixue-admin/target/zhixue-admin-0.0.1-SNAPSHOT.jar" \
    "-Xms256m -Xmx512m"
echo ""

log_info "===== 部署前端服务器 (${FRONTEND_SERVER}) 服务 ====="
deploy_backend_service "$FRONTEND_SERVER" \
    "zhixue-learning" \
    "${PROJECT_DIR}/zhixue-learning/target/zhixue-learning-0.0.1-SNAPSHOT.jar" \
    "-Xms256m -Xmx512m"
echo ""

deploy_frontend "$FRONTEND_SERVER"
echo ""

log_info "===== 部署题目服务器1 (${PROBLEM_SERVER_1}) 服务 ====="
deploy_backend_service "$PROBLEM_SERVER_1" \
    "zhixue-problem" \
    "${PROJECT_DIR}/zhixue-problem/target/zhixue-problem-0.0.1-SNAPSHOT.jar" \
    "-Xms256m -Xmx512m" \
    "NACOS_IP=${PROBLEM_SERVER_1:-127.0.0.1}"
echo ""

log_info "===== 部署题目服务器2 (${PROBLEM_SERVER_2}) 服务 ====="
deploy_backend_service "$PROBLEM_SERVER_2" \
    "zhixue-problem" \
    "${PROJECT_DIR}/zhixue-problem/target/zhixue-problem-0.0.1-SNAPSHOT.jar" \
    "-Xms256m -Xmx512m" \
    "NACOS_IP=${PROBLEM_SERVER_2:-127.0.0.1}"
echo ""

echo "=========================================="
echo "   健康检查"
echo "=========================================="
echo ""

log_info "等待服务启动 (20秒)..."
sleep 20

health_check "$MAIN_SERVER" "8080" "zhixue-gateway"
health_check "$MAIN_SERVER" "8081" "zhixue-user"
health_check "$MAIN_SERVER" "8086" "zhixue-ai"
health_check "$MAIN_SERVER" "8090" "zhixue-admin"
health_check "$FRONTEND_SERVER" "8083" "zhixue-learning"
health_check "$PROBLEM_SERVER_1" "8082" "zhixue-problem-1"
health_check "$PROBLEM_SERVER_2" "8082" "zhixue-problem-2"
health_check "$FRONTEND_SERVER" "80" "frontend" "http://${FRONTEND_SERVER}:80"

echo ""
echo "=========================================="
echo "   部署完成！"
echo "=========================================="
echo ""
echo "访问地址:"
echo "  前端应用:     http://${FRONTEND_SERVER}"
echo "  API网关:      http://${MAIN_SERVER}:8080"
echo "  Nacos控制台:  http://${MAIN_SERVER}:8848/nacos (nacos/nacos)"
echo ""
echo "服务分布:"
echo "  主服务器(${MAIN_SERVER}): gateway, user, ai, admin"
echo "  前端服务器(${FRONTEND_SERVER}): learning, frontend"
echo "  题目服务器1(${PROBLEM_SERVER_1}): problem"
echo "  题目服务器2(${PROBLEM_SERVER_2}): problem"
