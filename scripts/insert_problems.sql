-- 删除旧的测试题目
DELETE FROM problems WHERE id = 1;

-- 插入样例题目
INSERT INTO problems (title, description, difficulty, category_id, tags, initial_code, solution_code, test_cases, time_limit_ms, memory_limit_mb, points_awarded, experience_awarded, hint_text, explanation_text, is_public, review_status, view_count, submit_count, success_rate) VALUES
-- 1. 两数之和 (简单, 基础算法)
('两数之和', '给定一个整数数组 nums 和一个整数目标值 target，请你在该数组中找出和为目标值 target 的那两个整数，并返回它们的数组下标。\n\n你可以假设每种输入只会对应一个答案，并且你不能使用两次相同的元素。\n\n你可以按任意顺序返回答案。\n\n示例 1：\n输入：nums = [2,7,11,15], target = 9\n输出：[0,1]\n解释：因为 nums[0] + nums[1] == 9 ，返回 [0, 1] 。\n\n示例 2：\n输入：nums = [3,2,4], target = 6\n输出：[1,2]\n\n示例 3：\n输入：nums = [3,3], target = 6\n输出：[0,1]', 'easy', 1, '["数组","哈希表"]', 
'def twoSum(nums, target):\n    # 请在此处编写你的代码\n    pass', 
'def twoSum(nums, target):\n    hashmap = {}\n    for i, num in enumerate(nums):\n        if target - num in hashmap:\n            return [hashmap[target - num], i]\n        hashmap[num] = i\n    return []', 
'[{"input": {"nums": [2,7,11,15], "target": 9}, "output": [0,1]}, {"input": {"nums": [3,2,4], "target": 6}, "output": [1,2]}, {"input": {"nums": [3,3], "target": 6}, "output": [0,1]}]',
1000, 256, 10, 100, '可以使用哈希表来优化查找过程', '使用哈希表存储已经遍历过的数字及其索引，对于每个数字，检查target减去该数字是否在哈希表中。', 1, 'approved', 1500, 800, 0.75),

-- 2. 反转链表 (简单, 数据结构)
('反转链表', '给你单链表的头节点 head ，请你反转链表，并返回反转后的链表。\n\n示例 1：\n输入：head = [1,2,3,4,5]\n输出：[5,4,3,2,1]\n\n示例 2：\n输入：head = [1,2]\n输出：[2,1]\n\n示例 3：\n输入：head = []\n输出：[]', 'easy', 2, '["链表","递归"]',
'def reverseList(head):\n    # 请在此处编写你的代码\n    pass',
'def reverseList(head):\n    prev = None\n    curr = head\n    while curr:\n        next_temp = curr.next\n        curr.next = prev\n        prev = curr\n        curr = next_temp\n    return prev',
'[{"input": {"head": [1,2,3,4,5]}, "output": [5,4,3,2,1]}, {"input": {"head": [1,2]}, "output": [2,1]}, {"input": {"head": []}, "output": []}]',
1000, 256, 10, 100, '可以使用迭代或递归两种方法', '迭代方法：使用三个指针prev、curr、next_temp，依次反转每个节点的指向。', 1, 'approved', 1200, 600, 0.80),

-- 3. 有效的括号 (简单, 数据结构)
('有效的括号', '给定一个只包括 ( ) { } [ ] 的字符串 s ，判断字符串是否有效。\n\n有效字符串需满足：\n1. 左括号必须用相同类型的右括号闭合。\n2. 左括号必须以正确的顺序闭合。\n3. 每个右括号都有一个对应的相同类型的左括号。\n\n示例 1：\n输入：s = "()"\n输出：true\n\n示例 2：\n输入：s = "()[]{}"\n输出：true\n\n示例 3：\n输入：s = "(]"\n输出：false', 'easy', 2, '["栈","字符串"]',
'def isValid(s):\n    # 请在此处编写你的代码\n    pass',
'def isValid(s):\n    stack = []\n    mapping = {")": "(", "}": "{", "]": "["}\n    for char in s:\n        if char in mapping:\n            top_element = stack.pop() if stack else "#"\n            if mapping[char] != top_element:\n                return False\n        else:\n            stack.append(char)\n    return not stack',
'[{"input": {"s": "()"}, "output": true}, {"input": {"s": "()[]{}"}, "output": true}, {"input": {"s": "(]"}, "output": false}, {"input": {"s": "([)]"}, "output": false}]',
1000, 256, 10, 100, '使用栈数据结构来匹配括号', '遍历字符串，遇到左括号入栈，遇到右括号时检查栈顶元素是否匹配。', 1, 'approved', 1000, 500, 0.85),

