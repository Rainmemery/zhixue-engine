-- 创建管理服务数据库
CREATE DATABASE IF NOT EXISTS `zhixue_admin` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `zhixue_admin`;

-- 管理用户表
CREATE TABLE IF NOT EXISTS `admin_users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `email` VARCHAR(100) NOT NULL UNIQUE,
    `real_name` VARCHAR(50),
    `phone` VARCHAR(20),
    `avatar_url` VARCHAR(500),
    `role_id` BIGINT,
    `status` ENUM('active', 'inactive', 'locked') NOT NULL DEFAULT 'active',
    `last_login` TIMESTAMP NULL,
    `last_login_ip` VARCHAR(45),
    `login_count` INT DEFAULT 0,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` TIMESTAMP NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_username` (`username`),
    INDEX `idx_email` (`email`),
    INDEX `idx_status` (`status`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS `admin_roles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL UNIQUE,
    `code` VARCHAR(50) NOT NULL UNIQUE,
    `description` VARCHAR(255),
    `permissions` JSON,
    `is_system` TINYINT(1) DEFAULT 0,
    `status` ENUM('active', 'inactive') NOT NULL DEFAULT 'active',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` TIMESTAMP NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_code` (`code`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 系统日志表
CREATE TABLE IF NOT EXISTS `system_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT,
    `username` VARCHAR(50),
    `action` VARCHAR(50) NOT NULL,
    `resource` VARCHAR(100),
    `resource_id` VARCHAR(50),
    `method` VARCHAR(10),
    `url` VARCHAR(500),
    `params` TEXT,
    `status` ENUM('success', 'failed') NOT NULL DEFAULT 'success',
    `ip_address` VARCHAR(45),
    `user_agent` VARCHAR(500),
    `error_message` TEXT,
    `duration_ms` INT,
    `details` TEXT,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_action` (`action`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_resource` (`resource`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统日志表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS `system_configs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `config_key` VARCHAR(100) NOT NULL UNIQUE,
    `config_value` TEXT,
    `description` VARCHAR(255),
    `category` VARCHAR(50) DEFAULT 'general',
    `data_type` ENUM('string', 'number', 'boolean', 'json') DEFAULT 'string',
    `editable` TINYINT(1) DEFAULT 1,
    `is_public` TINYINT(1) DEFAULT 0,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_config_key` (`config_key`),
    INDEX `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 备份记录表
CREATE TABLE IF NOT EXISTS `backup_records` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `file_name` VARCHAR(255) NOT NULL,
    `file_path` VARCHAR(500),
    `file_size` BIGINT DEFAULT 0,
    `backup_type` ENUM('full', 'incremental', 'manual') NOT NULL DEFAULT 'manual',
    `status` ENUM('pending', 'running', 'completed', 'failed') NOT NULL DEFAULT 'pending',
    `start_time` TIMESTAMP NULL,
    `end_time` TIMESTAMP NULL,
    `duration_seconds` INT,
    `created_by` VARCHAR(50),
    `download_url` VARCHAR(500),
    `error_message` TEXT,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_backup_type` (`backup_type`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备份记录表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS `operation_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT,
    `username` VARCHAR(50),
    `module` VARCHAR(50),
    `operation` VARCHAR(50),
    `target_type` VARCHAR(50),
    `target_id` VARCHAR(50),
    `before_data` JSON,
    `after_data` JSON,
    `ip_address` VARCHAR(45),
    `status` ENUM('success', 'failed') DEFAULT 'success',
    `error_message` TEXT,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_module` (`module`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 插入默认角色
INSERT INTO `admin_roles` (`name`, `code`, `description`, `permissions`, `is_system`, `status`) VALUES
('超级管理员', 'super_admin', '拥有系统所有权限', '["*"]', 1, 'active'),
('系统管理员', 'admin', '拥有系统管理权限', '["user:*", "role:*", "config:*", "log:*"]', 1, 'active'),
('运营管理员', 'operator', '拥有运营相关权限', '["user:read", "problem:*", "learning:*"]', 0, 'active'),
('普通管理员', 'viewer', '只有查看权限', '["*:read"]', 0, 'active');

-- 插入默认超级管理员账号 (密码: admin123)
INSERT INTO `admin_users` (`username`, `password`, `email`, `real_name`, `role_id`, `status`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'admin@zhixue.com', '超级管理员', 1, 'active');

-- 插入默认系统配置
INSERT INTO `system_configs` (`config_key`, `config_value`, `description`, `category`, `data_type`, `editable`, `is_public`) VALUES
('site_name', '智学引擎', '网站名称', 'basic', 'string', 1, 1),
('site_description', '智能学习平台', '网站描述', 'basic', 'string', 1, 1),
('site_logo', '/logo.png', '网站Logo', 'basic', 'string', 1, 1),
('max_upload_size', '10485760', '最大上传文件大小(字节)', 'upload', 'number', 1, 0),
('allowed_file_types', '["jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx"]', '允许上传的文件类型', 'upload', 'json', 1, 0),
('session_timeout', '7200', '会话超时时间(秒)', 'security', 'number', 1, 0),
('max_login_attempts', '5', '最大登录尝试次数', 'security', 'number', 1, 0),
('enable_captcha', 'true', '是否启用验证码', 'security', 'boolean', 1, 0),
('smtp_host', '', 'SMTP服务器地址', 'email', 'string', 1, 0),
('smtp_port', '465', 'SMTP端口', 'email', 'number', 1, 0),
('smtp_username', '', 'SMTP用户名', 'email', 'string', 1, 0),
('smtp_password', '', 'SMTP密码', 'email', 'string', 0, 0),
('backup_enabled', 'true', '是否启用自动备份', 'backup', 'boolean', 1, 0),
('backup_schedule', '0 0 2 * * ?', '备份计划(cron表达式)', 'backup', 'string', 1, 0),
('backup_retention_days', '30', '备份保留天数', 'backup', 'number', 1, 0);
