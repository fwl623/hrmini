-- ============================================================
-- Flyway Migration: V1__init_hrms_schema.sql
-- HRMS 数据库初始化脚本 v2.0（四模块合并终版）
--
-- 合并来源（按模块）：
--   李俊毅 — 公共/权限/组织架构（12 表）
--   范文路 — 员工档案/个人中心（10 表）
--   郭策   — 入转调离/审批中心（10 表，不含 employee_transfer_history）
--   张浩杰 — 考勤请假/薪资管理（21 表，不含 employee_salary_profile）
--
-- 冲突解决：
--   employee_transfer_history   → 保留范文路版 + 补充 updated_at
--   employee_salary_profile     → 保留范文路版（含审计字段）
--   onboarding_application      → 郭策版 + 补充 base_salary / actual_onboard_date
--
-- 建表顺序：按依赖关系分层，逻辑外键无物理约束
-- ============================================================

-- ============================================================
-- LAYER 1: 基础表（零依赖，或仅自引用）
-- ============================================================

-- 1. 数据字典
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

-- 2. 角色表
CREATE TABLE sys_role (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    code        VARCHAR(32) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    data_scope  VARCHAR(16) NOT NULL COMMENT 'ALL/DEPT_TREE/SELF/PAYROLL/NONE_PAYROLL',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) COMMENT='角色表，PRD §2.1';

-- 3. 权限/菜单表
CREATE TABLE sys_permission (
    id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    code      VARCHAR(64) NOT NULL COMMENT '权限唯一标识，如 employee:create',
    name      VARCHAR(64) NOT NULL COMMENT '权限中文名',
    module    VARCHAR(32) NOT NULL COMMENT '归属模块：auth/org/employee/...',
    type      VARCHAR(16) NOT NULL COMMENT 'MENU/BUTTON/API',
    created_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) COMMENT='权限/菜单表';

-- 4. 工作日配置
CREATE TABLE workday_config (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    day_of_week TINYINT NOT NULL COMMENT '1=周一..7=周日',
    is_workday  TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_dow (day_of_week)
) COMMENT='工作日配置';

-- 5. 法定节假日
CREATE TABLE holiday_calendar (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    holiday_date DATE        NOT NULL,
    name         VARCHAR(64) NOT NULL COMMENT '节假日名称',
    UNIQUE KEY uk_date (holiday_date)
) COMMENT='法定节假日';

-- 6. 薪资账套
CREATE TABLE payroll_scheme (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(64)  NOT NULL COMMENT '账套名称',
    description     VARCHAR(256) NULL,
    effective_date  DATE         NOT NULL COMMENT '生效日期',
    status          VARCHAR(16)  NOT NULL DEFAULT 'enabled' COMMENT 'enabled/disabled',
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='薪资账套';

-- 7. 账套工资项目
CREATE TABLE payroll_scheme_item (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    scheme_id   BIGINT        NOT NULL COMMENT '关联 payroll_scheme.id',
    item_code   VARCHAR(32)   NOT NULL COMMENT '项目编码',
    item_name   VARCHAR(64)   NOT NULL COMMENT '项目名称',
    item_type   VARCHAR(20)   NOT NULL COMMENT 'FIXED/VARIABLE/ATTENDANCE_DEDUCT/SS_DEDUCT/HF_DEDUCT/TAX',
    calc_rule   VARCHAR(512)  NULL COMMENT 'SpEL 公式或规则描述',
    base_field  VARCHAR(32)   NULL COMMENT 'ssBase/hfBase/performanceBase',
    ratio       DECIMAL(8,4)  NULL COMMENT '社保公积金比例',
    sort_order  INT           NOT NULL DEFAULT 0,
    KEY idx_scheme (scheme_id)
) COMMENT='账套工资项目';

-- 8. 账套适用范围
CREATE TABLE payroll_scheme_scope (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    scheme_id   BIGINT      NOT NULL COMMENT '关联 payroll_scheme.id',
    scope_type  VARCHAR(16) NOT NULL COMMENT 'DEPARTMENT/POSITION/JOB_LEVEL',
    scope_id    VARCHAR(32) NOT NULL,
    KEY idx_scheme (scheme_id)
) COMMENT='账套适用范围';

-- 9. 个税税率表（累计预扣法）
CREATE TABLE pay_tax_bracket (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tax_year        INT           NOT NULL COMMENT '纳税年度',
    min_taxable     DECIMAL(12,2) NOT NULL COMMENT '起征金额',
    max_taxable     DECIMAL(12,2) NULL COMMENT '上限，NULL=无限',
    rate            DECIMAL(5,4)  NOT NULL COMMENT '税率，如 0.03',
    quick_deduction DECIMAL(12,2) NOT NULL COMMENT '速算扣除数',
    UNIQUE KEY uk_year_range (tax_year, min_taxable)
) COMMENT='个税税率表（累计预扣法）';

-- 10. 部门表（自引用 parent_id）
CREATE TABLE department (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    name              VARCHAR(64)  NOT NULL COMMENT '部门名称',
    code              VARCHAR(8)   NOT NULL COMMENT '部门编码（工号生成用）',
    parent_id         BIGINT       NULL COMMENT '上级部门ID，NULL=根',
    path              VARCHAR(256) NOT NULL COMMENT '路径枚举，如 /1/3/7/，深度≤5',
    level             TINYINT      NOT NULL DEFAULT 1 COMMENT '层级 1~5',
    head_employee_id  BIGINT       NULL COMMENT '部门负责人 employee_id',
    sort_order        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    description       VARCHAR(256) NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code),
    KEY idx_parent (parent_id),
    KEY idx_path (path(64))
) COMMENT='部门表，PRD §3.1';