-- 4. 最长回文子串 (中等, 字符串)
('最长回文子串', '给你一个字符串 s，找到 s 中最长的回文子串。\n\n如果字符串的反序与原始字符串相同，则该字符串称为回文字符串。\n\n示例 1：\n输入：s = "babad"\n输出："bab"\n解释："aba" 同样是符合题意的答案。\n\n示例 2：\n输入：s = "cbbd"\n输出："bb"', 'medium', 5, '["字符串","动态规划"]',
'def longestPalindrome(s):\n    # 请在此处编写你的代码\n    pass',
'def longestPalindrome(s):\n    if not s:\n        return ""\n    start, end = 0, 0\n    for i in range(len(s)):\n        len1 = expand_around_center(s, i, i)\n        len2 = expand_around_center(s, i, i + 1)\n        max_len = max(len1, len2)\n        if max_len > end - start:\n            start = i - (max_len - 1) // 2\n            end = i + max_len // 2\n    return s[start:end + 1]\n\ndef expand_around_center(s, left, right):\n    while left >= 0 and right < len(s) and s[left] == s[right]:\n        left -= 1\n        right += 1\n    return right - left - 1',
'[{"input": {"s": "babad"}, "output": "bab"}, {"input": {"s": "cbbd"}, "output": "bb"}, {"input": {"s": "a"}, "output": "a"}]',
2000, 256, 20, 200, '可以使用中心扩展法或动态规划', '中心扩展法：以每个字符为中心向两边扩展，寻找最长回文串。', 1, 'approved', 800, 400, 0.60),

-- 5. 三数之和 (中等, 基础算法)
('三数之和', '给你一个整数数组 nums ，判断是否存在三元组 [nums[i], nums[j], nums[k]] 满足 i != j、i != k 且 j != k ，同时还满足 nums[i] + nums[j] + nums[k] == 0 。请返回所有和为 0 且不重复的三元组。\n\n示例 1：\n输入：nums = [-1,0,1,2,-1,-4]\n输出：[[-1,-1,2],[-1,0,1]]\n解释：\nnums[0] + nums[1] + nums[2] = (-1) + 0 + 1 = 0 。\nnums[1] + nums[2] + nums[4] = 0 + 1 + (-1) = 0 。\nnums[0] + nums[3] + nums[4] = (-1) + 2 + (-1) = 0 。\n不同的三元组是 [-1,0,1] 和 [-1,-1,2] 。', 'medium', 1, '["数组","双指针","排序"]',
'def threeSum(nums):\n    # 请在此处编写你的代码\n    pass',
'def threeSum(nums):\n    nums.sort()\n    result = []\n    for i in range(len(nums) - 2):\n        if i > 0 and nums[i] == nums[i - 1]:\n            continue\n        left, right = i + 1, len(nums) - 1\n        while left < right:\n            total = nums[i] + nums[left] + nums[right]\n            if total < 0:\n                left += 1\n            elif total > 0:\n                right -= 1\n            else:\n                result.append([nums[i], nums[left], nums[right]])\n                while left < right and nums[left] == nums[left + 1]:\n                    left += 1\n                while left < right and nums[right] == nums[right - 1]:\n                    right -= 1\n                left += 1\n                right -= 1\n    return result',
'[{"input": {"nums": [-1,0,1,2,-1,-4]}, "output": [[-1,-1,2],[-1,0,1]]}, {"input": {"nums": [0,1,1]}, "output": []}, {"input": {"nums": [0,0,0]}, "output": [[0,0,0]]}]',
2000, 256, 20, 200, '先排序，然后使用双指针', '排序后固定一个数，使用双指针在剩余部分寻找两个数使三数之和为0。', 1, 'approved', 600, 300, 0.55),

-- 6. 二叉树的层序遍历 (中等, 数据结构)
('二叉树的层序遍历', '给你二叉树的根节点 root ，返回其节点值的层序遍历。（即逐层地，从左到右访问所有节点）。\n\n示例 1：\n输入：root = [3,9,20,null,null,15,7]\n输出：[[3],[9,20],[15,7]]\n\n示例 2：\n输入：root = [1]\n输出：[[1]]\n\n示例 3：\n输入：root = []\n输出：[]', 'medium', 2, '["树","广度优先搜索"]',
'def levelOrder(root):\n    # 请在此处编写你的代码\n    pass',
'def levelOrder(root):\n    if not root:\n        return []\n    result = []\n    queue = [root]\n    while queue:\n        level = []\n        for _ in range(len(queue)):\n            node = queue.pop(0)\n            level.append(node.val)\n            if node.left:\n                queue.append(node.left)\n            if node.right:\n                queue.append(node.right)\n        result.append(level)\n    return result',
'[{"input": {"root": [3,9,20,null,null,15,7]}, "output": [[3],[9,20],[15,7]]}, {"input": {"root": [1]}, "output": [[1]]}, {"input": {"root": []}, "output": []}]',
2000, 256, 20, 200, '使用队列进行广度优先搜索', '使用队列存储每一层的节点，依次处理每一层。', 1, 'approved', 500, 250, 0.65),

