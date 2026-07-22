-- ============================================================
-- 本地联调：追加 1 名实习生账号 + 6月/7月考勤 & 薪资数据
-- 登录：13800001013 / Admin@123
-- 角色：EMPLOYEE；档案：刘十一（技术部实习生）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 新增职位：实习生
-- ------------------------------------------------------------
INSERT INTO position (id, name, sequence, department_id, rank_min, rank_max, default_probation_months, is_standard, description, deleted)
VALUES (25, '实习生', 'P', 10, 'P1', 'P1', 0, 1, '实习岗位', 0)
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(deleted);

-- ------------------------------------------------------------
-- 2. 系统用户
-- ------------------------------------------------------------
INSERT INTO sys_user (id, username, password_hash, password_changed_at, employee_id, status)
VALUES
    (1013, '13800001013', '$2b$10$ycE0ACxTN3D3RmBLw01OJeq6Fr2b/m8ieTVtbd1RxO5sByeG25r22', NOW(), NULL, 1)
ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash), status=1, username=VALUES(username);

-- ------------------------------------------------------------
-- 3. 员工档案（技术部 / 实习生 / 直属王五 / 2026-06-01 入职）
-- ------------------------------------------------------------
INSERT INTO employee (
    id, employee_no, user_id, name, gender, mobile, email,
    department_id, position_id, grade, manager_id, work_location,
    hire_date, employment_type, employment_status, probation_pay_ratio, deleted
)
VALUES
    (113, '202610005', 1013, '刘十一', 'MALE', '13800001013', 'liushiyi.intern@example.com',
     10, 25, 'P1', 102, '北京',
     '2026-06-01', 'intern', 10, 1.00, 0)
ON DUPLICATE KEY UPDATE
    name=VALUES(name),
    mobile=VALUES(mobile),
    department_id=VALUES(department_id),
    position_id=VALUES(position_id),
    manager_id=VALUES(manager_id),
    employment_status=VALUES(employment_status),
    deleted=0;

UPDATE sys_user SET employee_id = 113 WHERE id = 1013;

-- ------------------------------------------------------------
-- 4. 角色：普通员工
-- ------------------------------------------------------------
INSERT INTO sys_user_role (user_id, role_id)
VALUES (1013, 5)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- ------------------------------------------------------------
-- 5. 个人信息 / 合同 / 银行卡 / 薪资档案
-- ------------------------------------------------------------
INSERT INTO employee_personal (employee_id, id_number_enc, id_number_hash, birthday, emergency_contact, emergency_phone)
VALUES
    (113, 'ENC:110101200110109999', SHA2('110101200110109999', 256), '2001-10-10', '紧急联系人I', '13900000013')
ON DUPLICATE KEY UPDATE emergency_contact=VALUES(emergency_contact);

-- 实习生合同：期限1年，无试用期
INSERT INTO employee_contract (employee_id, contract_type, contract_expire_date, probation_salary_ratio, scheme_id, base_salary)
VALUES
    (113, 'FIXED', '2027-06-01', 1.0000, 1, 4000.00)
ON DUPLICATE KEY UPDATE base_salary=VALUES(base_salary);

INSERT INTO employee_bank (employee_id, bank_account_enc, bank_account_tail, bank_name)
VALUES
    (113, 'ENC:6222021000000001013', '1013', '工商银行')
ON DUPLICATE KEY UPDATE bank_name=VALUES(bank_name), bank_account_tail=VALUES(bank_account_tail);

-- 实习生薪资档案：低基数，无社保公积金基数（实习生不缴纳）
INSERT INTO employee_salary_profile (employee_id, scheme_id, base_salary, ss_base, hf_base, performance_base, probation_ratio, effective_date)
VALUES
    (113, 1, 4000.00, 0.00, 0.00, 500.00, 1.0000, '2026-06-01')
ON DUPLICATE KEY UPDATE base_salary=VALUES(base_salary);

-- ------------------------------------------------------------
-- 6. 考勤组归属
-- ------------------------------------------------------------
INSERT INTO attendance_group_member (group_id, employee_id)
VALUES (1, 113)
ON DUPLICATE KEY UPDATE group_id=VALUES(group_id);

