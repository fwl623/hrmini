## 权限体系数据库表

### `sys_user` — 系统用户表

```mysql
CREATE TABLE sys_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(32)  NOT NULL COMMENT '手机号（登录账号）',
    password_hash   VARCHAR(128) NOT NULL,
    password_changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'PRD §11.2 90天轮换',
    employee_id     BIGINT       NULL COMMENT '关联员工ID，入职审批通过后写入',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username)
) COMMENT='系统用户表，PRD §2.1';
```

### `sys_role` — 角色表

```mysql
CREATE TABLE sys_role (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    code        VARCHAR(32) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    data_scope  VARCHAR(16) NOT NULL COMMENT 'ALL/DEPT_TREE/SELF/PAYROLL/NONE_PAYROLL',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
);
```

**预置角色数据（PRD §2.1）：**

| role.code    | data_scope   | 说明                                   |
| ------------ | ------------ | -------------------------------------- |
| SYS_ADMIN    | NONE_PAYROLL | 全平台；**不可访问薪资模块**（双拦截） |
| HR_STAFF     | ALL          | HR 专员：全部员工+薪资全量             |
| DEPT_MANAGER | DEPT_TREE    | 部门主管：本部门及下属                 |
| FINANCE      | PAYROLL      | 财务专员：仅薪资相关                   |
| EMPLOYEE     | SELF         | 普通员工：仅本人                       |

**`sys_permission` — 权限/菜单表**

```mysql
CREATE TABLE sys_permission (
    id      BIGINT PRIMARY KEY AUTO_INCREMENT,
    code    VARCHAR(64) NOT NULL COMMENT '权限唯一标识，如 employee:create',
    name    VARCHAR(64) NOT NULL COMMENT '权限中文名，如"新增员工"',
    module  VARCHAR(32) NOT NULL COMMENT '归属模块：auth/org/employee/...',
    type    VARCHAR(16) NOT NULL COMMENT 'MENU/BUTTON/API',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
);
```

**`sys_user_role` — 用户角色关联**

```mysql
CREATE TABLE sys_user_role (
    user_id    BIGINT NOT NULL COMMENT '关联 sys_user.id',
    role_id    BIGINT NOT NULL COMMENT '关联 sys_role.id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);
```

**`sys_role_permission` — 角色权限关联**

```mysql
CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL COMMENT '关联 sys_role.id',
    permission_id BIGINT NOT NULL COMMENT '关联 sys_permission.id',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);
```

**login_log** — 登录日志

```mysql
CREATE TABLE login_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL COMMENT '关联 sys_user.id',
    login_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    login_ip        VARCHAR(45)  NOT NULL,
    user_agent      VARCHAR(512) NULL,
    device          VARCHAR(64)  NULL COMMENT '解析自 UA',
    location        VARCHAR(64)  NULL COMMENT 'IP 归属地，可选',
    success         TINYINT      NOT NULL COMMENT '1=成功 0=失败',
    fail_reason     VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_time (user_id, login_time)
) COMMENT='登录日志，PRD §9.5';
```

## 公共common表：

**`operation_log` — 操作审计日志**

```mysql
CREATE TABLE operation_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '关联 sys_user.id',
    module      VARCHAR(32)  NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    target_id   VARCHAR(64)  NULL,
    request_ip  VARCHAR(45)  NOT NULL,
    detail      JSON         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_time (user_id, created_at)
);
```

**sys_dict——数据字典与枚举**：

```mysql
CREATE TABLE sys_dict (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_type   VARCHAR(32) NOT NULL,
    dict_code   VARCHAR(32) NOT NULL,
    dict_label  VARCHAR(64) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_type_code (dict_type, dict_code)
) COMMENT='数据字典';
```

**`import_batch` / `import_row_error` — 数据迁移**

```mysql
CREATE TABLE import_batch (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    import_type     VARCHAR(16) NOT NULL COMMENT 'DEPT/EMPLOYEE/SALARY/ATTENDANCE_SUMMARY',
    file_name       VARCHAR(256) NOT NULL,
    total_rows      INT NOT NULL DEFAULT 0,
    success_rows    INT NOT NULL DEFAULT 0,
    fail_rows       INT NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL COMMENT 'VALIDATING/VALIDATED/COMMITTED/FAILED',
    created_by      BIGINT NOT NULL COMMENT '操作人 sys_user.id',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_type_status (import_type, status)
) COMMENT='导入批次';

CREATE TABLE import_row_error (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id        BIGINT NOT NULL COMMENT '所属批次 import_batch.id',
    row_index       INT NOT NULL COMMENT 'Excel 行号',
    column_name     VARCHAR(64) NULL COMMENT '出错的列',
    error_message   VARCHAR(512) NOT NULL,
    raw_data_json   JSON NULL COMMENT '该行原始数据',
    KEY idx_batch (batch_id)
) COMMENT='导入错误行';
```



