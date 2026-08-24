# Milvus 二进制部署指南

## 方式一：手动下载二进制文件

### 1. 下载Milvus二进制文件

请手动从以下地址下载Milvus二进制文件：

**下载地址（选择一个）：**
- GitHub官方: https://github.com/milvus-io/milvus/releases/download/v2.3.3/milvus-linux-amd64
- 国内镜像: https://mirrors.huaweicloud.com/milvus/2.3.3/milvus-linux-amd64

下载后，将文件保存到：
```
/home/lyx/桌面/project/zhixue/deploy/milvus-binary/milvus
```

### 2. 添加执行权限并启动

```bash
cd /home/lyx/桌面/project/zhixue/deploy/milvus-binary
chmod +x milvus

# 启动Milvus（嵌入式模式，无需etcd和minio）
./milvus run standalone
```

### 3. 后台运行

```bash
nohup ./milvus run standalone > milvus.log 2>&1 &
```

---

## 方式二：使用Docker（推荐）

如果网络问题解决后，推荐使用Docker部署：

```bash
cd /home/lyx/桌面/project/zhixue/deploy/milvus
docker-compose up -d
```

---

## 方式三：使用Milvus云服务（Zilliz Cloud）

如果本地部署困难，可以使用Milvus官方云服务：

1. 注册Zilliz Cloud: https://cloud.zilliz.com
2. 创建免费集群
3. 获取连接地址和API Key
4. 修改application.yaml配置：

```yaml
milvus:
  host: your-cluster.zillizcloud.com
  port: 19530
  secure: true
```

---

## 验证部署

```bash
# 检查端口
netstat -tlnp | grep 19530

# 使用curl测试
curl http://localhost:19530/v1/vector/collections
```

---

## 当前部署目录

- 部署目录: `/home/lyx/桌面/project/zhixue/deploy/milvus-binary/`
- Docker Compose: `/home/lyx/桌面/project/zhixue/deploy/milvus/docker-compose.yml`
