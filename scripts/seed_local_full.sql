-- ============================================================
-- 本地全面联调种子（可重复执行）
-- 依赖：先跑 scripts/seed_local_e2e.sql（账号 1380000000x）
-- 密码统一：Admin@12345
-- AES Key：hrms.crypto.aes-key = aHJtcy1kZXYtYWVzLTI1Ni1rZXktMzJieXRlcyEhISE=
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 0. 确保基础种子账号已存在 ----------
-- 若尚未导入，请先：mysql -uroot -p123456 hrms < scripts/seed_local_e2e.sql

SET @pwd := '$2a$12$9bAzztAJZqkz5P9Fz3A4YONhIj9RNLHBC0Yvl6jKY6NLRqyiUKV8G';
SET @pos_fe := (SELECT id FROM position WHERE name = '前端开发工程师' AND deleted = 0 LIMIT 1);
SET @pos_hr := (SELECT id FROM position WHERE name = 'HR专员' AND deleted = 0 LIMIT 1);
SET @pos_mgr := (SELECT id FROM position WHERE name = '前端主管' AND deleted = 0 LIMIT 1);
SET @pos_fin := (SELECT id FROM position WHERE name = '财务专员' AND deleted = 0 LIMIT 1);
SET @pos_sale := (SELECT id FROM position WHERE name = '销售专员' AND deleted = 0 LIMIT 1);
SET @scheme := (SELECT id FROM payroll_scheme WHERE name = '标准账套' AND deleted = 0 LIMIT 1);
SET @ag := (SELECT id FROM attendance_group WHERE name = '默认考勤组' AND deleted = 0 LIMIT 1);
SET @mgr_emp := (SELECT id FROM employee WHERE mobile = '13800000002' LIMIT 1);
SET @hr_emp := (SELECT id FROM employee WHERE mobile = '13800000001' LIMIT 1);
SET @zs_emp := (SELECT id FROM employee WHERE mobile = '13800000004' LIMIT 1);
SET @period := DATE_FORMAT(CURDATE(), '%Y-%m');

-- 联调账号状态复位：张三恢复在职试用，避免先前离职联调残留影响白名单/统计用例
UPDATE employee SET employment_status = 10, probation_end_date = DATE_ADD('2025-10-01', INTERVAL 6 MONTH)
WHERE mobile = '13800000004' AND employment_status >= 30;
UPDATE sys_user SET status = 1 WHERE username = '13800000004';

-- ---------- 1. 真实 AES 密文覆盖占位（TC-EMP-008）----------
-- 明文：11010119900x0x1234（x 对应员工序号）
UPDATE employee_personal p
JOIN employee e ON e.id = p.employee_id
SET p.id_number_enc = CASE e.mobile
        WHEN '13800000001' THEN 'acgr0nbIm44ke4zoZKwMzzP8z0Qdok7PmRWAkmnrIeK+vJeTOrekrGkTui9CnQ=='
        WHEN '13800000002' THEN 'Q/94smdt7crslUapYOPSSrOrJvKhjCGXdFDEtrEMwYAZX754ScLuCWfuvojfjg=='
        WHEN '13800000003' THEN 'K/xC7GR+z1Fydw3jdPx4hB6TBU/mthXViMLcBDMo4Oq8VDom+KvIt2m+64qiwQ=='
        WHEN '13800000004' THEN 'L+gIMNGkGHBAqWz0AJovMiJOIX1jtT/KcOXkwupsPRM1Q54tVrvCYJULvQnM+Q=='
        WHEN '13800000005' THEN 'CXY6B4P5ngaA9zJXucNfZgtH7SNfHM6NfS2m+vPDB2+cEOyIimKnKpCk//zkGA=='
        ELSE p.id_number_enc
    END,
    p.id_number_hash = SHA2(CONCAT('11010119900', RIGHT(e.mobile, 1), '0', RIGHT(e.mobile, 1), '1234'), 256)
WHERE e.mobile IN ('13800000001','13800000002','13800000003','13800000004','13800000005');

