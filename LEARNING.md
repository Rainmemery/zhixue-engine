# 智学引擎 - 学习文档

> 本文档帮助你系统地学习智学引擎项目，理解它的设计思想、代码结构和关键技术。适合想要做二次开发或学习微服务架构的同学。

---

## 目录

- [你将学到什么](#你将学到什么)
- [技术栈全景](#技术栈全景)
- [核心概念速览](#核心概念速览)
- [后端架构详解](#后端架构详解)
- [前端架构详解](#前端架构详解)
- [关键设计模式](#关键设计模式)
- [从零开始读懂代码](#从零开始读懂代码)
- [动手实践指南](#动手实践指南)

---

## 你将学到什么

| 技能 | 你会接触到 |
|------|------------|
| **微服务架构** | 7个服务如何协作、网关路由、服务发现 |
| **Spring Boot 3** | Controller-Service-Mapper 分层、依赖注入、配置管理 |
| **Spring Cloud** | Nacos 服务注册、Gateway 网关、LoadBalancer 负载均衡 |
| **JWT 认证** | 双令牌机制、Token 生成与验证 |
| **Docker 容器化** | 代码评测隔离、Docker Compose 编排 |
| **AI 集成** | Ollama 本地模型、SSE 流式响应、RAG 知识库 |
| **向量数据库** | Milvus 向量存储与检索 |
| **Next.js 16** | App Router、Server Components、TypeScript |
| **状态管理** | Zustand 全局状态、持久化 |
| **API 设计** | RESTful 规范、统一响应格式、错误处理 |

---

## 技术栈全景

```
┌─────────────────────────────────────────────────────────────┐
│                        前端技术栈                            │
│                                                             │
│   Next.js 16 + React 19 + TypeScript                       │
│   ├── Ant Design 6        (UI 组件库)                       │
│   ├── Monaco Editor       (代码编辑器)                       │
│   ├── Zustand 5           (状态管理)                         │
│   ├── Axios               (HTTP 请求)                       │
│   ├── React Query         (服务端状态)                       │
│   └── Tauri 2             (桌面端)                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        后端技术栈                            │
│                                                             │
│   Java 17 + Spring Boot 3.2.4                               │
│   ├── Spring Cloud 2023.0    (微服务框架)                    │
│   ├── Spring Cloud Alibaba   (Nacos 服务发现)                │
│   ├── Spring Cloud Gateway   (API 网关)                     │
│   ├── MyBatis 3.0            (ORM 框架)                     │
│   ├── JWT (jjwt 0.11)       (身份认证)                       │
│   └── Lombok                (简化代码)                       │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        数据存储层                            │
│                                                             │
│   MySQL 8.0          关系数据库 (业务数据)                    │
│   Redis 7.x          缓存 + 限流                             │
│   Milvus 2.3+        向量数据库 (RAG 检索)                   │
│   Ollama             本地 AI 模型服务                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 核心概念速览

### 1. 微服务 (Microservices)

传统架构：所有功能打包在一个应用里 → 难以维护、难以扩展

微服务架构：拆分成多个小服务 → 每个服务独立开发、独立部署

```
智学引擎拆成了 7 个服务：

zhixue-common     → 公共工具（其他服务依赖它）
zhixue-getaway    → 网关（请求入口）
zhixue-user       → 用户（登录注册）
zhixue-problem    → 题目（刷题评测）
zhixue-learning   → 学习（学习记录）
zhixue-ai         → AI（对话、知识库）
zhixue-admin      → 管理（后台）
```

### 2. API 网关 (Gateway)

所有请求的统一入口：

```
用户请求 → 网关判断路径 → 转发到对应服务

/api/v1/auth/**    → zhixue-user
/api/v1/problems/** → zhixue-problem
/api/v1/ai/**      → zhixue-ai
```

好处：统一管理路由、限流、跨域、认证

### 3. 服务发现 (Nacos)

每个服务启动时向 Nacos 注册自己，其他服务通过 Nacos 找到它：

```
zhixue-user 启动 → 注册到 Nacos
zhixue-ai 需要调用用户信息 → 问 Nacos → Nacos 返回 zhixue-user 的地址
```

### 4. JWT 认证

```
登录 → 服务器生成 Token（包含用户ID）→ 返回给前端
后续请求 → 前端带上 Token → 服务器验证 Token → 确认身份
```

双令牌机制：
- Access Token：24小时有效（短期，安全）
- Refresh Token：7天有效（长期，用于续期）

### 5. SSE 流式响应

普通 HTTP：请求 → 等待 → 一次性返回结果

SSE 流式：请求 → 边生成边返回 → 用户实时看到 AI 输出

```
用户发送问题
    ↓
AI 服务开始生成回答
    ↓
每生成一段文字就推送给前端
    ↓
前端实时显示（像打字机效果）
```

### 6. RAG (检索增强生成)

让 AI 基于你上传的文档回答问题：

```
用户上传文档 → 文本提取 → 分块 → 向量化 → 存入 Milvus

用户提问 → 问题向量化 → 在 Milvus 中检索相似文档片段
    ↓
把检索到的内容 + 问题一起发给 AI
    ↓
AI 基于文档内容回答（而不是瞎编）
```

---

## 后端架构详解

### 分层架构 (MVC)

每个服务都遵循相同的分层模式：

```
Controller（控制器）
    ↓ 接收请求，调用 Service
Service（服务）
    ↓ 业务逻辑，调用 Mapper
Mapper（数据访问）
    ↓ SQL 操作，访问数据库
Database（数据库）
```

以用户服务为例：

```
zhixue-user/
├── controller/
│   ├── AuthController.java          ← 处理登录注册
│   ├── UserMangementController.java ← 用户管理
│   └── ...
├── service/
│   ├── AuthService.java             ← 认证业务逻辑
│   ├── impl/
│   │   └── AuthServiceImpl.java     ← 实现类
│   └── ...
├── mapper/
│   ├── UserMapper.java              ← 数据库操作
│   └── ...
├── entity/
│   ├── User.java                    ← 实体类
│   └── ...
├── dto/
│   └── ...                          ← 数据传输对象
└── ZhixueUserApplication.java       ← 启动类
```

### 统一响应格式

所有 API 返回统一格式：

```json
{
    "code": 200,
    "msg": "success",
    "data": { ... },
    "timestamp": 1735228800000,
    "requestId": "req_xxx"
}
```

对应代码：`zhixue-common/entity/Result.java`

```java
// 成功
Result.success(data);
Result.success(data, "操作成功");

// 失败
Result.wrong("用户名或密码错误");
```

### 认证流程详解

```
1. 用户登录
   POST /api/v1/auth/login
   Body: { "username": "xxx", "password": "xxx" }
   
2. 服务器验证密码，生成 Token
   JwtUtil.generateToken(userId)      → Access Token
   JwtUtil.generateRefreshToken(userId) → Refresh Token
   
3. 返回给前端
   { "token": "eyJ...", "refreshToken": "eyJ...", "user": {...} }
   
4. 前端存储 Token
   localStorage.setItem("token", token)
   
5. 后续请求带上 Token
   Header: Authorization: Bearer eyJ...
   
6. 服务器验证 Token
   JwtUtil.validateToken(token, userId)
```

### AI 服务核心：SSE 流式响应

```java
// Controller 返回 SseEmitter
@PostMapping("stream-chat")
public SseEmitter streamChat(@RequestBody AiRequest request) {
    return aiService.streamChat(request);
}

// Service 异步处理
public SseEmitter streamChat(AiRequest request) {
    SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时
    
    CompletableFuture.runAsync(() -> {
        // 调用 AI 模型，逐块发送
        modelClient.streamChat(request, response -> {
            emitter.send(response.getContent()); // 实时推送
        });
        emitter.complete(); // 完成
    });
    
    return emitter;
}
```

### 代码评测：Docker 隔离

用户提交的代码在 Docker 容器中执行，安全隔离：

```
用户提交代码
    ↓
从容器池借用容器
    ↓
写入代码文件 → 编译 → 运行测试用例
    ↓
收集输出结果 → 对比预期答案
    ↓
归还容器 → 返回评测结果
```

核心代码：`zhixue-problem/judge/JudgeEngine.java`

---

## 前端架构详解

### Next.js App Router

使用文件系统路由，文件结构决定 URL：

```
frontend/app/
├── page.tsx                    → /
├── (auth)/
│   ├── login/page.tsx          → /login
│   └── register/page.tsx       → /register
├── (main)/
│   ├── dashboard/page.tsx      → /dashboard
│   ├── problems/page.tsx       → /problems
│   ├── ai-chat/page.tsx        → /ai-chat
│   ├── learning/page.tsx       → /learning
│   └── ...
└── admin/
    ├── users/page.tsx          → /admin/users
    ├── problems/page.tsx       → /admin/problems
    └── ...
```

### API 调用模式

统一的 API 客户端：`frontend/services/api.ts`

```typescript
// 创建 axios 实例
const apiClient = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  timeout: 30000,
});

// 请求拦截器：自动添加 Token
apiClient.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器：统一处理错误
apiClient.interceptors.response.use(
  response => {
    if (response.data.code === 200) return response;
    // 业务错误处理...
  },
  error => {
    // 网络错误处理...
  }
);
```

具体服务调用：`frontend/services/authService.ts`

```typescript
export const authService = {
  async login(credentials: LoginCredentials): Promise<AuthResponse> {
    const response = await api.post<ApiResponse<AuthResponse>>('/auth/login', credentials);
    return response.data.data;
  },
};
```

### 状态管理 (Zustand)

全局状态存储：`frontend/stores/authStore.ts`

```typescript
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      isAuthenticated: false,
      
      setAuth: (auth) => {
        set({ user: auth.user, token: auth.token, isAuthenticated: true });
      },
      
      logout: () => {
        set({ user: null, token: null, isAuthenticated: false });
      },
    }),
    { name: 'auth-storage' } // 持久化到 localStorage
  )
);
```

使用：

```typescript
// 在组件中
const { user, isAuthenticated, logout } = useAuthStore();

// 检查登录状态
if (!isAuthenticated) redirect('/login');

// 登出
logout();
```

---

## 关键设计模式

### 1. 策略模式 (Strategy Pattern)

AI 服务支持多种模型切换：

```java
// 模型调度器根据配置选择不同的 AI 客户端
public interface AiModelClient {
    void streamChat(AiRequest request, Consumer<String> callback);
}

// Ollama 实现
@Component
public class OllamaClient implements AiModelClient { ... }

// OpenAI 实现
@Component
public class OpenAiClient implements AiModelClient { ... }

// 调度器
@Component
public class ModelDispatcher {
    public AiModelClient getCurrentClient() {
        // 根据配置选择客户端
    }
}
```

### 2. 池化模式 (Pool Pattern)

代码评测使用容器池，避免频繁创建销毁：

```java
@Component
public class ContainerPoolManager {
    // 预创建一批容器
    private Map<String, Queue<Container>> pools;
    
    // 借用容器
    public Container borrowContainer(String language, int timeout) {
        return pools.get(language).poll(timeout, TimeUnit.SECONDS);
    }
    
    // 归还容器
    public void returnContainer(Container container) {
        pools.get(container.getLanguage()).offer(container);
    }
}
```

### 3. 观察者模式 (Observer Pattern)

WebSocket 实时推送评测结果：

```java
// 评测完成后推送
@MessageMapping("/submit")
public void handleSubmission(CodeSubmission submission) {
    // 执行评测
    JudgeResult result = judgeEngine.judge(submission);
    
    // 推送给用户
    messagingTemplate.convertAndSend(
        "/topic/submission/" + submission.getUserId(), 
        result
    );
}
```

### 4. 中间件模式 (Middleware Pattern)

Axios 拦截器链：

```
请求 → [添加Token] → [添加请求ID] → [防缓存] → 发送到服务器
响应 → [检查状态码] → [处理业务错误] → [处理认证失败] → 返回给组件
```

---

## 从零开始读懂代码

### 推荐阅读顺序

```
1. zhixue-common/entity/Result.java
   → 理解统一响应格式

2. zhixue-common/utils/JwtUtil.java
   → 理解 JWT 认证机制

3. zhixue-user/controller/AuthController.java
   → 理解 Controller 写法

4. zhixue-user/service/AuthService.java + impl/AuthServiceImpl.java
   → 理解 Service 层

5. zhixue-user/mapper/UserMapper.java
   → 理解数据访问层

6. zhixue-getaway/src/main/resources/application.yaml
   → 理解网关路由配置

7. zhixue-ai/controller/AiController.java
   → 理解 SSE 流式响应

8. frontend/services/api.ts
   → 理解前端 API 调用

9. frontend/stores/authStore.ts
   → 理解状态管理

10. frontend/services/authService.ts
    → 理解前端如何调用后端
```

### 一个完整的请求流程

以"用户登录"为例，看请求如何流转：

```
前端 (authService.ts)
    ↓ POST /api/v1/auth/login
网关 (application.yaml)
    ↓ 路由到 zhixue-user
用户服务 (AuthController.java)
    ↓ 调用 AuthService
认证服务 (AuthServiceImpl.java)
    ↓ 查询数据库、验证密码
数据访问 (UserMapper.java)
    ↓ SELECT * FROM users WHERE username = ?
MySQL 数据库
    ↓ 返回用户数据
认证服务
    ↓ JwtUtil.generateToken(userId) 生成 Token
用户服务
    ↓ 返回 Result.success(data)
网关
    ↓ 转发响应
前端
    ↓ 收到 { token, refreshToken, user }
    ↓ useAuthStore.setAuth(data) 保存状态
    ↓ 跳转到首页
```

---

## 动手实践指南

### 实验 1：添加一个新的 API 接口

**目标**：在用户服务中添加"获取用户统计信息"接口

**步骤**：

```java
// 1. Controller 中添加方法
@GetMapping("/stats")
public Result getUserStats(@PathVariable Long id) {
    Map<String, Object> stats = userStatsService.getStats(id);
    return Result.success(stats);
}

// 2. Service 中添加业务逻辑
public interface UserStatsService {
    Map<String, Object> getStats(Long userId);
}

// 3. Mapper 中添加 SQL（如果需要）
@Select("SELECT COUNT(*) FROM code_submissions WHERE user_id = #{userId}")
int countSubmissions(Long userId);
```

### 实验 2：添加一个新的前端页面

**目标**：创建"我的成就"页面

```typescript
// 1. frontend/app/(main)/achievements/page.tsx
export default function AchievementsPage() {
  return (
    <div>
      <h1>我的成就</h1>
      {/* 成就列表 */}
    </div>
  );
}

// 2. frontend/services/achievementService.ts
export const achievementService = {
  async getAchievements() {
    const response = await api.get('/users/achievements');
    return response.data.data;
  },
};
```

### 实验 3：修改数据库表

**目标**：给 users 表添加一个 avatar_url 字段

```sql
-- docker/init-scripts/tables/zhixue_user.sql 中添加
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(255) DEFAULT NULL;
```

```java
// entity/User.java 中添加
private String avatarUrl;
```

### 实验 4：配置一个新的 AI 模型

**目标**：添加对 OpenAI GPT-4 的支持

```java
// 1. 创建 OpenAI 客户端
@Component
public class OpenAiClient implements AiModelClient {
    @Override
    public void streamChat(AiRequest request, Consumer<String> callback) {
        // 调用 OpenAI API
    }
}

// 2. 修改 ModelDispatcher 选择客户端
@Component
public class ModelDispatcher {
    public AiModelClient getCurrentClient() {
        if (config.isUseOpenAi()) {
            return openAiClient;
        }
        return ollamaClient;
    }
}
```

---

## 常见问题解答

**Q: 为什么要用微服务？直接写一个大项目不行吗？**

A: 微服务的好处是独立部署、独立扩展。比如 AI 服务需要更多内存，可以单独扩容；题目服务需要高并发，可以部署多个实例。缺点是复杂度增加，适合团队协作的中大型项目。

**Q: Nacos 是必须的吗？**

A: 在本地开发时可以不用，直接在 application.yaml 中写死服务地址。但在生产环境建议使用，方便服务发现和配置管理。

**Q: 为什么 Gateway 的 pom.xml 跟其他模块不一样？**

A: Gateway 需要 WebFlux（响应式），不能引入 spring-boot-starter-web。所以它独立配置 parent，不继承根 pom 的依赖。

**Q: SSE 和 WebSocket 有什么区别？**

A: SSE 是单向的（服务器 → 客户端），适合 AI 流式输出；WebSocket 是双向的，适合实时通信（如聊天、评测结果推送）。

**Q: 前端为什么用 Zustand 而不是 Redux？**

A: Zustand 更轻量、API 更简洁、没有 boilerplate 代码。对于中小型项目完全够用。

---

## 延伸阅读

- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Spring Cloud 官方文档](https://spring.io/projects/spring-cloud)
- [Next.js 官方文档](https://nextjs.org/docs)
- [Ant Design 官方文档](https://ant.design/)
- [Zustand 官方文档](https://github.com/pmndrs/zustand)
- [Milvus 官方文档](https://milvus.io/docs)
- [Ollama 官方文档](https://ollama.com/)

---

## 总结

智学引擎是一个完整的微服务项目，涵盖了现代 Web 开发的核心技术。通过学习这个项目，你不仅能掌握具体的技术栈，更能理解：

- 如何设计一个可扩展的系统架构
- 如何让多个服务协同工作
- 如何实现安全的身份认证
- 如何集成 AI 能力
- 如何编写可维护的前后端代码

**最重要的**：不要只看代码，要动手写！从修改一个小功能开始，逐步理解整个系统。

祝你学习愉快！ 🚀