-- 11. 月考勤锁定
CREATE TABLE attendance_month_lock (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `year_month`  CHAR(7)   NOT NULL COMMENT '账期 YYYY-MM',
    status      TINYINT   NOT NULL COMMENT '10=OPEN 20=LOCKED',
    locked_at   DATETIME  NULL COMMENT '锁定时间',
    locked_by   BIGINT    NULL COMMENT '锁定人',
    UNIQUE KEY uk_month (`year_month`)
) COMMENT='月考勤锁定';

-- 12. 流程定义（审批节点配置存储为 JSON）
CREATE TABLE approval_process_def (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    process_type    VARCHAR(32) NOT NULL COMMENT 'ONBOARDING/REGULARIZATION/TRANSFER/RESIGNATION/...',
    name            VARCHAR(64) NOT NULL,
    nodes_json      JSON        NOT NULL COMMENT '审批节点配置',
    sla_hours       INT         NOT NULL DEFAULT 48,
    status          TINYINT     NOT NULL DEFAULT 1,
    UNIQUE KEY uk_type (process_type)
) COMMENT='流程定义';


-- ============================================================
-- LAYER 2: 权限与组织
-- ============================================================

-- 13. 系统用户表
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

-- 14. 导入批次
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

-- 15. 导入错误行
CREATE TABLE import_row_error (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id        BIGINT NOT NULL COMMENT '所属批次 import_batch.id',
    row_index       INT NOT NULL COMMENT 'Excel 行号',
    column_name     VARCHAR(64) NULL COMMENT '出错的列',
    error_message   VARCHAR(512) NOT NULL,
    raw_data_json   JSON NULL COMMENT '该行原始数据',
    KEY idx_batch (batch_id)
) COMMENT='导入错误行';

-- 16. 角色权限关联
CREATE TABLE sys_role_permission (
    role_id       BIGINT   NOT NULL COMMENT '关联 sys_role.id',
    permission_id BIGINT   NOT NULL COMMENT '关联 sys_permission.id',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
) COMMENT='角色权限关联';

-- 17. 用户角色关联
CREATE TABLE sys_user_role (
    user_id    BIGINT   NOT NULL COMMENT '关联 sys_user.id',
    role_id    BIGINT   NOT NULL COMMENT '关联 sys_role.id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
) COMMENT='用户角色关联';

-- 18. 登录日志
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

-- 19. 操作审计日志
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
) COMMENT='操作审计日志';

-- 20. 职位表（依赖 department）
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


-- ============================================================
-- LAYER 3: 核心业务实体
-- ============================================================

-- 21. 员工主表（依赖 department, position）
CREATE TABLE employee (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（作 employee_id 业务主键使用，永不复用）',
    employee_no         VARCHAR(16)  NOT NULL COMMENT '工号，格式：年份(4位)+部门编码(2位)+序号(3位)',
    user_id             BIGINT       NULL COMMENT '系统账号ID，关联 sys_user.id',
    name                VARCHAR(32)  NOT NULL COMMENT '姓名',
    gender              VARCHAR(8)   NOT NULL COMMENT '性别：MALE/FEMALE',
    mobile              VARCHAR(16)  NOT NULL COMMENT '手机号（登录账号）',
    email               VARCHAR(128) NOT NULL COMMENT '邮箱',
    department_id       BIGINT       NOT NULL COMMENT '所属部门ID，关联 department.id',
    position_id         BIGINT       NOT NULL COMMENT '职位ID，关联 position.id',
    grade               VARCHAR(8)   NULL COMMENT '职级，如P5、M2',
    manager_id          BIGINT       NULL COMMENT '直属上级ID，关联 employee.id',
    work_location       VARCHAR(128) NULL COMMENT '工作地点',
    hire_date           DATE         NOT NULL COMMENT '入职日期',
    employment_type     VARCHAR(16)  NOT NULL COMMENT '用工类型：fulltime/parttime/intern',
    employment_status   TINYINT      NOT NULL COMMENT '在职状态：10=试用期 20=正式 30=待离职 40=已离职',
    last_work_day       DATE         NULL COMMENT '最后工作日',
    probation_pay_ratio DECIMAL(3,2) NULL COMMENT '试用薪资比例 0.80~1.00',
    deleted             TINYINT      NOT NULL DEFAULT 0,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_employee_no (employee_no),
    UNIQUE KEY uk_mobile (mobile),
    KEY idx_dept_status (department_id, employment_status),
    KEY idx_hire_date (hire_date),
    KEY idx_name (name)
) COMMENT='员工主表';

