-- 清理旧的学习记录（保留结构）
DELETE FROM zhixue_learning.learning_records WHERE user_id IN (15, 18);

-- 插入用户15的学习记录（关联到具体题目）
INSERT INTO zhixue_learning.learning_records (user_id, problem_id, learning_type, content_id, duration_seconds, score, feedback, learning_date, time_spent_ms, debug_count, attempts, status) VALUES
-- 已解决的题目
(15, 2, 'problem_solving', NULL, 300, 100.00, '完美通过！', DATE_SUB(CURDATE(), INTERVAL 7 DAY), 180000, 0, 1, 'solved'),
(15, 3, 'problem_solving', NULL, 450, 95.00, '代码简洁高效', DATE_SUB(CURDATE(), INTERVAL 6 DAY), 270000, 1, 2, 'solved'),
(15, 4, 'problem_solving', NULL, 200, 100.00, '一次通过', DATE_SUB(CURDATE(), INTERVAL 5 DAY), 120000, 0, 1, 'solved'),
(15, 13, 'problem_solving', NULL, 180, 100.00, '动态规划入门', DATE_SUB(CURDATE(), INTERVAL 4 DAY), 108000, 0, 1, 'solved'),
(15, 16, 'problem_solving', NULL, 150, 100.00, '递归解法', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 90000, 0, 1, 'solved'),
-- 尝试中的题目
(15, 5, 'problem_solving', NULL, 600, 60.00, '部分测试用例通过', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 360000, 2, 3, 'attempted'),
(15, 6, 'problem_solving', NULL, 480, 45.00, '需要优化算法', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 288000, 3, 4, 'attempted'),
-- 知识学习记录
(15, NULL, 'knowledge_learning', 1, 1200, NULL, '学习了数组基础知识', DATE_SUB(CURDATE(), INTERVAL 10 DAY), 720000, 0, 1, 'solved'),
(15, NULL, 'knowledge_learning', 2, 1800, NULL, '学习了链表操作', DATE_SUB(CURDATE(), INTERVAL 9 DAY), 1080000, 0, 1, 'solved'),
(15, NULL, 'knowledge_learning', 3, 900, NULL, '学习了栈的应用', DATE_SUB(CURDATE(), INTERVAL 8 DAY), 540000, 0, 1, 'solved'),
-- 复习记录
(15, 2, 'review', NULL, 60, NULL, '复习两数之和', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 36000, 0, 1, 'solved'),
-- 评估记录
(15, NULL, 'assessment', 1, 1800, 85.00, '算法基础评估', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 1080000, 0, 1, 'solved');

-- 插入用户18的学习记录
INSERT INTO zhixue_learning.learning_records (user_id, problem_id, learning_type, content_id, duration_seconds, score, feedback, learning_date, time_spent_ms, debug_count, attempts, status) VALUES
-- 已解决的题目
(18, 2, 'problem_solving', NULL, 420, 90.00, '通过', DATE_SUB(CURDATE(), INTERVAL 5 DAY), 252000, 1, 2, 'solved'),
(18, 4, 'problem_solving', NULL, 350, 100.00, '完美解答', DATE_SUB(CURDATE(), INTERVAL 4 DAY), 210000, 0, 1, 'solved'),
(18, 16, 'problem_solving', NULL, 200, 95.00, '递归实现', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 120000, 0, 1, 'solved'),
-- 尝试中的题目
(18, 3, 'problem_solving', NULL, 500, 30.00, '需要理解链表结构', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 300000, 2, 3, 'attempted'),
(18, 5, 'problem_solving', NULL, 700, 20.00, '思路正确但实现有误', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 420000, 4, 5, 'attempted'),
-- 知识学习记录
(18, NULL, 'knowledge_learning', 1, 1500, NULL, '学习了基础算法', DATE_SUB(CURDATE(), INTERVAL 7 DAY), 900000, 0, 1, 'solved'),
(18, NULL, 'knowledge_learning', 2, 2000, NULL, '学习了数据结构基础', DATE_SUB(CURDATE(), INTERVAL 6 DAY), 1200000, 0, 1, 'solved');

-- 更新用户经验值和等级
UPDATE zhixue_user.users SET 
    experience_points = 650,
    learning_level = 3,
    achievement_score = 120,
    daily_streak = 7,
    last_active_date = CURDATE()
WHERE id = 15;

UPDATE zhixue_user.users SET 
    experience_points = 280,
    learning_level = 2,
    achievement_score = 50,
    daily_streak = 5,
    last_active_date = CURDATE()
WHERE id = 18;

-- 插入用户成就记录
DELETE FROM zhixue_user.user_achievements WHERE user_id IN (15, 18);

-- 用户15的成就（部分已解锁）
INSERT INTO zhixue_user.user_achievements (user_id, achievement_id, progress_current, progress_target, unlocked_at) VALUES
-- 已解锁的成就
(15, 1, 1, 1, DATE_SUB(CURDATE(), INTERVAL 10 DAY)),
(15, 2, 1, 1, DATE_SUB(CURDATE(), INTERVAL 7 DAY)),
(15, 3, 7, 7, DATE_SUB(CURDATE(), INTERVAL 1 DAY)),
(15, 5, 1, 1, DATE_SUB(CURDATE(), INTERVAL 7 DAY)),
-- 进行中的成就
(15, 4, 5, 100, NULL),
(15, 6, 30, 120, NULL),
(15, 7, 0, 1, NULL),
(15, 8, 1, 1, NULL);

