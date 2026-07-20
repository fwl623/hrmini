-- ============================================================
-- 本地联调：追加 1 名普通员工账号
-- 登录：13800001009 / Admin@123
-- 角色：EMPLOYEE；档案：周九（前端开发）
-- ============================================================

SET NAMES utf8mb4;

-- 系统用户
INSERT INTO sys_user (id, username, password_hash, password_changed_at, employee_id, status)
VALUES
    (1009, '13800001009', '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22', NOW(), NULL, 1)
ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash), status=1, username=VALUES(username);

-- 员工档案（技术部 / 前端 / 直属王五）
INSERT INTO employee (
    id, employee_no, user_id, name, gender, mobile, email,
    department_id, position_id, grade, manager_id, work_location,
    hire_date, employment_type, employment_status, probation_pay_ratio, deleted
)
VALUES
    (110, '202610003', 1009, '周九', 'MALE', '13800001009', 'zhoujiu.fe@example.com',
     10, 21, 'P3', 102, '北京',
     '2025-06-01', 'fulltime', 20, 1.00, 0)
ON DUPLICATE KEY UPDATE
    name=VALUES(name),
    mobile=VALUES(mobile),
    department_id=VALUES(department_id),
    position_id=VALUES(position_id),
    manager_id=VALUES(manager_id),
    employment_status=VALUES(employment_status),
    deleted=0;

UPDATE sys_user SET employee_id = 110 WHERE id = 1009;

-- 角色：普通员工
INSERT INTO sys_user_role (user_id, role_id)
VALUES (1009, 5)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 个人信息 / 合同 / 银行卡 / 薪资档案
INSERT INTO employee_personal (employee_id, id_number_enc, id_number_hash, birthday, emergency_contact, emergency_phone)
VALUES
    (110, 'ENC:110101199609096666', SHA2('110101199609096666', 256), '1996-09-09', '紧急联系人G', '13900000009')
ON DUPLICATE KEY UPDATE emergency_contact=VALUES(emergency_contact);

INSERT INTO employee_contract (employee_id, contract_type, contract_expire_date, probation_salary_ratio, scheme_id, base_salary)
VALUES
    (110, 'FIXED', '2028-06-01', 1.0000, 1, 16000.00)
ON DUPLICATE KEY UPDATE base_salary=VALUES(base_salary);

INSERT INTO employee_bank (employee_id, bank_account_enc, bank_account_tail, bank_name)
VALUES
    (110, 'ENC:6222021000000001009', '1009', '招商银行')
ON DUPLICATE KEY UPDATE bank_name=VALUES(bank_name), bank_account_tail=VALUES(bank_account_tail);

INSERT INTO employee_salary_profile (employee_id, scheme_id, base_salary, ss_base, hf_base, performance_base, probation_ratio, effective_date)
VALUES
    (110, 1, 16000.00, 14000.00, 14000.00, 2500.00, 1.0000, '2025-06-01')
ON DUPLICATE KEY UPDATE base_salary=VALUES(base_salary);