-- 22. 考勤组
CREATE TABLE attendance_group (
    id                              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name                            VARCHAR(64)  NOT NULL COMMENT '考勤组名称',
    shift_type                      VARCHAR(16)  NOT NULL COMMENT 'FIXED/FLEXIBLE/SCHEDULE',
    work_start_time                 TIME         NOT NULL COMMENT '上班时间',
    work_end_time                   TIME         NOT NULL COMMENT '下班时间',
    lunch_start_time                TIME         NULL DEFAULT '12:00' COMMENT '午休开始',
    lunch_end_time                  TIME         NULL DEFAULT '13:00' COMMENT '午休结束',
    flex_start_earliest             TIME         NULL COMMENT '弹性最早打卡',
    flex_start_latest               TIME         NULL COMMENT '弹性最晚打卡',
    late_threshold_minutes          INT          NOT NULL DEFAULT 15 COMMENT '迟到阈值',
    early_leave_threshold_minutes   INT          NOT NULL DEFAULT 15 COMMENT '早退阈值',
    ip_whitelist_json               JSON         NULL COMMENT 'IP白名单',
    gps_range_json                  JSON         NULL COMMENT 'GPS范围 {lat,lng,radiusM}',
    deleted                         TINYINT      NOT NULL DEFAULT 0,
    created_at                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='考勤组';

-- 23. 考勤组适用范围
CREATE TABLE attendance_group_scope (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id    BIGINT      NOT NULL COMMENT '考勤组ID',
    scope_type  VARCHAR(16) NOT NULL COMMENT 'DEPARTMENT/POSITION/EMPLOYEE',
    scope_id    BIGINT      NOT NULL COMMENT '对应ID',
    KEY idx_group (group_id),
    KEY idx_scope (scope_type, scope_id)
) COMMENT='考勤组适用人员范围';


-- ============================================================
-- LAYER 4: 员工扩展表
-- ============================================================

-- 24. 员工个人信息表（含敏感加密字段）
CREATE TABLE employee_personal (
    employee_id         BIGINT PRIMARY KEY COMMENT '员工ID，关联 employee.id',
    id_number_enc       VARCHAR(256) NOT NULL COMMENT '身份证号密文（AES-256-GCM）',
    id_number_hash      VARCHAR(64)  NOT NULL COMMENT '身份证号SHA-256哈希（精确检索用）',
    birthday            DATE         NULL COMMENT '生日',
    household_address   VARCHAR(256) NULL COMMENT '户籍地址',
    residence_address   VARCHAR(256) NULL COMMENT '现居住地址',
    emergency_contact   VARCHAR(64)  NULL COMMENT '紧急联系人',
    emergency_phone     VARCHAR(16)  NULL COMMENT '紧急联系电话',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_id_number_hash (id_number_hash)
) COMMENT='员工个人信息表';

-- 25. 员工合同表（逻辑依赖 payroll_scheme）
CREATE TABLE employee_contract (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id             BIGINT       NOT NULL COMMENT '员工ID，关联 employee.id',
    contract_type           VARCHAR(16)  NOT NULL COMMENT '合同类型：FIXED=固定期限 UNFIXED=无固定期限 LABOR=劳务合同',
    contract_expire_date    DATE         NULL COMMENT '合同到期日，固定期限合同必填',
    probation_salary_ratio  DECIMAL(5,4) NOT NULL COMMENT '试用期待遇比例，范围0.80~1.00',
    scheme_id               BIGINT       NOT NULL COMMENT '薪资账套ID，关联 payroll_scheme.id',
    base_salary             DECIMAL(12,2) NOT NULL COMMENT '基本工资',
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_employee (employee_id)
) COMMENT='员工合同表';

-- 26. 员工银行卡信息表
CREATE TABLE employee_bank (
    employee_id         BIGINT PRIMARY KEY COMMENT '员工ID，关联 employee.id',
    bank_account_enc    VARCHAR(256) NULL COMMENT '银行卡号密文（AES-256-GCM）',
    bank_account_tail   VARCHAR(4)   NULL COMMENT '银行卡号后四位明文',
    bank_name           VARCHAR(64)  NULL COMMENT '开户行',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='员工银行卡信息表';

-- 27. 工号序列表（Redis 降级兜底）
CREATE TABLE employee_id_sequence_deprecated (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    year        CHAR(4)     NOT NULL COMMENT '年份',
    dept_code   VARCHAR(8)  NOT NULL COMMENT '部门编码',
    current_val INT         NOT NULL DEFAULT 0 COMMENT '当前序号',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_year_dept (year, dept_code)
) COMMENT='工号序列表';

-- 28. 工号复用历史表
CREATE TABLE employee_no_history (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_no     VARCHAR(16)  NOT NULL COMMENT '工号',
    year            CHAR(4)      NOT NULL COMMENT '年份',
    dept_code       VARCHAR(8)   NOT NULL COMMENT '部门编码',
    employee_id     BIGINT       NULL COMMENT '占用该工号的员工id，离职后置空',
    reuse_flag      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=占用中 1=可复用',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_reuse (reuse_flag, year, dept_code)
) COMMENT='工号复用历史表';

-- 29. 员工薪资档案（逻辑依赖 payroll_scheme）
CREATE TABLE employee_salary_profile (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id         BIGINT        NOT NULL COMMENT '员工ID，关联 employee.id',
    scheme_id           BIGINT        NOT NULL COMMENT '薪资账套ID，关联 payroll_scheme.id',
    base_salary         DECIMAL(12,2) NOT NULL COMMENT '基本工资',
    allowance_base_json JSON          NULL COMMENT '各项津贴基数JSON',
    ss_base             DECIMAL(12,2) NOT NULL COMMENT '社保基数',
    hf_base             DECIMAL(12,2) NOT NULL COMMENT '公积金基数',
    performance_base    DECIMAL(12,2) NULL COMMENT '绩效基数',
    probation_ratio     DECIMAL(5,4)  NOT NULL DEFAULT 1.0000 COMMENT '试用期待遇比例',
    effective_date      DATE          NOT NULL COMMENT '生效日期',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_employee (employee_id)
) COMMENT='员工薪资档案表';

-- 30. 调薪历史表
CREATE TABLE employee_salary_history (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id     BIGINT        NOT NULL COMMENT '员工ID，关联 employee.id',
    field_name      VARCHAR(32)   NOT NULL COMMENT '变更字段名',
    old_value       DECIMAL(12,2) NOT NULL COMMENT '变更前值',
    new_value       DECIMAL(12,2) NOT NULL COMMENT '变更后值',
    effective_date  DATE          NOT NULL COMMENT '生效日期',
    reason          VARCHAR(256)  NULL COMMENT '变更原因',
    operator_id     BIGINT        NOT NULL COMMENT '操作人 employee_id',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_employee (employee_id)
) COMMENT='调薪历史表';

-- 31. 手机号变更申请表（个人中心）
CREATE TABLE employee_mobile_change_application (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id     BIGINT       NULL COMMENT '审批实例ID，关联 approval_instance.id',
    employee_id     BIGINT       NOT NULL COMMENT '员工ID，关联 employee.id',
    user_id         BIGINT       NOT NULL COMMENT '系统用户ID，关联 sys_user.id',
    old_mobile      VARCHAR(16)  NOT NULL COMMENT '旧手机号',
    new_mobile      VARCHAR(16)  NOT NULL COMMENT '新手机号',
    sms_verified    TINYINT      NOT NULL DEFAULT 0 COMMENT '提交前已验证新号：0=否 1=是',
    reason          VARCHAR(256) NULL COMMENT '变更原因',
    status          VARCHAR(16)  NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_employee (employee_id),
    KEY idx_status (status)
) COMMENT='手机号变更申请表';


-- ============================================================
-- LAYER 5: 审批引擎
-- ============================================================

-- 32. 审批实例
CREATE TABLE approval_instance (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    process_type    VARCHAR(32) NOT NULL,
    business_key    VARCHAR(64) NOT NULL COMMENT '业务表主键',
    status          VARCHAR(32) NOT NULL,
    initiator_id    BIGINT      NOT NULL,
    current_node    INT         NOT NULL DEFAULT 1,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_type_status (process_type, status),
    KEY idx_initiator (initiator_id),
    KEY idx_business (process_type, business_key)
) COMMENT='审批实例';

-- 33. 审批任务（待办）；DB 列 sla_deadline，API 响应映射为 dueAt
CREATE TABLE approval_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id     BIGINT      NOT NULL,
    node_order      INT         NOT NULL,
    assignee_id     BIGINT      NOT NULL COMMENT '原审批人',
    actual_assignee_id BIGINT   NULL COMMENT '委托解析后的实际审批人',
    status          VARCHAR(16) NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/FORWARDED',
    comment         VARCHAR(512) NULL,
    sla_deadline    DATETIME     NULL,
    overdue         TINYINT      NOT NULL DEFAULT 0,
    completed_at    DATETIME     NULL,
    KEY idx_assignee_status (assignee_id, status),
    KEY idx_actual_assignee (actual_assignee_id, status),
    KEY idx_instance (instance_id)
) COMMENT='审批任务';

-- 34. 审批流转日志
CREATE TABLE approval_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id     BIGINT      NOT NULL,
    task_id         BIGINT      NULL,
    operator_id     BIGINT      NOT NULL COMMENT '实际操作人',
    on_behalf_of_id BIGINT      NULL COMMENT '被代审人',
    display_text    VARCHAR(128) NULL COMMENT '孙强 代 李明 审批',
    action          VARCHAR(16) NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/WITHDRAW/FORWARD',
    comment         VARCHAR(512) NULL,
    from_status     VARCHAR(32) NULL,
    to_status       VARCHAR(32) NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_instance (instance_id)
) COMMENT='审批流转日志';