-- 银行卡（张三）
INSERT INTO employee_bank (employee_id, bank_name, bank_account_enc, bank_account_tail)
SELECT e.id, '工商银行', 'z8oLNXyCDS2D9pHBxveD915dH4EBg/dnlb4ma0csYaSuUZQVuvRw86YHKFK4IgY=', '0123'
FROM employee e
WHERE e.mobile = '13800000004'
  AND NOT EXISTS (SELECT 1 FROM employee_bank b WHERE b.employee_id = e.id);

-- ---------- 2. 扩充同部门员工（DEPT_MANAGER DataScope / 花名册）----------
INSERT INTO sys_user (username, password_hash, password_changed_at, employee_id, status, created_at, updated_at)
SELECT v.u, @pwd, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 1, NOW(), NOW()
FROM (
    SELECT '13800000006' AS u UNION ALL SELECT '13800000007'
    UNION ALL SELECT '13800000008' UNION ALL SELECT '13800000009'
) v
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = v.u);

INSERT INTO employee (
    employee_no, user_id, name, gender, mobile, email,
    department_id, position_id, grade, manager_id, work_location,
    hire_date, employment_type, employment_status, probation_pay_ratio, probation_end_date, deleted
)
SELECT v.eno, NULL, v.nm, v.g, v.mob, v.em,
       v.dept, v.pos, v.grade, @mgr_emp, '总部',
       v.hire, 'fulltime', v.st, v.pr, v.pend, 0
FROM (
    SELECT '202603003' AS eno, '李四' AS nm, 'MALE' AS g, '13800000006' AS mob, 'lisi@demo.local' AS em,
           4 AS dept, @pos_fe AS pos, 'P2' AS grade, '2025-03-01' AS hire, 20 AS st, NULL AS pr, NULL AS pend
    UNION ALL
    SELECT '202603004', '王五', 'FEMALE', '13800000007', 'wangwu@demo.local',
           4, @pos_fe, 'P1', '2026-01-15', 10, 0.80, DATE_ADD('2026-01-15', INTERVAL 6 MONTH)
    UNION ALL
    SELECT '202602002', '周八', 'MALE', '13800000008', 'zhouba@demo.local',
           3, @pos_sale, 'S1', '2024-08-01', 20, NULL, NULL
    UNION ALL
    SELECT '2026Z0003', '吴九', 'FEMALE', '13800000009', 'wujiu@demo.local',
           1, @pos_hr, 'S1', '2025-06-01', 20, NULL, NULL
) v
WHERE NOT EXISTS (SELECT 1 FROM employee WHERE mobile = v.mob);

UPDATE employee e JOIN sys_user u ON u.username = e.mobile
SET e.user_id = u.id
WHERE e.mobile IN ('13800000006','13800000007','13800000008','13800000009');

UPDATE sys_user u JOIN employee e ON e.mobile = u.username
SET u.employee_id = e.id
WHERE u.username IN ('13800000006','13800000007','13800000008','13800000009');

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u CROSS JOIN sys_role r
WHERE u.username IN ('13800000006','13800000007','13800000008','13800000009')
  AND r.code = 'EMPLOYEE'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO employee_personal (
    employee_id, id_number_enc, id_number_hash, birthday,
    household_address, residence_address, emergency_contact, emergency_phone
)
SELECT e.id,
       'L+gIMNGkGHBAqWz0AJovMiJOIX1jtT/KcOXkwupsPRM1Q54tVrvCYJULvQnM+Q==',
       SHA2(CONCAT('11010119900404', LPAD(e.id, 4, '0')), 256),
       '1992-05-05', '北京市朝阳区', '北京市海淀区中关村', '家属', '13900001111'
FROM employee e
WHERE e.mobile IN ('13800000006','13800000007','13800000008','13800000009')
  AND NOT EXISTS (SELECT 1 FROM employee_personal p WHERE p.employee_id = e.id);

INSERT INTO attendance_group_member (group_id, employee_id)
SELECT @ag, e.id FROM employee e
WHERE e.mobile IN ('13800000006','13800000007','13800000008')
  AND @ag IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM attendance_group_member m WHERE m.group_id = @ag AND m.employee_id = e.id);

