-- ============================================================
-- 本地联调：追加 1 名普通员工账号
-- 登录：13800001011 / Admin@123
-- 角色：EMPLOYEE；档案：吴十（Java 开发）
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO sys_user (id, username, password_hash, password_changed_at, employee_id, status)
VALUES
    (1011, '13800001011', '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22', NOW(), NULL, 1)
ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash), status=1, username=VALUES(username);

INSERT INTO employee (
    id, employee_no, user_id, name, gender, mobile, email,
    department_id, position_id, grade, manager_id, work_location,
    hire_date, employment_type, employment_status, probation_pay_ratio, deleted
)
VALUES
    (112, '202610004', 1011, '吴十', 'MALE', '13800001011', 'wushi.java@example.com',
     10, 20, 'P3', 102, '北京',
     '2025-09-01', 'fulltime', 20, 1.00, 0)
ON DUPLICATE KEY UPDATE
    name=VALUES(name),
    mobile=VALUES(mobile),
    department_id=VALUES(department_id),
    position_id=VALUES(position_id),
    manager_id=VALUES(manager_id),
    employment_status=VALUES(employment_status),
    deleted=0;

UPDATE sys_user SET employee_id = 112 WHERE id = 1011;

INSERT INTO sys_user_role (user_id, role_id)
VALUES (1011, 5)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT INTO employee_personal (employee_id, id_number_enc, id_number_hash, birthday, household_address, residence_address, emergency_contact, emergency_phone)
VALUES
    (112, 'ENC:110101199712127777', SHA2('110101199712127777', 256), '1997-12-12', '北京市朝阳区', '北京市海淀区', '紧急联系人H', '13900000011')
ON DUPLICATE KEY UPDATE
    emergency_contact=VALUES(emergency_contact),
    household_address=VALUES(household_address),
    residence_address=VALUES(residence_address);

INSERT INTO employee_contract (employee_id, contract_type, contract_expire_date, probation_salary_ratio, scheme_id, base_salary)
VALUES
    (112, 'FIXED', '2028-09-01', 1.0000, 1, 15000.00)
ON DUPLICATE KEY UPDATE base_salary=VALUES(base_salary);

INSERT INTO employee_bank (employee_id, bank_account_enc, bank_account_tail, bank_name)
VALUES
    (112, 'ENC:6222021000000001011', '1011', '工商银行')
ON DUPLICATE KEY UPDATE bank_name=VALUES(bank_name), bank_account_tail=VALUES(bank_account_tail);

INSERT INTO employee_salary_profile (employee_id, scheme_id, base_salary, ss_base, hf_base, performance_base, probation_ratio, effective_date)
VALUES
    (112, 1, 15000.00, 13000.00, 13000.00, 2000.00, 1.0000, '2025-09-01')
ON DUPLICATE KEY UPDATE base_salary=VALUES(base_salary);