-- 7. 爬楼梯 (简单, 动态规划)
('爬楼梯', '假设你正在爬楼梯。需要 n 阶你才能到达楼顶。\n\n每次你可以爬 1 或 2 个台阶。你有多少种不同的方法可以爬到楼顶？\n\n示例 1：\n输入：n = 2\n输出：2\n解释：有两种方法可以爬到楼顶。\n1. 1 阶 + 1 阶\n2. 2 阶\n\n示例 2：\n输入：n = 3\n输出：3\n解释：有三种方法可以爬到楼顶。\n1. 1 阶 + 1 阶 + 1 阶\n2. 1 阶 + 2 阶\n3. 2 阶 + 1 阶', 'easy', 3, '["动态规划","数学"]',
'def climbStairs(n):\n    # 请在此处编写你的代码\n    pass',
'def climbStairs(n):\n    if n <= 2:\n        return n\n    a, b = 1, 2\n    for _ in range(3, n + 1):\n        a, b = b, a + b\n    return b',
'[{"input": {"n": 2}, "output": 2}, {"input": {"n": 3}, "output": 3}, {"input": {"n": 5}, "output": 8}]',
1000, 256, 10, 100, '这是一个经典的动态规划问题', 'dp[i] = dp[i-1] + dp[i-2]，可以使用滚动变量优化空间复杂度。', 1, 'approved', 900, 450, 0.78),

-- 8. 合并K个升序链表 (困难, 数据结构)
('合并K个升序链表', '给你一个链表数组，每个链表都已经按升序排列。\n\n请你将所有链表合并到一个升序链表中，返回合并后的链表。\n\n示例 1：\n输入：lists = [[1,4,5],[1,3,4],[2,6]]\n输出：[1,1,2,3,4,4,5,6]\n解释：链表数组如下：\n[\n  1->4->5,\n  1->3->4,\n  2->6\n]\n将它们合并到一个有序链表中得到。\n1->1->2->3->4->4->5->6\n\n示例 2：\n输入：lists = []\n输出：[]', 'hard', 2, '["链表","堆","分治"]',
'def mergeKLists(lists):\n    # 请在此处编写你的代码\n    pass',
'def mergeKLists(lists):\n    import heapq\n    heap = []\n    for i, node in enumerate(lists):\n        if node:\n            heapq.heappush(heap, (node.val, i, node))\n    dummy = ListNode(0)\n    curr = dummy\n    while heap:\n        val, i, node = heapq.heappop(heap)\n        curr.next = node\n        curr = curr.next\n        if node.next:\n            heapq.heappush(heap, (node.next.val, i, node.next))\n    return dummy.next',
'[{"input": {"lists": [[1,4,5],[1,3,4],[2,6]]}, "output": [1,1,2,3,4,4,5,6]}, {"input": {"lists": []}, "output": []}, {"input": {"lists": [[]]}, "output": []}]',
3000, 256, 50, 500, '可以使用优先队列（堆）来优化', '使用最小堆存储每个链表的头节点，每次取出最小值，并将该节点的下一个节点加入堆。', 1, 'approved', 300, 150, 0.45),

-- 9. 无重复字符的最长子串 (中等, 字符串)
('无重复字符的最长子串', '给定一个字符串 s ，请你找出其中不含有重复字符的最长子串的长度。\n\n示例 1:\n输入: s = "abcabcbb"\n输出: 3 \n解释: 因为无重复字符的最长子串是 "abc"，所以其长度为 3。\n\n示例 2:\n输入: s = "bbbbb"\n输出: 1\n解释: 因为无重复字符的最长子串是 "b"，所以其长度为 1。\n\n示例 3:\n输入: s = "pwwkew"\n输出: 3\n解释: 因为无重复字符的最长子串是 "wke"，所以其长度为 3。', 'medium', 5, '["字符串","滑动窗口"]',
'def lengthOfLongestSubstring(s):\n    # 请在此处编写你的代码\n    pass',
'def lengthOfLongestSubstring(s):\n    char_index = {}\n    left = 0\n    max_len = 0\n    for right, char in enumerate(s):\n        if char in char_index and char_index[char] >= left:\n            left = char_index[char] + 1\n        char_index[char] = right\n        max_len = max(max_len, right - left + 1)\n    return max_len',
'[{"input": {"s": "abcabcbb"}, "output": 3}, {"input": {"s": "bbbbb"}, "output": 1}, {"input": {"s": "pwwkew"}, "output": 3}]',
2000, 256, 20, 200, '使用滑动窗口和哈希表', '维护一个滑动窗口，使用哈希表记录字符的最新位置，遇到重复字符时移动左边界。', 1, 'approved', 700, 350, 0.58),

