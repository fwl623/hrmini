-- 交接人改由部门负责人在审批时确认，发起时允许为空
ALTER TABLE resignation_application
    MODIFY COLUMN handover_employee_id BIGINT NULL COMMENT '工作交接人（部门负责人审批时确认）';
