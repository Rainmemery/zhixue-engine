USE zhixue_ai;

SELECT '=== 模型组列表 ===' AS info;
SELECT
    id,
    name,
    model_type,
    scheduling_strategy,
    enabled,
    is_default,
    priority,
    created_at
FROM model_group
ORDER BY priority DESC;

SELECT '' AS separator;

SELECT '=== 模型实例列表 ===' AS info;
SELECT
    i.id,
    i.name AS instance_name,
    g.name AS group_name,
    i.model_name,
    i.api_endpoint,
    i.status,
    i.max_concurrent,
    i.weight,
    i.created_at
FROM model_instance i
JOIN model_group g ON i.group_id = g.id
ORDER BY g.priority DESC, i.weight DESC;

SELECT '' AS separator;

SELECT '=== 统计信息 ===' AS info;
SELECT
    CONCAT('模型组总数: ', COUNT(*)) AS stat
FROM model_group
UNION ALL
SELECT
    CONCAT('模型实例总数: ', COUNT(*))
FROM model_instance
UNION ALL
SELECT
    CONCAT('健康实例数: ', COUNT(*))
FROM model_instance
WHERE status = 'HEALTHY';