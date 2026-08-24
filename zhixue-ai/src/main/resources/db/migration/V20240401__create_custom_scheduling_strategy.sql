-- 自定义调度策略表
CREATE TABLE IF NOT EXISTS custom_scheduling_strategy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    strategy_name VARCHAR(100) NOT NULL UNIQUE COMMENT '策略名称',
    strategy_description VARCHAR(500) COMMENT '策略描述',
    preferred_model VARCHAR(50) NOT NULL COMMENT '首选模型类型',
    fallback_model VARCHAR(50) COMMENT '备用模型类型',
    condition_json TEXT COMMENT '策略条件JSON',
    priority INT DEFAULT 50 COMMENT '优先级',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    is_system BOOLEAN DEFAULT FALSE COMMENT '是否系统策略',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_strategy_name (strategy_name),
    INDEX idx_enabled (enabled),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义调度策略表';