## 组织架构表：

**`department` — 部门表**

```mysql
CREATE TABLE department (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    name              VARCHAR(64)  NOT NULL COMMENT '部门名称',
    code              VARCHAR(8)   NOT NULL COMMENT '部门编码（工号生成用）',
    parent_id         BIGINT       NULL COMMENT '上级部门ID，NULL=根',
    path              VARCHAR(256) NOT NULL COMMENT '路径枚举，如 /1/3/7/，深度≤5',
    level             TINYINT      NOT NULL DEFAULT 1 COMMENT '层级 1~5',
    head_employee_id  BIGINT       NULL COMMENT '部门负责人 employee_id，决定数据权限',
    sort_order        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    description       VARCHAR(256) NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code),
    KEY idx_parent (parent_id),
    KEY idx_path (path(64))
) COMMENT='部门表，PRD §3.1';
```

**`position` — 职位表**

```mysql
CREATE TABLE position (
    id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
    name                     VARCHAR(64)  NOT NULL COMMENT '职位名称',
    sequence                 VARCHAR(4)   NOT NULL COMMENT 'M/P/S',
    department_id            BIGINT       NULL COMMENT 'NULL=全公司通用',
    rank_min                 VARCHAR(8)   NOT NULL COMMENT '职级范围-最小值，如P1',
    rank_max                 VARCHAR(8)   NOT NULL COMMENT '职级范围-最大值，如P10',
    default_probation_months INT          NOT NULL DEFAULT 3 COMMENT '默认试用期（月）',
    is_standard              TINYINT      NOT NULL DEFAULT 1 COMMENT '是否标准职位，0→入职二审',
    description              VARCHAR(512) NULL,
    deleted                  TINYINT      NOT NULL DEFAULT 0,
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_dept (department_id),
    KEY idx_sequence (sequence)
) COMMENT='职位表，PRD §3.2';
```

**职位序列职级对照（PRD §3.2.2）：**

| 序列       | 职级范围 | 典型职位                       |
| ---------- | -------- | ------------------------------ |
| 管理序列 M | M1-M5    | M1主管、M2经理、M3总监、M5 VP  |
| 专业序列 P | P1-P10   | P3初级、P5中级、P7高级、P9专家 |
| 支持序列 S | S1-S5    | S1~S5 职能等级                 |

#### 索引设计

| 表           | 索引           | 用途                       |
| ------------ | -------------- | -------------------------- |
| `department` | `uk_code`      | 部门编码唯一（工号生成用） |
| `department` | `idx_parent`   | 按上级部门查询子部门       |
| `department` | `idx_path`     | `path LIKE` 子树查询       |
| `position`   | `idx_dept`     | 按部门查询职位             |
| `position`   | `idx_sequence` | 按序列查询职位             |

---

## 依赖的其他模块表清单

> 建表时**先建本脚本中的表**，再建依赖的其他模块表；**删除时顺序相反**。

### 跨模块依赖（需等待范文路建表）

| 本脚本字段 | 依赖方（负责人） | 对方表.字段 | 说明 |
|-----------|---------------|------------|------|
| `sys_user.employee_id` | 范文路 | `employee.id` | 入职审批通过后关联员工记录 |
| `department.head_employee_id` | 范文路 | `employee.id` | 部门负责人必须是现有员工 |

### 本模块内部引用

| 本脚本字段 | 对方表.字段 | 说明 |
|-----------|------------|------|
| `department.parent_id` | `department.id` | 上级部门自引用 |
| `position.department_id` | `department.id` | 职位所属部门 |
| `sys_user_role.user_id` | `sys_user.id` | 用户-角色关联 |
| `sys_user_role.role_id` | `sys_role.id` | 用户-角色关联 |
| `sys_role_permission.role_id` | `sys_role.id` | 角色-权限关联 |
| `sys_role_permission.permission_id` | `sys_permission.id` | 角色-权限关联 |
| `login_log.user_id` | `sys_user.id` | 登录用户 |
| `operation_log.user_id` | `sys_user.id` | 操作用户 |
| `import_batch.created_by` | `sys_user.id` | 导入操作人 |
| `import_row_error.batch_id` | `import_batch.id` | 所属导入批次 |