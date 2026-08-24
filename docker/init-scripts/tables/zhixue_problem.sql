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
DROP TABLE IF EXISTS `code_submissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `code_submissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID，外键',
  `problem_id` bigint NOT NULL COMMENT '题目ID，外键',
  `code` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提交的代码',
  `language` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '编程语言',
  `execution_result` json DEFAULT NULL COMMENT '执行结果',
  `execution_time_ms` int DEFAULT NULL COMMENT '执行时间（毫秒）',
  `execution_memory_kb` int DEFAULT NULL COMMENT '执行内存（KB）',
  `status` enum('pending','running','success','failed','error','timeout') COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '执行状态',
  `score` decimal(5,2) DEFAULT '0.00' COMMENT '得分',
  `feedback` text COLLATE utf8mb4_unicode_ci COMMENT '反馈信息',
  `is_best_solution` tinyint(1) DEFAULT '0' COMMENT '是否为最优解',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `problem_id` (`problem_id`),
  KEY `idx_user_problem` (`user_id`,`problem_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `code_submissions_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `zhixue_user`.`users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `code_submissions_ibfk_2` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码提交记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `after_code_submission_insert` AFTER INSERT ON `code_submissions` FOR EACH ROW BEGIN
    CALL UpdateProblemStats(NEW.problem_id);
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `after_code_submission_update` AFTER UPDATE ON `code_submissions` FOR EACH ROW BEGIN
    IF NEW.status = 'success' AND OLD.status != 'success' THEN
        INSERT INTO zhixue_learning.learning_records (
            user_id, problem_id, learning_type, duration_seconds, score, feedback, learning_date
        )
        VALUES (
                   NEW.user_id,
                   NEW.problem_id,
                   'problem_solving',
                   0, -- 时长需要从其他地方获取
                   NEW.score,
                   NEW.feedback,
                   CURDATE()
               );
    END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `problem_categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `problem_categories` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '分类描述',
  `parent_id` int DEFAULT NULL COMMENT '父分类ID',
  `sort_order` int DEFAULT '0' COMMENT '排序序号',
  `icon_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标URL',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否激活',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_sort_order` (`sort_order`),
  CONSTRAINT `problem_categories_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `problem_categories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目分类表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `problems`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `problems` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目标题',
  `description` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目描述',
  `difficulty` enum('easy','medium','hard') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '难度等级',
  `category_id` int DEFAULT NULL COMMENT '分类ID，外键',
  `tags` json DEFAULT NULL COMMENT '标签',
  `initial_code` text COLLATE utf8mb4_unicode_ci COMMENT '初始代码',
  `solution_code` text COLLATE utf8mb4_unicode_ci COMMENT '参考答案',
  `test_cases` json NOT NULL COMMENT '测试用例，JSON格式',
  `time_limit_ms` int DEFAULT '2000' COMMENT '时间限制（毫秒）',
  `memory_limit_mb` int DEFAULT '256' COMMENT '内存限制（MB）',
  `points_awarded` int DEFAULT '10' COMMENT '完成奖励积分',
  `experience_awarded` int DEFAULT '100' COMMENT '完成奖励经验',
  `hint_text` text COLLATE utf8mb4_unicode_ci COMMENT '提示文本',
  `explanation_text` text COLLATE utf8mb4_unicode_ci COMMENT '解释文本',
  `related_problems` json DEFAULT NULL COMMENT '相关题目',
  `is_public` tinyint(1) DEFAULT '1' COMMENT '是否公开',
  `creator_id` bigint DEFAULT NULL COMMENT '创建者ID，外键',
  `review_status` enum('pending','approved','rejected') COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '审核状态',
  `approved_by` bigint DEFAULT NULL COMMENT '审核人ID，外键',
  `approved_at` timestamp NULL DEFAULT NULL COMMENT '审核时间',
  `view_count` int DEFAULT '0' COMMENT '查看次数',
  `submit_count` int DEFAULT '0' COMMENT '提交次数',
  `success_rate` decimal(5,2) DEFAULT '0.00' COMMENT '通过率',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间，软删除',
  PRIMARY KEY (`id`),
  KEY `idx_difficulty` (`difficulty`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_created_at` (`created_at`),
  FULLTEXT KEY `ft_title_description` (`title`,`description`),
  CONSTRAINT `problems_ibfk_1` FOREIGN KEY (`category_id`) REFERENCES `problem_categories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
