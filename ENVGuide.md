# 🌿 智学平台 - 环境变量配置指南

> 本文档汇总了智学平台所有模块支持的环境变量配置，用于生产环境部署和定制化配置。

---

## 📋 目录

- [概述](#概述)
- [通用环境变量](#通用环境变量)
- [各模块详细配置](#各模块详细配置)
  - [zhixue-admin（管理后台）](#zhixue-admin管理后台)
  - [zhixue-user（用户服务）](#zhixue-user用户服务)
  - [zhixue-problem（题目服务）](#zhixue-problem题目服务)
  - [zhixue-learning（学习服务）](#zhixue-learning学习服务)
  - [zhixue-ai（AI 服务）](#zhixue-ai-ai-服务)
  - [zhixue-gateway（网关服务）](#zhixue-gateway网关服务)
  - [zhixue-common（公共模块）](#zhixue-common公共模块)
- [快速部署示例](#快速部署示例)
- [安全建议](#安全建议)

---

## 概述

智学平台采用 **Spring Cloud 微服务架构**，所有敏感配置均通过环境变量外置，支持灵活的部署方式。

### 配置优先级

```
环境变量 > application-{profile}.yaml > application.yaml 默认值
```

### 环境变量命名规范

- 使用大写字母和下划线（UPPER_SNAKE_CASE）
- 采用 `模块_功能_参数` 的命名模式
- 敏感信息必须通过环境变量配置（禁止硬编码）

---

## 通用环境变量

以下环境变量在多个模块中使用，属于全局配置：

| 变量名 | 说明 | 默认值 | 适用模块 | 必需性 |
|--------|------|--------|----------|--------|
| `MYSQL_PASSWORD` | MySQL 数据库密码 | `123456` | admin, user, problem, learning, ai | ⚠️ **生产必改** |
| `JWT_SECRET` | JWT 签名密钥 | `your-jwt-secret-change-in-production` | 所有业务模块 | 🔴 **生产必改** |
| `NACOS_IP` | Nacos 注册 IP | `127.0.0.1` | problem | 可选 |

---

## 各模块详细配置

### zhixue-admin（管理后台）

📁 **配置文件**: `zhixue-admin/src/main/resources/application.yaml`  
🔧 **服务端口**: `8090`

#### 数据库配置（多数据源）

| 变量名 | 说明 | 默认值 | 连接数据库 |
|--------|------|--------|------------|
| `MYSQL_PASSWORD` | 所有数据源统一密码 | `123456` | zhixue_admin, zhixue_user, zhixue_problem, zhixue_learning |

#### 数据库连接详情

```yaml
# 管理后台数据库
admin:
  jdbc-url: jdbc:mysql://localhost:3306/zhixue_admin
  
# 用户数据库
user:
  jdbc-url: jdbc:mysql://localhost:3306/zhixue_user
  
# 题目数据库  
problem:
  jdbc-url: jdbc:mysql://localhost:3306/zhixue_problem
  
# 学习数据库
learning:
  jdbc-url: jdbc:mysql://localhost:3306/zhixue_learning
```

#### 其他配置

- **Nacos**: `localhost:8848`
- **日志文件**: `logs/zhixue-admin.log`
- **文件上传限制**: 单文件 10MB，单次请求 50MB

---

### zhixue-user（用户服务）

📁 **配置文件**: `zhixue-user/src/main/resources/application.yaml`  
🔧 **服务端口**: `8081`

#### 环境变量列表

| 变量名 | 说明 | 默认值 | 配置位置 |
|--------|------|--------|----------|
| `MYSQL_PASSWORD` | MySQL 密码 | `123456` | `spring.datasource.password` |
| `JWT_SECRET` | JWT 密钥 | `your-jwt-secret-change-in-production` | `jwt.secret` |

#### 数据库连接

```yaml
datasource:
  url: jdbc:mysql://localhost:3306/zhixue_user
  username: root
  password: ${MYSQL_PASSWORD:123456}
```

#### 连接池配置（HikariCP）

- 最大连接数: `50`
- 最小空闲连接: `10`
- 连接超时: `30000ms`
- 空闲超时: `600000ms`
- 连接最大存活时间: `1800000ms`

---

### zhixue-problem（题目服务）

📁 **配置文件**: `zhixue-problem/src/main/resources/application.yaml`  
🔧 **服务端口**: `8082`

#### 环境变量列表

| 变量名 | 说明 | 默认值 | 配置位置 | 用途 |
|--------|------|--------|----------|------|
| `MYSQL_PASSWORD` | MySQL 密码 | `123456` | `spring.datasource.password` | 数据库认证 |
| `NACOS_IP` | Nacos 注册 IP | `127.0.0.1` | `spring.cloud.nacos.discovery.ip` | 服务注册地址 |
| `JWT_SECRET` | JWT 密钥 | `your-jwt-secret-change-in-production` | `jwt.secret` | Token 签名 |

#### 特色配置

##### Redis 缓存

```yaml
data:
  redis:
    host: localhost
    port: 6379
    database: 0
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

##### Docker 判题系统

```yaml
judge:
  docker:
    enabled: true
    host: unix:///var/run/docker.sock
  machine:
    cpu-cores: 4
    memory-mb: 4096
    container-memory-mb: 512
    container-cpus: 1
    max-concurrency: 4
```

##### 容器池配置

支持多语言判题容器池：
- **Java**: `judge-java:latest`（核心 2，最大 4）
- **C++**: `judge-cpp:latest`（核心 2，最大 4）
- **C**: `judge-cpp:latest`（核心 1，最大 2）

##### 测试用例存储

```yaml
testcase:
  storage-path: /data/testcases
```

---

### zhixue-learning（学习服务）

📁 **配置文件**: `zhixue-learning/src/main/resources/application.yaml`  
🔧 **服务端口**: `8083`

#### 环境变量列表

| 变量名 | 说明 | 默认值 | 配置位置 |
|--------|------|--------|----------|
| `MYSQL_PASSWORD` | MySQL 密码 | `123456` | `spring.datasource.password` |
| `JWT_SECRET` | JWT 密钥 | `your-jwt-secret-change-in-production` | `jwt.secret` |

#### 数据库连接

```yaml
datasource:
  url: jdbc:mysql://localhost:3306/zhixue_learning
  username: root
  password: ${MYSQL_PASSWORD:123456}
```

#### 连接池配置（HikariCP）

- 最大连接数: `20`
- 最小空闲连接: `5`
- 连接超时: `30000ms`

---

### zhixue-ai（AI 服务）

📁 **配置文件**: `zhixue-ai/src/main/resources/application.yaml`  
🔧 **服务端口**: `8086`

#### 环境变量列表

| 变量名 | 说明 | 默认值 | 配置位置 | 重要程度 |
|--------|------|--------|----------|----------|
| `MYSQL_PASSWORD` | MySQL 密码 | `123456` | `spring.datasource.password` | ⚠️ 高 |
| `JWT_SECRET` | JWT 密钥 | `your-jwt-secret-change-in-production` | `jwt.secret` | 🔴 **极高** |
| `RAG_UPLOAD_PATH` | RAG 文档上传路径 | `./uploads/rag-documents` | `rag.file.upload-path` | 中 |

#### AI 核心配置

##### Ollama 本地模型

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: qwen2.5-coder:3b-instruct-q5_K_M
          temperature: 0.7
      base-url: http://localhost:11434
```

##### Milvus 向量数据库

```yaml
milvus:
  host: localhost
  port: 19530
  collection-prefix: rag_vectors
  connect-timeout: 10
  secure: false
  max-retry-attempts: 3
```

##### RAG 功能配置

```yaml
rag:
  file:
    upload-path: ${RAG_UPLOAD_PATH:./uploads/rag-documents}
    max-size: 524288000  # 500MB
    allowed-types: PDF,DOCX,TXT,MD,DOC,HTML,RTF
  embedding:
    default-model: nomic-embed-text:latest
    ollama-base-url: http://localhost:11434
    timeout: 120000
    dimension: 768
  retrieval:
    default-top-k: 8
    default-threshold: 0.5
    hybrid-alpha: 0.7
  chunk:
    size: 1200
    overlap: 100
```

#### 文件上传配置（AI 特有）

```yaml
servlet:
  multipart:
    enabled: true
    max-file-size: 500MB      # 支持大文档上传
    max-request-size: 600MB
    file-size-threshold: 10MB
    location: ${java.io.tmpdir}
```

#### Redis 缓存

```yaml
data:
  redis:
    host: localhost
    port: 6379
    database: 0
    lettuce:
      pool:
        max-active: 8
```

---

### zhixue-gateway（网关服务）

📁 **配置文件**: `zhixue-getaway/src/main/resources/application.yaml`  
🔧 **服务端口**: `8080`

#### ⚠️ 注意：无独立环境变量

网关服务当前未定义独立的环境变量，但依赖以下基础设施：

| 基础设施 | 地址 | 用途 |
|----------|------|------|
| **Redis** | `localhost:6379` | 限流、会话管理 |
| **Nacos** | `localhost:8848` | 服务发现、路由动态刷新 |

#### 路由配置概览

网关配置了 **14 条路由规则**，覆盖所有微服务：

| 路由 ID | 目标服务 | 路径前缀 | 限流配置 |
|---------|----------|----------|----------|
| `ai-admin-service-route` | zhixue-ai | `/api/v1/admin/ai-model/**` | 10 req/s |
| `intelligent-scheduling-route` | zhixue-ai | `/api/v1/scheduling/**` | 10 req/s |
| `dispatcher-service-route` | zhixue-ai | `/api/v1/dispatcher/**` | 10 req/s |
| `rag-service-route` | zhixue-ai | `/api/v1/rag/**` | 10 req/s |
| `user-service-route` | zhixue-user | `/api/v1/auth/**`, `/api/v1/users/**` | 20 req/s |
| `admin-user-service-route` | zhixue-admin | `/api/v1/admin/admin-users/**` | 15 req/s |
| `admin-platform-user-route` | zhixue-user | `/api/v1/admin/users/**` | 15 req/s |
| `learning-service-route` | zhixue-learning | `/api/v1/learning/**` | 15 req/s |
| `ai-service-route` | zhixue-ai | `/api/v1/ai/**` | 10 req/s |
| `admin-problem-service-route` | zhixue-problem | `/api/v1/admin/problems/**` | 15 req/s |
| `admin-service-route` | zhixue-admin | `/api/v1/admin/**` | 15 req/s |
| `problem-websocket-route` | zhixue-problem | `/ws/**` (WebSocket) | 无限流 |
| `problem-websocket-http-route` | zhixue-problem | `/ws/**` (HTTP) | 无限流 |
| `problem-service-route` | zhixue-problem | `/api/v1/problems/**`, `/api/v1/submissions/**` | 15 req/s |

#### CORS 全局配置

```yaml
globalcors:
  cors-configurations:
    '[/**]':
      allowedOriginPatterns: "*"
      allowedMethods: "*"
      allowedHeaders: "*"
      allowCredentials: true
      maxAge: 3600
```

---

### zhixue-common（公共模块）

📁 **配置文件**: `zhixue-common/src/main/resources/application.yaml`  
⚠️ **说明**: 公共工具模块，不作为独立服务运行

#### 环境变量列表

| 变量名 | 说明 | 默认值 | 用途 |
|--------|------|--------|------|
| `JWT_SECRET` | JWT 密钥 | `your-jwt-secret-change-in-production` | 提供 JWT 工具类给其他模块 |

#### 功能说明

此模块提供：
- JWT Token 生成与验证工具
- 公共常量定义
- 通用工具方法

**注意**: 此模块不启动独立服务，仅被其他模块依赖。

---

## 快速部署示例

### Docker Compose 示例

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_PASSWORD:-ProdSecurePassword2024!}
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  nacos:
    image: nacos/nacos-server:v2.2.3
    environment:
      MODE: standalone
      NACOS_APPLICATION_PORT: 8848
    ports:
      - "8848:8848"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  zhixue-admin:
    build: ./zhixue-admin
    environment:
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-ProdSecurePassword2024!}
      JWT_SECRET: ${JWT_SECRET:-YourSuperSecretKeyForProduction2024}
    ports:
      - "8090:8090"
    depends_on:
      - mysql
      - nacos

volumes:
  mysql_data:
```

### Kubernetes ConfigMap 示例

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: zhixue-env-config
data:
  MYSQL_PASSWORD: "ProdSecurePassword2024!"
  JWT_SECRET: "YourSuperSecretKeyForProduction2024"
  NACOS_IP: "10.0.0.100"
  RAG_UPLOAD_PATH: "/data/rag-uploads"
```

### 环境变量导出模板

```bash
#!/bin/bash
# env_template.sh - 生产环境变量配置模板

# ====== 数据库配置 ======
export MYSQL_PASSWORD="YourSecureMySQLPassword!"

# ====== 安全配置（必须修改）=====
export JWT_SECRET="YourSuperSecretJWTKey-MustBeLongAndRandom"

# ====== 网络配置 ======
export NACOS_IP="192.168.1.100"  # 如果需要指定 Nacos 注册 IP

# ====== AI 服务配置 ======
export RAG_UPLOAD_PATH="/data/uploads/rag-documents"

echo "✅ 环境变量已加载"
```

---

## 安全建议

### 🔴 必须修改的生产环境配置

#### 1. JWT Secret（极高优先级）

**风险**: 使用默认密钥会导致 Token 被伪造，攻击者可伪装任意用户

```bash
# 生成安全的 JWT Secret（至少 256 位随机字符）
openssl rand -base64 32

# 示例输出: Xy7@bK9$mL2#pQ5&nR8*wT3!vY6(zA1
```

**配置方式**:
```bash
export JWT_SECRET="$(openssl rand -base64 32)"
```

#### 2. MySQL Password（高优先级）

**风险**: 弱密码导致数据库被入侵，数据泄露

```bash
# 生成强密码（至少 16 位，包含大小写字母、数字、特殊符号）
openssl rand -base64 24
```

**密码复杂度要求**:
- 长度 ≥ 16 字符
- 包含大写字母 (A-Z)
- 包含小写字母 (a-z)
- 包含数字 (0-9)
- 包含特殊字符 (!@#$%^&*)

#### 3. Nacos 认证信息（中优先级）

**当前状态**: 使用默认账号密码 `nacos:nacos`

**建议操作**:
1. 登录 Nacos 控制台（http://localhost:8848/nacos）
2. 修改默认管理员密码
3. 在配置文件中更新为强密码

### 🟡 推荐优化的配置

#### 1. 启用 HTTPS

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

#### 2. Redis 密码保护

```yaml
spring:
  data:
    redis:
      password: ${REDIS_PASSWORD:YourRedisPassword}
```

#### 3. 数据库连接加密

建议使用 SSL 连接 MySQL：

```yaml
datasource:
  url: jdbc:mysql://localhost:3306/zhixue?useSSL=true&requireSSL=true
```

### 🟢 安全最佳实践

#### 1. 环境隔离策略

```yaml
# 开发环境
application-dev.yaml:
  MYSQL_PASSWORD: dev_password_123

# 测试环境
application-test.yaml:
  MYSQL_PASSWORD: test_password_456

# 生产环境（通过环境变量注入）
# 不在代码中存储！
```

#### 2. 密钥轮换计划

| 配置项 | 轮换频率 | 操作方式 |
|--------|----------|----------|
| JWT Secret | 每 90 天 | 更新环境变量，旧 Token 失效期 24h |
| MySQL Password | 每 180 天 | 更新后重启服务 |
| Nacos Password | 随系统更新 | 同步更新所有微服务配置 |

#### 3. 审计日志监控

建议启用以下监控：
- 异常登录尝试
- JWT 签名失败次数
- 数据库连接失败告警
- 环境变量变更记录

---

## 附录：完整环境变量速查表

| 变量名 | 类型 | 默认值 | 适用模块 | 生产要求 | 修改难度 |
|--------|------|--------|----------|----------|----------|
| `MYSQL_PASSWORD` | string | `123456` | 5个模块 | 🔴 必须修改 | ⭐ 简单 |
| `JWT_SECRET` | string | `your-jwt-secret...` | 6个模块 | 🔴 **必须修改** | ⭐ 简单 |
| `NACOS_IP` | string | `127.0.0.1` | problem | 🟡 按需修改 | ⭐ 简单 |
| `RAG_UPLOAD_PATH` | string | `./uploads/...` | ai | 🟢 可选 | ⭐ 简单 |

---

## 技术支持

如遇到配置问题，请按以下顺序排查：

1. ✅ 检查环境变量是否正确设置：`env \| grep MYSQL`
2. ✅ 验证 YAML 语法：在线 YAML Linter
3. ✅ 查看启动日志中的配置加载情况
4. ✅ 确认基础服务（MySQL、Nacos、Redis）可用性
5. 📞 联系运维团队获取生产环境配置模板

---

**文档版本**: v1.0  
**最后更新**: 2026-08-19  
**适用版本**: 智学平台全模块  
**维护团队**: DevOps 团队