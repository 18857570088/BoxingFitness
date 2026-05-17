CREATE DATABASE IF NOT EXISTS `BoxingFitness`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `BoxingFitness`;

-- Removed serial and activation-code verification storage
SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS activation_logs;
DROP TABLE IF EXISTS activations;
DROP TABLE IF EXISTS licenses;
SET FOREIGN_KEY_CHECKS=1;




CREATE TABLE IF NOT EXISTS app_users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  serial CHAR(11) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  language_code VARCHAR(8) NOT NULL DEFAULT 'zh',
  country_code VARCHAR(8) DEFAULT NULL,
  avatar_color VARCHAR(16) NOT NULL DEFAULT '#145DA0',
  current_tier TINYINT UNSIGNED NOT NULL DEFAULT 1,
  highest_tier TINYINT UNSIGNED NOT NULL DEFAULT 1,
  tier_updated_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_users_serial (serial),
  KEY idx_app_users_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'total_sessions_cached'
  ),
  'ALTER TABLE app_users DROP COLUMN total_sessions_cached',
  'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'total_hits_cached'
  ),
  'ALTER TABLE app_users DROP COLUMN total_hits_cached',
  'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'best_score_cached'
  ),
  'ALTER TABLE app_users DROP COLUMN best_score_cached',
  'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'best_30_hits_cached'
  ),
  'ALTER TABLE app_users DROP COLUMN best_30_hits_cached',
  'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'best_60_hits_cached'
  ),
  'ALTER TABLE app_users DROP COLUMN best_60_hits_cached',
  'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'best_burst_cached'
  ),
  'ALTER TABLE app_users DROP COLUMN best_burst_cached',
  'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'longest_streak_cached'
  ),
  'ALTER TABLE app_users DROP COLUMN longest_streak_cached',
  'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'active_days_cached'
  ),
  'ALTER TABLE app_users DROP COLUMN active_days_cached',
  'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'current_tier'
  ),
  'SELECT 1',
  'ALTER TABLE app_users ADD COLUMN current_tier TINYINT UNSIGNED NOT NULL DEFAULT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'highest_tier'
  ),
  'SELECT 1',
  'ALTER TABLE app_users ADD COLUMN highest_tier TINYINT UNSIGNED NOT NULL DEFAULT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'tier_updated_at'
  ),
  'SELECT 1',
  'ALTER TABLE app_users ADD COLUMN tier_updated_at DATETIME DEFAULT NULL'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

CREATE TABLE IF NOT EXISTS user_achievements (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  achievement_key VARCHAR(64) NOT NULL,
  unlocked_at DATETIME DEFAULT NULL,
  progress_value INT UNSIGNED NOT NULL DEFAULT 0,
  goal_value INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_achievement (user_id, achievement_key),
  KEY idx_user_achievements_user (user_id, unlocked_at),
  CONSTRAINT fk_user_achievements_user
    FOREIGN KEY (user_id) REFERENCES app_users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS training_sessions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  serial CHAR(11) NOT NULL,
  mode_seconds SMALLINT UNSIGNED NOT NULL,
  total_hits INT UNSIGNED NOT NULL,
  average_frequency DECIMAL(8, 3) NOT NULL,
  best_burst_count INT UNSIGNED NOT NULL DEFAULT 0,
  best_burst_start_sec DECIMAL(8, 3) NOT NULL DEFAULT 0,
  started_at DATETIME DEFAULT NULL,
  ended_at DATETIME NOT NULL,
  device_hash VARCHAR(128) DEFAULT NULL,
  app_version VARCHAR(64) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_training_user_time (user_id, ended_at DESC),
  KEY idx_training_mode_time (mode_seconds, ended_at DESC),
  KEY idx_training_mode_hits (mode_seconds, total_hits DESC, ended_at DESC),
  CONSTRAINT fk_training_sessions_user
    FOREIGN KEY (user_id) REFERENCES app_users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS standard_count_samples;
DROP TABLE IF EXISTS audio_samples;
