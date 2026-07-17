-- 工号历史表增加唯一约束，防止并发生成重复工号
ALTER TABLE employee_no_history
    ADD UNIQUE KEY uk_employee_no (employee_no);
