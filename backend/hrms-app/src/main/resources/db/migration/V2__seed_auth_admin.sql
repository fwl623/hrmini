-- ============================================================
-- V2: 权限种子 + 管理员账号（联调登录用）
-- 管理员：13800000000 / Admin@12345
-- password_changed_at 早于 created_at → 首次不强制改密（方便联调）
-- ============================================================

-- 基础权限码（auth/org/system，后续各模块可再补）
INSERT INTO sys_permission (code, name, module, type) VALUES
    ('menu:workbench',     '工作台',     'system', 'MENU'),
    ('menu:org',           '组织管理',   'org',    'MENU'),
    ('menu:employee',      '员工档案',   'employee','MENU'),
    ('menu:system',        '系统管理',   'system', 'MENU'),
    ('system:user:view',  '用户查看',   'auth',   'API'),
    ('system:user:edit',  '用户编辑',   'auth',   'API'),
    ('system:role:view',   '角色查看',   'auth',   'API'),
    ('system:role:edit',   '角色编辑',   'auth',   'API'),
    ('org:dept:view',      '部门查看',   'org',    'API'),
    ('org:dept:edit',      '部门编辑',   'org',    'API'),
    ('org:position:view',  '职位查看',   'org',    'API'),
    ('org:position:edit',  '职位编辑',   'org',    'API');

-- SYS_ADMIN 绑定全部上述权限（薪资菜单不在此列，符合 NONE_PAYROLL）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'SYS_ADMIN';

-- HR_STAFF：组织+员工+工作台（不含系统用户管理）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'HR_STAFF'
  AND p.code IN (
    'menu:workbench', 'menu:org', 'menu:employee',
    'org:dept:view', 'org:dept:edit', 'org:position:view', 'org:position:edit'
  );

-- 管理员账号（BCrypt cost=12，明文 Admin@12345）
INSERT INTO sys_user (username, password_hash, password_changed_at, employee_id, status, created_at, updated_at)
VALUES (
    '13800000000',
    '$2a$12$9bAzztAJZqkz5P9Fz3A4YONhIj9RNLHBC0Yvl6jKY6NLRqyiUKV8G',
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    NULL,
    1,
    NOW(),
    NOW()
);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
CROSS JOIN sys_role r
WHERE u.username = '13800000000' AND r.code = 'SYS_ADMIN';