-- ------------------------------------------------------------
-- 7. 假期余额（实习生不享有年假；调休余额初始为0）
-- ------------------------------------------------------------
INSERT INTO leave_balance (employee_id, leave_type, year, total_quota, used_quota, remaining_quota, balance, effective_date, version)
VALUES
    (113, 'ANNUAL',   2026, 0.000, 0.000, 0.000, 0.000, '2026-06-01', 0),
    (113, 'COMP_OFF', 2026, 0.000, 0.000, 0.000, 0.000, '2026-06-01', 0)
ON DUPLICATE KEY UPDATE balance=VALUES(balance);

-- ============================================================
-- 8. 六月考勤数据（2026-06）
--    应出勤22天（6/1周一 ~ 6/30周二）
--    实际表现：正常出勤19天，迟到2次，事假1天，早退1次，旷工0
-- ============================================================
DELETE FROM attendance_daily_summary WHERE employee_id=113 AND summary_date BETWEEN '2026-06-01' AND '2026-06-30';
DELETE FROM attendance_record WHERE employee_id=113 AND punch_date BETWEEN '2026-06-01' AND '2026-06-30';

-- 6月日汇总（使用 v2.1 双槽位格式 "am:code,pm:code"）
INSERT INTO attendance_daily_summary (employee_id, summary_date, day_status, clock_in_time, clock_out_time, leave_days, overtime_hours) VALUES
(113,'2026-06-01','am:0,pm:0','2026-06-01 08:55:00','2026-06-01 18:05:00',0,0),
(113,'2026-06-02','am:0,pm:0','2026-06-02 08:50:00','2026-06-02 18:00:00',0,0),
(113,'2026-06-03','am:0,pm:0','2026-06-03 08:58:00','2026-06-03 18:02:00',0,0),
(113,'2026-06-04','am:1,pm:0','2026-06-04 09:20:00','2026-06-04 18:10:00',0,0),
(113,'2026-06-05','am:0,pm:0','2026-06-05 08:52:00','2026-06-05 18:03:00',0,0),
(113,'2026-06-08','am:0,pm:0','2026-06-08 08:48:00','2026-06-08 18:01:00',0,0),
(113,'2026-06-09','am:0,pm:0','2026-06-09 08:56:00','2026-06-09 18:05:00',0,0),
(113,'2026-06-10','am:0,pm:0','2026-06-10 08:59:00','2026-06-10 18:00:00',0,0),
(113,'2026-06-11','am:4,pm:4',NULL,NULL,1.0,0),
(113,'2026-06-12','am:0,pm:0','2026-06-12 08:53:00','2026-06-12 18:02:00',0,0),
(113,'2026-06-15','am:0,pm:0','2026-06-15 08:57:00','2026-06-15 18:04:00',0,0),
(113,'2026-06-16','am:0,pm:0','2026-06-16 08:51:00','2026-06-16 18:00:00',0,0),
(113,'2026-06-17','am:0,pm:0','2026-06-17 09:00:00','2026-06-17 18:08:00',0,0),
(113,'2026-06-18','am:0,pm:0','2026-06-18 08:54:00','2026-06-18 20:00:00',0,2.0),
(113,'2026-06-19','am:0,pm:0','2026-06-19 08:49:00','2026-06-19 18:03:00',0,0),
(113,'2026-06-22','am:0,pm:2','2026-06-22 08:55:00','2026-06-22 16:30:00',0,0),
(113,'2026-06-23','am:0,pm:0','2026-06-23 08:58:00','2026-06-23 18:01:00',0,0),
(113,'2026-06-24','am:1,pm:0','2026-06-24 09:15:00','2026-06-24 18:05:00',0,0),
(113,'2026-06-25','am:0,pm:0','2026-06-25 08:52:00','2026-06-25 18:00:00',0,0),
(113,'2026-06-26','am:0,pm:0','2026-06-26 08:56:00','2026-06-26 19:30:00',0,1.5),
(113,'2026-06-29','am:0,pm:0','2026-06-29 08:50:00','2026-06-29 18:03:00',0,0),
(113,'2026-06-30','am:0,pm:0','2026-06-30 08:55:00','2026-06-30 18:00:00',0,0);

