-- =============================================================
-- V16: 按职级创建薪资账套
-- 实习生 / 试用员工 / M序列 / P序列 / S序列
-- =============================================================

-- 1. 创建账套
INSERT IGNORE INTO payroll_scheme (id, name, description, effective_date, status, deleted) VALUES
(2, '实习生薪酬方案', '实习生：劳务报酬，无社保公积金', '2026-01-01', 'enabled', 0),
(3, '试用员工薪酬方案', '试用期：社保公积金正常缴纳', '2026-01-01', 'enabled', 0),
(4, '管理序列薪酬方案', 'M1-M5：含岗位津贴', '2026-01-01', 'enabled', 0),
(5, '专业序列薪酬方案', 'P1-P10：标准薪酬', '2026-01-01', 'enabled', 0),
(6, '支持序列薪酬方案', 'S1-S5：基础薪酬', '2026-01-01', 'enabled', 0);

-- ========== 2. 实习生薪酬方案 (id=20) ==========
-- 无社保公积金、无个税累计预扣
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, ratio, sort_order) VALUES
(2, 'BASE_PAY', '基本工资', 'FIXED', NULL, 1),
(2, 'OVERTIME_PAY', '加班费', 'VARIABLE', NULL, 2),
(2, 'LATE_DEDUCT', '迟到扣款', 'ATTENDANCE_DEDUCT', NULL, 3),
(2, 'LEAVE_DEDUCT', '请假扣款', 'ATTENDANCE_DEDUCT', NULL, 4),
(2, 'TAX', '个人所得税（劳务报酬）', 'TAX', NULL, 5);

-- ========== 3. 试用员工薪酬方案 (id=21) ==========
-- 和标准方案一致，试用期比例由分段计薪逻辑处理
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, ratio, sort_order) VALUES
(3, 'BASE_PAY', '基本工资', 'FIXED', NULL, 1),
(3, 'PERFORMANCE_BONUS', '绩效奖金', 'VARIABLE', 1.0, 2),
(3, 'OVERTIME_PAY', '加班费', 'VARIABLE', NULL, 3),
(3, 'LATE_DEDUCT', '迟到扣款', 'ATTENDANCE_DEDUCT', NULL, 4),
(3, 'LEAVE_DEDUCT', '请假扣款', 'ATTENDANCE_DEDUCT', NULL, 5),
(3, 'SS_DEDUCT', '社保扣款（养老8%+医疗2%+失业0.5%）', 'SS_DEDUCT', 0.105, 6),
(3, 'HF_DEDUCT', '住房公积金扣款（7%）', 'HF_DEDUCT', 0.07, 7),
(3, 'TAX', '个人所得税', 'TAX', NULL, 8);

-- ========== 4. 管理序列 M1-M5 (id=22) ==========
-- 有岗位津贴 + 绩效
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, ratio, sort_order) VALUES
(4, 'BASE_PAY', '基本工资', 'FIXED', NULL, 1),
(4, 'POSITION_ALLOWANCE', '岗位津贴', 'FIXED', NULL, 2),
(4, 'PERFORMANCE_BONUS', '绩效奖金', 'VARIABLE', 1.0, 3),
(4, 'OVERTIME_PAY', '加班费', 'VARIABLE', NULL, 4),
(4, 'LATE_DEDUCT', '迟到扣款', 'ATTENDANCE_DEDUCT', NULL, 5),
(4, 'LEAVE_DEDUCT', '请假扣款', 'ATTENDANCE_DEDUCT', NULL, 6),
(4, 'SS_DEDUCT', '社保扣款（养老8%+医疗2%+失业0.5%）', 'SS_DEDUCT', 0.105, 7),
(4, 'HF_DEDUCT', '住房公积金扣款（7%）', 'HF_DEDUCT', 0.07, 8),
(4, 'TAX', '个人所得税', 'TAX', NULL, 9);

-- ========== 5. 专业序列 P1-P10 (id=5) ==========
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, ratio, sort_order) VALUES
(5, 'BASE_PAY', '基本工资', 'FIXED', NULL, 1),
(5, 'PERFORMANCE_BONUS', '绩效奖金', 'VARIABLE', 1.0, 2),
(5, 'OVERTIME_PAY', '加班费', 'VARIABLE', NULL, 3),
(5, 'LATE_DEDUCT', '迟到扣款', 'ATTENDANCE_DEDUCT', NULL, 4),
(5, 'LEAVE_DEDUCT', '请假扣款', 'ATTENDANCE_DEDUCT', NULL, 5),
(5, 'SS_DEDUCT', '社保扣款（养老8%+医疗2%+失业0.5%）', 'SS_DEDUCT', 0.105, 6),
(5, 'HF_DEDUCT', '住房公积金扣款（7%）', 'HF_DEDUCT', 0.07, 7),
(5, 'TAX', '个人所得税', 'TAX', NULL, 8);

