-- ============================================================
-- HRMS 本地联调种子数据（dev）
-- 适用库：hrms
-- 默认登录密码（全部测试账号）：Admin@123
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 组织：公司 / 技术部 / 人力资源部
-- ------------------------------------------------------------
INSERT INTO department (id, name, code, parent_id, path, level, head_employee_id, sort_order, description, deleted)
VALUES
    (1,  '数字马力',   '00', NULL, '/1/',     1, NULL, 0, '总公司', 0),
    (10, '技术部',     '10', 1,    '/1/10/',  2, NULL, 1, '研发中心', 0),
    (11, '人力资源部', '11', 1,    '/1/11/',  2, NULL, 2, 'HR', 0),
    (12, '财务部',     '12', 1,    '/1/12/',  2, NULL, 3, '财务', 0)
ON DUPLICATE KEY UPDATE name=VALUES(name), path=VALUES(path);

-- ------------------------------------------------------------
-- 2. 职位
-- ------------------------------------------------------------
INSERT INTO position (id, name, sequence, department_id, rank_min, rank_max, default_probation_months, is_standard, description, deleted)
VALUES
    (20, 'Java开发工程师', 'P', 10, 'P1', 'P7', 3, 1, '后端开发', 0),
    (21, '前端开发工程师', 'P', 10, 'P1', 'P7', 3, 1, '前端开发', 0),
    (22, 'HR专员',         'S', 11, 'S1', 'S5', 3, 1, '人力资源', 0),
    (23, '部门经理',       'M', 10, 'M1', 'M3', 3, 0, '非标职位（触发入职二审）', 0),
    (24, '财务专员',       'S', 12, 'S1', 'S5', 3, 1, '财务核算', 0)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ------------------------------------------------------------
-- 3. 薪资账套
-- ------------------------------------------------------------
INSERT INTO payroll_scheme (id, name, description, effective_date, status, deleted)
VALUES
    (1, '标准月薪账套', '本地联调默认账套', '2026-01-01', 'enabled', 0)
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO payroll_scheme_item (scheme_id, item_code, item_name, item_type, calc_rule, sort_order)
SELECT 1, 'BASE', '基本工资', 'FIXED', NULL, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM payroll_scheme_item WHERE scheme_id=1 AND item_code='BASE');

-- ------------------------------------------------------------
-- 4. 系统用户（固定 ID，对齐审批演示账号）
--    1001 HR李四 / 1002 部门负责人王五 / 1003 HR赵六 / 1004 代审孙七 / 1005 管理员
--    1008 财务钱八（勿用 1006/1007，本地常被联调账号占用）
--    密码均为 Admin@123
-- ------------------------------------------------------------
INSERT INTO sys_user (id, username, password_hash, password_changed_at, employee_id, status)
VALUES
    (1001, '13800001001', '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22', NOW(), NULL, 1),
    (1002, '13800001002', '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22', NOW(), NULL, 1),
    (1003, '13800001003', '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22', NOW(), NULL, 1),
    (1004, '13800001004', '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22', NOW(), NULL, 1),
    (1005, '13800001005', '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22', NOW(), NULL, 1),
    (1008, '13800001006', '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22', NOW(), NULL, 1)
ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash), status=1;

-- ------------------------------------------------------------
-- 5. 员工档案（与用户绑定）
-- ------------------------------------------------------------
INSERT INTO employee (id, employee_no, user_id, name, gender, mobile, email, department_id, position_id, grade, manager_id, work_location, hire_date, employment_type, employment_status, probation_pay_ratio, deleted)
VALUES
    (101, '202611001', 1001, '李四', 'FEMALE', '13800001001', 'lisi.hr@example.com',     11, 22, 'S3', NULL, '北京', '2024-03-01', 'fulltime', 20, 1.00, 0),
    (102, '202610001', 1002, '王五', 'MALE',   '13800001002', 'wangwu.mgr@example.com',  10, 23, 'M2', NULL, '北京', '2023-06-01', 'fulltime', 20, 1.00, 0),
    (103, '202611002', 1003, '赵六', 'FEMALE', '13800001003', 'zhaoliu.hr@example.com',  11, 22, 'S2', 101,  '北京', '2024-08-01', 'fulltime', 20, 1.00, 0),
    (104, '202610002', 1004, '孙七', 'MALE',   '13800001004', 'sunqi.dev@example.com',   10, 20, 'P4', 102,  '北京', '2025-01-15', 'fulltime', 20, 1.00, 0),
    (105, '202600001', 1005, '管理员', 'MALE', '13800001005', 'admin@example.com',       1,  23, 'M3', NULL, '北京', '2022-01-01', 'fulltime', 20, 1.00, 0),
    (106, '202612001', 1008, '钱八', 'FEMALE', '13800001006', 'finance@example.com',     12, 24, 'S3', NULL, '北京', '2024-05-01', 'fulltime', 20, 1.00, 0)