-- 6月打卡流水
INSERT INTO attendance_record (employee_id, punch_date, punch_time, punch_type, punch_status, source) VALUES
(113,'2026-06-01','2026-06-01 08:55:00','IN','NORMAL','WEB'),
(113,'2026-06-01','2026-06-01 18:05:00','OUT','NORMAL','WEB'),
(113,'2026-06-02','2026-06-02 08:50:00','IN','NORMAL','WEB'),
(113,'2026-06-02','2026-06-02 18:00:00','OUT','NORMAL','WEB'),
(113,'2026-06-03','2026-06-03 08:58:00','IN','NORMAL','WEB'),
(113,'2026-06-03','2026-06-03 18:02:00','OUT','NORMAL','WEB'),
(113,'2026-06-04','2026-06-04 09:20:00','IN','LATE','WEB'),
(113,'2026-06-04','2026-06-04 18:10:00','OUT','NORMAL','WEB'),
(113,'2026-06-05','2026-06-05 08:52:00','IN','NORMAL','WEB'),
(113,'2026-06-05','2026-06-05 18:03:00','OUT','NORMAL','WEB'),
(113,'2026-06-08','2026-06-08 08:48:00','IN','NORMAL','WEB'),
(113,'2026-06-08','2026-06-08 18:01:00','OUT','NORMAL','WEB'),
(113,'2026-06-09','2026-06-09 08:56:00','IN','NORMAL','WEB'),
(113,'2026-06-09','2026-06-09 18:05:00','OUT','NORMAL','WEB'),
(113,'2026-06-10','2026-06-10 08:59:00','IN','NORMAL','WEB'),
(113,'2026-06-10','2026-06-10 18:00:00','OUT','NORMAL','WEB'),
(113,'2026-06-12','2026-06-12 08:53:00','IN','NORMAL','WEB'),
(113,'2026-06-12','2026-06-12 18:02:00','OUT','NORMAL','WEB'),
(113,'2026-06-15','2026-06-15 08:57:00','IN','NORMAL','WEB'),
(113,'2026-06-15','2026-06-15 18:04:00','OUT','NORMAL','WEB'),
(113,'2026-06-16','2026-06-16 08:51:00','IN','NORMAL','WEB'),
(113,'2026-06-16','2026-06-16 18:00:00','OUT','NORMAL','WEB'),
(113,'2026-06-17','2026-06-17 09:00:00','IN','NORMAL','WEB'),
(113,'2026-06-17','2026-06-17 18:08:00','OUT','NORMAL','WEB'),
(113,'2026-06-18','2026-06-18 08:54:00','IN','NORMAL','WEB'),
(113,'2026-06-18','2026-06-18 20:00:00','OUT','NORMAL','WEB'),
(113,'2026-06-19','2026-06-19 08:49:00','IN','NORMAL','WEB'),
(113,'2026-06-19','2026-06-19 18:03:00','OUT','NORMAL','WEB'),
(113,'2026-06-22','2026-06-22 08:55:00','IN','NORMAL','WEB'),
(113,'2026-06-22','2026-06-22 16:30:00','OUT','EARLY_LEAVE','WEB'),
(113,'2026-06-23','2026-06-23 08:58:00','IN','NORMAL','WEB'),
(113,'2026-06-23','2026-06-23 18:01:00','OUT','NORMAL','WEB'),
(113,'2026-06-24','2026-06-24 09:15:00','IN','LATE','WEB'),
(113,'2026-06-24','2026-06-24 18:05:00','OUT','NORMAL','WEB'),
(113,'2026-06-25','2026-06-25 08:52:00','IN','NORMAL','WEB'),
(113,'2026-06-25','2026-06-25 18:00:00','OUT','NORMAL','WEB'),
(113,'2026-06-26','2026-06-26 08:56:00','IN','NORMAL','WEB'),
(113,'2026-06-26','2026-06-26 19:30:00','OUT','NORMAL','WEB'),
(113,'2026-06-29','2026-06-29 08:50:00','IN','NORMAL','WEB'),
(113,'2026-06-29','2026-06-29 18:03:00','OUT','NORMAL','WEB'),
(113,'2026-06-30','2026-06-30 08:55:00','IN','NORMAL','WEB'),
(113,'2026-06-30','2026-06-30 18:00:00','OUT','NORMAL','WEB');

-- ============================================================
-- 9. 七月考勤数据（2026-07）
--    应出勤23天（7/1周三 ~ 7/31周五）
--    实际表现：正常出勤19天，迟到2次，事假1天，旷工1天，加班4.5h
-- ============================================================
DELETE FROM attendance_daily_summary WHERE employee_id=113 AND summary_date BETWEEN '2026-07-01' AND '2026-07-31';
DELETE FROM attendance_record WHERE employee_id=113 AND punch_date BETWEEN '2026-07-01' AND '2026-07-31';

