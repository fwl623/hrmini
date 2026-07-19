-- ============================================================
-- 本地全链路联调种子数据（可重复执行，按手机号幂等）
-- 统一密码：Admin@12345
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 1. 补充权限码（前端 access / 菜单用）----------
INSERT INTO sys_permission (code, name, module, type)
SELECT v.code, v.name, v.module, v.type
FROM (
    SELECT 'menu:attendance' AS code, '考勤菜单' AS name, 'attendance' AS module, 'MENU' AS type
    UNION ALL SELECT 'menu:payroll', '薪资菜单', 'payroll', 'MENU'
    UNION ALL SELECT 'menu:workflow', '流程菜单', 'workflow', 'MENU'
    UNION ALL SELECT 'payroll:view', '薪资查看', 'payroll', 'API'
    UNION ALL SELECT 'approval:handle', '审批处理', 'workflow', 'API'
    UNION ALL SELECT 'attendance:manage', '考勤管理', 'attendance', 'API'
) v
WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.code = v.code);

-- SYS_ADMIN：绑定全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'SYS_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- HR_STAFF：组织/员工/工作台/考勤/薪资/审批（无系统管理）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'HR_STAFF'
  AND p.code IN (
    'menu:workbench', 'menu:org', 'menu:employee', 'menu:attendance', 'menu:payroll', 'menu:workflow',
    'org:dept:view', 'org:dept:edit', 'org:position:view', 'org:position:edit',
    'payroll:view', 'approval:handle', 'attendance:manage'
  )
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- DEPT_MANAGER：本部门组织/花名册/审批，无系统与薪资菜单码
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'DEPT_MANAGER'
  AND p.code IN (
    'menu:workbench', 'menu:org', 'menu:employee', 'menu:workflow',
    'org:dept:view', 'org:position:view', 'approval:handle'
  )
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- FINANCE：仅工作台 + 薪资（PRD：组织/审批/考勤/花名册均不可见）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'FINANCE'
  AND p.code IN ('menu:workbench', 'menu:payroll', 'payroll:view')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 收回历史误授的组织权限（可重复执行）
DELETE rp FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.code = 'FINANCE'
  AND p.code IN ('org:dept:view', 'org:position:view', 'org:dept:edit', 'org:position:edit', 'menu:org',
                 'menu:employee', 'menu:workflow', 'menu:attendance', 'approval:handle', 'attendance:manage');

-- ---------- 2. 职位 ----------
INSERT INTO position (name, sequence, department_id, rank_min, rank_max, default_probation_months, is_standard, description)
SELECT 'HR专员', 'S', 1, 'S1', 'S5', 3, 1, '人事行政'
WHERE NOT EXISTS (SELECT 1 FROM position WHERE name = 'HR专员' AND deleted = 0);

INSERT INTO position (name, sequence, department_id, rank_min, rank_max, default_probation_months, is_standard, description)
SELECT '前端主管', 'M', 4, 'M1', 'M3', 3, 1, '前端团队负责人'
WHERE NOT EXISTS (SELECT 1 FROM position WHERE name = '前端主管' AND deleted = 0);

INSERT INTO position (name, sequence, department_id, rank_min, rank_max, default_probation_months, is_standard, description)
SELECT '财务专员', 'S', 1, 'S1', 'S5', 3, 1, '薪资核算'
WHERE NOT EXISTS (SELECT 1 FROM position WHERE name = '财务专员' AND deleted = 0);

INSERT INTO position (name, sequence, department_id, rank_min, rank_max, default_probation_months, is_standard, description)
SELECT '销售专员', 'S', 3, 'S1', 'S4', 3, 1, '销售业务'
WHERE NOT EXISTS (SELECT 1 FROM position WHERE name = '销售专员' AND deleted = 0);

-- ---------- 3. 账号（密码均为 Admin@12345）----------
SET @pwd := '$2a$12$9bAzztAJZqkz5P9Fz3A4YONhIj9RNLHBC0Yvl6jKY6NLRqyiUKV8G';

