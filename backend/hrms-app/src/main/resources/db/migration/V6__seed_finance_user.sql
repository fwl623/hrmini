-- ============================================================
-- V6: FINANCE 联调账号占位（实际账号在 V7 修复为 1008，避免与本地 1006 冲突）
-- 本脚本仅确保财务部/职位存在；账号创建见 V7。
-- ============================================================

INSERT INTO department (id, name, code, parent_id, path, level, head_employee_id, sort_order, description, deleted)
SELECT 12, '财务部', '12', 1, '/1/12/', 2, NULL, 3, '财务', 0 FROM DUAL
WHERE EXISTS (SELECT 1 FROM department WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM department WHERE id = 12);

INSERT INTO position (id, name, sequence, department_id, rank_min, rank_max, default_probation_months, is_standard, description, deleted)
SELECT 24, '财务专员', 'S', 12, 'S1', 'S5', 3, 1, '财务核算', 0 FROM DUAL
WHERE EXISTS (SELECT 1 FROM department WHERE id = 12)
  AND NOT EXISTS (SELECT 1 FROM position WHERE id = 24);

-- 财务员工档案（可先无 user；V7 再绑 13800001006）
INSERT INTO employee (id, employee_no, user_id, name, gender, mobile, email,
                      department_id, position_id, grade, manager_id, work_location,
                      hire_date, employment_type, employment_status, probation_pay_ratio, deleted)
SELECT
    106, '202612001', NULL, '钱八', 'FEMALE', '13800001006', 'finance@example.com',
    12, 24, 'S3', NULL, '北京',
    '2024-05-01', 'fulltime', 20, 1.00, 0
FROM DUAL
WHERE EXISTS (SELECT 1 FROM department WHERE id = 12)
  AND EXISTS (SELECT 1 FROM position WHERE id = 24)
  AND NOT EXISTS (SELECT 1 FROM employee WHERE id = 106);

INSERT INTO employee_personal (employee_id, id_number_enc, id_number_hash, birthday, emergency_contact, emergency_phone)
VALUES (106, 'ENC:110101199404044444', SHA2('110101199404044444', 256), '1994-04-04', '紧急联系人F', '13900000006')
ON DUPLICATE KEY UPDATE emergency_contact = VALUES(emergency_contact);

INSERT INTO employee_contract (employee_id, contract_type, contract_expire_date, probation_salary_ratio, scheme_id, base_salary)
SELECT 106, 'FIXED', '2027-05-01', 1.0000, 1, 15000.00 FROM DUAL
WHERE EXISTS (SELECT 1 FROM employee WHERE id = 106)
  AND NOT EXISTS (SELECT 1 FROM employee_contract WHERE employee_id = 106);

INSERT INTO employee_salary_profile (employee_id, scheme_id, base_salary, ss_base, hf_base, performance_base, probation_ratio, effective_date)
SELECT 106, 1, 15000.00, 15000.00, 15000.00, 2000.00, 1.0000, '2024-05-01' FROM DUAL
WHERE EXISTS (SELECT 1 FROM employee WHERE id = 106)
  AND EXISTS (SELECT 1 FROM payroll_scheme WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM employee_salary_profile WHERE employee_id = 106);

INSERT INTO attendance_group_member (group_id, employee_id)
SELECT 1, 106 FROM DUAL
WHERE EXISTS (SELECT 1 FROM attendance_group WHERE id = 1 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM employee WHERE id = 106)
  AND NOT EXISTS (SELECT 1 FROM attendance_group_member WHERE group_id = 1 AND employee_id = 106);
