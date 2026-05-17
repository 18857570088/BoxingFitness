-- 鏅鸿兘鎷冲嚮鍙嶅簲鐞冩暟鎹簱琛ㄧ粨鏋勫浠?
-- Database: reflex_auth
-- Generated at: 2026-04-15 14:51:03

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;

-- Removed serial and activation-code verification storage
DROP TABLE IF EXISTS `activation_logs`;
DROP TABLE IF EXISTS `activations`;
DROP TABLE IF EXISTS `licenses`;


-- ----------------------------
-- Table structure for `app_users`
-- ----------------------------
DROP TABLE IF EXISTS `app_users`;
CREATE TABLE `app_users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `serial` char(11) NOT NULL,
  `nickname` varchar(64) NOT NULL,
  `language_code` varchar(8) NOT NULL DEFAULT 'zh',
  `country_code` varchar(8) DEFAULT NULL,
  `avatar_color` varchar(16) NOT NULL DEFAULT '#145DA0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `total_sessions_cached` int unsigned NOT NULL DEFAULT '0',
  `total_hits_cached` int unsigned NOT NULL DEFAULT '0',
  `best_score_cached` int unsigned NOT NULL DEFAULT '0',
  `current_tier` tinyint unsigned NOT NULL DEFAULT '1',
  `highest_tier` tinyint unsigned NOT NULL DEFAULT '1',
  `tier_updated_at` datetime DEFAULT NULL,
  `best_30_hits_cached` int unsigned NOT NULL DEFAULT '0',
  `best_60_hits_cached` int unsigned NOT NULL DEFAULT '0',
  `best_burst_cached` int unsigned NOT NULL DEFAULT '0',
  `longest_streak_cached` int unsigned NOT NULL DEFAULT '0',
  `active_days_cached` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_users_serial` (`serial`),
  KEY `idx_app_users_nickname` (`nickname`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Removed audio sample storage
DROP TABLE IF EXISTS `standard_count_samples`;
DROP TABLE IF EXISTS `audio_samples`;
-- Table structure for `training_sessions`
-- ----------------------------
DROP TABLE IF EXISTS `training_sessions`;
CREATE TABLE `training_sessions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `serial` char(11) NOT NULL,
  `mode_seconds` smallint unsigned NOT NULL,
  `total_hits` int unsigned NOT NULL,
  `average_frequency` decimal(8,3) NOT NULL,
  `best_burst_count` int unsigned NOT NULL DEFAULT '0',
  `best_burst_start_sec` decimal(8,3) NOT NULL DEFAULT '0.000',
  `started_at` datetime DEFAULT NULL,
  `ended_at` datetime NOT NULL,
  `device_hash` varchar(128) DEFAULT NULL,
  `app_version` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_training_user_time` (`user_id`,`ended_at` DESC),
  KEY `idx_training_mode_time` (`mode_seconds`,`ended_at` DESC),
  KEY `idx_training_mode_hits` (`mode_seconds`,`total_hits` DESC,`ended_at` DESC),
  CONSTRAINT `fk_training_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `app_users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=186 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for `user_achievements`
-- ----------------------------
DROP TABLE IF EXISTS `user_achievements`;
CREATE TABLE `user_achievements` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `achievement_key` varchar(64) NOT NULL,
  `unlocked_at` datetime DEFAULT NULL,
  `progress_value` int unsigned NOT NULL DEFAULT '0',
  `goal_value` int unsigned NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_achievement` (`user_id`,`achievement_key`),
  KEY `idx_user_achievements_user` (`user_id`,`unlocked_at`),
  CONSTRAINT `fk_user_achievements_user` FOREIGN KEY (`user_id`) REFERENCES `app_users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=327 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS=1;


