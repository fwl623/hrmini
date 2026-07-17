-- 试用期结束日：待转正列表按该字段筛选（试用结束前 7 天）
ALTER TABLE employee
    ADD COLUMN probation_end_date DATE NULL COMMENT '试用期结束日' AFTER probation_pay_ratio;

-- 存量试用员工：默认入职日 + 3 个月
UPDATE employee
SET probation_end_date = DATE_ADD(hire_date, INTERVAL 3 MONTH)
WHERE employment_status = 10
  AND probation_end_date IS NULL
  AND hire_date IS NOT NULL;