ON DUPLICATE KEY UPDATE name=VALUES(name), department_id=VALUES(department_id), position_id=VALUES(position_id);

UPDATE sys_user SET employee_id = 101 WHERE id = 1001;
UPDATE sys_user SET employee_id = 102 WHERE id = 1002;
UPDATE sys_user SET employee_id = 103 WHERE id = 1003;
UPDATE sys_user SET employee_id = 104 WHERE id = 1004;
UPDATE sys_user SET employee_id = 105 WHERE id = 1005;
UPDATE sys_user SET employee_id = 106 WHERE id = 1008;

UPDATE department SET head_employee_id = 102 WHERE id = 10;
UPDATE department SET head_employee_id = 101 WHERE id = 11;
UPDATE department SET head_employee_id = 105 WHERE id = 1;
UPDATE department SET head_employee_id = 106 WHERE id = 12;

-- 员工个人信息（身份证字段本地联调用占位密文/哈希）
INSERT INTO employee_personal (employee_id, id_number_enc, id_number_hash, birthday, emergency_contact, emergency_phone)
VALUES
    (101, 'ENC:110101199001011111', SHA2('110101199001011111', 256), '1990-01-01', '紧急联系人A', '13900000001'),
    (102, 'ENC:110101198805052222', SHA2('110101198805052222', 256), '1988-05-05', '紧急联系人B', '13900000002'),
    (103, 'ENC:110101199203033333', SHA2('110101199203033333', 256), '1992-03-03', '紧急联系人C', '13900000003'),
    (104, 'ENC:110101199507074444', SHA2('110101199507074444', 256), '1995-07-07', '紧急联系人D', '13900000004'),
    (105, 'ENC:110101198001015555', SHA2('110101198001015555', 256), '1980-01-01', '紧急联系人E', '13900000005'),
    (106, 'ENC:110101199404044444', SHA2('110101199404044444', 256), '1994-04-04', '紧急联系人F', '13900000006')
ON DUPLICATE KEY UPDATE emergency_contact=VALUES(emergency_contact);

INSERT INTO employee_contract (employee_id, contract_type, contract_expire_date, probation_salary_ratio, scheme_id, base_salary)
VALUES
    (101, 'FIXED', '2027-03-01', 1.0000, 1, 12000.00),
    (102, 'UNFIXED', NULL,       1.0000, 1, 25000.00),
    (103, 'FIXED', '2027-08-01', 1.0000, 1, 10000.00),
    (104, 'FIXED', '2028-01-15', 1.0000, 1, 18000.00),
    (105, 'UNFIXED', NULL,       1.0000, 1, 30000.00),
    (106, 'FIXED', '2027-05-01', 1.0000, 1, 15000.00)
ON DUPLICATE KEY UPDATE base_salary=VALUES(base_salary);

INSERT INTO employee_bank (employee_id, bank_account_enc, bank_account_tail, bank_name)
VALUES
    (101, 'ENC:6222021000000001001', '1001', '工商银行'),
    (102, 'ENC:6222021000000001002', '1002', '建设银行'),
    (103, 'ENC:6222021000000001003', '1003', '农业银行'),
    (104, 'ENC:6222021000000001004', '1004', '招商银行'),
    (105, 'ENC:6222021000000001005', '1005', '中国银行'),
    (106, 'ENC:6222021000000001006', '1006', '交通银行')
ON DUPLICATE KEY UPDATE bank_name=VALUES(bank_name), bank_account_tail=VALUES(bank_account_tail);

INSERT INTO employee_salary_profile (employee_id, scheme_id, base_salary, ss_base, hf_base, performance_base, probation_ratio, effective_date)
VALUES
    (101, 1, 12000.00, 12000.00, 12000.00, 2000.00, 1.0000, '2024-03-01'),
    (102, 1, 25000.00, 20000.00, 20000.00, 5000.00, 1.0000, '2023-06-01'),
    (103, 1, 10000.00, 10000.00, 10000.00, 1500.00, 1.0000, '2024-08-01'),
    (104, 1, 18000.00, 15000.00, 15000.00, 3000.00, 1.0000, '2025-01-15'),
    (105, 1, 30000.00, 20000.00, 20000.00, 8000.00, 1.0000, '2022-01-01'),
    (106, 1, 15000.00, 15000.00, 15000.00, 2000.00, 1.0000, '2024-05-01')