INSERT INTO leave_balance (employee_id, leave_type, balance, year, expire_date)
SELECT e.id, 'ANNUAL', 5.0, YEAR(CURDATE()), NULL
FROM employee e
WHERE e.mobile IN ('13800000006','13800000007','13800000008','13800000009')
  AND NOT EXISTS (
      SELECT 1 FROM leave_balance b
      WHERE b.employee_id = e.id AND b.leave_type = 'ANNUAL' AND b.year = YEAR(CURDATE())
  );

INSERT INTO leave_balance (employee_id, leave_type, balance, year, expire_date)
SELECT e.id, 'COMP_OFF', 1.5, NULL, DATE_ADD(CURDATE(), INTERVAL 90 DAY)
FROM employee e
WHERE e.mobile IN ('13800000006','13800000007')
  AND NOT EXISTS (
      SELECT 1 FROM leave_balance b
      WHERE b.employee_id = e.id AND b.leave_type = 'COMP_OFF' AND b.year IS NULL
  );

INSERT INTO employee_salary_profile (
    employee_id, scheme_id, base_salary, ss_base, hf_base, performance_base, probation_ratio, effective_date
)
SELECT e.id, @scheme, 10000.00, 9000.00, 9000.00, 1500.00, 0.8000, e.hire_date
FROM employee e
WHERE e.mobile IN ('13800000006','13800000007','13800000008')
  AND @scheme IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM employee_salary_profile s WHERE s.employee_id = e.id);

-- ---------- 3. 已离职员工（TC-EMP-005 → 30003）----------
INSERT INTO sys_user (username, password_hash, password_changed_at, employee_id, status, created_at, updated_at)
SELECT '13800000010', @pwd, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = '13800000010');

INSERT INTO employee (
    employee_no, user_id, name, gender, mobile, email,
    department_id, position_id, grade, manager_id, work_location,
    hire_date, employment_type, employment_status, deleted
)
SELECT '202603099', NULL, '已离职-钱十', 'MALE', '13800000010', 'resigned@demo.local',
       4, @pos_fe, 'P2', @mgr_emp, '总部',
       '2023-01-01', 'fulltime', 40, 0
WHERE NOT EXISTS (SELECT 1 FROM employee WHERE mobile = '13800000010');

UPDATE employee e JOIN sys_user u ON u.username = e.mobile
SET e.user_id = u.id WHERE e.mobile = '13800000010';
UPDATE sys_user u JOIN employee e ON e.mobile = u.username
SET u.employee_id = e.id, u.status = 0 WHERE u.username = '13800000010';

-- ---------- 4. 工作日 / 节假日 ----------
INSERT INTO workday_config (day_of_week, is_workday)
SELECT v.d, v.w FROM (
    SELECT 1 AS d, 1 AS w UNION ALL SELECT 2, 1 UNION ALL SELECT 3, 1
    UNION ALL SELECT 4, 1 UNION ALL SELECT 5, 1 UNION ALL SELECT 6, 0 UNION ALL SELECT 7, 0
) v
WHERE NOT EXISTS (SELECT 1 FROM workday_config w WHERE w.day_of_week = v.d);

INSERT INTO holiday_calendar (holiday_date, name)
SELECT v.dt, v.nm FROM (
    SELECT '2026-01-01' AS dt, '元旦' AS nm
    UNION ALL SELECT '2026-05-01', '劳动节'
    UNION ALL SELECT '2026-10-01', '国庆'
    UNION ALL SELECT '2026-10-02', '国庆调休'
    UNION ALL SELECT '2026-10-03', '国庆调休'
) v
WHERE NOT EXISTS (SELECT 1 FROM holiday_calendar h WHERE h.holiday_date = v.dt);

-- ---------- 5. 个人/部门统计用月汇总（TC-STAT-001/002/003）----------
INSERT INTO attendance_monthly_summary (
    employee_id, period, should_attend_days, actual_attend_days,
    late_count, early_leave_count, absent_days, leave_days, overtime_hours
)
SELECT e.id, @period, 22, 20.0, 1, 0, 0.5, 1.0, 4.0
FROM employee e
WHERE e.mobile IN ('13800000002','13800000004','13800000005','13800000006','13800000007')
  AND NOT EXISTS (
      SELECT 1 FROM attendance_monthly_summary m
      WHERE m.employee_id = e.id AND m.period = @period
  );

