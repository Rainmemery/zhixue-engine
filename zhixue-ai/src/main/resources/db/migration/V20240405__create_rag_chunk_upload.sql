-- 创建分片上传记录表
CREATE TABLE IF NOT EXISTS rag_chunk_upload (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    upload_id VARCHAR(64) NOT NULL UNIQUE COMMENT '上传唯一标识',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type VARCHAR(20) NOT NULL COMMENT '文件类型',
    file_size BIGINT NOT NULL COMMENT '文件总大小',
    total_chunks INT NOT NULL COMMENT '总分片数',
    temp_path VARCHAR(500) NOT NULL COMMENT '临时存储路径',
    knowledge_base_id BIGINT NOT NULL COMMENT '知识库ID',
    title VARCHAR(255) COMMENT '文档标题',
    created_by BIGINT COMMENT '创建者ID',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待上传, 1-上传中, 2-已完成, 3-失败',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_upload_id (upload_id),
    INDEX idx_knowledge_base_id (knowledge_base_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传记录表';