-- 35. 委托审批（同一 delegator 仅一条 ACTIVE）
CREATE TABLE approval_delegation (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    delegator_id    BIGINT      NOT NULL,
    delegate_user_id BIGINT     NOT NULL,
    start_date      DATE        NOT NULL,
    end_date        DATE        NOT NULL,
    reason          VARCHAR(256) NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CANCELLED',
    cancelled_at    DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_delegator (delegator_id, status),
    KEY idx_delegate (delegate_user_id, status, start_date, end_date)
) COMMENT='委托审批表';


-- ============================================================
-- LAYER 6: 入转调离
-- ============================================================

-- 36. 入职申请（依赖 department, position, employee）
CREATE TABLE onboarding_application (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id             BIGINT       NULL,
    status                  VARCHAR(16)  NOT NULL COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED/ONBOARDED/ABANDONED',
    name                    VARCHAR(32)  NOT NULL,
    gender                  VARCHAR(8)   NOT NULL COMMENT 'MALE/FEMALE',
    mobile                  VARCHAR(16)  NOT NULL,
    email                   VARCHAR(128) NOT NULL,
    id_number_enc           VARCHAR(256) NOT NULL COMMENT '身份证密文',
    id_number_hash          VARCHAR(64)  NOT NULL COMMENT 'SHA256检索',
    expected_onboard_date   DATE         NOT NULL,
    department_id           BIGINT       NOT NULL,
    position_id             BIGINT       NOT NULL,
    employment_type         VARCHAR(16)  NOT NULL COMMENT 'fulltime/parttime/intern',
    probation_months        INT          NOT NULL,
    probation_salary_ratio  DECIMAL(5,4) NOT NULL,
    base_salary             DECIMAL(12,2) NULL COMMENT '约定薪资',
    actual_onboard_date     DATE         NULL COMMENT '实际入职日',
    manager_id              BIGINT       NULL,
    employee_id             BIGINT       NULL COMMENT '审批通过后关联',
    created_by              BIGINT       NOT NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_status (status),
    KEY idx_dept (department_id),
    KEY idx_mobile (mobile)
) COMMENT='入职申请表';

