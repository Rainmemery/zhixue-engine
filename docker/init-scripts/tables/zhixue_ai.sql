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
DROP TABLE IF EXISTS `ai_model_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_model_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型类型：ollama/openai',
  `model_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `api_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API密钥（加密存储）',
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'API基础URL',
  `timeout_ms` int DEFAULT '300000' COMMENT '超时时间（毫秒）',
  `max_retries` int DEFAULT '3' COMMENT '最大重试次数',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `is_default` tinyint(1) DEFAULT '0' COMMENT '是否为默认配置',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置描述',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_model_type` (`model_type`),
  KEY `idx_is_default` (`is_default`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `custom_scheduling_strategy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `custom_scheduling_strategy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `strategy_name` varchar(100) NOT NULL COMMENT '策略名称',
  `strategy_description` varchar(500) DEFAULT NULL COMMENT '策略描述',
  `preferred_model` varchar(50) NOT NULL COMMENT '首选模型类型',
  `fallback_model` varchar(50) DEFAULT NULL COMMENT '备用模型类型',
  `condition_json` text COMMENT '策略条件JSON',
  `priority` int DEFAULT '50' COMMENT '优先级',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `is_system` tinyint(1) DEFAULT '0' COMMENT '是否系统策略',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `strategy_name` (`strategy_name`),
  KEY `idx_strategy_name` (`strategy_name`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自定义调度策略表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_chunk`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_id` bigint NOT NULL COMMENT '所属文档ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `chunk_index` int NOT NULL COMMENT '分块序号',
  `content` text NOT NULL COMMENT '分块内容',
  `vector_id` varchar(100) DEFAULT NULL COMMENT 'Milvus向量ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_knowledge_base_id` (`knowledge_base_id`),
  KEY `idx_vector_id` (`vector_id`),
  CONSTRAINT `fk_chunk_document` FOREIGN KEY (`document_id`) REFERENCES `rag_document` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_chunk_knowledge_base` FOREIGN KEY (`knowledge_base_id`) REFERENCES `rag_knowledge_base` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1528 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档分块表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_chunk_upload`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_chunk_upload` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `upload_id` varchar(64) NOT NULL COMMENT '上传唯一标识',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_type` varchar(20) NOT NULL COMMENT '文件类型',
  `file_size` bigint NOT NULL COMMENT '文件总大小',
  `total_chunks` int NOT NULL COMMENT '总分片数',
  `temp_path` varchar(500) NOT NULL COMMENT '临时存储路径',
  `knowledge_base_id` bigint NOT NULL COMMENT '知识库ID',
  `title` varchar(255) DEFAULT NULL COMMENT '文档标题',
  `created_by` bigint DEFAULT NULL COMMENT '创建者ID',
  `status` tinyint DEFAULT '0' COMMENT '状态: 0-待上传, 1-上传中, 2-已完成, 3-失败',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `upload_id` (`upload_id`),
  KEY `idx_upload_id` (`upload_id`),
  KEY `idx_knowledge_base_id` (`knowledge_base_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分片上传记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_type` varchar(50) NOT NULL COMMENT '配置类型: GLOBAL/MODULE',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` text NOT NULL COMMENT '配置值(JSON格式)',
  `description` varchar(500) DEFAULT NULL COMMENT '配置描述',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config` (`config_type`,`config_key`),
  KEY `idx_config_type` (`config_type`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RAG配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `title` varchar(500) NOT NULL COMMENT '文档标题',
  `file_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `file_type` varchar(20) DEFAULT NULL COMMENT '文件类型: PDF/DOCX/TXT/MD',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `file_path` varchar(500) DEFAULT NULL COMMENT '文件存储路径',
  `chunk_count` int DEFAULT '0' COMMENT '分块数量',
  `status` tinyint DEFAULT '0' COMMENT '状态: 0-待处理, 1-处理中, 2-已完成, 3-失败',
  `error_message` text COMMENT '错误信息',
  `created_by` bigint DEFAULT NULL COMMENT '创建者ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_base_id` (`knowledge_base_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_doc_knowledge_base` FOREIGN KEY (`knowledge_base_id`) REFERENCES `rag_knowledge_base` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文档表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_embedding_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_embedding_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_name` varchar(100) NOT NULL COMMENT '模型名称（Ollama模型名）',
  `display_name` varchar(200) DEFAULT NULL COMMENT '显示名称',
  `dimension` int NOT NULL COMMENT '向量维度',
  `description` varchar(500) DEFAULT NULL COMMENT '模型描述',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认模型: 0-否, 1-是',
  `status` tinyint DEFAULT '1' COMMENT '状态: 0-禁用, 1-启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_name` (`model_name`),
  KEY `idx_status` (`status`),
  KEY `idx_is_default` (`is_default`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Embedding模型配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_embedding_models`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_embedding_models` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_name` varchar(100) NOT NULL,
  `display_name` varchar(200) DEFAULT NULL,
  `dimension` int DEFAULT NULL,
  `description` text,
  `is_default` tinyint(1) DEFAULT '0',
  `status` tinyint(1) DEFAULT '1',
  `model_type` varchar(20) DEFAULT 'ollama' COMMENT '模型类型: ollama/openai/zhipu/aliyun',
  `api_endpoint` varchar(500) DEFAULT NULL COMMENT 'API端点URL',
  `api_key` varchar(500) DEFAULT NULL COMMENT 'API密钥',
  `provider` varchar(100) DEFAULT NULL COMMENT '提供商名称',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `model_name` (`model_name`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_knowledge_base`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_knowledge_base` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(200) NOT NULL COMMENT '知识库名称',
  `description` text COMMENT '知识库描述',
  `embedding_model_id` bigint DEFAULT NULL COMMENT '使用的Embedding模型ID',
  `milvus_collection` varchar(100) DEFAULT 'rag_vectors' COMMENT 'Milvus集合名称',
  `document_count` int DEFAULT '0' COMMENT '文档数量',
  `chunk_count` int DEFAULT '0' COMMENT '分块数量',
  `status` tinyint DEFAULT '1' COMMENT '状态: 0-禁用, 1-启用',
  `owner_id` bigint DEFAULT NULL COMMENT '所有者ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_status` (`status`),
  KEY `idx_owner` (`owner_id`),
  KEY `idx_embedding_model` (`embedding_model_id`),
  CONSTRAINT `fk_kb_embedding_model` FOREIGN KEY (`embedding_model_id`) REFERENCES `rag_embedding_model` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库表';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `model_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `model_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型组名称',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型组描述',
  `scheduling_strategy` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ROUND_ROBIN' COMMENT '调度策略（ROUND_ROBIN/WEIGHTED_ROUND_ROBIN/LEAST_CONNECTION）',
  `priority` int DEFAULT '50' COMMENT '优先级',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型组表';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `model_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `model_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `group_id` bigint NOT NULL COMMENT '模型组ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实例名称',
  `api_endpoint` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'API端点URL',
  `model_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `api_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API密钥（加密存储）',
  `weight` int DEFAULT '1' COMMENT '权重',
  `max_concurrent` int DEFAULT '10' COMMENT '最大并发数',
  `current_connections` int DEFAULT '0' COMMENT '当前连接数',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'HEALTHY' COMMENT '状态（HEALTHY/DEGRADED/UNHEALTHY/DISABLED）',
  `health_check_endpoint` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '健康检查端点',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_group_id` (`group_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_instance_group` FOREIGN KEY (`group_id`) REFERENCES `model_group` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型实例表';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `model_instance_health`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `model_instance_health` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '实例ID',
  `health_state` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'HEALTHY' COMMENT '健康状态',
  `response_time_ms` bigint DEFAULT NULL COMMENT '响应时间（毫秒）',
  `consecutive_failures` int DEFAULT '0' COMMENT '连续失败次数',
  `consecutive_successes` int DEFAULT '0' COMMENT '连续成功次数',
  `last_check_time` datetime DEFAULT NULL COMMENT '最后检查时间',
  `last_success_time` datetime DEFAULT NULL COMMENT '最后成功时间',
  `last_failure_time` datetime DEFAULT NULL COMMENT '最后失败时间',
  `last_error_message` text COLLATE utf8mb4_unicode_ci COMMENT '最后错误信息',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_instance_id` (`instance_id`),
  CONSTRAINT `fk_health_instance` FOREIGN KEY (`instance_id`) REFERENCES `model_instance` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实例健康状态表';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `scheduling_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scheduling_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `group_id` bigint NOT NULL COMMENT '模型组ID',
  `instance_id` bigint DEFAULT NULL COMMENT '实例ID',
  `request_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求ID',
  `action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型（SELECT/FAILOVER/RECOVERY/HEALTH_CHECK）',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态（SUCCESS/FAILURE）',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时（毫秒）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_group_id` (`group_id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调度日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
