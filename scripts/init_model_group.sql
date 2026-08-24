-- AI 模型组与实例初始化脚本
USE zhixue_ai;

-- 1. 插入默认模型组
INSERT INTO model_group (
    name, 
    description, 
    scheduling_strategy, 
    priority, 
    enabled, 
    is_default,
    model_type,
    default_base_url,
    default_timeout_ms,
    default_max_retries,
    created_at, 
    updated_at
) VALUES (
    '默认Ollama组',
    '本地Ollama模型实例组，用于开发和测试',
    'ROUND_ROBIN',
    100,
    TRUE,
    TRUE,
    'OLLAMA',
    'http://localhost:11434',
    300000,
    3,
    NOW(),
    NOW()
);

SET @default_group_id = LAST_INSERT_ID();

-- 2. 插入 Ollama 模型实例
INSERT INTO model_instance (
    group_id,
    name,
    api_endpoint,
    model_name,
    api_key,
    weight,
    max_concurrent,
    current_connections,
    status,
    health_check_endpoint,
    created_at,
    updated_at
) VALUES (
    @default_group_id,
    'Ollama本地-Qwen2.5-Coder',
    'http://localhost:11434/api/chat',
    'qwen2.5-coder:3b-instruct-q5_K_M',
    NULL,
    10,
    50,
    0,
    'HEALTHY',
    'http://localhost:11434/api/tags',
    NOW(),
    NOW()
);

SET @ollama_instance_id = LAST_INSERT_ID();

-- 3. 插入 OpenAI 兼容实例（禁用状态）
INSERT INTO model_instance (
    group_id,
    name,
    api_endpoint,
    model_name,
    api_key,
    weight,
    max_concurrent,
    current_connections,
    status,
    health_check_endpoint,
    created_at,
    updated_at
) VALUES (
    @default_group_id,
    'OpenAI兼容API-Qwen3-Coder',
    'https://apis.iflow.cn/v1/chat/completions',
    'Qwen3-Coder-Plus',
    '',
    5,
    20,
    0,
    'DISABLED',
    NULL,
    NOW(),
    NOW()
);

-- 4. 初始化健康检查记录
INSERT INTO model_instance_health (
    instance_id,
    health_state,
    response_time_ms,
    consecutive_failures,
    consecutive_successes,
    last_check_time,
    last_success_time,
    created_at,
    updated_at
) SELECT 
    id,
    'HEALTHY',
    48,
    0,
    100,
    NOW(),
    NOW(),
    NOW(),
    NOW()
FROM model_instance 
WHERE id = @ollama_instance_id;

-- 5. 验证结果
SELECT '=== 初始化完成 ===' AS info;
SELECT CONCAT('模型组数量: ', COUNT(*)) AS `groups_count` FROM model_group;
SELECT CONCAT('模型实例数量: ', COUNT(*)) AS `instances_count` FROM model_instance;