-- 7月日汇总（双槽位格式）
INSERT INTO attendance_daily_summary (employee_id, summary_date, day_status, clock_in_time, clock_out_time, leave_days, overtime_hours) VALUES
(113,'2026-07-01','am:0,pm:0','2026-07-01 08:53:00','2026-07-01 18:01:00',0,0),
(113,'2026-07-02','am:0,pm:0','2026-07-02 08:57:00','2026-07-02 18:05:00',0,0),
(113,'2026-07-03','am:1,pm:0','2026-07-03 09:12:00','2026-07-03 18:02:00',0,0),
(113,'2026-07-06','am:0,pm:0','2026-07-06 08:50:00','2026-07-06 18:00:00',0,0),
(113,'2026-07-07','am:0,pm:0','2026-07-07 08:55:00','2026-07-07 18:03:00',0,0),
(113,'2026-07-08','am:0,pm:0','2026-07-08 08:58:00','2026-07-08 19:00:00',0,1.0),
(113,'2026-07-09','am:0,pm:0','2026-07-09 08:52:00','2026-07-09 18:01:00',0,0),
(113,'2026-07-10','am:0,pm:0','2026-07-10 08:48:00','2026-07-10 18:00:00',0,0),
(113,'2026-07-13','am:0,pm:0','2026-07-13 08:56:00','2026-07-13 18:04:00',0,0),
(113,'2026-07-14','am:4,pm:4',NULL,NULL,1.0,0),  -- 事假1天
(113,'2026-07-15','am:0,pm:0','2026-07-15 08:51:00','2026-07-15 18:02:00',0,0),
(113,'2026-07-16','am:0,pm:0','2026-07-16 08:59:00','2026-07-16 18:00:00',0,0),
(113,'2026-07-17','am:0,pm:0','2026-07-17 08:54:00','2026-07-17 20:00:00',0,2.0),
(113,'2026-07-20','am:0,pm:0','2026-07-20 08:50:00','2026-07-20 18:01:00',0,0),
(113,'2026-07-21','am:0,pm:0','2026-07-21 08:57:00','2026-07-21 18:03:00',0,0),
(113,'2026-07-22','am:0,pm:0','2026-07-22 08:53:00','2026-07-22 18:00:00',0,0),
(113,'2026-07-23','am:0,pm:0','2026-07-23 08:55:00','2026-07-23 19:30:00',0,1.5),
(113,'2026-07-24','am:3,pm:3',NULL,NULL,0,0),  -- 旷工全天
(113,'2026-07-27','am:0,pm:0','2026-07-27 08:52:00','2026-07-27 18:04:00',0,0),
(113,'2026-07-28','am:1,pm:0','2026-07-28 09:30:00','2026-07-28 18:05:00',0,0),
(113,'2026-07-29','am:0,pm:0','2026-07-29 08:58:00','2026-07-29 18:01:00',0,0),
(113,'2026-07-30','am:0,pm:0','2026-07-30 08:49:00','2026-07-30 18:00:00',0,0),
(113,'2026-07-31','am:0,pm:0','2026-07-31 08:55:00','2026-07-31 18:02:00',0,0);

