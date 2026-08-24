USE zhixue_problem;

CREATE TABLE `zhixue_problem` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目标题',
  `title_en` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '英文标题',
  `description` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目描述',
  `input_description` text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '输入描述',
  `output_description` text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '输出描述',
  `hint` text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提示',
  `source` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '题目来源',
  `difficulty` enum('easy','medium','hard') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '难度等级',
  `problem_type` enum('traditional','interactive','special_judge') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'traditional' COMMENT '题目类型',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `time_limit_ms` int NOT NULL DEFAULT 2000 COMMENT '时间限制（毫秒）',
  `memory_limit_mb` int NOT NULL DEFAULT 256 COMMENT '内存限制（MB）',
  `template_code` json DEFAULT NULL COMMENT '模板代码',
  `solution_code` json DEFAULT NULL COMMENT '参考答案',
  `is_public` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否公开',
  `view_count` int NOT NULL DEFAULT 0 COMMENT '查看次数',
  `submit_count` int NOT NULL DEFAULT 0 COMMENT '提交次数',
  `accepted_count` int NOT NULL DEFAULT 0 COMMENT '通过次数',
  `acceptance_rate` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '通过率',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序序号',
  `created_by` bigint DEFAULT NULL COMMENT '创建者ID',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_difficulty` (`difficulty`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_is_public` (`is_public`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_acceptance_rate` (`acceptance_rate`),
  FULLTEXT KEY `ft_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表';

CREATE TABLE `zhixue_problem_sample` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '样例ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `sample_input` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '样例输入',
  `sample_output` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '样例输出',
  `explanation` text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '样例解释',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序序号',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_problem_id` (`problem_id`),
  CONSTRAINT `fk_sample_problem` FOREIGN KEY (`problem_id`) REFERENCES `zhixue_problem` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目样例表';

CREATE TABLE `zhixue_problem_testcase` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '测试用例ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `testcase_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试用例名称',
  `input_file_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '输入文件路径',
  `output_file_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '输出文件路径',
  `input_file_size` bigint DEFAULT NULL COMMENT '输入文件大小（字节）',
  `output_file_size` bigint DEFAULT NULL COMMENT '输出文件大小（字节）',
  `time_limit_ms` int DEFAULT NULL COMMENT '时间限制（毫秒），覆盖题目默认值',
  `memory_limit_mb` int DEFAULT NULL COMMENT '内存限制（MB），覆盖题目默认值',
  `is_sample` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为样例用例',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序序号',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_problem_testcase_name` (`problem_id`, `testcase_name`),
  KEY `idx_problem_id` (`problem_id`),
  CONSTRAINT `fk_testcase_problem` FOREIGN KEY (`problem_id`) REFERENCES `zhixue_problem` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试数据表';

CREATE TABLE `zhixue_problem_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `name_en` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '英文分类名称',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类描述',
  `icon` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标',
  `parent_id` bigint DEFAULT NULL COMMENT '父分类ID',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序序号',
  `problem_count` int NOT NULL DEFAULT 0 COMMENT '题目数量',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目分类表';

CREATE TABLE `zhixue_problem_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `name_en` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '英文标签名称',
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签颜色',
  `problem_count` int NOT NULL DEFAULT 0 COMMENT '题目数量',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目标签表';

CREATE TABLE `zhixue_problem_tag_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_problem_tag` (`problem_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`),
  CONSTRAINT `fk_tag_rel_problem` FOREIGN KEY (`problem_id`) REFERENCES `zhixue_problem` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_tag_rel_tag` FOREIGN KEY (`tag_id`) REFERENCES `zhixue_problem_tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目-标签关联表';

CREATE TABLE `zhixue_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '提交ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `language` enum('java','cpp','c') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '编程语言',
  `code` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提交代码',
  `code_length` int NOT NULL DEFAULT 0 COMMENT '代码长度（字节）',
  `status` enum('pending','judging','accepted','wrong_answer','time_limit_exceeded','memory_limit_exceeded','runtime_error','compilation_error','system_error','output_limit_exceeded') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '判题状态',
  `score` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '得分',
  `total_time_ms` int DEFAULT NULL COMMENT '总执行时间（毫秒）',
  `max_memory_kb` int DEFAULT NULL COMMENT '最大内存使用（KB）',
  `passed_count` int NOT NULL DEFAULT 0 COMMENT '通过用例数',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '总用例数',
  `error_message` text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `is_contest` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为比赛提交',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `judged_at` timestamp NULL DEFAULT NULL COMMENT '判题完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_problem` (`user_id`, `problem_id`),
  KEY `idx_problem_id` (`problem_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提交记录表';

CREATE TABLE `zhixue_testcase_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '结果ID',
  `submission_id` bigint NOT NULL COMMENT '提交ID',
  `testcase_id` bigint DEFAULT NULL COMMENT '测试用例ID',
  `testcase_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '测试用例名称',
  `status` enum('accepted','wrong_answer','time_limit_exceeded','memory_limit_exceeded','runtime_error','system_error') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '判题结果',
  `execution_time_ms` int DEFAULT NULL COMMENT '执行时间（毫秒）',
  `memory_used_kb` int DEFAULT NULL COMMENT '内存使用（KB）',
  `actual_output` longtext COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际输出',
  `error_output` text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误输出',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_submission_id` (`submission_id`),
  CONSTRAINT `fk_testcase_result_submission` FOREIGN KEY (`submission_id`) REFERENCES `zhixue_submission` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例结果表';

CREATE TABLE `zhixue_judge_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `submission_id` bigint NOT NULL COMMENT '提交ID',
  `status` enum('queued','running','completed','failed') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'queued' COMMENT '任务状态',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `max_retry` int NOT NULL DEFAULT 3 COMMENT '最大重试次数',
  `worker_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行者ID',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '开始执行时间',
  `completed_at` timestamp NULL DEFAULT NULL COMMENT '完成时间',
  `error_message` text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_submission_id` (`submission_id`),
  KEY `idx_status_priority` (`status`, `priority` DESC),
  CONSTRAINT `fk_judge_task_submission` FOREIGN KEY (`submission_id`) REFERENCES `zhixue_submission` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='判题任务队列表';

CREATE TABLE `zhixue_user_problem_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `status` enum('not_attempted','attempted','accepted') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'not_attempted' COMMENT '解题状态',
  `best_submission_id` bigint DEFAULT NULL COMMENT '最佳提交ID',
  `best_time_ms` int DEFAULT NULL COMMENT '最佳执行时间（毫秒）',
  `best_memory_kb` int DEFAULT NULL COMMENT '最佳内存使用（KB）',
  `attempt_count` int NOT NULL DEFAULT 0 COMMENT '尝试次数',
  `accepted_at` timestamp NULL DEFAULT NULL COMMENT '通过时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_problem` (`user_id`, `problem_id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_problem_status` (`problem_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户题目状态表';