-- 37. 转正申请（依赖 employee）
CREATE TABLE regularization_application (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id             BIGINT       NULL,
    employee_id             BIGINT       NOT NULL,
    status                  VARCHAR(16)  NOT NULL,
    probation_start_date    DATE         NOT NULL,
    probation_end_date      DATE         NOT NULL,
    performance_evaluation  TEXT         NOT NULL,
    salary_adjustment       DECIMAL(12,2) NULL,
    approval_result         VARCHAR(16)  NULL COMMENT 'PASS/EXTEND/FAIL',
    extend_months           INT          NULL,
    created_by              BIGINT       NOT NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_employee (employee_id)
) COMMENT='转正申请表';

-- 38. 调岗申请（依赖 employee, department, position）
CREATE TABLE transfer_application (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id             BIGINT       NULL,
    employee_id             BIGINT       NOT NULL,
    status                  VARCHAR(16)  NOT NULL,
    from_department_id      BIGINT       NOT NULL,
    new_department_id       BIGINT       NOT NULL,
    new_position_id         BIGINT       NULL,
    new_job_level           VARCHAR(8)   NULL,
    new_manager_id          BIGINT       NULL,
    salary_adjustment       DECIMAL(12,2) NULL,
    effective_date          DATE         NOT NULL,
    reason                  VARCHAR(512) NULL,
    created_by              BIGINT       NOT NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_employee (employee_id)
) COMMENT='调岗申请表';

-- 39. 调岗历史表（依赖 employee, transfer_application）
CREATE TABLE employee_transfer_history (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id         BIGINT       NOT NULL COMMENT '员工ID，关联 employee.id',
    transfer_app_id     BIGINT       NOT NULL COMMENT '调岗申请ID，关联 transfer_application.id',
    from_department_id  BIGINT       NOT NULL COMMENT '原部门ID，关联 department.id',
    to_department_id    BIGINT       NOT NULL COMMENT '新部门ID，关联 department.id',
    from_position_id    BIGINT       NULL COMMENT '原职位ID，关联 position.id',
    to_position_id      BIGINT       NULL COMMENT '新职位ID，关联 position.id',
    transfer_date       DATE         NOT NULL COMMENT '调岗日期',
    reason              VARCHAR(512) NULL COMMENT '调岗原因',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_employee (employee_id)
) COMMENT='调岗历史表';

