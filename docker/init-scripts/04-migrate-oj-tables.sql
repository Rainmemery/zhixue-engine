USE zhixue_problem;

SET FOREIGN_KEY_CHECKS=0;

INSERT IGNORE INTO zhixue_problem (
    id, title, title_en, description, input_description, output_description,
    hint, source, difficulty, problem_type, category_id,
    time_limit_ms, memory_limit_mb, template_code, solution_code,
    is_public, view_count, submit_count, accepted_count, acceptance_rate,
    sort_order, created_by, created_at, updated_at, deleted_at
)
SELECT
    id,
    title,
    NULL,
    description,
    NULL,
    NULL,
    NULL,
    NULL,
    difficulty,
    'traditional',
    category_id,
    time_limit_ms,
    memory_limit_mb,
    CASE WHEN initial_code IS NOT NULL THEN JSON_OBJECT('java', initial_code) ELSE NULL END,
    CASE WHEN solution_code IS NOT NULL THEN JSON_OBJECT('java', solution_code) ELSE NULL END,
    COALESCE(is_public, 1),
    view_count,
    submit_count,
    0,
    success_rate,
    0,
    creator_id,
    created_at,
    updated_at,
    deleted_at
FROM problems
WHERE deleted_at IS NULL;

INSERT IGNORE INTO zhixue_problem_category (
    id, name, name_en, description, icon, parent_id, sort_order, problem_count, is_active, created_at
)
SELECT
    id,
    name,
    NULL,
    description,
    icon_url,
    parent_id,
    sort_order,
    0,
    is_active,
    created_at
FROM problem_categories;

INSERT IGNORE INTO zhixue_submission (
    id, user_id, problem_id, language, code, code_length, status, score,
    total_time_ms, max_memory_kb, passed_count, total_count,
    error_message, is_contest, created_at, judged_at
)
SELECT
    id,
    user_id,
    problem_id,
    language,
    code,
    CHAR_LENGTH(code),
    CASE status
        WHEN 'pending' THEN 'pending'
        WHEN 'running' THEN 'judging'
        WHEN 'success' THEN 'accepted'
        WHEN 'failed' THEN 'wrong_answer'
        WHEN 'error' THEN 'runtime_error'
        WHEN 'timeout' THEN 'time_limit_exceeded'
    END,
    score,
    execution_time_ms,
    execution_memory_kb,
    0,
    0,
    feedback,
    0,
    created_at,
    CASE WHEN status IN ('success', 'failed', 'error', 'timeout') THEN created_at ELSE NULL END
FROM code_submissions;

INSERT IGNORE INTO zhixue_problem_tag (name, name_en, color, problem_count)
SELECT DISTINCT jt.tag_name, NULL, NULL, 0
FROM problems,
     JSON_TABLE(tags, '$[*]' COLUMNS (tag_name VARCHAR(50) PATH '$')) jt
WHERE tags IS NOT NULL
  AND deleted_at IS NULL;

INSERT IGNORE INTO zhixue_problem_tag_rel (problem_id, tag_id)
SELECT p.id, t.id
FROM problems p,
     JSON_TABLE(p.tags, '$[*]' COLUMNS (tag_name VARCHAR(50) PATH '$')) jt
JOIN zhixue_problem_tag t ON t.name = jt.tag_name
WHERE p.tags IS NOT NULL
  AND p.deleted_at IS NULL;

UPDATE zhixue_problem_tag t
SET problem_count = (
    SELECT COUNT(*) FROM zhixue_problem_tag_rel r WHERE r.tag_id = t.id
);

SET FOREIGN_KEY_CHECKS=1;
