-- AI模型配置表
-- 用于存储不同AI模型的配置信息，包括API Key等


CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    model_type VARCHAR(50) NOT NULL COMMENT '模型类型：ollama/openai',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    api_key VARCHAR(500) DEFAULT NULL COMMENT 'API密钥（加密存储）',
    base_url VARCHAR(500) NOT NULL COMMENT 'API基础URL',
    timeout_ms INT DEFAULT 300000 COMMENT '超时时间（毫秒）',
    max_retries INT DEFAULT 3 COMMENT '最大重试次数',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    is_default BOOLEAN DEFAULT FALSE COMMENT '是否为默认配置',
    description VARCHAR(500) DEFAULT NULL COMMENT '配置描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX idx_model_type (model_type),
    INDEX idx_is_default (is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';

-- 插入默认配置
INSERT INTO ai_model_config (model_type, model_name, api_key, base_url, timeout_ms, max_retries, enabled, is_default, description) 
VALUES 
('ollama', 'qwen2.5-coder:3b-instruct-q5_K_M', NULL, 'http://localhost:11434', 300000, 3, TRUE, TRUE, 'Ollama本地模型'),
('openai', 'Qwen3-Coder-Plus', '', 'https://apis.iflow.cn/v1', 300000, 3, FALSE, FALSE, 'OpenAI兼容API');