-- 40. 员工离职申请表（员工门户发起）
CREATE TABLE employee_resignation_request (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id         BIGINT       NULL,
    employee_id         BIGINT       NOT NULL,
    status              VARCHAR(16)  NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
    expected_resign_date DATE        NOT NULL,
    reason_category     VARCHAR(16)  NOT NULL COMMENT 'VOLUNTARY/INVOLUNTARY/NEGOTIATED',
    resignation_type    VARCHAR(16)  NOT NULL COMMENT 'RESIGN/DISMISS/CONTRACT_EXPIRE/OTHER',
    reason_detail       VARCHAR(512) NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_employee (employee_id),
    KEY idx_status (status)
) COMMENT='员工离职申请表';

-- 41. HR 正式离职表（依赖 employee, employee_resignation_request）
CREATE TABLE resignation_application (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id             BIGINT       NULL,
    request_id              BIGINT       NOT NULL COMMENT '关联 employee_resignation_request.id',
    employee_id             BIGINT       NOT NULL,
    status                  VARCHAR(16)  NOT NULL COMMENT 'APPROVING/PENDING_RESIGN/REJECTED/RESIGNED',
    resignation_date        DATE         NOT NULL,
    reason_category         VARCHAR(16)  NOT NULL COMMENT 'VOLUNTARY/INVOLUNTARY/NEGOTIATED',
    resignation_type        VARCHAR(16)  NOT NULL COMMENT 'RESIGN/DISMISS/CONTRACT_EXPIRE/OTHER',
    reason_detail           VARCHAR(512) NULL,
    handover_employee_id    BIGINT       NOT NULL,
    created_by              BIGINT       NOT NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_employee (employee_id),
    KEY idx_request (request_id),
    KEY idx_resign_date (resignation_date, status)
) COMMENT='HR正式离职申请表';


-- ============================================================
-- LAYER 7: 考勤打卡
-- ============================================================

-- 42. 考勤组成员（物化映射）
CREATE TABLE attendance_group_member (
    group_id    BIGINT NOT NULL COMMENT '考勤组ID',
    employee_id BIGINT NOT NULL COMMENT '员工ID',
    PRIMARY KEY (employee_id),
    KEY idx_group (group_id)
) COMMENT='员工-考勤组映射（物化）';

-- 43. 打卡流水
CREATE TABLE attendance_record (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id     BIGINT      NOT NULL,
    punch_date      DATE        NOT NULL COMMENT '考勤日',
    punch_time      DATETIME    NOT NULL COMMENT '打卡时间',
    punch_type      VARCHAR(8)  NOT NULL COMMENT 'IN/OUT',
    punch_status    VARCHAR(16) NOT NULL COMMENT 'NORMAL/LATE/EARLY_LEAVE/ABSENT_HALF',
    source          VARCHAR(16) NOT NULL COMMENT 'WEB/APP/MAKEUP',
    client_ip       VARCHAR(45) NULL,
    gps_json        JSON        NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_emp_date (employee_id, punch_date),
    KEY idx_emp_time (employee_id, punch_time)
) COMMENT='打卡流水';

-- 44. 日考勤汇总
CREATE TABLE attendance_daily_summary (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id     BIGINT       NOT NULL,
    summary_date    DATE         NOT NULL,
    day_status      VARCHAR(16)  NOT NULL COMMENT 'NORMAL/LATE/EARLY_LEAVE/ABSENT_HALF/ABSENT/MISSING_IN/MISSING_OUT/LEAVE',
    clock_in_time   DATETIME     NULL,
    clock_out_time  DATETIME     NULL,
    leave_days      DECIMAL(3,1) NOT NULL DEFAULT 0 COMMENT '当日请假天数',
    overtime_hours  DECIMAL(5,2) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_emp_date (employee_id, summary_date),
    KEY idx_date_status (summary_date, day_status)
) COMMENT='日考勤汇总';

-- 45. 补卡申请
CREATE TABLE attendance_supplement (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id     BIGINT       NOT NULL,
    makeup_date     DATE         NOT NULL COMMENT '补卡日期',
    punch_type      VARCHAR(8)   NOT NULL COMMENT 'IN/OUT',
    makeup_time     DATETIME     NOT NULL COMMENT '补卡时间',
    reason          VARCHAR(256) NOT NULL,
    status          VARCHAR(16)  NOT NULL COMMENT 'PENDING/APPROVED/REJECTED',
    instance_id     BIGINT       NULL COMMENT '审批实例ID',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_emp_month (employee_id, created_at)
) COMMENT='补卡申请表';

-- 46. 假期余额
CREATE TABLE leave_balance (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id     BIGINT        NOT NULL,
    leave_type      VARCHAR(16)   NOT NULL COMMENT 'ANNUAL/COMP_OFF',
    balance         DECIMAL(6,1)  NOT NULL DEFAULT 0,
    year            INT           NULL COMMENT '年假年度',
    expire_date     DATE          NULL COMMENT '调休过期日',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_emp_type_year (employee_id, leave_type, year)
) COMMENT='假期余额表';