-- 固定 2026-07 方便脚本写死 period/month
INSERT INTO attendance_monthly_summary (
    employee_id, period, should_attend_days, actual_attend_days,
    late_count, early_leave_count, absent_days, leave_days, overtime_hours
)
SELECT e.id, '2026-07', 22, 19.5, 2, 1, 1.0, 0.5, 6.0
FROM employee e
WHERE e.mobile IN ('13800000002','13800000004','13800000005','13800000006')
  AND NOT EXISTS (
      SELECT 1 FROM attendance_monthly_summary m
      WHERE m.employee_id = e.id AND m.period = '2026-07'
  );

-- ---------- 6. 打卡记录样例（门户打卡页 / 记录列表）----------
INSERT INTO attendance_record (employee_id, punch_date, punch_type, punch_time, punch_status, source)
SELECT e.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'IN',
       CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 08:55:00'), 'NORMAL', 'APP'
FROM employee e
WHERE e.mobile = '13800000004'
  AND NOT EXISTS (
      SELECT 1 FROM attendance_record r
      WHERE r.employee_id = e.id AND r.punch_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY) AND r.punch_type = 'IN'
  );

INSERT INTO attendance_record (employee_id, punch_date, punch_type, punch_time, punch_status, source)
SELECT e.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'OUT',
       CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 18:05:00'), 'NORMAL', 'APP'
FROM employee e
WHERE e.mobile = '13800000004'
  AND NOT EXISTS (
      SELECT 1 FROM attendance_record r
      WHERE r.employee_id = e.id AND r.punch_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY) AND r.punch_type = 'OUT'
  );

-- ---------- 7. 补卡样例（配额已用 1 次，剩 1）----------
INSERT INTO attendance_supplement (employee_id, makeup_date, punch_type, makeup_time, reason, status)
SELECT e.id, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '%Y-%m-%d'), 'IN',
       CONCAT(DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '%Y-%m-%d'), ' 09:00:00'),
       '忘记打卡-种子', 'APPROVED'
FROM employee e
WHERE e.mobile = '13800000004'
  AND NOT EXISTS (
      SELECT 1 FROM attendance_supplement s
      WHERE s.employee_id = e.id AND s.reason = '忘记打卡-种子'
  );

-- ---------- 8. 日汇总样例 ----------
INSERT INTO attendance_daily_summary (
    employee_id, summary_date, day_status, leave_days, overtime_hours
)
SELECT e.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'NORMAL', 0, 0
FROM employee e
WHERE e.mobile IN ('13800000004','13800000006')
  AND NOT EXISTS (
      SELECT 1 FROM attendance_daily_summary d
      WHERE d.employee_id = e.id AND d.summary_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
  );

-- ---------- 校验 ----------
SELECT 'accounts' AS section, u.username, r.code AS role, e.id AS emp_id, e.name, e.employment_status, d.name AS dept
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
LEFT JOIN employee e ON e.id = u.employee_id
LEFT JOIN department d ON d.id = e.department_id
WHERE u.username LIKE '138000000%'
ORDER BY u.username;

SELECT 'monthly' AS section, e.name, m.period, m.actual_attend_days, m.late_count
FROM attendance_monthly_summary m
JOIN employee e ON e.id = m.employee_id
WHERE e.mobile IN ('13800000004','13800000002')
ORDER BY m.period, e.name;

SELECT 'sensitive' AS section, e.name,
       CASE WHEN p.id_number_enc LIKE 'ENC_%' THEN 'PLACEHOLDER' ELSE 'AES_OK' END AS id_enc_status,
       CASE WHEN b.employee_id IS NULL THEN 'NO_BANK' ELSE 'HAS_BANK' END AS bank
FROM employee e
LEFT JOIN employee_personal p ON p.employee_id = e.id
LEFT JOIN employee_bank b ON b.employee_id = e.id
WHERE e.mobile IN ('13800000001','13800000004');
