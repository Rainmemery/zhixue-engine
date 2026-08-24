USE zhixue_ai;

SELECT '=== 健康状态 ===' AS info;
SELECT
    h.instance_id,
    i.name,
    h.health_state,
    h.response_time_ms,
    h.consecutive_failures,
    h.consecutive_successes,
    h.last_check_time,
    h.last_error_message
FROM model_instance_health h
JOIN model_instance i ON h.instance_id = i.id;

SELECT '' AS line;

SELECT '=== 默认组详情 ===' AS info;
SELECT
    id,
    name,
    is_default,
    enabled,
    priority
FROM model_group
WHERE is_default = TRUE OR name = '默认Ollama组';