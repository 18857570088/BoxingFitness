USE `BoxingFitness`;

SET FOREIGN_KEY_CHECKS=0;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'app_users'
      AND CONSTRAINT_NAME = 'fk_app_users_license_serial'
  ),
  'ALTER TABLE `app_users` DROP FOREIGN KEY `fk_app_users_license_serial`',
  'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

SET @stmt = IF(
  EXISTS(
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'training_sessions'
      AND CONSTRAINT_NAME = 'fk_training_sessions_license_serial'
  ),
  'ALTER TABLE `training_sessions` DROP FOREIGN KEY `fk_training_sessions_license_serial`',
  'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;

DROP TABLE IF EXISTS `activation_logs`;
DROP TABLE IF EXISTS `activations`;
DROP TABLE IF EXISTS `licenses`;

SET FOREIGN_KEY_CHECKS=1;
