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
DROP TABLE IF EXISTS `achievements`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `achievements` (
  `id` int NOT NULL AUTO_INCREMENT,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '成就代码，唯一',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '成就名称',
  `description` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '成就描述',
  `icon_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标URL',
  `category` enum('learning','coding','persistence','mastery','social','challenge') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '成就分类',
  `rarity` enum('common','uncommon','rare','epic','legendary') COLLATE utf8mb4_unicode_ci DEFAULT 'common' COMMENT '稀有度',
  `points_awarded` int DEFAULT '0' COMMENT '奖励积分',
  `coins_awarded` int DEFAULT '0' COMMENT '奖励积分',
  `badge_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '徽章URL',
  `condition_type` enum('problem_solved','streak','score','time_spent','custom','combo') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '条件类型',
  `condition_config` json NOT NULL COMMENT '条件配置',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否激活',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_category` (`category`),
  KEY `idx_rarity` (`rarity`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成就表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_achievements`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_achievements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID，外键',
  `achievement_id` int NOT NULL COMMENT '成就ID',
  `progress_current` int DEFAULT '0' COMMENT '当前进度',
  `progress_target` int DEFAULT '1' COMMENT '目标进度',
  `unlocked_at` timestamp NULL DEFAULT NULL COMMENT '解锁时间',
  `notification_sent` tinyint(1) DEFAULT '0' COMMENT '通知是否已发送',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_achievement` (`user_id`,`achievement_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_unlocked` (`unlocked_at`),
  KEY `idx_progress` (`progress_current`,`progress_target`),
  CONSTRAINT `user_achievements_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户成就表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_learning_stats`;
/*!50001 DROP VIEW IF EXISTS `user_learning_stats`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `user_learning_stats` AS SELECT 
 1 AS `user_id`,
 1 AS `username`,
 1 AS `learning_level`,
 1 AS `experience_points`,
 1 AS `coins`,
 1 AS `total_learning_records`,
 1 AS `total_submissions`,
 1 AS `successful_submissions`,
 1 AS `avg_submission_score`,
 1 AS `last_learning_date`*/;
SET character_set_client = @saved_cs_client;
DROP TABLE IF EXISTS `user_profiles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID，外键',
  `programming_language` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'python' COMMENT '编程语言偏好',
  `difficulty_preference` enum('easy','medium','hard','adaptive') COLLATE utf8mb4_unicode_ci DEFAULT 'adaptive' COMMENT '难度偏好',
  `daily_goal_minutes` int DEFAULT '60' COMMENT '每日学习目标分钟数',
  `learning_style` enum('visual','auditory','reading_writing','kinesthetic') COLLATE utf8mb4_unicode_ci DEFAULT 'visual' COMMENT '学习风格',
  `weekly_learning_days` int DEFAULT '5' COMMENT '每周学习天数',
  `preferred_topics` json DEFAULT NULL COMMENT '偏好主题',
  `weak_areas` json DEFAULT NULL COMMENT '薄弱知识点',
  `learning_goals` text COLLATE utf8mb4_unicode_ci COMMENT '学习目标',
  `notification_preferences` json DEFAULT NULL COMMENT '通知偏好设置',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `user_profiles_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户档案表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名，唯一',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱，唯一',
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希值',
  `real_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '真实姓名',
  `avatar_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `school` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学校',
  `major` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '专业',
  `grade` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '年级',
  `learning_level` int DEFAULT '1' COMMENT '学习等级',
  `experience_points` int DEFAULT '0' COMMENT '经验值',
  `coins` int DEFAULT '0' COMMENT '积分',
  `achievement_score` int DEFAULT '0' COMMENT '成就分数',
  `daily_streak` int DEFAULT '0' COMMENT '连续学习天数',
  `last_active_date` date DEFAULT NULL COMMENT '最后活跃日期',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否活跃',
  `email_verified` tinyint(1) DEFAULT '0' COMMENT '邮箱是否已验证',
  `phone_verified` tinyint(1) DEFAULT '0' COMMENT '手机号是否已验证',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间，软删除',
  `role` enum('student','teacher','admin') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'student' COMMENT '用户类型',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  KEY `idx_username` (`username`),
  KEY `idx_email` (`email`),
  KEY `idx_learning_level` (`learning_level`),
  KEY `idx_experience_points` (`experience_points`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50001 DROP VIEW IF EXISTS `user_learning_stats`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `user_learning_stats` AS select `u`.`id` AS `user_id`,`u`.`username` AS `username`,`u`.`learning_level` AS `learning_level`,`u`.`experience_points` AS `experience_points`,`u`.`coins` AS `coins`,count(`lr`.`id`) AS `total_learning_records`,count(`cs`.`id`) AS `total_submissions`,sum((case when (`cs`.`status` = 'success') then 1 else 0 end)) AS `successful_submissions`,avg(`cs`.`score`) AS `avg_submission_score`,max(`lr`.`learning_date`) AS `last_learning_date` from ((`users` `u` left join `zhixue_learning`.`learning_records` `lr` on((`u`.`id` = `lr`.`user_id`))) left join `zhixue_problem`.`code_submissions` `cs` on((`u`.`id` = `cs`.`user_id`))) group by `u`.`id`,`u`.`username`,`u`.`learning_level`,`u`.`experience_points`,`u`.`coins` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
