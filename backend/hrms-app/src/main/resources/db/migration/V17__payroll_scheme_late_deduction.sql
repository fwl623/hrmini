-- =============================================================
-- V17: 账套迟到扣款配置字段
-- 支持 FIXED（固定金额）/ RATIO（日工资比例）/ STEP（阶梯累进）
-- =============================================================

ALTER TABLE payroll_scheme
    ADD COLUMN late_deduction_type   VARCHAR(16)  NULL COMMENT 'FIXED/RATIO/STEP' AFTER description,
    ADD COLUMN late_deduction_value  DECIMAL(10,2) NULL COMMENT '扣款值：固定金额(元)或比例(0~1)或阶梯基准' AFTER late_deduction_type,
    ADD COLUMN late_deduction_config JSON         NULL COMMENT '阶梯规则配置JSON' AFTER late_deduction_value;

-- 设置默认配置（保持现有行为）
UPDATE payroll_scheme SET
    late_deduction_type = 'FIXED',
    late_deduction_value = 50.00
WHERE deleted = 0;
