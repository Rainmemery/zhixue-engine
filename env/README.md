# 📁 环境配置目录说明

## 🎯 目录用途

本目录用于存放智学平台的环境变量配置文件，实现开发/测试/生产环境的配置隔离。

## 📂 文件结构

```
env/
├── .env.example      # 环境变量模板（包含详细注释）
├── .env              # 实际环境变量（从 .env.example 复制并修改，⚠️ 不提交到 Git）
├── .gitignore        # Git 忽略规则（保护敏感信息）
└── README.md         # 本说明文档
```

## 🚀 快速开始

### 1️⃣ 复制配置模板

```bash
# Linux/macOS
cp .env.example .env

# Windows (PowerShell)
Copy-Item .env.example .env

# Windows (CMD)
copy .env.example .env
```

### 2️⃣ 编辑环境变量

根据你的部署环境修改 `.env` 文件：

```bash
# 使用文本编辑器打开
nano .env          # Linux
code .env          # VS Code
notepad .env       # Windows
```

### 3️⃣ 最小化配置（本地开发）

只需修改这两个关键配置：

```env
MYSQL_PASSWORD=123456                    # 开发环境可使用简单密码
JWT_SECRET=dev-secret-for-testing-only   # 开发环境专用密钥
```

### 4️⃣ 加载环境变量

#### 方式一：手动加载（推荐用于开发）

```bash
# 加载当前 shell 会话
source .env

# 或使用 export（Linux/macOS）
export $(cat .env | xargs)
```

#### 方式二：Docker Compose 自动加载

Docker Compose 会自动读取 `.env` 文件：

```bash
docker-compose up -d
```

#### 方式三：启动脚本中加载

创建 `start.sh`（Linux）或 `start.bat`（Windows）：

```bash
#!/bin/bash
set -a
source .env
set +b
java -jar your-app.jar
```

## 🔐 安全注意事项

### ⚠️ 绝对不能做的事

- ❌ **不要**将 `.env` 文件提交到 Git/GitHub
- ❌ **不要**在生产环境使用默认密码
- ❌ **不要**在日志、代码中打印环境变量
- ❌ **不要**使用 `.env.example` 中的示例值作为生产配置

### ✅ 必须遵守的安全实践

1. **强密码策略**
   ```bash
   # MySQL 密码（≥16字符，混合字符类型）
   openssl rand -base64 24
   
   # JWT Secret（≥32字符，高熵随机）
   openssl rand -base64 32
   ```

2. **权限控制**
   ```bash
   # 限制 .env 文件权限（仅当前用户可读写）
   chmod 600 .env
   ```

3. **定期轮换**
   - JWT Secret：每 90 天更换一次
   - 数据库密码：每 180 天更换一次
   - 更换后需重启所有微服务

4. **备份与恢复**
   ```bash
   # 备份（加密存储）
   tar -czf env-backup-$(date +%Y%m%d).tar.gz .env
   gpg -c env-backup-$(date +%Y%m%d).tar.gz
   
   # 恢复
   gpg env-backup-YYYYMMDD.tar.gz.gpg
   tar -xzf env-backup-YYYYMMDD.tar.gz
   ```

## 🌍 多环境管理

### 推荐的目录结构

```
project-root/
├── env/
│   ├── .env.example           # 模板
│   ├── .env.development       # 开发环境
│   ├── .env.staging           # 预发布环境
│   └── .env.production        # 生产环境（⚠️ 高度敏感）
└── ...
```

### 环境切换脚本

```bash
#!/bin/bash
# switch-env.sh - 环境切换工具

ENV_NAME=${1:-development}

if [ ! -f ".env.$ENV_NAME" ]; then
    echo "❌ 环境 $ENV_NAME 不存在"
    echo "可用环境："
    ls -1 .env.* | sed 's/.env.//'
    exit 1
fi

cp .env.$ENV_NAME .env
echo "✅ 已切换到 $ENV_NAME 环境"
echo "📝 当前配置："
cat .env | grep -E "^(MYSQL_PASSWORD|JWT_SECRET)=" | sed 's/=.*/=***/'
```

使用方式：
```bash
./switch-env.sh production
```

## 🛠️ 常见场景配置

### 场景 1：本地开发（最简配置）

```env
# 最小化开发配置
MYSQL_PASSWORD=123456
JWT_SECRET=dev-secret-key-local
DEV_MODE=true
LOGGING_LEVEL_ROOT=DEBUG
```

### 场景 2：团队共享开发环境

```env
# 团队开发环境（数据库共享，其他独立）
MYSQL_PASSWORD=team-dev-db-pass-2024
JWT_SECRET=team-jwt-secret-dev-2024
NACOS_IP=
RAG_UPLOAD_PATH=./uploads/rag-documents
DEV_MODE=true
LOGGING_LEVEL_ROOT=DEBUG
```

### 场景 3：Docker Compose 本地开发

```env
# Docker 开发环境（服务间通过容器名通信）
MYSQL_PASSWORD=docker-dev-mysql-2024
JWT_SECRET=docker-jwt-secret-dev-2024
MYSQL_HOST=mysql-container      # 覆盖 localhost
REDIS_HOST=redis-container     # 覆盖 localhost
NACOS_HOST=nacos-container     # 覆盖 localhost
```

