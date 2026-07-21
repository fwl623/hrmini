-- 部门负责人自动授予 DEPT_MANAGER 的来源标记。
-- 仅回收带此标记的角色，避免误删管理员手工授予的部门主管。
CREATE TABLE IF NOT EXISTS dept_head_auto_role (
    user_id     BIGINT   NOT NULL COMMENT 'sys_user.id',
    employee_id BIGINT   NOT NULL COMMENT 'employee.id',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    KEY idx_dept_head_auto_role_emp (employee_id)
) COMMENT='因担任部门负责人而自动授予 DEPT_MANAGER';