-- 用户18的成就
INSERT INTO zhixue_user.user_achievements (user_id, achievement_id, progress_current, progress_target, unlocked_at) VALUES
-- 已解锁的成就
(18, 1, 1, 1, DATE_SUB(CURDATE(), INTERVAL 7 DAY)),
(18, 2, 1, 1, DATE_SUB(CURDATE(), INTERVAL 5 DAY)),
(18, 5, 1, 1, DATE_SUB(CURDATE(), INTERVAL 4 DAY)),
-- 进行中的成就
(18, 3, 5, 7, NULL),
(18, 4, 3, 100, NULL),
(18, 6, 20, 120, NULL),
(18, 7, 0, 1, NULL),
(18, 8, 0, 1, NULL);

-- 插入学习路径
DELETE FROM zhixue_learning.learning_path_steps;
DELETE FROM zhixue_learning.learning_paths;

-- 学习路径1：算法入门
INSERT INTO zhixue_learning.learning_paths (user_id, name, description, goal, total_steps, current_step, status, progress_percentage, estimated_completion_hours, start_date, target_days, daily_minutes, focus_areas, progress) VALUES
(15, '算法入门之路', '从零开始学习基础算法，掌握数组、链表、栈等基本数据结构', '能够独立解决简单和中等难度的算法题目', 5, 3, 'active', 60.00, 20.00, DATE_SUB(CURDATE(), INTERVAL 10 DAY), 30, 60, '["数组","链表","栈","递归"]', 60);

SET @path1_id = LAST_INSERT_ID();

INSERT INTO zhixue_learning.learning_path_steps (learning_path_id, step_number, step_type, problem_id, title, description, estimated_time_minutes, status, score) VALUES
(@path1_id, 1, 'knowledge', NULL, '数组基础', '学习数组的基本概念和操作', 60, 'completed', NULL),
(@path1_id, 2, 'problem', 2, '两数之和', '使用哈希表解决两数之和问题', 30, 'completed', 100.00),
(@path1_id, 3, 'knowledge', NULL, '链表基础', '学习链表的结构和基本操作', 60, 'completed', NULL),
(@path1_id, 4, 'problem', 3, '反转链表', '实现链表反转', 45, 'in_progress', NULL),
(@path1_id, 5, 'assessment', NULL, '阶段测试', '测试数组与链表知识掌握程度', 30, 'pending', NULL);

-- 学习路径2：数据结构精讲
INSERT INTO zhixue_learning.learning_paths (user_id, name, description, goal, total_steps, current_step, status, progress_percentage, estimated_completion_hours, start_date, target_days, daily_minutes, focus_areas, progress) VALUES
(15, '数据结构精讲', '深入学习栈、队列、树等数据结构', '熟练掌握常用数据结构及其应用场景', 6, 2, 'active', 33.00, 25.00, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 45, 60, '["栈","队列","树","图"]', 33);

SET @path2_id = LAST_INSERT_ID();

INSERT INTO zhixue_learning.learning_path_steps (learning_path_id, step_number, step_type, problem_id, title, description, estimated_time_minutes, status, score) VALUES
(@path2_id, 1, 'knowledge', NULL, '栈与队列', '学习栈和队列的原理与应用', 90, 'completed', NULL),
(@path2_id, 2, 'problem', 4, '有效的括号', '使用栈解决括号匹配问题', 30, 'completed', 100.00),
(@path2_id, 3, 'knowledge', NULL, '二叉树基础', '学习二叉树的遍历方式', 90, 'in_progress', NULL),
(@path2_id, 4, 'problem', 16, '二叉树的最大深度', '计算二叉树的最大深度', 45, 'pending', NULL),
(@path2_id, 5, 'problem', 12, '二叉树的层序遍历', '实现二叉树的层序遍历', 60, 'pending', NULL),
(@path2_id, 6, 'assessment', NULL, '数据结构综合测试', '检验数据结构学习成果', 45, 'pending', NULL);

-- 学习路径3：动态规划专题（用户18）
INSERT INTO zhixue_learning.learning_paths (user_id, name, description, goal, total_steps, current_step, status, progress_percentage, estimated_completion_hours, start_date, target_days, daily_minutes, focus_areas, progress) VALUES
(18, '动态规划专题', '系统学习动态规划思想', '能够识别并解决动态规划问题', 4, 1, 'active', 25.00, 15.00, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 20, 90, '["动态规划","递归","记忆化"]', 25);

SET @path3_id = LAST_INSERT_ID();

