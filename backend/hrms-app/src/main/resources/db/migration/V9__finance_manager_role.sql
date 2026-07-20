-- V9: 财务经理角色（可进审批中心处理调岗调薪）；财务专员仅薪资域
INSERT INTO sys_role (code, name, data_scope)
SELECT 'FINANCE_MANAGER', '财务经理', 'PAYROLL'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'FINANCE_MANAGER');

-- 原 FINANCE 联调账号 1008 升级为财务经理（负责调薪审批）
UPDATE sys_user_role ur
INNER JOIN sys_role oldr ON oldr.id = ur.role_id AND oldr.code = 'FINANCE'
INNER JOIN sys_role newr ON newr.code = 'FINANCE_MANAGER'
SET ur.role_id = newr.id
WHERE ur.user_id = 1008;
