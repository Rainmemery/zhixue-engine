#!/usr/bin/env python3
"""
Milvus Lite 启动脚本
启动一个轻量级Milvus服务，监听19530端口
"""

import os
import sys
import signal
import subprocess
import time

MILVUS_DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "milvus_data")
MILVUS_PORT = 19530

def start_milvus():
    print(f"正在启动Milvus Lite服务...")
    print(f"数据目录: {MILVUS_DATA_DIR}")
    print(f"端口: {MILVUS_PORT}")
    
    os.makedirs(MILVUS_DATA_DIR, exist_ok=True)
    
    from milvus_lite import MilvusServer
    
    server = MilvusServer()
    server.set_base_dir(MILVUS_DATA_DIR)
    server.start(host="0.0.0.0", port=MILVUS_PORT)
    
    print(f"\n✅ Milvus Lite服务已启动!")
    print(f"   连接地址: http://localhost:{MILVUS_PORT}")
    print(f"   按 Ctrl+C 停止服务\n")
    
    def signal_handler(sig, frame):
        print("\n正在停止Milvus服务...")
        server.stop()
        print("Milvus服务已停止")
        sys.exit(0)
    
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    while True:
        time.sleep(1)

if __name__ == "__main__":
    start_milvus()
