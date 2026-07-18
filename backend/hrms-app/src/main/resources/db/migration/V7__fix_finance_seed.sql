-- ============================================================
-- V7: 修复 V6 FINANCE 种子冲突 + 恢复 EMPLOYEE 13800001999
-- 原因：本地已存在 sys_user.id=1006（13800001999），V6 按主键冲突未改用户名，
--       却把 FINANCE/employee_id=106 绑到了该账号。
-- FINANCE 登录：13800001006 / Admin@123（新用户 id=1008）
-- ============================================================

-- 1) 恢复 13800001999 为普通员工（EMPLOYEE），去掉误绑的 FINANCE
DELETE ur FROM sys_user_role ur
INNER JOIN sys_role r ON r.id = ur.role_id
WHERE ur.user_id = 1006 AND r.code = 'FINANCE';

INSERT INTO employee (id, employee_no, user_id, name, gender, mobile, email,
                      department_id, position_id, grade, manager_id, work_location,
                      hire_date, employment_type, employment_status, probation_pay_ratio, deleted)
SELECT
    108, '202610108', 1006, '测试员工', 'MALE', '13800001999', 'employee@example.com',
    10, 20, 'P2', 102, '北京',
    '2025-03-01', 'fulltime', 20, 1.00, 0
FROM DUAL
WHERE EXISTS (SELECT 1 FROM department WHERE id = 10)
  AND EXISTS (SELECT 1 FROM position WHERE id = 20)
  AND NOT EXISTS (SELECT 1 FROM employee WHERE id = 108)
  AND NOT EXISTS (SELECT 1 FROM employee WHERE employee_no = '202610108')
  AND NOT EXISTS (SELECT 1 FROM employee WHERE mobile = '13800001999');

UPDATE sys_user SET employee_id = 108 WHERE id = 1006 AND username = '13800001999';

INSERT INTO sys_user_role (user_id, role_id)
SELECT 1006, r.id FROM sys_role r
WHERE r.code = 'EMPLOYEE'
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role ur
      WHERE ur.user_id = 1006 AND ur.role_id = r.id
  );

INSERT INTO employee_personal (employee_id, id_number_enc, id_number_hash, birthday, emergency_contact, emergency_phone)
SELECT 108, 'ENC:110101199909099999', SHA2('110101199909099999', 256), '1999-09-09', '紧急联系人G', '13900000099'
FROM DUAL
WHERE EXISTS (SELECT 1 FROM employee WHERE id = 108)
  AND NOT EXISTS (SELECT 1 FROM employee_personal WHERE employee_id = 108);

-- 2) 独立 FINANCE 账号 1008 / 13800001006 → 员工 106
INSERT INTO sys_user (id, username, password_hash, password_changed_at, employee_id, status, created_at, updated_at)
SELECT
    1008,
    '13800001006',
    '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22',
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    106,
    1,
    NOW(),
    NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1008)
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = '13800001006');

UPDATE sys_user SET
    password_hash = '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22',
    password_changed_at = DATE_SUB(NOW(), INTERVAL 1 DAY),
    employee_id = 106,
    status = 1
WHERE username = '13800001006';

UPDATE employee SET user_id = (SELECT id FROM sys_user WHERE username = '13800001006' LIMIT 1)
WHERE id = 106;

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
CROSS JOIN sys_role r
WHERE u.username = '13800001006'
  AND r.code = 'FINANCE'
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

UPDATE department SET head_employee_id = 106
WHERE id = 12 AND (head_employee_id IS NULL OR head_employee_id = 0 OR head_employee_id = 106);
