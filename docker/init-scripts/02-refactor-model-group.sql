-- 模型组管理重构 - 表结构变更
-- 数据库: zhixue_ai
-- 用户: root
-- 密码: 123456

USE zhixue_ai;

-- 1. 为model_group表添加新字段（使用存储过程处理IF NOT EXISTS）
DELIMITER //

DROP PROCEDURE IF EXISTS add_column_if_not_exists //

CREATE PROCEDURE add_column_if_not_exists()
BEGIN
    -- 添加 model_type 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'zhixue_ai' 
        AND table_name = 'model_group' 
        AND column_name = 'model_type'
    ) THEN
        ALTER TABLE model_group ADD COLUMN model_type VARCHAR(50) DEFAULT 'CUSTOM' COMMENT '模型类型: OLLAMA, OPENAI, CUSTOM' AFTER name;
    END IF;
    
    -- 添加 default_api_key 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'zhixue_ai' 
        AND table_name = 'model_group' 
        AND column_name = 'default_api_key'
    ) THEN
        ALTER TABLE model_group ADD COLUMN default_api_key VARCHAR(255) COMMENT '默认API密钥' AFTER scheduling_strategy;
    END IF;
    
    -- 添加 default_base_url 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'zhixue_ai' 
        AND table_name = 'model_group' 
        AND column_name = 'default_base_url'
    ) THEN
        ALTER TABLE model_group ADD COLUMN default_base_url VARCHAR(500) COMMENT '默认基础URL' AFTER default_api_key;
    END IF;
    
    -- 添加 default_timeout_ms 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'zhixue_ai' 
        AND table_name = 'model_group' 
        AND column_name = 'default_timeout_ms'
    ) THEN
        ALTER TABLE model_group ADD COLUMN default_timeout_ms INT DEFAULT 300000 COMMENT '默认超时时间(毫秒)' AFTER default_base_url;
    END IF;
    
    -- 添加 default_max_retries 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'zhixue_ai' 
        AND table_name = 'model_group' 
        AND column_name = 'default_max_retries'
    ) THEN
        ALTER TABLE model_group ADD COLUMN default_max_retries INT DEFAULT 3 COMMENT '默认最大重试次数' AFTER default_timeout_ms;
    END IF;
    
    -- 添加 is_default 字段
    IF NOT EXISTS (
        SELECT * FROM information_schema.columns 
        WHERE table_schema = 'zhixue_ai' 
        AND table_name = 'model_group' 
        AND column_name = 'is_default'
    ) THEN
        ALTER TABLE model_group ADD COLUMN is_default BOOLEAN DEFAULT FALSE COMMENT '是否默认模型组' AFTER enabled;
    END IF;
END //

DELIMITER ;

CALL add_column_if_not_exists();
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- 2. 创建实例健康状态表（使用正确的表名 model_instance_health）
CREATE TABLE IF NOT EXISTS model_instance_health (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id BIGINT NOT NULL UNIQUE COMMENT '实例ID',
    health_state VARCHAR(20) DEFAULT 'HEALTHY' COMMENT '健康状态: HEALTHY, DEGRADED, UNHEALTHY',
    response_time_ms BIGINT COMMENT '响应时间(毫秒)',
    consecutive_failures INT DEFAULT 0 COMMENT '连续失败次数',
    consecutive_successes INT DEFAULT 0 COMMENT '连续成功次数',
    last_check_time DATETIME COMMENT '最后检查时间',
    last_success_time DATETIME COMMENT '最后成功时间',
    last_failure_time DATETIME COMMENT '最后失败时间',
    last_error_message TEXT COMMENT '最后错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (instance_id) REFERENCES model_instance(id) ON DELETE CASCADE
);

-- 3. 创建健康检查日志表
CREATE TABLE IF NOT EXISTS health_check_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id BIGINT NOT NULL COMMENT '实例ID',
    check_time DATETIME NOT NULL COMMENT '检查时间',
    healthy BOOLEAN NOT NULL COMMENT '是否健康',
    response_time_ms INT COMMENT '响应时间(毫秒)',
    error_message TEXT COMMENT '错误信息',
    check_endpoint VARCHAR(100) COMMENT '检查端点',
    http_status INT COMMENT 'HTTP状态码',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_instance_time (instance_id, check_time),
    FOREIGN KEY (instance_id) REFERENCES model_instance(id) ON DELETE CASCADE
);

-- 4. 创建调度策略配置表
CREATE TABLE IF NOT EXISTS scheduling_strategy_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE COMMENT '策略名称',
    description TEXT COMMENT '策略描述',
    is_active BOOLEAN DEFAULT FALSE COMMENT '是否激活',
    config_json TEXT COMMENT '策略配置JSON',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 5. 插入默认调度策略
INSERT INTO scheduling_strategy_config (name, description, is_active, config_json) VALUES
('ROUND_ROBIN', '轮询调度 - 按顺序依次选择实例', TRUE, '{"type": "ROUND_ROBIN"}'),
('WEIGHTED_ROUND_ROBIN', '加权轮询 - 根据权重分配请求', FALSE, '{"type": "WEIGHTED_ROUND_ROBIN"}'),
('LEAST_CONNECTIONS', '最少连接 - 选择当前连接数最少的实例', FALSE, '{"type": "LEAST_CONNECTIONS"}'),
('INTELLIGENT', '智能调度 - 根据任务特征自动选择最佳实例', FALSE, '{"type": "INTELLIGENT", "factors": ["response_time", "load", "task_type"]}')
ON DUPLICATE KEY UPDATE description = VALUES(description);