INSERT INTO sys_user (username, password_hash, password_changed_at, employee_id, status, created_at, updated_at)
SELECT '13800000001', @pwd, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = '13800000001');

INSERT INTO sys_user (username, password_hash, password_changed_at, employee_id, status, created_at, updated_at)
SELECT '13800000002', @pwd, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = '13800000002');

INSERT INTO sys_user (username, password_hash, password_changed_at, employee_id, status, created_at, updated_at)
SELECT '13800000003', @pwd, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = '13800000003');

INSERT INTO sys_user (username, password_hash, password_changed_at, employee_id, status, created_at, updated_at)
SELECT '13800000004', @pwd, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = '13800000004');

INSERT INTO sys_user (username, password_hash, password_changed_at, employee_id, status, created_at, updated_at)
SELECT '13800000005', @pwd, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = '13800000005');

-- ---------- 4. 员工档案 ----------
SET @pos_fe := (SELECT id FROM position WHERE name = '前端开发工程师' AND deleted = 0 LIMIT 1);
SET @pos_hr := (SELECT id FROM position WHERE name = 'HR专员' AND deleted = 0 LIMIT 1);
SET @pos_mgr := (SELECT id FROM position WHERE name = '前端主管' AND deleted = 0 LIMIT 1);
SET @pos_fin := (SELECT id FROM position WHERE name = '财务专员' AND deleted = 0 LIMIT 1);
SET @pos_sale := (SELECT id FROM position WHERE name = '销售专员' AND deleted = 0 LIMIT 1);

INSERT INTO employee (
    employee_no, user_id, name, gender, mobile, email,
    department_id, position_id, grade, manager_id, work_location,
    hire_date, employment_type, employment_status, probation_pay_ratio, probation_end_date, deleted
)
SELECT '2026Z0001', NULL, '王人事', 'FEMALE', '13800000001', 'hr@demo.local',
       1, @pos_hr, 'S3', NULL, '总部',
       '2024-01-15', 'fulltime', 20, NULL, NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM employee WHERE mobile = '13800000001');

INSERT INTO employee (
    employee_no, user_id, name, gender, mobile, email,
    department_id, position_id, grade, manager_id, work_location,
    hire_date, employment_type, employment_status, probation_pay_ratio, probation_end_date, deleted
)
SELECT '202603001', NULL, '李主管', 'MALE', '13800000002', 'mgr@demo.local',
       4, @pos_mgr, 'M2', NULL, '总部',
       '2023-06-01', 'fulltime', 20, NULL, NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM employee WHERE mobile = '13800000002');

INSERT INTO employee (
    employee_no, user_id, name, gender, mobile, email,
    department_id, position_id, grade, manager_id, work_location,
    hire_date, employment_type, employment_status, probation_pay_ratio, probation_end_date, deleted
)
SELECT '2026Z0002', NULL, '赵财务', 'FEMALE', '13800000003', 'finance@demo.local',
       1, @pos_fin, 'S2', NULL, '总部',
       '2024-03-01', 'fulltime', 20, NULL, NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM employee WHERE mobile = '13800000003');

INSERT INTO employee (
    employee_no, user_id, name, gender, mobile, email,
    department_id, position_id, grade, manager_id, work_location,
    hire_date, employment_type, employment_status, probation_pay_ratio, probation_end_date, deleted
)
SELECT '202603002', NULL, '张三', 'MALE', '13800000004', 'zhangsan@demo.local',
       4, @pos_fe, 'P3', NULL, '总部',
       '2025-10-01', 'fulltime', 10, 0.80, DATE_ADD('2025-10-01', INTERVAL 6 MONTH), 0
WHERE NOT EXISTS (SELECT 1 FROM employee WHERE mobile = '13800000004');

INSERT INTO employee (
    employee_no, user_id, name, gender, mobile, email,
    department_id, position_id, grade, manager_id, work_location,
    hire_date, employment_type, employment_status, probation_pay_ratio, probation_end_date, deleted
)
SELECT '202602001', NULL, '陈七', 'MALE', '13800000005', 'chenqi@demo.local',
       3, @pos_sale, 'S2', NULL, '总部',
       '2025-01-08', 'fulltime', 20, NULL, NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM employee WHERE mobile = '13800000005');

