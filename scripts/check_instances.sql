USE zhixue_ai;

SELECT '=== 模型实例 ===' AS info;
SELECT
    i.id,
    i.name,
    g.name AS group_name,
    i.model_name,
    i.api_endpoint,
    i.status,
    i.max_concurrent,
    i.weight
FROM model_instance i
JOIN model_group g ON i.group_id = g.id
ORDER BY g.priority DESC, i.weight DESC;