USE zhixue_ai;

-- 1. 清除所有默认标记
UPDATE model_group SET is_default = FALSE;

-- 2. 只设置"默认Ollama组"为默认组
UPDATE model_group SET is_default = TRUE WHERE name = '默认Ollama组';

-- 3. 验证修复结果
SELECT '=== 修复后的默认组 ===' AS info;
SELECT id, name, is_default, enabled, priority
FROM model_group
WHERE is_default = TRUE;

SELECT '' AS line;
SELECT '✅ 默认组设置完成' AS status;