# 智学引擎 - 部署与运行指南

> 本文档帮助你把智学引擎跑起来。无论你是想快速体验还是进行二次开发，都能找到适合你的方式。

---

## 目录

- [选择你的部署方式](#选择你的部署方式)
- [方式一：Docker 一键部署（推荐）](#方式一docker-一键部署推荐)
- [方式二：本地开发环境](#方式二本地开发环境)
- [方式三：手动部署](#方式三手动部署)
- [AI 功能配置](#ai-功能配置)
- [生产环境部署](#生产环境部署)
- [常见问题与排错](#常见问题与排错)
- [实用命令速查](#实用命令速查)

---

## 选择你的部署方式

| 你的情况 | 推荐方式 | 耗时 |
|----------|----------|------|
| 想快速看看效果 | Docker 一键部署 | 5-10 分钟 |
| 要进行二次开发 | 本地开发环境 | 15-20 分钟 |
| 部署到服务器 | 手动部署 / 生产环境 | 30-60 分钟 |

---

## 方式一：Docker 一键部署（推荐）

> **最适合**：快速体验、不想折腾环境的同学

### 前置条件

你需要安装 Docker 和 Docker Compose：

```bash
# 检查是否已安装
docker --version        # 需要 20.10+
docker-compose --version  # 需要 2.0+

# 没有？安装它！
# Linux (Ubuntu/Debian)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# 重新登录终端生效
```

### 第一步：进入项目

```bash
cd docker
```

### 第二步：配置环境变量（可选）

默认配置可以直接用，如果你想改密码：

```bash
# 编辑 .env 文件
vim .env

# 改这一行（可选）：
MYSQL_ROOT_PASSWORD=你的密码
```

### 第三步：启动！

```bash
# 构建镜像（首次需要，之后不用重复执行）
docker-compose build

# 启动所有服务
docker-compose up -d
```

### 第四步：等待并访问

```bash
# 查看启动进度（等所有服务变成 healthy）
docker-compose ps

# 大约需要 1-2 分钟
# 看到所有服务都是 healthy 状态后，打开浏览器：
```

**访问地址**：http://localhost

### 第五步：注册并开始使用

1. 打开 http://localhost
2. 点击"注册"创建账号
3. 登录后即可使用所有功能

---

## 方式二：本地开发环境

> **最适合**：要改代码、做二次开发的同学

### 整体流程

```
启动基础设施 → 初始化数据库 → 启动后端 → 启动前端 → 开始开发
```

### 第一步：启动基础设施

只需要 MySQL、Redis、Nacos 三个服务：

```bash
cd docker

# 只启动基础服务
docker-compose up -d mysql redis nacos

# 等待启动完成（约30秒）
# 检查是否就绪：
docker-compose ps
# 看到 mysql、redis、nacos 都是 healthy 状态即可
```

### 第二步：初始化数据库

数据库首次启动会自动执行初始化脚本。如果没有自动执行，手动来：

```bash
# 创建所有数据库
docker exec -i zhixue-mysql mysql -uroot -pchangeme < docker/init-scripts/01-create-databases.sql

# 导入各服务的表结构
docker exec -i zhixue-mysql mysql -uroot -pchangeme zhixue_user < docker/init-scripts/tables/zhixue_user.sql
docker exec -i zhixue-mysql mysql -uroot -pchangeme zhixue_problem < docker/init-scripts/tables/zhixue_problem.sql
docker exec -i zhixue-mysql mysql -uroot -pchangeme zhixue_learning < docker/init-scripts/tables/zhixue_learning.sql
docker exec -i zhixue-mysql mysql -uroot -pchangeme zhixue_ai < docker/init-scripts/tables/zhixue_ai.sql
docker exec -i zhixue-mysql mysql -uroot -pchangeme zhixue_admin < docker/init-scripts/tables/zhixue_admin.sql
```

### 第三步：编译后端

```bash
# 回到项目根目录
cd ..

# 编译所有模块（首次需要下载依赖，可能较慢）
mvn clean package -DskipTests

# 看到 BUILD SUCCESS 就成功了！
```

**编译报错？** 确保你安装了：
- Java 17+：`java -version`
- Maven 3.8+：`mvn -version`

### 第四步：启动后端服务

> **启动顺序很重要！** 必须按顺序启动。

```bash
# 1️⃣ 先启动网关（其他服务依赖它）
java -jar zhixue-getaway/target/zhixue-getaway-0.0.1-SNAPSHOT.jar &

# 等待看到 "Started ZhixueGetawayApplication" 后再启动下一个

# 2️⃣ 启动用户服务
java -jar zhixue-user/target/zhixue-user-0.0.1-SNAPSHOT.jar &

# 3️⃣ 启动题目服务
java -jar zhixue-problem/target/zhixue-problem-0.0.1-SNAPSHOT.jar &

# 4️⃣ 启动学习服务
java -jar zhixue-learning/target/zhixue-learning-0.0.1-SNAPSHOT.jar &

# 5️⃣ 启动 AI 服务（启动较慢，约30秒）
java -jar zhixue-ai/target/zhixue-ai-0.0.1-SNAPSHOT.jar &

# 6️⃣ 启动管理服务
java -jar zhixue-admin/target/zhixue-admin-0.0.1-SNAPSHOT.jar &
```

**验证**：打开 http://localhost:8080/actuator/health 返回 JSON 说明网关正常。

### 第五步：启动前端

打开一个**新的终端窗口**：

```bash
cd frontend

# 安装依赖（首次需要）
npm install

# 启动开发服务器
npm run dev
```

看到 `Ready in xxxms` 后，打开 http://localhost:3000

---

## 方式三：手动部署

> **最适合**：没有 Docker 环境，或者想深入了解部署过程的同学

### 3.1 安装依赖软件

| 软件 | 版本要求 | 安装方式 |
|------|----------|----------|
| Java JDK | 17+ | [Adoptium](https://adoptium.net/) 或系统包管理器 |
| Maven | 3.8+ | `sudo apt install maven` 或手动安装 |
| MySQL | 8.0+ | [官方下载](https://dev.mysql.com/downloads/mysql/) |
| Redis | 7+ | `sudo apt install redis-server` |
| Nacos | 2.x | [官方下载](https://github.com/alibaba/nacos/releases) |
| Node.js | 18+ | [Node.js 官网](https://nodejs.org/) |

### 3.2 手动启动 Nacos

```bash
# 下载并解压 Nacos
# https://github.com/alibaba/nacos/releases

# 单机模式启动
sh nacos/bin/startup.sh -m standalone

# 访问控制台：http://localhost:8848/nacos
```

### 3.3 手动启动 MySQL

```bash
# 创建数据库和表（参考 docker/init-scripts/ 中的 SQL 文件）
mysql -u root -p < docker/init-scripts/01-create-databases.sql
```

---

## AI 功能配置

AI 功能需要额外配置 Ollama（本地 AI 模型）和 Milvus（向量数据库）。

### 配置 Ollama

```bash
# 1. 安装 Ollama
curl -fsSL https://ollama.com/install.sh | sh

# 2. 启动 Ollama 服务
ollama serve &

# 3. 下载对话模型（约 2GB）
ollama pull qwen2.5-coder:3b-instruct-q5_K_M

# 4. 下载嵌入模型（RAG 用，约 300MB）
ollama pull nomic-embed-text:latest

# 5. 验证
curl http://localhost:11434/api/tags
# 应该看到刚下载的两个模型
```

### 配置 Milvus

```bash
# 使用 Docker 启动（最简单）
cd docker
docker-compose up -d etcd minio milvus

# 等待启动完成
curl http://localhost:9091/healthz
```

### 不用 AI 功能？

如果只是做用户管理、题目管理等基础功能的开发，不需要配置 Ollama 和 Milvus。

---

## 生产环境部署

### 安全检查清单

- [ ] 修改 MySQL 密码
- [ ] 修改 JWT 密钥（必须！）
- [ ] 修改 Nacos 密码
- [ ] 关闭不需要的端口
- [ ] 配置 HTTPS

### 修改密码

```bash
# 1. 修改 MySQL 密码
docker exec -it zhixue-mysql mysql -uroot -pchangeme -e "ALTER USER 'root'@'%' IDENTIFIED BY '你的新密码';"

# 2. 修改 docker/.env 中的密码
MYSQL_ROOT_PASSWORD=你的新密码

# 3. 修改各服务 application.yaml 中的数据库密码
#    或通过环境变量传入：MYSQL_PASSWORD=你的新密码
```

### Nginx 反向代理

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端
    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # API
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
    }

    # AI 流式响应（重要！必须关闭缓冲）
    location /api/v1/ai/ {
        proxy_pass http://localhost:8080;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }
}
```

---

## 常见问题与排错

### 问题 1：端口被占用

```
Error: Address already in use
```

**解决**：
```bash
# 找到占用端口的进程
netstat -tlnp | grep :8080

# 杀掉它
kill -9 <PID>

# 或者改 docker-compose.yml 中的端口映射
ports:
  - "8081:80"  # 改成别的端口
```

### 问题 2：MySQL 连接失败

```
Communications link failure
```

**解决**：
```bash
# 1. 检查 MySQL 是否在运行
docker-compose ps mysql

# 2. 查看 MySQL 日志
docker-compose logs mysql

# 3. 手动测试连接
docker exec -it zhixue-mysql mysql -uroot -pchangeme -e "SELECT 1"

# 4. 如果密码不对，检查 .env 文件
cat .env
```

### 问题 3：Nacos 连接失败

**解决**：
```bash
# 1. 检查 Nacos 是否健康
curl http://localhost:8848/nacos/v1/console/health/readiness

# 2. 查看 Nacos 日志
docker-compose logs nacos

# 3. 确保 application.yaml 中的 server-addr 正确
```

### 问题 4：前端页面空白

**解决**：
```bash
# 检查后端是否正常
curl http://localhost:8080/actuator/health

# 如果后端正常，检查前端 API 地址配置
# frontend/next.config.ts 中的 NEXT_PUBLIC_API_URL
```

### 问题 5：AI 对话无响应

**解决**：
```bash
# 1. 检查 Ollama 是否运行
curl http://localhost:11434/api/tags

# 2. 检查模型是否下载
ollama list

# 3. 手动测试模型
curl http://localhost:11434/api/generate -d '{"model":"qwen2.5-coder:3b-instruct-q5_K_M","prompt":"hello"}'
```

### 问题 6：内存不足

```bash
# 检查内存
free -h

# 降低 JVM 内存（编辑 .env）
GATEWAY_JAVA_OPTS=-Xms128m -Xmx256m
USER_JAVA_OPTS=-Xms128m -Xmx256m
AI_JAVA_OPTS=-Xms256m -Xmx512m
```

### 问题 7：编译报错

```bash
# 确保 Java 版本正确
java -version  # 需要 17+

# 确保 Maven 版本正确
mvn -version  # 需要 3.8+

# 清理重新编译
mvn clean package -DskipTests
```

---

## 实用命令速查

### Docker 常用命令

```bash
# 查看所有服务状态
docker-compose ps

# 启动/停止/重启
docker-compose up -d          # 启动
docker-compose down           # 停止
docker-compose restart        # 重启
docker-compose restart zhixue-ai  # 重启单个服务

# 查看日志
docker-compose logs -f                  # 所有服务
docker-compose logs -f zhixue-ai        # 单个服务
docker-compose logs --tail=50 zhixue-ai # 最近50行

# 进入容器
docker exec -it zhixue-mysql bash
docker exec -it zhixue-redis redis-cli
```

### 后端常用命令

```bash
# 编译
mvn clean package -DskipTests

# 运行单个服务
java -jar zhixue-user/target/zhixue-user-0.0.1-SNAPSHOT.jar

# 带环境变量运行
MYSQL_PASSWORD=xxx java -jar zhixue-user/target/zhixue-user-0.0.1-SNAPSHOT.jar
```

### 前端常用命令

```bash
# 安装依赖
npm install

# 开发模式
npm run dev

# 构建生产版本
npm run build

# 代码检查
npm run lint
```

### 数据库常用命令

```bash
# 连接 MySQL
docker exec -it zhixue-mysql mysql -uroot -pchangeme

# 查看数据库
SHOW DATABASES;

# 查看表
USE zhixue_user;
SHOW TABLES;

# 备份
docker exec zhixue-mysql mysqldump -uroot -pchangeme --all-databases > backup.sql
```

---

## 需要帮助？

1. 先看 [README.md](README.md) 了解项目概况
2. 查看 [ARCHITECTURE.md](ARCHITECTURE.md) 了解系统架构
3. 遇到问题？检查上面的常见问题
4. 还是不行？查看各服务的日志文件