-- 7月打卡流水
INSERT INTO attendance_record (employee_id, punch_date, punch_time, punch_type, punch_status, source) VALUES
(113,'2026-07-01','2026-07-01 08:53:00','IN','NORMAL','WEB'),
(113,'2026-07-01','2026-07-01 18:01:00','OUT','NORMAL','WEB'),
(113,'2026-07-02','2026-07-02 08:57:00','IN','NORMAL','WEB'),
(113,'2026-07-02','2026-07-02 18:05:00','OUT','NORMAL','WEB'),
(113,'2026-07-03','2026-07-03 09:12:00','IN','LATE','WEB'),
(113,'2026-07-03','2026-07-03 18:02:00','OUT','NORMAL','WEB'),
(113,'2026-07-06','2026-07-06 08:50:00','IN','NORMAL','WEB'),
(113,'2026-07-06','2026-07-06 18:00:00','OUT','NORMAL','WEB'),
(113,'2026-07-07','2026-07-07 08:55:00','IN','NORMAL','WEB'),
(113,'2026-07-07','2026-07-07 18:03:00','OUT','NORMAL','WEB'),
(113,'2026-07-08','2026-07-08 08:58:00','IN','NORMAL','WEB'),
(113,'2026-07-08','2026-07-08 19:00:00','OUT','NORMAL','WEB'),
(113,'2026-07-09','2026-07-09 08:52:00','IN','NORMAL','WEB'),
(113,'2026-07-09','2026-07-09 18:01:00','OUT','NORMAL','WEB'),
(113,'2026-07-10','2026-07-10 08:48:00','IN','NORMAL','WEB'),
(113,'2026-07-10','2026-07-10 18:00:00','OUT','NORMAL','WEB'),
(113,'2026-07-13','2026-07-13 08:56:00','IN','NORMAL','WEB'),
(113,'2026-07-13','2026-07-13 18:04:00','OUT','NORMAL','WEB'),
(113,'2026-07-15','2026-07-15 08:51:00','IN','NORMAL','WEB'),
(113,'2026-07-15','2026-07-15 18:02:00','OUT','NORMAL','WEB'),
(113,'2026-07-16','2026-07-16 08:59:00','IN','NORMAL','WEB'),
(113,'2026-07-16','2026-07-16 18:00:00','OUT','NORMAL','WEB'),
(113,'2026-07-17','2026-07-17 08:54:00','IN','NORMAL','WEB'),
(113,'2026-07-17','2026-07-17 20:00:00','OUT','NORMAL','WEB'),
(113,'2026-07-20','2026-07-20 08:50:00','IN','NORMAL','WEB'),
(113,'2026-07-20','2026-07-20 18:01:00','OUT','NORMAL','WEB'),
(113,'2026-07-21','2026-07-21 08:57:00','IN','NORMAL','WEB'),
(113,'2026-07-21','2026-07-21 18:03:00','OUT','NORMAL','WEB'),
(113,'2026-07-22','2026-07-22 08:53:00','IN','NORMAL','WEB'),
(113,'2026-07-22','2026-07-22 18:00:00','OUT','NORMAL','WEB'),
(113,'2026-07-23','2026-07-23 08:55:00','IN','NORMAL','WEB'),
(113,'2026-07-23','2026-07-23 19:30:00','OUT','NORMAL','WEB'),
(113,'2026-07-27','2026-07-27 08:52:00','IN','NORMAL','WEB'),
(113,'2026-07-27','2026-07-27 18:04:00','OUT','NORMAL','WEB'),
(113,'2026-07-28','2026-07-28 09:30:00','IN','LATE','WEB'),
(113,'2026-07-28','2026-07-28 18:05:00','OUT','NORMAL','WEB'),
(113,'2026-07-29','2026-07-29 08:58:00','IN','NORMAL','WEB'),
(113,'2026-07-29','2026-07-29 18:01:00','OUT','NORMAL','WEB'),
(113,'2026-07-30','2026-07-30 08:49:00','IN','NORMAL','WEB'),
(113,'2026-07-30','2026-07-30 18:00:00','OUT','NORMAL','WEB'),
(113,'2026-07-31','2026-07-31 08:55:00','IN','NORMAL','WEB'),
(113,'2026-07-31','2026-07-31 18:02:00','OUT','NORMAL','WEB');

-- ============================================================
-- 10. 六月月考勤汇总
--     应出勤22天 / 实际出勤19.5天 / 迟到2次 / 早退1次 / 旷工0 / 请假1天 / 加班3.5h
-- ============================================================
INSERT INTO attendance_monthly_summary (employee_id, period, should_attend_days, actual_attend_days, late_count, early_leave_count, absent_days, leave_days, overtime_hours, annual_balance)
VALUES
    (113, '2026-06', 22, 19.5, 2, 1, 0, 1.0, 3.5, 0)
ON DUPLICATE KEY UPDATE
    actual_attend_days=VALUES(actual_attend_days),
    late_count=VALUES(late_count),
    early_leave_count=VALUES(early_leave_count),
    leave_days=VALUES(leave_days),
    overtime_hours=VALUES(overtime_hours);

-- ============================================================
-- 11. 七月月考勤汇总
--     应出勤23天 / 实际出勤19.5天 / 迟到2次 / 早退0 / 旷工1天 / 请假1天 / 加班4.5h
-- ============================================================
INSERT INTO attendance_monthly_summary (employee_id, period, should_attend_days, actual_attend_days, late_count, early_leave_count, absent_days, leave_days, overtime_hours, annual_balance)
VALUES
    (113, '2026-07', 23, 19.5, 2, 0, 1.0, 1.0, 4.5, 0)
