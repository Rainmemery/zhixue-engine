#!/bin/bash

echo "停止所有服务..."
docker-compose down

echo "清理未使用的资源..."
docker system prune -f

echo "所有服务已停止"
