-- 模型组管理重构 - 数据迁移
-- 将ai_model_config数据迁移到model_group和model_instance
-- 数据库: zhixue_ai

USE zhixue_ai;

-- 步骤1: 为每个ai_model_config创建对应的model_group
INSERT INTO model_group (name, model_type, description, scheduling_strategy, priority, enabled, is_default, default_api_key, default_base_url, default_timeout_ms, default_max_retries, created_at, updated_at)
SELECT 
    CONCAT(UPPER(model_type), ' - ', model_name) as name,
    UPPER(model_type) as model_type,
    COALESCE(description, CONCAT(UPPER(model_type), '模型配置')) as description,
    'ROUND_ROBIN' as scheduling_strategy,
    CASE WHEN is_default = TRUE THEN 100 ELSE 50 END as priority,
    enabled,
    COALESCE(is_default, FALSE),
    api_key as default_api_key,
    base_url as default_base_url,
    COALESCE(timeout_ms, 300000) as default_timeout_ms,
    COALESCE(max_retries, 3) as default_max_retries,
    COALESCE(created_at, NOW()),
    COALESCE(updated_at, NOW())
FROM ai_model_config
ON DUPLICATE KEY UPDATE 
    description = VALUES(description),
    enabled = VALUES(enabled),
    is_default = VALUES(is_default),
    updated_at = NOW();

-- 步骤2: 为每个ai_model_config创建对应的model_instance
INSERT INTO model_instance (group_id, name, api_endpoint, model_name, api_key, weight, max_concurrent, status, created_at, updated_at)
SELECT 
    mg.id as group_id,
    CONCAT(amc.model_name, '-instance-01') as name,
    amc.base_url as api_endpoint,
    amc.model_name,
    amc.api_key,
    1 as weight,
    10 as max_concurrent,
    CASE WHEN amc.enabled = TRUE THEN 'HEALTHY' ELSE 'DISABLED' END as status,
    COALESCE(amc.created_at, NOW()),
    COALESCE(amc.updated_at, NOW())
FROM ai_model_config amc
JOIN model_group mg ON mg.name = CONCAT(UPPER(amc.model_type), ' - ', amc.model_name)
WHERE NOT EXISTS (
    SELECT 1 FROM model_instance mi WHERE mi.name = CONCAT(amc.model_name, '-instance-01')
);

-- 步骤3: 为每个实例创建健康状态记录
INSERT INTO instance_health_status (instance_id, health_state, last_check_time, consecutive_failures, consecutive_successes, created_at, updated_at)
SELECT 
    mi.id as instance_id,
    CASE WHEN mi.status = 'HEALTHY' THEN 'HEALTHY' ELSE 'UNKNOWN' END as health_state,
    NOW() as last_check_time,
    0 as consecutive_failures,
    0 as consecutive_successes,
    NOW() as created_at,
    NOW() as updated_at
FROM model_instance mi
WHERE NOT EXISTS (
    SELECT 1 FROM instance_health_status ihs WHERE ihs.instance_id = mi.id
);

-- 步骤4: 验证迁移结果
SELECT 
    '原ai_model_config记录数' as item, COUNT(*) as count FROM ai_model_config
UNION ALL
SELECT 
    '新model_group记录数', COUNT(*) FROM model_group
UNION ALL
SELECT 
    '新model_instance记录数', COUNT(*) FROM model_instance
UNION ALL
SELECT 
    '新instance_health_status记录数', COUNT(*) FROM instance_health_status;