对应的 `docker-compose.yml`：
```yaml
services:
  app:
    environment:
      - MYSQL_HOST=mysql-container
      - REDIS_HOST=redis-container
    env_file:
      - .env
  
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_PASSWORD}
```

### 场景 4：生产环境（完整安全配置）

```env
# ===== 生产环境配置（已脱敏）=====
MYSQL_PASSWORD=<openssl rand -base64 24 的输出>
JWT_SECRET=<openssl rand -base64 32 的输出>
NACOS_IP=10.0.0.100
RAG_UPLOAD_PATH=/data/zhixue/rag-uploads
REDIS_PASSWORD=<Redis 强密码>
SSL_KEYSTORE_PASSWORD=<SSL 证书密码>

# 性能调优
JAVA_OPTS=-Xms4096m -Xmx8192m -XX:+UseG1GC

# 日志配置
LOGGING_LEVEL_ROOT=WARN
LOG_PATH=/var/log/zhixue
```

## 🔧 故障排查

### 问题 1：环境变量未生效

**症状**：应用启动后仍使用默认值

**排查步骤**：
```bash
# 1. 检查文件是否存在
ls -la .env

# 2. 检查文件格式（不能有 BOM，行尾要正确）
file .env
hexdump -C .env | head

# 3. 手动验证加载
export $(cat .env | xargs)
echo $MYSQL_PASSWORD

# 4. 检查是否有语法错误（多余的空格、引号等）
cat -A .env | grep "^" | head -20
```

**常见原因**：
- 文件名错误（`.env.txt` 或 `.env ` 尾部有空格）
- 行尾格式问题（Windows CRLF vs Unix LF）
- 变量值中有未转义的特殊字符
- Shell 未重新加载

### 问题 2：特殊字符处理

如果密码包含以下字符，需要适当处理：
- `$` - 用单引号包裹或转义 `\$`
- `"` - 用单引号包裹
- `空格` - 用引号包裹
- `#` - 这是注释符，确保不在行首

示例：
```env
# 错误写法
MYSQL_PASSWORD=P@ss$word

# 正确写法
MYSQL_PASSWORD='P@ss$word'
MYSQL_PASSWORD="P@ss\$word"
```

### 问题 3：多服务配置冲突

当多个微服务需要不同配置时：

**方案 A**：按模块拆分
```bash
# env/.env.user
MYSQL_PASSWORD=user-db-password

# env/.env.admin  
MYSQL_PASSWORD=admin-db-password
```

**方案 B**：统一前缀
```env
USER_MYSQL_PASSWORD=pass1
ADMIN_MYSQL_PASSWORD=pass2
PROBLEM_MYSQL_PASSWORD=pass3
```

然后在各模块的启动脚本中选择性加载。

## 📊 配置验证

### 创建验证脚本 `validate-env.sh`

```bash
#!/bin/bash
# 验证 .env 文件配置是否完整且安全

source .env

ERRORS=0

echo "🔍 开始环境配置检查..."
echo ""

# 检查必需变量
check_var() {
    if [ -z "${!1}" ]; then
        echo "❌ 缺少必需变量: $1"
        ((ERRORS++))
    else
        echo "✅ $1: 已配置"
    fi
}

# 安全性检查
check_security() {
    local var=$1
    local value=${!var}
    local min_len=$2
    
    if [ ${#value} -lt $min_len ]; then
        echo "⚠️  $var: 密码长度不足（当前 ${#value} 字符，要求 ≥$min_len）"
        ((ERRORS++))
    elif [[ "$value" == *"123456"* ]] || [[ "$value" == *"password"* ]]; then
        echo "🔴 $val: 使用了弱密码！"
        ((ERRORS++))
    else
        echo "✅ $var: 安全性检查通过"
    fi
}

# 执行检查
echo "=== 必需变量检查 ==="
check_var "MYSQL_PASSWORD"
check_var "JWT_SECRET"

echo ""
echo "=== 安全强度检查 ==="
check_security "MYSQL_PASSWORD" 16
check_security "JWT_SECRET" 32

echo ""
if [ $ERRORS -eq 0 ]; then
    echo "🎉 所有检查通过！配置可以安全使用。"
    exit 0
else
    echo "💥 发现 $ERRORS 个问题，请修复后再部署！"
    exit 1
fi
```

运行验证：
```bash
chmod +x validate-env.sh
./validate-env.sh
```

## 📚 相关资源

- **详细配置文档**: [ENVGuide.md](../ENVGuide.md)
- **项目架构**: [ARCHITECTURE.md](../ARCHITECTURE.md)
- **Spring Boot 外部配置**: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config
- **dotenv 规范**: https://github.com/motdotla/dotenv
- **密码生成器**: https://generate-passwords.appspot.com/

## 🤝 贡献指南

如需添加新的环境变量：

1. 在 `.env.example` 中添加变量和注释
2. 更新此 README 的相关章节
3. 在 [ENVGuide.md](../ENVGuide.md) 中同步更新文档
4. 提交 PR 时确保不包含真实的 `.env` 文件

---

**最后更新**: 2026-08-19  
**维护者**: DevOps 团队  
**版本**: v1.0