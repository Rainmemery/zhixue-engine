/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `learning_path_steps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_path_steps` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `learning_path_id` bigint NOT NULL COMMENT '学习路径ID，外键',
  `step_number` int NOT NULL COMMENT '步骤序号',
  `step_type` enum('problem','knowledge','review','assessment','project') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '步骤类型',
  `problem_id` bigint DEFAULT NULL COMMENT '题目ID，外键',
  `knowledge_point_id` bigint DEFAULT NULL COMMENT '知识点ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '步骤标题',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '描述',
  `prerequisites` json DEFAULT NULL COMMENT '前置步骤ID',
  `estimated_time_minutes` int NOT NULL DEFAULT '120' COMMENT '预估时长（分钟）',
  `actual_duration_minutes` int DEFAULT NULL COMMENT '实际时长（分钟）',
  `status` enum('pending','in_progress','completed','skipped') COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '状态',
  `completed_at` timestamp NULL DEFAULT NULL COMMENT '完成时间',
  `score` decimal(5,2) DEFAULT NULL COMMENT '得分',
  `feedback` text COLLATE utf8mb4_unicode_ci COMMENT '反馈',
  `resources` json DEFAULT NULL COMMENT '学习资源',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_path_step` (`learning_path_id`,`step_number`),
  KEY `problem_id` (`problem_id`),
  KEY `idx_learning_path_step` (`learning_path_id`,`step_number`),
  KEY `idx_status` (`status`),
  CONSTRAINT `learning_path_steps_ibfk_1` FOREIGN KEY (`learning_path_id`) REFERENCES `learning_paths` (`id`) ON DELETE CASCADE,
  CONSTRAINT `learning_path_steps_ibfk_2` FOREIGN KEY (`problem_id`) REFERENCES `zhixue_problem`.`problems` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径步骤表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `learning_paths`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_paths` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID，外键',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '学习路径名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '描述',
  `goal` text COLLATE utf8mb4_unicode_ci COMMENT '学习目标',
  `total_steps` int DEFAULT '0' COMMENT '总步数',
  `current_step` int DEFAULT '0' COMMENT '当前步数',
  `status` enum('active','completed','paused','abandoned') COLLATE utf8mb4_unicode_ci DEFAULT 'active' COMMENT '状态',
  `progress_percentage` decimal(5,2) DEFAULT '0.00' COMMENT '进度百分比',
  `estimated_completion_hours` decimal(6,2) DEFAULT NULL COMMENT '预估完成小时数',
  `actual_completion_hours` decimal(6,2) DEFAULT NULL COMMENT '实际完成小时数',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `target_completion_date` date DEFAULT NULL COMMENT '目标完成日期',
  `actual_completion_date` date DEFAULT NULL COMMENT '实际完成日期',
  `satisfaction_rating` int DEFAULT NULL COMMENT '满意度评分',
  `feedback_text` text COLLATE utf8mb4_unicode_ci COMMENT '反馈文本',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `target_days` int NOT NULL DEFAULT '30' COMMENT '目标天数',
  `daily_minutes` int NOT NULL DEFAULT '60' COMMENT '每日学习分钟数',
  `focus_areas` json DEFAULT NULL COMMENT '专注领域',
  `progress` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`,`status`),
  KEY `idx_progress` (`progress_percentage`),
  KEY `idx_target_date` (`target_completion_date`),
  CONSTRAINT `learning_paths_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `zhixue_user`.`users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `learning_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID，外键',
  `problem_id` bigint DEFAULT NULL COMMENT '题目ID，外键',
  `learning_type` enum('problem_solving','knowledge_learning','review','assessment') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '学习类型',
  `content_id` bigint DEFAULT NULL COMMENT '内容ID',
  `duration_seconds` int DEFAULT '0' COMMENT '学习时长（秒）',
  `score` decimal(5,2) DEFAULT NULL COMMENT '得分',
  `feedback` text COLLATE utf8mb4_unicode_ci COMMENT '反馈',
  `learning_date` date NOT NULL DEFAULT (curdate()) COMMENT '学习日期',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `submission_id` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提交记录ID',
  `time_spent_ms` int NOT NULL DEFAULT '1000' COMMENT '运行耗时(ms)',
  `debug_count` int NOT NULL DEFAULT '0' COMMENT '调试次数',
  `attempts` int NOT NULL DEFAULT '1' COMMENT '尝试次数',
  `first_solved_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次解决时间',
  `last_attempt_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最终尝试时间',
  `status` enum('solved','attempted','unsolved') COLLATE utf8mb4_unicode_ci DEFAULT 'unsolved' COMMENT '状态',
  PRIMARY KEY (`id`),
  KEY `problem_id` (`problem_id`),
  KEY `idx_user_date` (`user_id`,`learning_date`),
  KEY `idx_learning_type` (`learning_type`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `learning_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `zhixue_user`.`users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `learning_records_ibfk_2` FOREIGN KEY (`problem_id`) REFERENCES `zhixue_problem`.`problems` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=78 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