ON DUPLICATE KEY UPDATE base_salary=VALUES(base_salary);

-- ------------------------------------------------------------
-- 6. 角色绑定
--    sys_role: 1 SYS_ADMIN / 2 HR_STAFF / 3 DEPT_MANAGER / 4 FINANCE / 5 EMPLOYEE
-- ------------------------------------------------------------
INSERT INTO sys_user_role (user_id, role_id)
VALUES
    (1005, 1),
    (1001, 2),
    (1003, 2),
    (1002, 3),
    (1008, 4),
    (1004, 5)
ON DUPLICATE KEY UPDATE created_at=VALUES(created_at);

-- ------------------------------------------------------------
-- 7. 基础权限 + 角色授权（便于后续登录鉴权）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, module, type)
VALUES
    (1,  'menu:home',              '工作台',     'auth',       'MENU'),
    (2,  'menu:org',               '组织管理',   'org',        'MENU'),
    (3,  'menu:employee',          '员工档案',   'employee',   'MENU'),
    (4,  'menu:onboarding',        '入转调离',   'workflow',   'MENU'),
    (5,  'menu:approval',          '审批中心',   'workflow',   'MENU'),
    (6,  'menu:attendance',        '考勤管理',   'attendance', 'MENU'),
    (7,  'menu:payroll',           '薪资管理',   'payroll',    'MENU'),
    (8,  'onboarding:create',      '创建入职申请', 'workflow', 'BUTTON'),
    (9,  'onboarding:submit',      '提交入职申请', 'workflow', 'BUTTON'),
    (10, 'approval:action',        '审批操作',   'workflow',   'BUTTON'),
    (11, 'employee:view',          '查看员工',   'employee',   'API'),
    (12, 'employee:edit',          '编辑员工',   'employee',   'API')
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission
ON DUPLICATE KEY UPDATE created_at=VALUES(created_at);

INSERT INTO sys_role_permission (role_id, permission_id)
VALUES
    (2, 1),(2, 3),(2, 4),(2, 5),(2, 8),(2, 9),(2, 10),(2, 11),(2, 12),
    (3, 1),(3, 4),(3, 5),(3, 10),(3, 11),
    (4, 1),(4, 7),
    (5, 1)
ON DUPLICATE KEY UPDATE created_at=VALUES(created_at);

-- ------------------------------------------------------------
-- 8. 审批流程定义（与内存演示节点一致）
-- ------------------------------------------------------------
INSERT INTO approval_process_def (id, process_type, name, nodes_json, sla_hours, status)
VALUES
    (1, 'ONBOARDING', '入职审批',
     JSON_ARRAY(
       JSON_OBJECT('order', 1, 'label', '部门负责人审批', 'assigneeUserId', 1002, 'optional', false),
       JSON_OBJECT('order', 2, 'label', 'HR二审', 'assigneeUserId', 1003, 'optional', true, 'condition', 'needSecondApproval')
     ), 48, 1),
    (2, 'REGULARIZATION', '转正审批',
     JSON_ARRAY(
       JSON_OBJECT('order', 1, 'label', '部门负责人审批', 'assigneeUserId', 1002, 'optional', false),
       JSON_OBJECT('order', 2, 'label', 'HR审批', 'assigneeUserId', 1001, 'optional', false)
     ), 48, 1),
    (3, 'TRANSFER', '调岗审批',
     JSON_ARRAY(
       JSON_OBJECT('order', 1, 'label', '原部门负责人', 'assigneeUserId', 1002, 'optional', false),
       JSON_OBJECT('order', 2, 'label', 'HR审批', 'assigneeUserId', 1001, 'optional', false)
     ), 72, 1),
    (4, 'RESIGNATION', '离职审批',
     JSON_ARRAY(
       JSON_OBJECT('order', 1, 'label', '部门负责人审批', 'assigneeUserId', 1002, 'optional', false),
       JSON_OBJECT('order', 2, 'label', 'HR审批', 'assigneeUserId', 1001, 'optional', false)
     ), 72, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), nodes_json=VALUES(nodes_json), status=1;

