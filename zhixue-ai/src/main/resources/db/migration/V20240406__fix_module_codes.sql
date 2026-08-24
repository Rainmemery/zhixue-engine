-- 修复模块代码不一致问题
-- 将旧的模块代码更新为新的统一模块代码

-- 1. 更新 QUESTIONER -> intelligent_question
UPDATE `rag_config` 
SET `config_key` = 'intelligent_question', 
    `description` = '智能提问模块RAG配置'
WHERE `config_type` = 'MODULE' AND `config_key` = 'QUESTIONER';

-- 2. 更新 CHAT -> ai_chat
UPDATE `rag_config` 
SET `config_key` = 'ai_chat', 
    `description` = 'AI智能问答模块RAG配置'
WHERE `config_type` = 'MODULE' AND `config_key` = 'CHAT';

-- 3. 更新 CODE_EXPLAIN -> code_explain
UPDATE `rag_config` 
SET `config_key` = 'code_explain', 
    `description` = '代码解释模块RAG配置'
WHERE `config_type` = 'MODULE' AND `config_key` = 'CODE_EXPLAIN';

-- 4. 更新 CODE_REVIEW -> code_review
UPDATE `rag_config` 
SET `config_key` = 'code_review', 
    `description` = '代码评审模块RAG配置'
WHERE `config_type` = 'MODULE' AND `config_key` = 'CODE_REVIEW';

-- 5. 更新 COLLABORATE -> code_collaborate
UPDATE `rag_config` 
SET `config_key` = 'code_collaborate', 
    `description` = '代码协作模块RAG配置'
WHERE `config_type` = 'MODULE' AND `config_key` = 'COLLABORATE';
