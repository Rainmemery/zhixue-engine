# 智学引擎 

> AI驱动的智能编程学习平台 — 集编程练习、代码辅助、知识管理于一体

---

## 快速体验

**最快的方式（3步启动）：**

```bash
# 1. 进入 Docker 目录
cd docker

# 2. 一键启动所有服务
docker-compose up -d

# 3. 打开浏览器访问
open http://localhost
```

就这么简单！默认密码和账号见下方。

---

## 这个项目能做什么？

| 功能 | 说明 |
|------|------|
| **编程练习** | 支持多语言代码编辑、实时评测、测试用例验证 |
| **AI代码助手** | 代码解释、代码评审、智能问答、协作优化 |
| **知识库 (RAG)** | 上传文档 → 自动向量化 → 语义检索 → AI增强回答 |
| **学习系统** | 学习记录追踪、个性化学习路径、成就徽章 |
| **管理后台** | 用户管理、题目管理、系统监控 |

---

## 技术栈一览

### 前端

| 技术 | 用途 | 为什么选它 |
|------|------|------------|
| Next.js 16 + React 19 | 前端框架 | SSR支持、生态丰富 |
| TypeScript 5 | 类型安全 | 减少运行时错误 |
| Ant Design 6 | UI组件库 | 企业级组件、开箱即用 |
| Monaco Editor | 代码编辑器 | VS Code 同款编辑器 |
| Zustand 5 | 状态管理 | 轻量、简洁 |
| Tauri 2 | 桌面端 | 可打包为原生应用 |

### 后端

| 技术 | 用途 | 为什么选它 |
|------|------|------------|
| Java 17 + Spring Boot 3.2 | 后端框架 | 成熟稳定、生态完善 |
| Spring Cloud + Nacos | 微服务 | 服务发现、配置中心 |
| MyBatis | ORM | SQL灵活、性能可控 |
| MySQL 8 | 关系数据库 | 可靠、广泛使用 |
| Milvus | 向量数据库 | 开源、高性能 |
| Ollama | AI模型 | 本地运行、数据安全 |
| Redis | 缓存 | 高性能、多功能 |

---

## 项目结构

```
zhixue/
│
├── 📄 README.md              ← 你在这里
├── 📄 DEPLOY.md              ← 部署指南（详细版）
├── 📄 ARCHITECTURE.md        ← 系统架构文档
├── 📄 pom.xml                ← Maven 根配置
│
├── 📁 zhixue-common/         ← 公共模块（工具类、JWT、统一响应）
├── 📁 zhixue-getaway/        ← API 网关（路由、限流、CORS）
├── 📁 zhixue-user/           ← 用户服务（认证、用户管理）
├── 📁 zhixue-problem/        ← 题目服务（题目管理、代码评测）
├── 📁 zhixue-learning/       ← 学习服务（学习记录、学习路径）
├── 📁 zhixue-ai/             ← AI 服务（AI对话、RAG知识库）
├── 📁 zhixue-admin/          ← 管理服务（后台管理、系统监控）
│
├── 📁 frontend/              ← Next.js 前端
│   ├── app/                  │  页面路由
│   ├── components/           │  UI 组件
│   ├── services/             │  API 调用层
│   ├── stores/               │  状态管理 (Zustand)
│   └── types/                │  TypeScript 类型定义
│
├── 📁 docker/                ← Docker 部署配置
│   ├── docker-compose.yml    │  服务编排（一键启动）
│   ├── .env                  │  环境变量（密码、内存配置）
│   ├── deploy.sh             │  部署脚本
│   ├── backend/              │  后端 Dockerfile
│   ├── frontend/             │  前端 Dockerfile + Nginx
│   └── init-scripts/         │  数据库初始化 SQL
│
├── 📁 scripts/               ← 测试数据 SQL
└── 📁 deploy/                ← Milvus 部署配置
```

---

## 默认账号密码

| 服务 | 用户名 | 密码 | 地址 |
|------|--------|------|------|
| **前端应用** | 注册即可 | — | http://localhost |
| **Nacos 控制台** | nacos | nacos | http://localhost:8848/nacos |
| **MinIO 控制台** | minioadmin | minioadmin | http://localhost:9001 |
| **MySQL** | root | changeme | localhost:3306 |

> **注意**：首次使用请先注册账号！

---

## 服务端口速查

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 | 3000 / 80 | 开发模式 / Docker |
| API 网关 | 8080 | 所有 API 入口 |
| 用户服务 | 8081 | 登录注册 |
| 题目服务 | 8082 | 刷题评测 |
| 学习服务 | 8083 | 学习记录 |
| AI 服务 | 8086 | AI 对话 |
| 管理服务 | 8090 | 后台管理 |

---

## 环境变量配置

所有服务支持环境变量覆盖默认配置：

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `MYSQL_PASSWORD` | changeme | MySQL 密码 |
| `JWT_SECRET` | your-jwt-secret | JWT 密钥 (**生产环境必须修改**) |
| `NACOS_IP` | 127.0.0.1 | Nacos 注册 IP |
| `RAG_UPLOAD_PATH` | ./uploads/rag-documents | RAG 文档上传路径 |

---

## 二次开发指南

### 作为新手，建议按以下顺序阅读：

1. **[DEPLOY.md](DEPLOY.md)** — 先把环境跑起来
2. **[ARCHITECTURE.md](ARCHITECTURE.md)** — 了解系统是怎么工作的
3. **各模块 README** — 了解你负责的模块

### 开发流程

```
1. Fork / 拷贝项目
       ↓
2. 搭建本地开发环境（DEPLOY.md 有详细步骤）
       ↓
3. 选择你要开发的模块
       ↓
4. 修改代码 → 测试 → 提交
       ↓
5. 新增服务？在 gateway 添加路由
   新增表？在 init-scripts 添加 SQL
```

### 常见开发场景

| 场景 | 操作 |
|------|------|
| 新增一个 API 接口 | 在对应服务的 Controller 中添加 |
| 新增一个页面 | 在 `frontend/app/` 中添加路由 |
| 新增一个服务 | 创建模块 → pom.xml 配置 → 在 gateway 添加路由 |
| 修改数据库 | 在 `docker/init-scripts/tables/` 添加 SQL |
| 调试 AI 功能 | 确保 Ollama 已启动并下载模型 |

---

## 常见问题

**Q: Docker 启动后访问不了前端？**
A: 等待所有服务健康检查通过（约1-2分钟），查看日志：`docker-compose logs -f`

**Q: AI 对话没有响应？**
A: 检查 Ollama 是否启动：`curl http://localhost:11434/api/tags`

**Q: 代码评测报错？**
A: 确保 Docker Socket 可访问，评测在容器中执行

---

## 许可证

本项目仅供学习交流使用。