-- 47. 请假申请
CREATE TABLE leave_application (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id         BIGINT        NOT NULL,
    leave_type          VARCHAR(16)   NOT NULL COMMENT 'ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/BEREAVEMENT/COMP_OFF',
    start_time          DATETIME      NOT NULL COMMENT '含上午/下午',
    end_time            DATETIME      NOT NULL COMMENT '含上午/下午',
    leave_days          DECIMAL(4,1)  NOT NULL COMMENT '支持0.5天',
    reason              VARCHAR(512)  NOT NULL,
    handover_employee_id BIGINT       NULL COMMENT '交接人',
    attachment_url      VARCHAR(512)  NULL COMMENT '附件URL',
    status              VARCHAR(16)   NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
    instance_id         BIGINT        NULL COMMENT '审批实例ID',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_emp_status (employee_id, status),
    KEY idx_date_range (start_time, end_time)
) COMMENT='请假申请表';

-- 48. 加班申请
CREATE TABLE overtime_application (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id     BIGINT        NOT NULL,
    overtime_date   DATE          NOT NULL COMMENT '加班日期',
    start_time      DATETIME      NOT NULL COMMENT '加班开始时间',
    end_time        DATETIME      NOT NULL COMMENT '加班结束时间',
    hours           DECIMAL(5,2)  NOT NULL COMMENT '系统计算时长',
    reason          VARCHAR(512)  NOT NULL COMMENT '加班原因',
    status          VARCHAR(16)   NOT NULL COMMENT 'PENDING/APPROVED/REJECTED',
    comp_off_hours  DECIMAL(5,2)  NULL COMMENT '折算调休小时',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_emp_date (employee_id, overtime_date)
) COMMENT='加班申请表';

-- 49. 加班批准台账（供算薪）
CREATE TABLE overtime_ledger (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id         BIGINT        NOT NULL,
    application_id      BIGINT        NULL COMMENT '关联 overtime_application.id',
    period              CHAR(7)       NOT NULL COMMENT '归属账期 2026-07',
    total_hours         DECIMAL(5,2)  NOT NULL COMMENT '审批总加班时长',
    rate_type           TINYINT       NOT NULL COMMENT '倍率: 15=1.5倍/20=2.0倍/30=3.0倍',
    ledger_date         DATE          NOT NULL COMMENT '加班日期',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_emp_period (employee_id, period),
    KEY idx_application (application_id)
) COMMENT='加班批准台账';

-- 50. 月考勤汇总
CREATE TABLE attendance_monthly_summary (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id         BIGINT        NOT NULL,
    period              CHAR(7)       NOT NULL COMMENT '2026-07',
    should_attend_days  INT           NOT NULL COMMENT '应出勤',
    actual_attend_days  DECIMAL(5,1)  NOT NULL COMMENT '实际出勤',
    late_count          INT           NOT NULL DEFAULT 0,
    early_leave_count   INT           NOT NULL DEFAULT 0,
    absent_days         DECIMAL(5,1)  NOT NULL DEFAULT 0,
    leave_days          DECIMAL(5,1)  NOT NULL DEFAULT 0,
    overtime_hours      DECIMAL(6,2)  NOT NULL DEFAULT 0,
    annual_balance      DECIMAL(5,1)  NULL COMMENT '年假余额',
    detail_json         JSON          NULL COMMENT '明细快照',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_emp_period (employee_id, period)
) COMMENT='月考勤汇总表';


-- ============================================================
-- LAYER 8: 薪资核算
-- ============================================================

-- 51. 月度核算批次
CREATE TABLE payroll_batch (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    period              CHAR(7)       NOT NULL COMMENT '账期 YYYY-MM',
    status              VARCHAR(20)   NOT NULL COMMENT 'DRAFT/CALCULATING/PENDING_CONFIRM/APPROVING/APPROVED/DISTRIBUTED/REJECTED',
    total_count         INT           NOT NULL DEFAULT 0,
    success_count       INT           NOT NULL DEFAULT 0,
    gross_total         DECIMAL(14,2) NULL COMMENT '应发合计',
    net_total           DECIMAL(14,2) NULL COMMENT '实发合计',
    anomaly_count       INT           NOT NULL DEFAULT 0,
    instance_id         BIGINT        NULL COMMENT '审批实例',
    attendance_locked   TINYINT       NOT NULL DEFAULT 0,
    created_by          BIGINT        NOT NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_period (period)
) COMMENT='月度核算批次表';

-- 52. 个税累计预扣记录
CREATE TABLE pay_tax_ytd_record (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id     BIGINT        NOT NULL,
    period          CHAR(7)       NOT NULL,
    taxable_income  DECIMAL(12,2) NOT NULL COMMENT '应纳税所得额',
    tax_deducted    DECIMAL(12,2) NOT NULL COMMENT '本期预扣税额',
    cumulative_tax  DECIMAL(12,2) NOT NULL COMMENT '累计预扣税额',
    UNIQUE KEY uk_emp_period (employee_id, period)
) COMMENT='个税累计预扣记录';