-- 关联 user <-> employee
UPDATE employee e
JOIN sys_user u ON u.username = e.mobile
SET e.user_id = u.id
WHERE e.mobile IN ('13800000001','13800000002','13800000003','13800000004','13800000005');

UPDATE sys_user u
JOIN employee e ON e.mobile = u.username
SET u.employee_id = e.id
WHERE u.username IN ('13800000001','13800000002','13800000003','13800000004','13800000005');

-- 张三的直属上级 = 李主管；前端部门负责人 = 李主管
SET @mgr_emp := (SELECT id FROM employee WHERE mobile = '13800000002');
UPDATE employee SET manager_id = @mgr_emp WHERE mobile = '13800000004';
UPDATE employee SET manager_id = @mgr_emp WHERE mobile = '13800000005';
UPDATE department SET head_employee_id = @mgr_emp WHERE id = 4;
UPDATE department SET head_employee_id = @mgr_emp WHERE id = 3 AND (head_employee_id IS NULL OR head_employee_id = 0);

-- ---------- 5. 用户角色 ----------
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u CROSS JOIN sys_role r
WHERE u.username = '13800000001' AND r.code = 'HR_STAFF'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u CROSS JOIN sys_role r
WHERE u.username = '13800000002' AND r.code = 'DEPT_MANAGER'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u CROSS JOIN sys_role r
WHERE u.username = '13800000003' AND r.code = 'FINANCE'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u CROSS JOIN sys_role r
WHERE u.username = '13800000004' AND r.code = 'EMPLOYEE'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u CROSS JOIN sys_role r
WHERE u.username = '13800000005' AND r.code = 'EMPLOYEE'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

-- ---------- 6. 个人信息（真实 AES 密文，可用 TC-EMP-008；密钥见 application.yml hrms.crypto.aes-key）----------
-- 明文示例：110101199001011234（王人事）… 详见 seed_local_full.sql
INSERT INTO employee_personal (
    employee_id, id_number_enc, id_number_hash, birthday,
    household_address, residence_address, emergency_contact, emergency_phone
)
SELECT e.id,
       CASE e.mobile
         WHEN '13800000001' THEN 'acgr0nbIm44ke4zoZKwMzzP8z0Qdok7PmRWAkmnrIeK+vJeTOrekrGkTui9CnQ=='
         WHEN '13800000002' THEN 'Q/94smdt7crslUapYOPSSrOrJvKhjCGXdFDEtrEMwYAZX754ScLuCWfuvojfjg=='
         WHEN '13800000003' THEN 'K/xC7GR+z1Fydw3jdPx4hB6TBU/mthXViMLcBDMo4Oq8VDom+KvIt2m+64qiwQ=='
         WHEN '13800000004' THEN 'L+gIMNGkGHBAqWz0AJovMiJOIX1jtT/KcOXkwupsPRM1Q54tVrvCYJULvQnM+Q=='
         WHEN '13800000005' THEN 'CXY6B4P5ngaA9zJXucNfZgtH7SNfHM6NfS2m+vPDB2+cEOyIimKnKpCk//zkGA=='
         ELSE CONCAT('ENC_PLACEHOLDER_', e.id)
       END,
       SHA2(CONCAT('11010119900', RIGHT(e.mobile, 1), '0', RIGHT(e.mobile, 1), '1234'), 256),
       '1990-01-01',
       '北京市东城区', '北京市海淀区', '紧急联系人', '13900000000'
FROM employee e
WHERE e.mobile IN ('13800000001','13800000002','13800000003','13800000004','13800000005')
  AND NOT EXISTS (SELECT 1 FROM employee_personal p WHERE p.employee_id = e.id);

