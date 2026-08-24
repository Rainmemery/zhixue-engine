# Docker 部署快速参考

## 快速命令

### 启动服务
```bash
cd docker
./deploy.sh
```

### 停止服务
```bash
./stop.sh
```

### 查看日志
```bash
docker-compose logs -f [service-name]
```

### 重启服务
```bash
docker-compose restart [service-name]
```

## 端口映射表

| 服务 | 外部端口 | 内部端口 | 说明 |
|------|---------|---------|------|
| 前端 | 80 | 80 | Nginx |
| API网关 | 8080 | 8080 | Spring Cloud Gateway |
| 用户服务 | - | 8081 | 内部访问 |
| 问题服务 | - | 8082 | 内部访问 |
| 学习服务 | - | 8083 | 内部访问 |
| AI服务 | - | 8086 | 内部访问 |
| 管理服务 | - | 8090 | 内部访问 |
| MySQL | 3306 | 3306 | 数据库 |
| Redis | 6379 | 6379 | 缓存 |
| Nacos | 8848, 9848 | 8848, 9848 | 注册中心 |
| Milvus | 19530, 9091 | 19530, 9091 | 向量数据库 |
| MinIO | 9000, 9001 | 9000, 9001 | 对象存储 |
| Ollama | 11434 | 11434 | AI模型 |

## 访问地址

- **前端应用**: http://localhost
- **API网关**: http://localhost:8080
- **Nacos控制台**: http://localhost:8848/nacos (nacos/nacos)
- **MinIO控制台**: http://localhost:9001 (minioadmin/minioadmin)

## 数据库连接

### MySQL
- **主机**: localhost
- **端口**: 3306
- **用户名**: root
- **密码**: 123456
- **数据库**:
  - zhixue_user
  - zhixue_problem
  - zhixue_learning
  - zhixue_ai
  - zhixue_admin
  - nacos

### Redis
- **主机**: localhost
- **端口**: 6379

### Milvus
- **主机**: localhost
- **端口**: 19530

## 环境变量

主要环境变量配置（`.env` 文件）：

```bash
# MySQL密码
MYSQL_ROOT_PASSWORD=123456

# Java应用内存配置
GATEWAY_JAVA_OPTS=-Xms256m -Xmx512m
USER_JAVA_OPTS=-Xms256m -Xmx512m
PROBLEM_JAVA_OPTS=-Xms256m -Xmx512m
LEARNING_JAVA_OPTS=-Xms256m -Xmx512m
AI_JAVA_OPTS=-Xms512m -Xmx1024m
ADMIN_JAVA_OPTS=-Xms256m -Xmx512m
```

## 故障排查

### 查看服务状态
```bash
docker-compose ps
```

### 查看服务日志
```bash
# 所有服务
docker-compose logs -f

# 特定服务
docker-compose logs -f zhixue-gateway
```

### 进入容器
```bash
# 进入MySQL
docker exec -it zhixue-mysql bash

# 进入应用容器
docker exec -it zhixue-gateway sh
```

### 检查健康状态
```bash
docker inspect --format='{{.State.Health.Status}}' zhixue-mysql
```

### 重启单个服务
```bash
docker-compose restart zhixue-gateway
```

### 完全重置
```bash
# 停止并删除容器（保留数据）
docker-compose down

# 停止并删除容器和数据卷（危险操作！）
docker-compose down -v
```

## 数据备份

### 备份MySQL
```bash
docker exec zhixue-mysql mysqldump -uroot -p123456 --all-databases > backup_$(date +%Y%m%d).sql
```

### 恢复MySQL
```bash
docker exec -i zhixue-mysql mysql -uroot -p123456 < backup_20240410.sql
```

## 性能调优

### 调整内存
编辑 `.env` 文件，修改对应的 `JAVA_OPTS` 参数。

### 查看资源使用
```bash
docker stats
```

## 安全建议

1. 修改默认密码
2. 限制端口暴露
3. 启用HTTPS
4. 定期备份数据
5. 更新镜像版本

## 目录结构

```
docker/
├── docker-compose.yml      # 主配置文件
├── .env                    # 环境变量
├── deploy.sh              # 部署脚本
├── stop.sh                # 停止脚本
├── README.md              # 详细文档
├── QUICK_REFERENCE.md     # 本文件
├── frontend/              # 前端Docker配置
│   ├── Dockerfile
│   └── nginx.conf
├── backend/               # 后端Docker配置
│   ├── Dockerfile.gateway
│   ├── Dockerfile.user
│   ├── Dockerfile.problem
│   ├── Dockerfile.learning
│   ├── Dockerfile.ai
│   └── Dockerfile.admin
└── init-scripts/          # 初始化脚本
    └── 01-create-databases.sql
```