INSERT INTO zhixue_learning.learning_path_steps (learning_path_id, step_number, step_type, problem_id, title, description, estimated_time_minutes, status, score) VALUES
(@path3_id, 1, 'knowledge', NULL, '动态规划入门', '理解动态规划的基本概念', 60, 'completed', NULL),
(@path3_id, 2, 'problem', 13, '爬楼梯', '经典的动态规划入门题', 30, 'in_progress', NULL),
(@path3_id, 3, 'problem', 5, '最长回文子串', '使用动态规划解决回文问题', 60, 'pending', NULL),
(@path3_id, 4, 'assessment', NULL, '动态规划测试', '检验动态规划学习成果', 30, 'pending', NULL);

-- 插入代码提交记录
DELETE FROM zhixue_problem.code_submissions;

-- 用户15的提交记录
INSERT INTO zhixue_problem.code_submissions (user_id, problem_id, code, language, execution_result, execution_time_ms, execution_memory_kb, status, score, feedback, is_best_solution) VALUES
-- 成功提交
(15, 2, 'def twoSum(nums, target):\n    hashmap = {}\n    for i, num in enumerate(nums):\n        if target - num in hashmap:\n            return [hashmap[target - num], i]\n        hashmap[num] = i\n    return []', 'python', '{"passed": 3, "total": 3, "details": [{"input": "[2,7,11,15], 9", "expected": "[0,1]", "actual": "[0,1]", "passed": true}, {"input": "[3,2,4], 6", "expected": "[1,2]", "actual": "[1,2]", "passed": true}, {"input": "[3,3], 6", "expected": "[0,1]", "actual": "[0,1]", "passed": true}]}', 45, 1024, 'success', 100.00, '所有测试用例通过！', 1),
(15, 3, 'def reverseList(head):\n    prev = None\n    curr = head\n    while curr:\n        next_temp = curr.next\n        curr.next = prev\n        prev = curr\n        curr = next_temp\n    return prev', 'python', '{"passed": 3, "total": 3}', 38, 2048, 'success', 95.00, '代码简洁高效', 0),
(15, 4, 'def isValid(s):\n    stack = []\n    mapping = {")": "(", "}": "{", "]": "["}\n    for char in s:\n        if char in mapping:\n            top_element = stack.pop() if stack else "#"\n            if mapping[char] != top_element:\n                return False\n        else:\n            stack.append(char)\n    return not stack', 'python', '{"passed": 4, "total": 4}', 28, 512, 'success', 100.00, '完美解答！', 1),
(15, 13, 'def climbStairs(n):\n    if n <= 2:\n        return n\n    a, b = 1, 2\n    for _ in range(3, n + 1):\n        a, b = b, a + b\n    return b', 'python', '{"passed": 3, "total": 3}', 15, 256, 'success', 100.00, '空间优化很好！', 1),
(15, 16, 'def maxDepth(root):\n    if not root:\n        return 0\n    return 1 + max(maxDepth(root.left), maxDepth(root.right))', 'python', '{"passed": 3, "total": 3}', 32, 512, 'success', 100.00, '递归解法清晰', 0),
-- 失败提交
(15, 5, 'def longestPalindrome(s):\n    return s[0] if s else ""', 'python', '{"passed": 1, "total": 3, "failed_cases": [{"input": "babad", "expected": "bab", "actual": "b"}]}', 5, 128, 'failed', 33.33, '只通过了基础测试用例', 0),
(15, 6, 'def threeSum(nums):\n    return []', 'python', '{"passed": 0, "total": 3}', 2, 64, 'failed', 0.00, '需要实现完整逻辑', 0);

-- 用户18的提交记录
INSERT INTO zhixue_problem.code_submissions (user_id, problem_id, code, language, execution_result, execution_time_ms, execution_memory_kb, status, score, feedback, is_best_solution) VALUES
(18, 2, 'def twoSum(nums, target):\n    for i in range(len(nums)):\n        for j in range(i+1, len(nums)):\n            if nums[i] + nums[j] == target:\n                return [i, j]\n    return []', 'python', '{"passed": 3, "total": 3}', 120, 512, 'success', 90.00, '暴力解法可以通过，建议优化', 0),
(18, 4, 'def isValid(s):\n    stack = []\n    pairs = {")": "(", "]": "[", "}": "{"}\n    for c in s:\n        if c in pairs:\n            if not stack or stack.pop() != pairs[c]:\n                return False\n        else:\n            stack.append(c)\n    return len(stack) == 0', 'python', '{"passed": 4, "total": 4}', 25, 512, 'success', 100.00, '完美解答！', 1),
(18, 16, 'def maxDepth(root):\n    if not root:\n        return 0\n    left = maxDepth(root.left)\n    right = maxDepth(root.right)\n    return max(left, right) + 1', 'python', '{"passed": 3, "total": 3}', 35, 512, 'success', 95.00, '递归解法正确', 0),
-- 失败提交
(18, 3, 'def reverseList(head):\n    return head', 'python', '{"passed": 1, "total": 3}', 5, 64, 'failed', 33.33, '未正确实现反转', 0),
(18, 5, 'def longestPalindrome(s):\n    n = len(s)\n    for i in range(n):\n        for j in range(n-1, i, -1):\n            if s[i] == s[j]:\n                return s[i:j+1]\n    return s[0] if s else ""', 'python', '{"passed": 1, "total": 3}', 150, 256, 'failed', 33.33, '逻辑有误，需要检查回文条件', 0);
