-- =============================================================
-- V10: 补全标准薪资账套的工资项目
-- 为 scheme_id=1 的标准月薪账套添加社保、公积金、个税等扣款项
-- =============================================================

-- 只在标准月薪账套存在且项目数=1（仅有BASE_PAY）时执行
SET @scheme_id = 1;
SET @item_count = (SELECT COUNT(*) FROM payroll_scheme_item WHERE scheme_id = @scheme_id);

-- 新增绩效奖金
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, ratio, sort_order)
SELECT @scheme_id, 'PERFORMANCE_BONUS', '绩效奖金', 'VARIABLE', 1.0, 2
FROM dual WHERE @item_count <= 1;

-- 新增加班费
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, sort_order)
SELECT @scheme_id, 'OVERTIME_PAY', '加班费', 'VARIABLE', 3
FROM dual WHERE @item_count <= 1;

-- 新增迟到扣款
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, sort_order)
SELECT @scheme_id, 'LATE_DEDUCT', '迟到扣款', 'ATTENDANCE_DEDUCT', 4
FROM dual WHERE @item_count <= 1;

-- 新增请假扣款
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, sort_order)
SELECT @scheme_id, 'LEAVE_DEDUCT', '请假扣款', 'ATTENDANCE_DEDUCT', 5
FROM dual WHERE @item_count <= 1;

-- 新增社保扣款（养老8%+医疗2%+失业0.5%）
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, ratio, sort_order)
SELECT @scheme_id, 'SS_DEDUCT', '社保扣款（养老8%+医疗2%+失业0.5%）', 'SS_DEDUCT', 0.105, 6
FROM dual WHERE @item_count <= 1;

-- 新增住房公积金扣款（7%）
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, ratio, sort_order)
SELECT @scheme_id, 'HF_DEDUCT', '住房公积金扣款（7%）', 'HF_DEDUCT', 0.07, 7
FROM dual WHERE @item_count <= 1;

-- 新增个税
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, sort_order)
SELECT @scheme_id, 'TAX', '个人所得税', 'TAX', 8
FROM dual WHERE @item_count <= 1;
