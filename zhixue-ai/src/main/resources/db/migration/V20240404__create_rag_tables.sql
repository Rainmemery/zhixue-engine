-- RAG功能数据库表结构
-- 版本: V3.1
-- 创建日期: 2026-04-04

-- 1. Embedding模型配置表
CREATE TABLE IF NOT EXISTS `rag_embedding_model` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称（Ollama模型名）',
    `display_name` VARCHAR(200) COMMENT '显示名称',
    `dimension` INT NOT NULL COMMENT '向量维度',
    `description` VARCHAR(500) COMMENT '模型描述',
    `is_default` TINYINT DEFAULT 0 COMMENT '是否默认模型: 0-否, 1-是',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_model_name` (`model_name`),
    INDEX `idx_status` (`status`),
    INDEX `idx_is_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Embedding模型配置表';

-- 初始化Embedding模型数据
INSERT INTO `rag_embedding_model` (`model_name`, `display_name`, `dimension`, `description`, `is_default`, `status`) VALUES
('nomic-embed-text', 'Nomic Embed Text', 768, '推荐使用，性能与效果平衡', 1, 1),
('mxbai-embed-large', 'MXBai Embed Large', 1024, '更高精度，更大资源消耗', 0, 1),
('all-minilm', 'All MiniLM', 384, '轻量级，适合资源受限场景', 0, 1);

-- 2. 知识库表
CREATE TABLE IF NOT EXISTS `rag_knowledge_base` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(200) NOT NULL COMMENT '知识库名称',
    `description` TEXT COMMENT '知识库描述',
    `embedding_model_id` BIGINT COMMENT '使用的Embedding模型ID',
    `milvus_collection` VARCHAR(100) DEFAULT 'rag_vectors' COMMENT 'Milvus集合名称',
    `document_count` INT DEFAULT 0 COMMENT '文档数量',
    `chunk_count` INT DEFAULT 0 COMMENT '分块数量',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `owner_id` BIGINT COMMENT '所有者ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_name` (`name`),
    INDEX `idx_status` (`status`),
    INDEX `idx_owner` (`owner_id`),
    INDEX `idx_embedding_model` (`embedding_model_id`),
    CONSTRAINT `fk_kb_embedding_model` FOREIGN KEY (`embedding_model_id`) REFERENCES `rag_embedding_model`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 3. 知识文档表
CREATE TABLE IF NOT EXISTS `rag_document` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `knowledge_base_id` BIGINT NOT NULL COMMENT '所属知识库ID',
    `title` VARCHAR(500) NOT NULL COMMENT '文档标题',
    `file_name` VARCHAR(255) COMMENT '原始文件名',
    `file_type` VARCHAR(20) COMMENT '文件类型: PDF/DOCX/TXT/MD',
    `file_size` BIGINT COMMENT '文件大小(字节)',
    `file_path` VARCHAR(500) COMMENT '文件存储路径',
    `chunk_count` INT DEFAULT 0 COMMENT '分块数量',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待处理, 1-处理中, 2-已完成, 3-失败',
    `error_message` TEXT COMMENT '错误信息',
    `created_by` BIGINT COMMENT '创建者ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_knowledge_base_id` (`knowledge_base_id`),
    INDEX `idx_status` (`status`),
    CONSTRAINT `fk_doc_knowledge_base` FOREIGN KEY (`knowledge_base_id`) REFERENCES `rag_knowledge_base`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档表';

-- 4. 文档分块表
CREATE TABLE IF NOT EXISTS `rag_chunk` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `document_id` BIGINT NOT NULL COMMENT '所属文档ID',
    `knowledge_base_id` BIGINT NOT NULL COMMENT '所属知识库ID',
    `chunk_index` INT NOT NULL COMMENT '分块序号',
    `content` TEXT NOT NULL COMMENT '分块内容',
    `vector_id` VARCHAR(100) COMMENT 'Milvus向量ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_document_id` (`document_id`),
    INDEX `idx_knowledge_base_id` (`knowledge_base_id`),
    INDEX `idx_vector_id` (`vector_id`),
    CONSTRAINT `fk_chunk_document` FOREIGN KEY (`document_id`) REFERENCES `rag_document`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_chunk_knowledge_base` FOREIGN KEY (`knowledge_base_id`) REFERENCES `rag_knowledge_base`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分块表';

-- 5. RAG配置表
CREATE TABLE IF NOT EXISTS `rag_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `config_type` VARCHAR(50) NOT NULL COMMENT '配置类型: GLOBAL/MODULE',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT NOT NULL COMMENT '配置值(JSON格式)',
    `description` VARCHAR(500) COMMENT '配置描述',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_config` (`config_type`, `config_key`),
    INDEX `idx_config_type` (`config_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG配置表';

-- 初始化RAG配置数据
INSERT INTO `rag_config` (`config_type`, `config_key`, `config_value`, `description`) VALUES
('GLOBAL', 'rag_enabled', 'true', 'RAG功能全局开关'),
('GLOBAL', 'retrieval_config', '{"similarityThreshold": 0.7, "topK": 5, "maxContextLength": 4000}', '检索策略配置'),
('GLOBAL', 'embedding_config', '{"defaultModelId": 1, "allowModelSwitch": true}', 'Embedding模型配置'),
('GLOBAL', 'chunk_config', '{"chunkSize": 500, "chunkOverlap": 50}', '文档分块配置'),
('MODULE', 'intelligent_question', '{"enabled": true, "knowledgeBaseIds": []}', '智能提问模块RAG配置'),
('MODULE', 'ai_chat', '{"enabled": true, "knowledgeBaseIds": []}', 'AI智能问答模块RAG配置'),
('MODULE', 'code_explain', '{"enabled": true, "knowledgeBaseIds": []}', '代码解释模块RAG配置'),
('MODULE', 'code_review', '{"enabled": true, "knowledgeBaseIds": []}', '代码评审模块RAG配置'),
('MODULE', 'code_collaborate', '{"enabled": false, "knowledgeBaseIds": []}', '代码协作模块RAG配置');