-- ------------------------------------------------------------
-- 9. 考勤组示例
-- ------------------------------------------------------------
INSERT INTO attendance_group (id, name, shift_type, work_start_time, work_end_time, lunch_start_time, lunch_end_time, late_threshold_minutes, early_leave_threshold_minutes, deleted)
VALUES
    (1, '默认固定班', 'FIXED', '09:00:00', '18:00:00', '12:00:00', '13:00:00', 15, 15, 0)
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO attendance_group_member (group_id, employee_id)
VALUES (1, 101), (1, 102), (1, 103), (1, 104), (1, 105), (1, 106)
ON DUPLICATE KEY UPDATE group_id=VALUES(group_id);

INSERT INTO attendance_group_scope (group_id, scope_type, scope_id)
SELECT 1, 'DEPARTMENT', 10 FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM attendance_group_scope WHERE group_id=1 AND scope_type='DEPARTMENT' AND scope_id=10
);

-- ------------------------------------------------------------
-- 10. 节假日（示例）
-- ------------------------------------------------------------
INSERT INTO holiday_calendar (holiday_date, name)
VALUES
    ('2026-01-01', '元旦'),
    ('2026-01-02', '元旦调休'),
    ('2026-02-16', '春节'),
    ('2026-02-17', '春节'),
    ('2026-05-01', '劳动节'),
    ('2026-10-01', '国庆节')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ------------------------------------------------------------
-- 11. 门户「我的薪资」联调：近6个月已发放工资条（员工 104 孙七）
-- ------------------------------------------------------------
INSERT INTO payroll_batch (id, period, status, total_count, success_count, gross_total, net_total, anomaly_count, attendance_locked, created_by)
VALUES
    (201, '2026-02', 'DISTRIBUTED', 1, 1, 21000.00, 16800.00, 0, 1, 1001),
    (202, '2026-03', 'DISTRIBUTED', 1, 1, 21500.00, 17150.00, 0, 1, 1001),
    (203, '2026-04', 'DISTRIBUTED', 1, 1, 22000.00, 17500.00, 0, 1, 1001),
    (204, '2026-05', 'DISTRIBUTED', 1, 1, 21800.00, 17320.00, 0, 1, 1001),
    (205, '2026-06', 'DISTRIBUTED', 1, 1, 22500.00, 17900.00, 0, 1, 1001),
    (206, '2026-07', 'DISTRIBUTED', 1, 1, 23000.00, 18250.00, 0, 1, 1001)
ON DUPLICATE KEY UPDATE status=VALUES(status), gross_total=VALUES(gross_total), net_total=VALUES(net_total);

INSERT INTO payroll_detail (batch_id, employee_id, calc_status, gross_salary, net_salary, detail_json, prev_net_salary, manual_adjusted)
VALUES
    (201, 104, 'SUCCESS', 21000.00, 16800.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 3000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 660, 'type', 'DEDUCTION')
     ), NULL, 0),
    (202, 104, 'SUCCESS', 21500.00, 17150.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 3500, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 810, 'type', 'DEDUCTION')
     ), 16800.00, 0),
    (203, 104, 'SUCCESS', 22000.00, 17500.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 4000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 960, 'type', 'DEDUCTION')
     ), 17150.00, 0),
    (204, 104, 'SUCCESS', 21800.00, 17320.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 3800, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 940, 'type', 'DEDUCTION')
     ), 17500.00, 0),
    (205, 104, 'SUCCESS', 22500.00, 17900.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 4500, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 1060, 'type', 'DEDUCTION')
     ), 17320.00, 0),
    (206, 104, 'SUCCESS', 23000.00, 18250.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 5000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 1210, 'type', 'DEDUCTION')
     ), 17900.00, 0)
ON DUPLICATE KEY UPDATE gross_salary=VALUES(gross_salary), net_salary=VALUES(net_salary), detail_json=VALUES(detail_json);

-- 自增起点，避免后续插入与固定 ID 冲突
ALTER TABLE department AUTO_INCREMENT = 100;
ALTER TABLE position AUTO_INCREMENT = 100;
ALTER TABLE sys_user AUTO_INCREMENT = 2000;
ALTER TABLE employee AUTO_INCREMENT = 1000;
ALTER TABLE payroll_scheme AUTO_INCREMENT = 10;
ALTER TABLE approval_process_def AUTO_INCREMENT = 10;
ALTER TABLE attendance_group AUTO_INCREMENT = 10;
ALTER TABLE sys_permission AUTO_INCREMENT = 100;
ALTER TABLE payroll_batch AUTO_INCREMENT = 300;