ON DUPLICATE KEY UPDATE
    actual_attend_days=VALUES(actual_attend_days),
    late_count=VALUES(late_count),
    early_leave_count=VALUES(early_leave_count),
    absent_days=VALUES(absent_days),
    leave_days=VALUES(leave_days),
    overtime_hours=VALUES(overtime_hours);

-- ============================================================
-- 12. 六月薪资数据
--     加入实习生到已有批次 205（2026-06 批次）
--     底薪4000 + 无社保公积金 + 无个税 = 应发4000 / 实发4000
--     更新批次统计
-- ============================================================
INSERT INTO payroll_detail (batch_id, employee_id, calc_status, gross_salary, net_salary, detail_json, prev_net_salary, manual_adjusted)
VALUES
    (205, 113, 'SUCCESS', 4000.00, 4000.00,
     JSON_ARRAY(
       JSON_OBJECT('itemCode', 'BASE', 'itemName', '基本工资', 'amount', 4000.00, 'type', 'EARNING'),
       JSON_OBJECT('itemCode', 'PERFORMANCE', 'itemName', '绩效工资', 'amount', 0.00, 'type', 'EARNING'),
       JSON_OBJECT('itemCode', 'SS_DEDUCT', 'itemName', '社保个人', 'amount', 0.00, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemCode', 'HF_DEDUCT', 'itemName', '公积金个人', 'amount', 0.00, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemCode', 'TAX', 'itemName', '个税', 'amount', 0.00, 'type', 'DEDUCTION')
     ), NULL, 0)
ON DUPLICATE KEY UPDATE gross_salary=VALUES(gross_salary), net_salary=VALUES(net_salary), detail_json=VALUES(detail_json);

-- 更新六月批次统计（原 totalCount=1 → +1, successCount=1 → +1, grossTotal=22500+4000, netTotal=17900+4000）
UPDATE payroll_batch
SET total_count = total_count + 1,
    success_count = success_count + 1,
    gross_total = gross_total + 4000.00,
    net_total = net_total + 4000.00
WHERE id = 205;

-- ============================================================
-- 13. 七月薪资数据
--     加入实习生到已有批次 206（2026-07 批次）
--     底薪4000 + 无社保公积金 + 无个税 = 应发4000 / 实发4000
-- ============================================================
INSERT INTO payroll_detail (batch_id, employee_id, calc_status, gross_salary, net_salary, detail_json, prev_net_salary, manual_adjusted)
VALUES
    (206, 113, 'SUCCESS', 4000.00, 4000.00,
     JSON_ARRAY(
       JSON_OBJECT('itemCode', 'BASE', 'itemName', '基本工资', 'amount', 4000.00, 'type', 'EARNING'),
       JSON_OBJECT('itemCode', 'PERFORMANCE', 'itemName', '绩效工资', 'amount', 0.00, 'type', 'EARNING'),
       JSON_OBJECT('itemCode', 'SS_DEDUCT', 'itemName', '社保个人', 'amount', 0.00, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemCode', 'HF_DEDUCT', 'itemName', '公积金个人', 'amount', 0.00, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemCode', 'TAX', 'itemName', '个税', 'amount', 0.00, 'type', 'DEDUCTION')
     ), 4000.00, 0)
ON DUPLICATE KEY UPDATE gross_salary=VALUES(gross_salary), net_salary=VALUES(net_salary), detail_json=VALUES(detail_json);

-- 更新七月批次统计（原 totalCount=1 → +1, successCount=1 → +1, grossTotal=23000+4000, netTotal=18250+4000）
UPDATE payroll_batch
SET total_count = total_count + 1,
    success_count = success_count + 1,
    gross_total = gross_total + 4000.00,
    net_total = net_total + 4000.00
WHERE id = 206;

-- ============================================================
-- 14. 调整自增起点
-- ============================================================
ALTER TABLE employee AUTO_INCREMENT = 1000;
ALTER TABLE payroll_batch AUTO_INCREMENT = 300;
ALTER TABLE sys_user AUTO_INCREMENT = 2000;
ALTER TABLE position AUTO_INCREMENT = 100;

-- ============================================================
-- 完成
-- ============================================================
SELECT '实习生刘十一种子数据已导入 ✓' AS result;
