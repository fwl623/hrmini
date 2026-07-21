-- =============================================================
-- V14: 假期余额审计 + 请假表扩展
-- 1. balance_change_log 表（余额变动审计）
-- 2. leave_balance 扩展（total_quota/used_quota/remaining_quota/version）
-- 3. leave_application 扩展（cancel_reason/deducted_balance_snapshot）
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

-- 2. leave_balance 扩展（精确到3位小数、乐观锁）
ALTER TABLE leave_balance
    ADD COLUMN total_quota      DECIMAL(10,3) NULL AFTER leave_type,
    ADD COLUMN used_quota       DECIMAL(10,3) NULL AFTER total_quota,
    ADD COLUMN remaining_quota  DECIMAL(10,3) NULL AFTER used_quota,
    ADD COLUMN effective_date   DATE          NULL AFTER remaining_quota,
    ADD COLUMN version          INT           NOT NULL DEFAULT 0 AFTER expire_date;

-- 2a. 从现有 balance 字段回填新字段
UPDATE leave_balance SET
    total_quota = balance,
    used_quota = 0,
    remaining_quota = balance,
    effective_date = '2026-01-01';

-- 2b. balance 改为 DECIMAL(10,3)
ALTER TABLE leave_balance
    MODIFY balance DECIMAL(10,3) NOT NULL DEFAULT 0.000;

-- 2c. 后续新业务用新字段，balance 保持同步
UPDATE leave_balance SET balance = remaining_quota WHERE balance != remaining_quota;

-- 3. leave_application 扩展
ALTER TABLE leave_application
    ADD COLUMN cancel_reason          VARCHAR(16)  NULL COMMENT 'WITHDRAW(撤回)/REVOKE(撤销)' AFTER status,
    ADD COLUMN deducted_balance_snapshot JSON      NULL COMMENT '扣减时余额快照(审计用)' AFTER cancel_reason;

-- 4. balance 精度改为 DECIMAL(10,3) 支持 0.125
-- 已在 2b 中执行
