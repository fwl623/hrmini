-- =============================================================
-- V19: 补齐请假余额审计相关结构（幂等）
-- 背景：部分环境库 flyway_history 中 V14 被本地脚本占用，
--       未实际执行 leave_balance / leave_application 扩展，导致
--       考勤日历等查询 Unknown column 'cancel_reason'。
-- =============================================================

-- 1. 余额变动日志表
CREATE TABLE IF NOT EXISTS balance_change_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id     BIGINT          NOT NULL COMMENT '员工ID',
    leave_type      VARCHAR(16)     NOT NULL COMMENT 'ANNUAL / COMPENSATORY',
    change_amount   DECIMAL(10,3)   NOT NULL COMMENT '变动数量(正=消耗预扣，负=归还)',
    source_type     VARCHAR(16)     NOT NULL COMMENT 'SUBMIT/CONFIRM/REFUND/OVERTIME/EXPIRE',
    source_id       BIGINT          NULL     COMMENT '关联源ID(leave_application.id/overtime_ledger.id)',
    balance_before  DECIMAL(10,3)   NULL     COMMENT '变动前余额',
    balance_after   DECIMAL(10,3)   NULL     COMMENT '变动后余额',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/REFUNDED',
    remark          VARCHAR(256)    NULL     COMMENT '备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_employee (employee_id),
    INDEX idx_source (source_type, source_id)
) COMMENT='假期余额变动日志';

-- 2. leave_balance 扩展列（已存在则跳过）
SET @db := DATABASE();

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'leave_balance' AND COLUMN_NAME = 'total_quota'),
        'SELECT 1',
        'ALTER TABLE leave_balance ADD COLUMN total_quota DECIMAL(10,3) NULL AFTER leave_type'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'leave_balance' AND COLUMN_NAME = 'used_quota'),
        'SELECT 1',
        'ALTER TABLE leave_balance ADD COLUMN used_quota DECIMAL(10,3) NULL AFTER total_quota'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'leave_balance' AND COLUMN_NAME = 'remaining_quota'),
        'SELECT 1',
        'ALTER TABLE leave_balance ADD COLUMN remaining_quota DECIMAL(10,3) NULL AFTER used_quota'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'leave_balance' AND COLUMN_NAME = 'effective_date'),
        'SELECT 1',
        'ALTER TABLE leave_balance ADD COLUMN effective_date DATE NULL AFTER remaining_quota'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'leave_balance' AND COLUMN_NAME = 'version'),
        'SELECT 1',
        'ALTER TABLE leave_balance ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER expire_date'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE leave_balance SET
    total_quota = COALESCE(total_quota, balance),
    used_quota = COALESCE(used_quota, 0),
    remaining_quota = COALESCE(remaining_quota, balance),
    effective_date = COALESCE(effective_date, '2026-01-01')
WHERE total_quota IS NULL OR remaining_quota IS NULL OR used_quota IS NULL OR effective_date IS NULL;

ALTER TABLE leave_balance
    MODIFY balance DECIMAL(10,3) NOT NULL DEFAULT 0.000;

UPDATE leave_balance SET balance = remaining_quota
WHERE remaining_quota IS NOT NULL AND balance != remaining_quota;

-- 3. leave_application 扩展列
SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'leave_application' AND COLUMN_NAME = 'cancel_reason'),
        'SELECT 1',
        'ALTER TABLE leave_application ADD COLUMN cancel_reason VARCHAR(16) NULL COMMENT ''WITHDRAW(撤回)/REVOKE(撤销)'' AFTER status'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'leave_application' AND COLUMN_NAME = 'deducted_balance_snapshot'),
        'SELECT 1',
        'ALTER TABLE leave_application ADD COLUMN deducted_balance_snapshot JSON NULL COMMENT ''扣减时余额快照(审计用)'' AFTER cancel_reason'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