-- 53. 工资条查看日志
CREATE TABLE payslip_view_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id     BIGINT      NOT NULL,
    batch_id        BIGINT      NOT NULL,
    viewed_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '查看时间',
    verify_method   VARCHAR(16) NULL COMMENT 'PASSWORD/SMS',
    KEY idx_emp (employee_id)
) COMMENT='工资条查看日志';

-- 54. 批次核算明细
CREATE TABLE payroll_detail (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id        BIGINT        NOT NULL,
    employee_id     BIGINT        NOT NULL,
    calc_status     VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
    gross_salary    DECIMAL(12,2) NULL,
    net_salary      DECIMAL(12,2) NULL,
    detail_json     JSON          NOT NULL COMMENT '各薪资项明细',
    anomaly_flags   JSON          NULL COMMENT '异常标记数组',
    prev_net_salary DECIMAL(12,2) NULL COMMENT '上月实发',
    manual_adjusted TINYINT       NOT NULL DEFAULT 0,
    KEY idx_batch (batch_id),
    KEY idx_employee (employee_id),
    UNIQUE KEY uk_batch_emp (batch_id, employee_id)
) COMMENT='批次核算明细表';

-- 55. 核算手动调整记录
CREATE TABLE payroll_adjustment (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    detail_id       BIGINT        NOT NULL,
    item_code       VARCHAR(32)   NOT NULL,
    adjust_amount   DECIMAL(12,2) NOT NULL,
    reason          VARCHAR(256)  NOT NULL,
    operator_id     BIGINT        NOT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_detail (detail_id)
) COMMENT='核算手动调整记录';


-- ============================================================
-- 预置数据：角色初始化
-- ============================================================

INSERT INTO sys_role (code, name, data_scope) VALUES
    ('SYS_ADMIN',    '系统管理员', 'NONE_PAYROLL'),
    ('HR_STAFF',     'HR专员',     'ALL'),
    ('DEPT_MANAGER', '部门主管',   'DEPT_TREE'),
    ('FINANCE',      '财务专员',   'PAYROLL'),
    ('EMPLOYEE',     '普通员工',   'SELF');

-- ============================================================
-- 预置数据：工作日配置（周一至周五工作）
-- ============================================================

INSERT INTO workday_config (day_of_week, is_workday) VALUES
    (1, 1), (2, 1), (3, 1), (4, 1), (5, 1), (6, 0), (7, 0);

-- ============================================================
-- 预置数据：个税税率表（2024 年度，起征点 5000）
-- ============================================================

INSERT INTO pay_tax_bracket (tax_year, min_taxable, max_taxable, rate, quick_deduction) VALUES
    (2024, 0,       36000,     0.03, 0),
    (2024, 36000,   144000,    0.10, 2520),
    (2024, 144000,  300000,    0.20, 16920),
    (2024, 300000,  420000,    0.25, 31920),
    (2024, 420000,  660000,    0.30, 52920),
    (2024, 660000,  960000,    0.35, 85920),
    (2024, 960000,  NULL,      0.45, 181920);

-- ============================================================
-- 预置数据：数据字典（业务枚举）
-- ============================================================

INSERT INTO sys_dict (dict_type, dict_code, dict_label, sort_order) VALUES
    ('employment_status', 'PROBATION',     '试用期',   1),
    ('employment_status', 'REGULAR',       '正式',     2),
    ('employment_status', 'PENDING_RESIGN','待离职',   3),
    ('employment_status', 'RESIGNED',      '已离职',   4),
    ('contract_type',     'FIXED',         '固定期限', 1),
    ('contract_type',     'UNFIXED',       '无固定期限',2),
    ('contract_type',     'LABOR',         '劳务合同', 3),
    ('position_sequence', 'M',             '管理序列', 1),
    ('position_sequence', 'P',             '专业序列', 2),
    ('position_sequence', 'S',             '支持序列', 3),
    ('leave_type',        'ANNUAL',        '年假',     1),
    ('leave_type',        'SICK',          '病假',     2),
    ('leave_type',        'PERSONAL',      '事假',     3),
    ('leave_type',        'MARRIAGE',      '婚假',     4),
    ('leave_type',        'MATERNITY',     '产假',     5),
    ('leave_type',        'BEREAVEMENT',   '丧假',     6),
    ('leave_type',        'COMP_OFF',      '调休',     7),
    ('process_type',      'ONBOARDING',           '入职审批',     1),
    ('process_type',      'REGULARIZATION',       '转正审批',     2),
    ('process_type',      'TRANSFER',             '调岗审批',     3),
    ('process_type',      'RESIGNATION',          '离职审批',     4),
    ('process_type',      'RESIGNATION_REQUEST',  '员工离职申请', 5),
    ('process_type',      'MOBILE_CHANGE',        '手机号变更',   6),
    ('process_type',      'LEAVE',                '请假审批',     7),
    ('process_type',      'MAKEUP',               '补卡审批',     8),
    ('process_type',      'OVERTIME',             '加班审批',     9),
    ('process_type',      'PAYROLL_BATCH',        '薪资批次审批', 10);