-- ========== 6. 支持序列 S1-S5 (id=6) ==========
INSERT IGNORE INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, ratio, sort_order) VALUES
(6, 'BASE_PAY', '基本工资', 'FIXED', NULL, 1),
(6, 'PERFORMANCE_BONUS', '绩效奖金', 'VARIABLE', 1.0, 2),
(6, 'OVERTIME_PAY', '加班费', 'VARIABLE', NULL, 3),
(6, 'LATE_DEDUCT', '迟到扣款', 'ATTENDANCE_DEDUCT', NULL, 4),
(6, 'LEAVE_DEDUCT', '请假扣款', 'ATTENDANCE_DEDUCT', NULL, 5),
(6, 'SS_DEDUCT', '社保扣款（养老8%+医疗2%+失业0.5%）', 'SS_DEDUCT', 0.105, 6),
(6, 'HF_DEDUCT', '住房公积金扣款（7%）', 'HF_DEDUCT', 0.07, 7),
(6, 'TAX', '个人所得税', 'TAX', NULL, 8);

-- ========== 7. 设置适用范围（按职级） ==========
-- M序列：M1~M5
INSERT IGNORE INTO payroll_scheme_scope (scheme_id, scope_type, scope_id) VALUES
(4, 'JOB_LEVEL', 'M1'), (4, 'JOB_LEVEL', 'M2'), (4, 'JOB_LEVEL', 'M3'),
(4, 'JOB_LEVEL', 'M4'), (4, 'JOB_LEVEL', 'M5');

-- P序列：P1~P10
INSERT IGNORE INTO payroll_scheme_scope (scheme_id, scope_type, scope_id) VALUES
(5, 'JOB_LEVEL', 'P1'), (5, 'JOB_LEVEL', 'P2'), (5, 'JOB_LEVEL', 'P3'),
(5, 'JOB_LEVEL', 'P4'), (5, 'JOB_LEVEL', 'P5'), (5, 'JOB_LEVEL', 'P6'),
(5, 'JOB_LEVEL', 'P7'), (5, 'JOB_LEVEL', 'P8'), (5, 'JOB_LEVEL', 'P9'),
(5, 'JOB_LEVEL', 'P10');

-- S序列：S1~S5
INSERT IGNORE INTO payroll_scheme_scope (scheme_id, scope_type, scope_id) VALUES
(6, 'JOB_LEVEL', 'S1'), (6, 'JOB_LEVEL', 'S2'), (6, 'JOB_LEVEL', 'S3'),
(6, 'JOB_LEVEL', 'S4'), (6, 'JOB_LEVEL', 'S5');

-- ========== 8. 更新现有员工薪资档案，匹配对应账套 ==========
-- M系列 → scheme_id=4
UPDATE employee_salary_profile esp
JOIN employee e ON esp.employee_id = e.id
SET esp.scheme_id = 4
WHERE e.grade IN ('M1','M2','M3','M4','M5') AND esp.scheme_id = 1;

-- P系列 → scheme_id=5
UPDATE employee_salary_profile esp
JOIN employee e ON esp.employee_id = e.id
SET esp.scheme_id = 5
WHERE e.grade IN ('P1','P2','P3','P4','P5','P6','P7','P8','P9','P10') AND esp.scheme_id = 1;

-- S系列 → scheme_id=6
UPDATE employee_salary_profile esp
JOIN employee e ON esp.employee_id = e.id
SET esp.scheme_id = 6
WHERE e.grade IN ('S1','S2','S3','S4','S5') AND esp.scheme_id = 1;

-- 验证
SELECT ps.id, ps.name, COUNT(psi.id) AS items, COUNT(pss.id) AS scopes
FROM payroll_scheme ps
LEFT JOIN payroll_scheme_item psi ON psi.scheme_id = ps.id
LEFT JOIN payroll_scheme_scope pss ON pss.scheme_id = ps.id
WHERE ps.id IN (2,3,4,5,6)
GROUP BY ps.id, ps.name
ORDER BY ps.id;