-- 10. 二叉树的最大深度 (简单, 数据结构)
('二叉树的最大深度', '给定一个二叉树 root ，返回其最大深度。\n\n二叉树的最大深度是指从根节点到最远叶子节点的最长路径上的节点数。\n\n示例 1：\n输入：root = [3,9,20,null,null,15,7]\n输出：3\n\n示例 2：\n输入：root = [1,null,2]\n输出：2', 'easy', 2, '["树","递归"]',
'def maxDepth(root):\n    # 请在此处编写你的代码\n    pass',
'def maxDepth(root):\n    if not root:\n        return 0\n    return 1 + max(maxDepth(root.left), maxDepth(root.right))',
'[{"input": {"root": [3,9,20,null,null,15,7]}, "output": 3}, {"input": {"root": [1,null,2]}, "output": 2}, {"input": {"root": []}, "output": 0}]',
1000, 256, 10, 100, '使用递归或迭代方法', '递归计算左右子树的最大深度，取较大值加1。', 1, 'approved', 1100, 550, 0.82),

-- 11. 正则表达式匹配 (困难, 字符串)
('正则表达式匹配', '给你一个字符串 s 和一个字符规律 p，请你来实现一个支持 . 和 * 的正则表达式匹配。\n\n. 匹配任意单个字符\n* 匹配零个或多个前面的那一个元素\n所谓匹配，是要涵盖整个字符串s的，而不是部分字符串。\n\n示例 1：\n输入：s = "aa", p = "a"\n输出：false\n解释："a" 无法匹配 "aa" 整个字符串。\n\n示例 2：\n输入：s = "aa", p = "a*"\n输出：true\n解释：因为 * 代表可以匹配零个或多个前面的那一个元素, 在这里前面的元素就是 a。因此，字符串 "aa" 可被视为 "a" 重复了一次。\n\n示例 3：\n输入：s = "ab", p = ".*"\n输出：true\n解释：".*" 表示可匹配零个或多个（*）任意字符（.）。', 'hard', 5, '["字符串","动态规划","递归"]',
'def isMatch(s, p):\n    # 请在此处编写你的代码\n    pass',
'def isMatch(s, p):\n    m, n = len(s), len(p)\n    dp = [[False] * (n + 1) for _ in range(m + 1)]\n    dp[0][0] = True\n    for j in range(2, n + 1):\n        if p[j-1] == "*":\n            dp[0][j] = dp[0][j-2]\n    for i in range(1, m + 1):\n        for j in range(1, n + 1):\n            if p[j-1] == s[i-1] or p[j-1] == ".":\n                dp[i][j] = dp[i-1][j-1]\n            elif p[j-1] == "*":\n                dp[i][j] = dp[i][j-2]\n                if p[j-2] == s[i-1] or p[j-2] == ".":\n                    dp[i][j] = dp[i][j] or dp[i-1][j]\n    return dp[m][n]',
'[{"input": {"s": "aa", "p": "a"}, "output": false}, {"input": {"s": "aa", "p": "a*"}, "output": true}, {"input": {"s": "ab", "p": ".*"}, "output": true}]',
3000, 256, 50, 500, '使用动态规划', 'dp[i][j]表示s的前i个字符和p的前j个字符是否匹配。', 1, 'approved', 200, 100, 0.35),

-- 12. 全排列 (中等, 搜索算法)
('全排列', '给定一个不含重复数字的数组 nums ，返回其所有可能的全排列。你可以按任意顺序返回答案。\n\n示例 1：\n输入：nums = [1,2,3]\n输出：[[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]\n\n示例 2：\n输入：nums = [0,1]\n输出：[[0,1],[1,0]]\n\n示例 3：\n输入：nums = [1]\n输出：[[1]]', 'medium', 7, '["数组","回溯"]',
'def permute(nums):\n    # 请在此处编写你的代码\n    pass',
'def permute(nums):\n    result = []\n    def backtrack(path, remaining):\n        if not remaining:\n            result.append(path[:])\n            return\n        for i in range(len(remaining)):\n            path.append(remaining[i])\n            backtrack(path, remaining[:i] + remaining[i+1:])\n            path.pop()\n    backtrack([], nums)\n    return result',
'[{"input": {"nums": [1,2,3]}, "output": [[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]}, {"input": {"nums": [0,1]}, "output": [[0,1],[1,0]]}, {"input": {"nums": [1]}, "output": [[1]]}]',
2000, 256, 20, 200, '使用回溯算法', '通过交换或选择元素来生成所有排列。', 1, 'approved', 400, 200, 0.62);
