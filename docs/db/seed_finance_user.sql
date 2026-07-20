-- 本地联调：财务角色
-- 财务经理 13800001006 / Admin@123  → 可进审批中心（调岗调薪）
-- 财务专员 13800001010 / Admin@123  → 仅薪资管理，无审批中心
SET NAMES utf8mb4;

INSERT INTO sys_role (code, name, data_scope)
SELECT 'FINANCE_MANAGER', '财务经理', 'PAYROLL' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'FINANCE_MANAGER');

INSERT INTO sys_user (id, username, password_hash, password_changed_at, employee_id, status)
VALUES
    (1008, '13800001006', '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22', NOW(), NULL, 1),
    (1010, '13800001010', '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22', NOW(), NULL, 1)
ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash), status=1, username=VALUES(username);

INSERT INTO employee (
    id, employee_no, user_id, name, gender, mobile, email,
    department_id, position_id, grade, manager_id, work_location,
    hire_date, employment_type, employment_status, probation_pay_ratio, deleted
)
VALUES
    (106, '202612001', 1008, CONVERT(UNHEX('E992B1E585AB') USING utf8mb4), 'FEMALE', '13800001006', 'finance@example.com',
     12, 24, 'S4', NULL, CONVERT(UNHEX('E58C97E4BAAC') USING utf8mb4),
     '2024-05-01', 'fulltime', 20, 1.00, 0),
    (111, '202612002', 1010, CONVERT(UNHEX('E8B4A2E58AA1E4B893E59198') USING utf8mb4), 'FEMALE', '13800001010', 'finance.staff@example.com',
     12, 24, 'S2', 106, CONVERT(UNHEX('E58C97E4BAAC') USING utf8mb4),
     '2025-03-01', 'fulltime', 20, 1.00, 0)
ON DUPLICATE KEY UPDATE
    name=VALUES(name), mobile=VALUES(mobile), department_id=VALUES(department_id),
    position_id=VALUES(position_id), employment_status=20, deleted=0;

UPDATE sys_user SET employee_id = 106 WHERE id = 1008;
UPDATE sys_user SET employee_id = 111 WHERE id = 1010;
UPDATE department SET head_employee_id = 106 WHERE id = 12;

DELETE ur FROM sys_user_role ur
JOIN sys_role r ON r.id = ur.role_id
WHERE ur.user_id IN (1008, 1010) AND r.code IN ('FINANCE', 'FINANCE_MANAGER');

INSERT INTO sys_user_role (user_id, role_id)
SELECT 1008, id FROM sys_role WHERE code = 'FINANCE_MANAGER';
INSERT INTO sys_user_role (user_id, role_id)
SELECT 1010, id FROM sys_role WHERE code = 'FINANCE';