-- ---------- 7. 工号历史（避免生成器撞号）----------
INSERT INTO employee_no_history (employee_no, year, dept_code, employee_id, reuse_flag)
SELECT e.employee_no, LEFT(e.employee_no, 4),
       CASE
         WHEN e.department_id = 1 THEN 'Z0'
         WHEN e.department_id = 2 THEN '01'
         WHEN e.department_id = 3 THEN '02'
         WHEN e.department_id = 4 THEN '03'
         ELSE '00'
       END,
       e.id, 0
FROM employee e
WHERE e.mobile IN ('13800000001','13800000002','13800000003','13800000004','13800000005')
  AND NOT EXISTS (SELECT 1 FROM employee_no_history h WHERE h.employee_no = e.employee_no);

-- ---------- 8. 考勤组 + 成员（打卡联调）----------
INSERT INTO attendance_group (
    name, shift_type, work_start_time, work_end_time,
    lunch_start_time, lunch_end_time, late_threshold_minutes, early_leave_threshold_minutes, deleted
)
SELECT '默认考勤组', 'FIXED', '09:00:00', '18:00:00', '12:00:00', '13:00:00', 15, 15, 0
WHERE NOT EXISTS (SELECT 1 FROM attendance_group WHERE name = '默认考勤组' AND deleted = 0);

SET @ag := (SELECT id FROM attendance_group WHERE name = '默认考勤组' AND deleted = 0 LIMIT 1);

INSERT INTO attendance_group_member (group_id, employee_id)
SELECT @ag, e.id
FROM employee e
WHERE e.mobile IN ('13800000002','13800000004','13800000005')
  AND NOT EXISTS (
      SELECT 1 FROM attendance_group_member m
      WHERE m.group_id = @ag AND m.employee_id = e.id
  );

-- ---------- 9. 假期余额 ----------
INSERT INTO leave_balance (employee_id, leave_type, balance, year, expire_date)
SELECT e.id, 'ANNUAL', 5.0, YEAR(CURDATE()), NULL
FROM employee e
WHERE e.mobile IN ('13800000004','13800000005')
  AND NOT EXISTS (
      SELECT 1 FROM leave_balance b
      WHERE b.employee_id = e.id AND b.leave_type = 'ANNUAL' AND b.year = YEAR(CURDATE())
  );

INSERT INTO leave_balance (employee_id, leave_type, balance, year, expire_date)
SELECT e.id, 'COMP_OFF', 2.0, NULL, DATE_ADD(CURDATE(), INTERVAL 90 DAY)
FROM employee e
WHERE e.mobile IN ('13800000004','13800000005')
  AND NOT EXISTS (
      SELECT 1 FROM leave_balance b
      WHERE b.employee_id = e.id AND b.leave_type = 'COMP_OFF' AND b.year IS NULL
  );

-- ---------- 10. 最小账套 + 薪资档案（财务/HR 测薪资页）----------
INSERT INTO payroll_scheme (name, description, effective_date, status, deleted)
SELECT '标准账套', '联调默认账套', '2026-01-01', 'enabled', 0
WHERE NOT EXISTS (SELECT 1 FROM payroll_scheme WHERE name = '标准账套' AND deleted = 0);

SET @scheme := (SELECT id FROM payroll_scheme WHERE name = '标准账套' AND deleted = 0 LIMIT 1);

INSERT INTO employee_salary_profile (
    employee_id, scheme_id, base_salary, ss_base, hf_base, performance_base, probation_ratio, effective_date
)
SELECT e.id, @scheme, 12000.00, 10000.00, 10000.00, 2000.00, 0.8000, e.hire_date
FROM employee e
WHERE e.mobile IN ('13800000002','13800000004','13800000005')
  AND NOT EXISTS (SELECT 1 FROM employee_salary_profile s WHERE s.employee_id = e.id);

-- ---------- 校验输出 ----------
SELECT u.id AS user_id, u.username, r.code AS role_code, e.id AS employee_id, e.name, e.department_id, d.name AS dept_name
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
LEFT JOIN employee e ON e.id = u.employee_id
LEFT JOIN department d ON d.id = e.department_id
WHERE u.username LIKE '1380000000%'
ORDER BY u.username;
