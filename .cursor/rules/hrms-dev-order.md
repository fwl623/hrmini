---
description: HRMS 6 天开发日历（四人并行）
globs:
  - backend/**/*
  - frontend/**/*
alwaysApply: false
---

# HRMS 6 天开发日历

> 四人并行，各负责自己模块的前后端。总 DDL 已合并为 `V1__init_hrms_schema.sql`（55 张表 + 种子数据），无需再写 DDL。

## 工作规范

### Git 分支策略

| 分支 | 用途 | 负责人 |
| --- | --- | --- |
| `master` | 主分支，保护分支 | 四人可合并 |
| `test` | 测试分支，联调用 | 四人可合并 |
| `feature/LJY` | A（李俊毅）个人开发分支 | 李俊毅 |
| `feature/FWL` | B（范文路）个人开发分支 | 范文路 |
| `feature/GC` | C（郭策）个人开发分支 | 郭策 |
| `feature/ZHJ` | D（张浩杰）个人开发分支 | 张浩杰 |

**流程：**
1. 每个人在自己的 feature 分支上开发，每日多次提交
2. 一个模块完成后，合并到 `test` 分支进行联调
3. Day 5 联调通过后，各自合并到 `master`
4. 合并前确保 `mvn clean install` 通过，无编译错误
5. 禁止直接往 `master` 提交未联调通过的代码

### 沟通

- **钉钉群**：随时同步进度和问题
- **遇到阻塞**：群里 @ 对应人员，如果 30 分钟未响应则找组长协调
- **接口变更**：改接口的人必须在钉钉群发公告，@ 所有调用方

### Code Review

- **各人只管自己模块的代码**，不需要交叉 review
- 合并到 `test` 前，自己快速过一遍自己的 diff，确认没有：
  - 硬编码的敏感信息（密码、密钥）
  - 注释掉的代码
  - 明显的空指针风险
- Day 6 验收前做一次全量 self-review

### 环境

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| MySQL | 云服务器 | 连接信息见钉钉群置顶 |
| Redis | 云服务器 | 连接信息见钉钉群置顶 |
| RabbitMQ | 云服务器 | 连接信息见钉钉群置顶 |
| 后端 | `localhost:8080` | 本地启动 |
| 前端 | `localhost:8000` | 本地启动 |

### 每日收工验收标准

| 天数 | 最低标准 |
| --- | --- |
| Day 1 | 各自模块 `mvn compile` 通过，前端骨架页面无报错渲染 |
| Day 2 | A 的登录→Profile 闭环可用（Postman 调通）；B/C/D 的 Swagger 接口可调用 |
| Day 3 | 各自核心 CRUD 前后端跑通，字段增删改查正常 |
| Day 4 | 各自模块功能完整，Swagger 可覆盖 80% 接口 |
| Day 5 | 跨模块 E2E 链路走通，联调清单打勾 |
| Day 6 | SEC 测试通过 + 无 P0/P1 Bug |

### Bug 优先级定义

| 级别 | 定义 | 例子 | 处理方式 |
| --- | --- | --- | --- |
| **P0** | 阻塞上线 | 登录失败、越权访问薪资、核心链路不通、数据丢失 | 必须立即修复 |
| **P1** | 核心功能受损 | 权限不生效、刷新 Token 失败、审批状态不对 | 当天内修复 |
| **P2** | 一般功能异常 | 搜索筛选无效、分页错误、日志缺失 | 记录，Day 5~6 修复 |
| **P3** | 体验问题 | 按钮样式不对、文案错字、记住登录失效 | 记录，视时间修复 |

### 阻塞处理流程

```
遇到依赖被阻塞
  → 先做自己模块内无依赖的部分（UI、纯逻辑、单元测试）
  → 跨模块调用先用 Mock 数据占位
  → 钉钉群 @ 依赖方，说明阻塞点和期望交付时间
  → 如果依赖方 30 分钟内未回复 → 找组长协调
  → 依赖交付后，花 30 分钟内完成对接，不要拖
```

### 接口变更通知

```
改接口的人必须做：
  1. 在钉钉群发公告，格式：【接口变更】模块+路径+变更内容
  2. @ 所有调用该接口的人
  3. 更新 API 契约文档（openapi.yaml 或 HRMS-API-Contract.md）

收到通知的人必须做：
  1. 评估影响范围
  2. 在 1 小时内确认是否适配
  3. 如果无法适配，立即回复说明原因
```

## 依赖关系速查

```
hrms-common(A) → hrms-auth(A) → hrms-org(A)
                                    ↓
                    hrms-employee(B) → hrms-workflow(C)
                           ↓                ↓
               hrms-attendance(D) → hrms-payroll(D)
```

## 第一天

**A 产出 common（供所有人用） + 并行开工**

| 人员 | 后端 | 前端 |
| --- | --- | --- |
| **A（李俊毅）** | hrms-app 父 POM、Flyway 总脚本<br>hrms-common：`Result<T>`、全局异常、DataScope、FieldPermission、分页、审计字段<br>✅ 无依赖（基础工程） | — |
| **B（范文路）** | hrms-employee 模块 9 张表的实体 + Mapper + XML<br>✅ 无依赖（只需 DDL + Lombok）| 花名册 ProTable 骨架 + 详情三 Tab 骨架 + 编辑表单骨架（Mock 数据）<br>✅ 无依赖（纯 UI）|
| **C（郭策）** | hrms-workflow 模块 11 张表的实体 + Mapper + XML<br>审批状态机纯逻辑（无 Spring 注解的状态模式）<br>✅ 无依赖（纯 Java 枚举 + 状态模式）| ApprovalTimeline / ApprovalActions / ProcessStatusTag 组件 + 审批工作台 UI 骨架<br>✅ 无依赖（纯 UI 组件）|
| **D（张浩杰）** | hrms-attendance 15 张表 + hrms-payroll 9 张表的实体 + Mapper + XML<br>✅ 无依赖（只需 DDL + Lombok）| 考勤组管理页骨架 + 账套管理页骨架 + 打卡页 UI 骨架<br>✅ 无依赖（纯 UI）|

> **验证点：** 所有人 `mvn compile` 通过。A 的 `Result<T>` 被 B/C/D 引用编译无报错。

#### 第一天提示词

##### A（李俊毅）- hrms-app 父工程 + hrms-common

```prompt
@后端系分模板.md
@HRMS-Backend-System-Design.md

请创建 hrms-app 父 POM 和 hrms-common 公共模块：

后端文件：
- backend/pom.xml（Maven 父工程，聚合所有子模块）
- backend/hrms-app/pom.xml（启动模块）
- backend/hrms-common/pom.xml
- backend/hrms-common/src/main/java/com/company/hrms/common/Result.java
  └── { code, message, data, traceId, timestamp }
- backend/hrms-common/src/main/java/com/company/hrms/common/PageParam.java（page+pageSize 校验）
- backend/hrms-common/src/main/java/com/company/hrms/common/PageResult.java（list+total）
- backend/hrms-common/src/main/java/com/company/hrms/common/GlobalExceptionHandler.java
  └── @ControllerAdvice，异常→错误码：参数校验(10001)、业务异常(自定义code)、未登录(20001)、无权限(20002)、90001
- backend/hrms-common/src/main/java/com/company/hrms/common/annotation/DataScope.java
- backend/hrms-common/src/main/java/com/company/hrms/common/interceptor/DataScopeInterceptor.java
  └── 支持 ALL / DEPT_TREE / SELF / PAYROLL / NONE_PAYROLL
- backend/hrms-common/src/main/java/com/company/hrms/common/filter/FieldPermissionFilter.java
- backend/hrms-common/src/main/java/com/company/hrms/common/utils/SecurityUtils.java
- backend/hrms-common/src/main/java/com/company/hrms/common/utils/TraceIdUtil.java
- backend/hrms-common/src/main/java/com/company/hrms/common/config/MyMetaObjectHandler.java
  └── MyBatis-Plus 自动填充 createdAt / updatedAt
- backend/hrms-app/src/main/resources/db/migration/V1__init_hrms_schema.sql（已有，确认路径正确）

要求：
1. Result 泛型支持任意 data 类型
2. 全局异常覆盖 ValidationException、BusinessException、AccessDeniedException、NPE 兜底
3. DataScope 拦截器在 MyBatis Executor 层追加 SQL WHERE 条件
4. FieldPermission 在 Controller 响应返回前拦截，敏感字段置 null
```

##### B（范文路）- hrms-employee 实体+Mapper+前端骨架

```prompt
@后端系分模板.md
@backend/hrms-app/src/main/resources/db/migration/V1__init_hrms_schema.sql

请基于 DDL 表 #21~#31 生成 hrms-employee 模块的基础代码：

后端文件：
- backend/hrms-employee/pom.xml
- backend/hrms-employee/src/main/java/com/company/hrms/employee/entity/
  └── Employee.java / EmployeePersonal.java / EmployeeContract.java / EmployeeBank.java
      EmployeeSalaryProfile.java / EmployeeSalaryHistory.java / EmployeeTransferHistory.java
      EmployeeNoHistory.java / EmployeeMobileChangeApplication.java
  └── 所有实体用 Lombok @Data + @TableName，字段类型与 DDL 一致
- backend/hrms-employee/src/main/java/com/company/hrms/employee/mapper/
  └── 每张表一个 Mapper 接口 + src/main/resources/mapper/*.xml

DDL 关键参考：
- employee：id(employee_id), employee_no, user_id, name, gender, mobile, email,
  department_id, position_id, grade, manager_id, hire_date, employment_type,
  employment_status(TINYINT 10/20/30/40), deleted
- employee_personal：employee_id(PK), id_number_enc, id_number_hash, birthday, residence_address
- employee_mobile_change_application：status(PENDING/APPROVED/REJECTED/CANCELLED)

前端骨架文件（数据用 Mock，不调后端）：
- frontend/src/pages/admin/employee/list/index.tsx（ProTable + SearchBar + StatusTag 蓝/绿/黄/灰）
- frontend/src/pages/admin/employee/detail/index.tsx（三 Tab：个人信息/工作信息/薪资合同）
- frontend/src/pages/admin/employee/edit/index.tsx（白名单字段可编辑，非白名单 disabled + Tooltip）
- frontend/src/services/employee.ts（API 定义占位）
```

##### C（郭策）- hrms-workflow 实体+Mapper+状态机+前端组件

```prompt
@后端系分模板.md
@backend/hrms-app/src/main/resources/db/migration/V1__init_hrms_schema.sql

请基于 DDL 表 #12、#32~#41 生成 hrms-workflow 模块基础代码：

后端文件：
- backend/hrms-workflow/pom.xml
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/entity/
  └── ApprovalProcessDef.java / ApprovalInstance.java / ApprovalTask.java
      ApprovalLog.java / ApprovalDelegation.java
      OnboardingApplication.java / RegularizationApplication.java
      TransferApplication.java / EmployeeTransferHistory.java
      EmployeeResignationRequest.java / ResignationApplication.java
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/mapper/
  └── 每表一个 Mapper + XML
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/enums/
  └── ApprovalAction.java（SUBMIT/APPROVE/REJECT/FORWARD/WITHDRAW）
      ApprovalStatus.java（各业务状态枚举）
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/core/
  └── ApprovalStateMachine.java（状态模式，纯 Java 无 Spring 注解）
      ├── 入职：draft→pending→approved_pending→onboarded/rejected/abandoned
      ├── 转正：pending→PASS/EXTEND/FAIL
      ├── 调岗：pending→三节点→APPROVED
      └── 离职：pending→APPROVED→PENDING_RESIGN→RESIGNED

前端组件（数据用 Mock）：
- frontend/src/components/ApprovalTimeline.tsx（审批进度时间线，按节点展示）
- frontend/src/components/ApprovalActions.tsx（按钮组：同意/驳回/转交/撤回）
- frontend/src/components/ProcessStatusTag.tsx（流程状态彩色 Tag）
- frontend/src/pages/admin/approval/index.tsx（三 Tab：我的待办/我的已办/我发起的）
- frontend/src/services/workflow.ts（API 定义占位）
```

##### D（张浩杰）- hrms-attendance + hrms-payroll 实体+Mapper+前端骨架

```prompt
@考勤请假-后端系分.md
@薪资-后端系分.md
@backend/hrms-app/src/main/resources/db/migration/V1__init_hrms_schema.sql

请基于 DDL 表 #4~#11、#22~#23、#42~#55 生成考勤和薪资模块基础代码：

考勤模块后端文件：
- backend/hrms-attendance/pom.xml
- backend/hrms-attendance/src/main/java/com/company/hrms/attendance/entity/
  └── 共 15 张表：workday_config / holiday_calendar / attendance_month_lock
      attendance_group / attendance_group_scope / attendance_group_member
      attendance_record / attendance_daily_summary / attendance_supplement
      leave_balance / leave_application / overtime_application / overtime_ledger
      attendance_monthly_summary
- backend/hrms-attendance/src/main/java/com/company/hrms/attendance/mapper/
  └── 每表一个 Mapper + XML

薪资模块后端文件：
- backend/hrms-payroll/pom.xml
- backend/hrms-payroll/src/main/java/com/company/hrms/payroll/entity/
  └── 共 9 张表：payroll_scheme / payroll_scheme_item / payroll_scheme_scope
      pay_tax_bracket / payroll_batch / pay_tax_ytd_record
      payslip_view_log / payroll_detail / payroll_adjustment
- backend/hrms-payroll/src/main/java/com/company/hrms/payroll/mapper/
  └── 每表一个 Mapper + XML

前端骨架（数据用 Mock）：
- frontend/src/pages/admin/attendance/groups/index.tsx（考勤组 CRUD + 班次/时间/阈值表单）
- frontend/src/pages/admin/payroll/schemes/index.tsx（账套列表 + 工资项目 SpEL 公式编辑）
- frontend/src/pages/portal/attendance/index.tsx（打卡页：今日状态卡片 + 打卡按钮）
- frontend/src/services/attendance.ts（API 占位）
- frontend/src/services/payroll.ts（API 占位）
```

**A 做 auth（认证） + 各人开始对接后端**

| 人员 | 后端 | 前端 |
| --- | --- | --- |
| **A（李俊毅）** | hrms-auth：登录/登出/刷新/Profile、JWT Filter + Security 配置<br>安全策略：5 次锁定、90 天密码轮换、首次改密<br>系统管理：用户 CRUD、角色管理、权限分配 Tree<br>内部 Feign 接口：创建账号/禁用/同步用户名<br>种子数据：5 预置角色 + 权限码 + 管理员<br>✅ 依赖自己的 common（Day 1 已交付）| 全局 request 拦截器 + access.ts + useUserStore + 登录页 + AdminLayout/PortalLayout + 路由守卫<br>✅ 无依赖（纯前端）|
| **B（范文路）** | employee Service 层 + Controller（用 A 的 `Result<T>` 和异常）<br>花名册分页 + 详情 + 白名单编辑接口<br>⚠️ 依赖 A 的 common（Day 1 晚上交付）| 花名册页对接真实 API，替换 Mock<br>⚠️ 依赖 A 的前端基座（Day 2 晚上合并）|
| **C（郭策）** | approval 引擎 Service + Controller：创建实例、待办任务、审批操作(action)、催办<br>入职申请 CRUD + 提交/撤回/确认/放弃<br>⚠️ 依赖 A 的 common（Day 1 晚上交付）| 审批工作台对接真实 API，替换 Mock<br>⚠️ 依赖 A 的前端基座（Day 2 晚上合并）|
| **D（张浩杰）** | 考勤组 Service + Controller：CRUD + 工作日/节假日配置<br>打卡 Service：打卡判定逻辑（NORMAL/LATE/ABSENT_HALF）+ Redis 幂等<br>⚠️ 依赖 A 的 common（Day 1 晚上交付）| 考勤组管理页对接真实 API，替换 Mock<br>⚠️ 依赖 A 的前端基座（Day 2 晚上合并）|

> **验证点：** A 的登录→Profile 全链路闭环（Postman 可调通）。B/C/D 的业务接口在 Swagger 可调通。
> **晚上联调：** A 提供前端基座代码给其他人合并，确保前端框架跑通。

#### 第二天提示词

##### A（李俊毅）- hrms-auth 认证鉴权 + 前端基座

```prompt
@后端系分模板.md
@HRMS-Backend-System-Design.md
@HRMS-API-Contract.md
@frontend/src/services/auth.ts

请开发 hrms-auth 认证鉴权模块和前端基座：

后端文件：
- backend/hrms-auth/pom.xml
- backend/hrms-auth/src/main/java/com/company/hrms/auth/controller/AuthController.java
  └── POST /auth/login → { accessToken, refreshToken, mustChangePassword }
  └── POST /auth/logout → jti 入黑名单
  └── POST /auth/refresh → 轮换新 Token 对
  └── GET /auth/profile → { userId, roles, permissions, dataScope }
  └── PUT /auth/password（校验旧密+强度+新旧不同+BCrypt 更新）
  └── POST /auth/verify（工资条二次验证，Redis TTL 30min）
- backend/hrms-auth/src/main/java/com/company/hrms/auth/controller/SystemController.java
  └── GET/POST/PUT /system/users（用户 CRUD，SYS_ADMIN 权限）
  └── GET/PUT /system/roles（角色列表/编辑，编码只读）
  └── GET/PUT /system/roles/{id}/permissions（权限 Tree 分配）
  └── GET /system/operation-logs / login-logs
- backend/hrms-auth/src/main/java/com/company/hrms/auth/controller/InternalController.java
  └── POST /internal/users（建号，默认 EMPLOYEE 角色）
  └── PUT /internal/users/{id}/status（禁用/启用）
  └── PUT /internal/users/{id}/username（同步手机号）
- backend/hrms-auth/src/main/java/com/company/hrms/auth/config/JwtAuthFilter.java
  └── 解析 JWT → 校验签名 + 黑名单 + user:last-active 续期
- backend/hrms-auth/src/main/java/com/company/hrms/auth/config/SecurityConfig.java
  └── Spring Security + BCrypt + CORS
- backend/hrms-auth/src/main/java/com/company/hrms/auth/config/RedisConfig.java
- backend/hrms-auth/src/main/java/com/company/hrms/auth/service/LoginService.java
  └── 登录失败计数(login:fail INCR EX 900) + 5 次锁定
  └── 密码 90 天轮换校验(mustChangePassword)
- backend/hrms-auth/src/main/java/com/company/hrms/auth/service/PermissionService.java
  └── 权限缓存 user:permissions TTL 10min，变更时删 Key

安全策略要求：
1. AccessToken TTL 2h（7200s），RefreshToken TTL 7d（Redis 白名单）
2. 无操作超时 30min（user:last-active Key 过期 → 401）
3. login:fail 第 5 次 EXPIRE 900，锁定期正确密码也拒绝
4. 旧 refreshToken 轮换后立即作废
5. 篡改 JWT payload → 签名失败 401

前端基座文件：
- frontend/src/app.tsx（全局配置 + request 拦截器 → 自动携带 Bearer Token）
- frontend/src/access.ts（Umi access 定义：canHr / canApprove / canViewSalary 等）
- frontend/src/stores/userStore.ts（userId, roles, permissions, dataScope）
- frontend/src/stores/permissionStore.ts（权限码集合 + hasPermission 方法）
- frontend/src/pages/login/index.tsx（手机号登录 + 记住登录 + 首次改密弹窗）
- frontend/src/layouts/AdminLayout.tsx（Sider 动态菜单 + Header 面包屑/用户下拉 + Content）
- frontend/src/layouts/PortalLayout.tsx（员工门户导航：档案/考勤/请假/加班/薪资/离职/安全）
- frontend/src/utils/idleDetector.ts（30min 无操作 forceLogout）
- frontend/src/utils/tokenRefresher.ts（JWT exp 提前 60s 静默刷新）
```

##### B（范文路）- hrms-employee Service + Controller + 前端对接

```prompt
@后端系分-员工档案个人中心.pdf
@hrms-employee-后端系分.md
@HRMS-API-Contract.md
@backend/hrms-employee/src/main/java/com/company/hrms/employee/entity/Employee.java（已有）

请开发 hrms-employee 的业务层和接口层：

后端文件：
- backend/hrms-employee/src/main/java/com/company/hrms/employee/controller/EmployeeController.java
  └── GET /employees（分页 + 关键词/部门/状态/日期范围筛选 + DataScope）
  └── GET /employees/{id}（详情 + fieldPermissions + 敏感字段脱敏）
  └── PUT /employees/{id}（白名单校验：非白名单字段→20003）
- backend/hrms-employee/src/main/java/com/company/hrms/employee/controller/ProfileController.java
  └── GET/PUT /profile/me（SELF 数据范围，白名单：邮箱/地址/紧急联系人）
  └── PUT /profile/security/password（改密）
  └── POST /profile/security/mobile/bind（首次绑定）
  └── GET /profile/security/login-logs（本人登录日志）
- backend/hrms-employee/src/main/java/com/company/hrms/employee/service/EmployeeService.java
  └── 列表查询 + DataScope + 脱敏处理
  └── 白名单编辑 + 审计日志记录

关键逻辑：
1. 白名单字段：name/email/birthday/residenceAddress/emergencyContact/emergencyPhone
2. 越权字段 PUT → 20003
3. 离职员工编辑 → 30003
4. 角色权限：HR_STAFF 看全部、DEPT_MANAGER 看本部门、EMPLOYEE 仅 SELF

前端对接：
- frontend/src/pages/admin/employee/list/index.tsx → 对接真实 GET /employees API
- frontend/src/pages/admin/employee/detail/index.tsx → 对接 GET /employees/{id}
- frontend/src/pages/admin/employee/edit/index.tsx → 对接 PUT /employees/{id}
- frontend/src/services/employee.ts → 补全全部 API 调用
```

##### C（郭策）- 审批引擎 + 入职申请 Service/Controller

```prompt
@人员C-后端系分.md
@HRMS-Backend-System-Design.md
@HRMS-API-Contract.md

请开发 hrms-workflow 的审批引擎和入职申请业务层：

后端文件：
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/controller/ApprovalController.java
  └── GET /approvals/tasks/stats（待办统计，含 overdueCount）
  └── GET /approvals/tasks（待办/已办列表 + 筛选）
  └── GET /approvals/tasks/{id}（审批详情 + 业务 Detail）
  └── POST /approvals/tasks/{id}/action（APPROVE/REJECT/FORWARD）
  └── POST /approvals/tasks/{id}/remind（催办）
  └── POST /approvals/instances/{id}/withdraw（撤回）
  └── GET /approvals/instances（我发起的）
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/controller/OnboardingController.java
  └── GET        /onboarding/applications（列表 + stats 统计）
  └── POST       /onboarding/applications（新建草稿）
  └── PUT        /onboarding/applications/{id}（编辑，仅 draft）
  └── DELETE     /onboarding/applications/{id}（删除，仅 draft）
  └── POST       /onboarding/applications/{id}/submit（提交审批）
  └── POST       /onboarding/applications/{id}/withdraw（撤回，第一级）
  └── POST       /onboarding/applications/{id}/confirm（确认入职）
  └── POST       /onboarding/applications/{id}/abandon（放弃入职）
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/service/ApprovalEngine.java
  └── 创建 instance + 解析审批链 + 生成 task
  └── 执行 action（状态机校验 + 状态流转 + 日志记录）
  └── 委托解析（按 delegator→delegate 分配 actual_assignee_id）
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/service/OnboardingService.java
  └── 状态机校验：draft→pending→approved_pending→onboarded/rejected/abandoned
  └── 手机号唯一校验：409
  └── 二级审批：非标准职位或薪资超职级

前端对接：
- frontend/src/pages/admin/approval/index.tsx → 对接待办/已办/我发起 API
- frontend/src/pages/admin/onboarding/index.tsx → 对接入职 CRUD + submit/confirm
- frontend/src/services/workflow.ts → 补全全部 API 调用
```

##### D（张浩杰）- 考勤组 + 打卡 Service/Controller

```prompt
@考勤请假-后端系分.md
@HRMS-Backend-System-Design.md
@HRMS-API-Contract.md

请开发 hrms-attendance 的考勤组和打卡业务层：

后端文件：
- backend/hrms-attendance/src/main/java/com/company/hrms/attendance/controller/AttendanceGroupController.java
  └── GET/POST/PUT/DELETE /attendance/groups（考勤组 CRUD）
  └── GET/PUT /attendance/workdays（工作日配置全量覆盖）
  └── GET/POST/PUT/DELETE /attendance/holidays（节假日管理）
- backend/hrms-attendance/src/main/java/com/company/hrms/attendance/controller/PunchController.java
  └── POST /attendance/punch（type=IN/OUT, 返回 punchStatus）
  └── GET  /attendance/punch/today（今日状态：已打卡次数/迟到/早退/旷工）
  └── GET  /attendance/punch/records（打卡记录分页）
  └── POST /attendance/punch-fix（补卡申请，校验配额≤2次/月）
  └── GET  /attendance/punch-fix/quota（剩余补卡次数）

打卡判定逻辑（PunchService）：
- 上班判定：punchTime ≤ onDuty → NORMAL
              ≤ onDuty+lateThreshold → LATE
              > onDuty+lateThreshold → ABSENT_HALF
- 下班判定：punchTime ≥ offDuty → NORMAL
              ≥ offDuty-earlyLeaveThreshold → EARLY_LEAVE
              < offDuty-earlyLeaveThreshold → ABSENT_HALF
- 幂等：Redis SETNX hrms:punch:{empId}:{date}:{type}（24h TTL）
- GPS 校验：配置了 gps_range 的考勤组打卡时校验距离
- 补卡配额：Redis supplement:{empId}:{ym} 原子自增

前端对接：
- frontend/src/pages/admin/attendance/groups/index.tsx → 对接考勤组/工作日/节假日 API
- frontend/src/pages/portal/attendance/index.tsx → 对接打卡/补卡 API
- frontend/src/services/attendance.ts → 补全全部 API 调用
```

**A 做 org（组织架构） + 各人深入模块**

| 人员 | 后端 | 前端 |
| --- | --- | --- |
| **A（李俊毅）** | hrms-org：部门 CRUD + 树 + 合并 + 人数统计<br>职位 CRUD + 序列 M/P/S 校验 + 职级范围级联<br>工号生成（Redis 分布式锁 + 序号复用）<br>✅ 依赖自己的 common + auth | 部门管理页（树 + 详情 + CRUD + 合并弹窗）+ 职位管理页（ProTable + 序列级联）<br>⚠️ 前端依赖自己 Day 2 的前端基座 |
| **B（范文路）** | 敏感字段二次验证 + AES 加解密<br>薪资档案查看/编辑<br>员工编辑白名单校验 + 20003 兜底<br>✅ 依赖自己的 employee Controller（Day 2）| 详情页敏感字段 SensitiveField 组件对接<br>Portal 我的档案页 + 账号安全页<br>⚠️ Portal 页需要 A 的登录功能（Day 2）<br>⚠️ 部门选择器需要 A 的 org 接口（Day 3 晚上）|
| **C（郭策）** | 转正申请 PASS/EXTEND/FAIL + 试用期延长<br>调岗申请三节点审批 + 部门变更 30004 约束<br>离职双通道：员工申请 → HR 正式离职<br>✅ 依赖自己的审批引擎（Day 2）| 转正管理页 + 调岗管理页 + 离职管理页<br>⚠️ 调岗需要 A 的部门树选择器（Day 3 晚上）|
| **D（张浩杰）** | 请假 Service：余额预扣/恢复、calc-days 天数计算、审批链路由<br>加班 Service：倍率计算(1.5/2.0/3.0)、二审阈值<br>月汇总 + 日终 Job（手动触发验证）<br>⚠️ 请假审批依赖 C 的审批引擎（Day 2，先写申请逻辑，审批回调待 Day 4 联调）| Portal 请假页 + Portal 加班页 + 月汇总页<br>✅ 无依赖|

> **验证点：** A 的部门树 CRUD 和职位级联前后端跑通。B 的 Portal 页可编辑本人信息。C 的入职工作流全链路走通。D 的考勤打卡判定正确。

#### 第三天提示词

##### A（李俊毅）- hrms-org 组织架构

```prompt
@后端系分模板.md
@HRMS-Backend-System-Design.md
@HRMS-API-Contract.md

请开发 hrms-org 组织架构模块：

后端文件：
- backend/hrms-org/pom.xml
- backend/hrms-org/src/main/java/com/company/hrms/org/controller/DeptController.java
  └── GET    /departments/tree（部门树 + headcount + manager 姓名）
  └── GET    /departments/{id}（详情）
  └── GET    /departments/{id}/headcount（含下属人数）
  └── GET    /departments/{id}/can-delete（有员工/子部门→false）
  └── POST   /departments（新增，deptCode 唯一校验 + 层级≤5→30001）
  └── PUT    /departments/{id}（编辑 + 移动上级→级联更新 path/level）
  └── DELETE /departments/{id}（逻辑删除，非空拦截）
  └── PUT    /departments/{id}/merge（事务：员工转移+子部门重挂+源隐藏）
- backend/hrms-org/src/main/java/com/company/hrms/org/controller/PositionController.java
  └── GET    /positions（分页 + departmentId/sequence 筛选）
  └── POST   /positions（sequence M/P/S + rank 范围校验，越界拒绝）
  └── PUT    /positions/{id} / GET /positions/{id} / DELETE /positions/{id}
- backend/hrms-org/src/main/java/com/company/hrms/org/service/EmployeeIdGenerator.java
  └── format：年份(4位)+部门编码(2位)+序号(3位)，如 2026JS005
  └── Redis SETNX 分布式锁 + employee_no_history 复用
- backend/hrms-org/src/main/java/com/company/hrms/org/config/CacheConfig.java
  └── dept:tree Redis 缓存 TTL 5min，变更主动删 Key

前端文件：
- frontend/src/pages/admin/org/departments/index.tsx（左侧树 + 右侧详情/员工列表 + CRUD + 合并弹窗）
- frontend/src/pages/admin/org/positions/index.tsx（ProTable + 序列级联 M/P/S→职级 Select）
- frontend/src/services/org.ts
```

##### B（范文路）- 敏感字段 + Portal 页 + 薪资档案

```prompt
@hrms-employee-后端系分.md
@HRMS-API-Contract.md

请开发员工详情敏感字段、Portal 页和薪资档案：

后端文件：
- backend/hrms-employee/src/main/java/com/company/hrms/employee/controller/EmployeeController.java（追加）
  └── GET /employees/{id}/sensitive/{field}（二次验证后返回明文 + 审计日志）
  └── GET/PUT /employees/{id}/salary（HR/FINANCE 可查看/编辑，SYS_ADMIN→403）
  └── GET /employees/{id}/transfer-history（调岗历史时间倒序）
- backend/hrms-employee/src/main/java/com/company/hrms/employee/controller/ProfileController.java（追加）
  └── POST /profile/mobile-change-applications（提交申请）
  └── GET /profile/mobile-change-applications（本人记录）
  └── POST /profile/mobile-change-applications/{id}/cancel（撤销，仅 PENDING）
- backend/hrms-employee/src/main/java/com/company/hrms/employee/service/SensitiveFieldService.java
  └── AES-256-GCM 解密 + 校验二次验证态（不占用工资条 verify Key）
- backend/hrms-employee/src/main/java/com/company/hrms/employee/service/SalaryService.java
  └── 薪资档案读写，记录调薪历史

前端文件：
- frontend/src/pages/admin/employee/detail/index.tsx → 补充 SensitiveField 组件（点击→验证→明文）
- frontend/src/pages/portal/profile/index.tsx（本人档案：基础信息只读 + 邮箱/地址可编辑）
- frontend/src/pages/portal/security/index.tsx（改密/绑定/解绑/登录日志）
- frontend/src/components/SensitiveField.tsx（敏感字段脱敏展示 + 点击验证弹窗）
```

##### C（郭策）- 转正/调岗/离职前后端

```prompt
@人员C-后端系分.md
@HRMS-API-Contract.md

请开发转正、调岗、离职模块：

后端文件：
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/controller/RegularizationController.java
  └── GET /regularization/applications/pending（试用期结束-7天员工）
  └── GET /regularization/applications（转正记录列表）
  └── POST /regularization/applications（发起转正：PASS/EXTEND/FAIL）
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/controller/TransferController.java
  └── POST /transfers（newDepartmentId ≠ 原部门→30004）
  └── GET  /transfers（列表）/ GET /transfers/{id}（详情）
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/controller/ResignationController.java
  └── POST /profile/resignation-requests（员工发起，SELF 范围）
  └── POST /profile/resignation-requests/{id}/cancel（撤销 PENDING）
  └── GET /resignation-requests（HR 管理列表）
  └── POST /resignations（HR 正式离职）
  └── GET /resignations / GET /resignations/stats / GET /resignations/{id}

关键约束：
1. 调岗三节点审批：原部门负责人→新部门负责人→HR 备案
2. 离职双通道：员工 RESIGNATION_REQUEST→审批通过→HR 发起 RESIGNATION→Job 生效
3. 转正 PASS→employee 状态 10 变 20；EXTEND→延长试用期；FAIL→辞退分支

前端文件：
- frontend/src/pages/admin/regularization/index.tsx（待转正列表 + 发起转正 Modal 三分支）
- frontend/src/pages/admin/transfers/index.tsx（列表 + 发起弹窗 + 详情 Drawer 三节点）
- frontend/src/pages/admin/resignation/index.tsx（双 Tab：员工申请 / 正式离职）
- frontend/src/pages/portal/resignation/index.tsx（申请表单 + 本人记录）
```

##### D（张浩杰）- 请假/加班/月汇总前后端

```prompt
@考勤请假-后端系分.md
@HRMS-API-Contract.md

请开发请假、加班和月汇总模块：

后端文件：
- backend/hrms-attendance/src/main/java/com/company/hrms/attendance/controller/LeaveController.java
  └── GET /leaves/balances（年假+调休余额）
  └── GET/POST /leaves/applications（申请/记录）
  └── GET /leaves/calc-days（预览天数，排除周末+节假日，支持 0.5 天）
  └── PUT /leaves/applications/{id}/cancel（撤销，仅 PENDING）
- backend/hrms-attendance/src/main/java/com/company/hrms/attendance/controller/OvertimeController.java
  └── GET/POST /overtime/applications（加班倍率：工作日 1.5/休息日 2.0/法定 3.0）
- backend/hrms-attendance/src/main/java/com/company/hrms/attendance/controller/MonthlySummaryController.java
  └── GET /attendance/monthly-summary（分页 + period 筛选）
  └── PUT /attendance/monthly-summary（锁定/解锁，锁定后→补卡 422:40001）
- backend/hrms-attendance/src/main/java/com/company/hrms/attendance/controller/StatisticsController.java
  └── GET /attendance/statistics/personal（8 项指标）
  └── GET /attendance/statistics/department（3 项率）
- backend/hrms-attendance/src/main/java/com/company/hrms/attendance/service/LeaveService.java
  └── 余额预扣→审批→确认扣减/驳回恢复
  └── 年假计算：工龄<1年按比例 / 1~10年5天 / 10~20年10天 / ≥20年15天
- backend/hrms-attendance/src/main/java/com/company/hrms/attendance/service/SummaryService.java
  └── 日终聚合：daily→monthly（幂等，不重复累加）

Job 文件：
- backend/hrms-attendance/src/main/java/com/company/hrms/attendance/job/AttendanceSummaryJob.java（每日 02:00，手动触发验证）

前端文件：
- frontend/src/pages/portal/leave/index.tsx（假期余额 + 申请表单 + calc-days 预览 + 记录列表）
- frontend/src/pages/portal/overtime/index.tsx（加班申请 + 倍率展示 + 记录列表）
- frontend/src/pages/admin/attendance/summary/index.tsx（月汇总表格 + 锁定/解锁按钮）
```

**A 收尾前端 + 各人完成模块**

| 人员 | 后端 | 前端 |
| --- | --- | --- |
| **A（李俊毅）** | 权限缓存即时失效逻辑<br>工作台 Dashboard 聚合接口<br>✅ 依赖自己的 auth | 用户管理页 + 角色管理页 + 工作台（KPI 卡片 + 趋势图 + 操作日志）<br>✅ 依赖自己的前端基座 |
| **B（范文路）** | 手机号变更：提交→审批→同步 auth 账号<br>入职建档联动：confirm→create employee→auth create user→MQ event<br>⚠️ 手机号变更审批依赖 C 的审批实例<br>⚠️ 同步 username 依赖 A 的 auth 内部 Feign<br>⚠️ 入职建档调 A 的 auth 建号（先约定接口，联调验证）| MobileChangeModal 组件对接 + HR 手机号变更待办页<br>✅ 无依赖（UI 先行）|
| **C（郭策）** | 审批催办（RabbitMQ 48h 延迟）<br>委托审批 ACTIVE/CANCELLED<br>跨模块 Feign 联调：入职建档调 B、建号调 A<br>⚠️ 入职 confirm→调 B 的 Feign 建档（Day 3 约定接口）<br>⚠️ 建号→调 A 的 auth 内部接口（Day 2 约定接口）| 入职管理页 StatCards + 按钮显隐矩阵 + 审批操作 Drawer<br>✅ 无依赖|
| **D（张浩杰）** | 账套 SpEL 公式校验<br>核算批次状态机：draft→calculating(MQ)→pending_confirm→approving→approved→distributed<br>分段计薪逻辑<br>⚠️ 核算需要 B 的薪资档案数据（先 mock，Day 5 联调验证）| 核算批次管理页（进度轮询 + 状态按钮）+ 工资条门户页（二次验证 Modal）<br>⚠️ 工资条二次验证依赖 A 的 auth verify 接口|

> **验证点：** 手机号变更全链路（申请→HR审批→账号同步）。核算批次状态机从 draft 走到 distributed。
> **第一个 E2E：** 入职→建档→开号→登录→打卡，A+B+C+D 首次联调。

#### 第四天提示词

##### A（李俊毅）- 系统管理页 + 工作台 + 权限缓存

```prompt
@前端系分模板.md
@HRMS-Frontend-System-Design.md

请开发系统管理前端页面和收尾工作：

后端追加：
- backend/hrms-auth/src/main/java/com/company/hrms/auth/config/PermissionCacheManager.java
  └── 权限变更时 DEL user:permissions:{userId}，即时生效
- backend/hrms-auth/src/main/java/com/company/hrms/auth/controller/WorkbenchController.java
  └── GET /workbench/summary（总人数/本月入职/待审批/考勤异常 四项 KPI）

前端文件：
- frontend/src/pages/admin/system/users/index.tsx（用户管理 ProTable + 新增 Modal + Switch 状态）
- frontend/src/pages/admin/system/roles/index.tsx（角色列表 5 预置 + 编辑 Modal + 权限分配 Tree Modal）
- frontend/src/pages/admin/system/operation-logs/index.tsx（操作审计日志分页）
- frontend/src/pages/admin/system/login-logs/index.tsx（登录日志分页）
- frontend/src/pages/admin/workbench/index.tsx（4 张 KPI 卡片 + 6 快捷入口 + 趋势图 + 操作日志）

要求：
1. 角色管理不可新增/删除，编码只读
2. 权限 Tree 按 module 分组，支持半选/全选
3. 工作台接口失败→友好降级，不白屏
4. SYS_ADMIN 不显示薪资菜单（access.ts 中薪资菜单权限码控制）
```

##### B（范文路）- 手机号变更全链路 + 建档联动

> ⚠️ **跨模块依赖提醒：** 手机号变更审批需要 C 组创建审批实例，入职建档需要 A 组的 auth 建号接口。如果 C 组的审批引擎或 A 组的 auth 内部接口今天还没调通，先写本地逻辑（状态流转+DB 操作），等联调阶段再接通。不要停下来等。

```prompt
@hrms-employee-后端系分.md
@HRMS-API-Contract.md

请开发手机号变更全链路和入职建档联动：

后端文件：
- backend/hrms-employee/src/main/java/com/company/hrms/employee/controller/EmployeeController.java（追加）
  └── GET /employees/mobile-change-applications（HR 待办列表，筛选 PENDING）
- backend/hrms-employee/src/main/java/com/company/hrms/employee/service/MobileChangeService.java
  └── 提交申请→创建审批实例（MOBILE_CHANGE processType）
  └── 审批通过→update employee.mobile → Feign 调 auth 同步 username
  └── 状态机：PENDING→APPROVED/REJECTED/CANCELLED
- backend/hrms-employee/src/main/java/com/company/hrms/employee/service/OnboardingIntegrationService.java
  └── confirm→事务：创建 employee→创建 auth 账号→生成工号→发 MQ
  └── 回滚策略：auth 建号失败→事务回滚

前端文件：
- frontend/src/pages/portal/profile/index.tsx → 补充 MobileChangeModal（新手机号+验证码+提交）
- frontend/src/pages/admin/employee/mobile-change/index.tsx（HR 待办列表+审批通过/驳回）
- frontend/src/components/MobileChangeModal.tsx（验证码输入+提交）

联调注意：
- 手机号变更审批回调需 C 组提供审批引擎支持
- 入职建档需 C 组确认入职后触发
```

##### C（郭策）- 催办/委托 + 审批前端对接

> ⚠️ **跨模块依赖提醒：** 入职 confirm→建档需要调 B 组的 Feign 接口，建号需要调 A 组的 auth 内部接口。今天先确认两个接口的入参出参（Sprint 0 已冻结），用 Mock 数据测试，等 Day 5 联调再接通真实调用。不要因为依赖没调通而阻塞。

```prompt
@人员C-后端系分.md
@HRMS-API-Contract.md

请开发催办、委托功能和前端页面对接：

后端文件：
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/service/DelegationService.java
  └── POST /approvals/delegations（新增委托，已有 ACTIVE→60003）
  └── DELETE /approvals/delegations/{id}（取消委托→CANCELLED）
  └── GET /approvals/delegations（委托列表）
  └── resolveAssignee：委托期内 actual_assignee_id = delegate_user_id
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/config/RabbitConfig.java
  └── 延迟队列 hrms.approval.notify（48h 催办）
- backend/hrms-workflow/src/main/java/com/company/hrms/workflow/job/OverdueCheckJob.java
  └── 检查 approval_task.sla_deadline，标记 overdue=1

前端文件：
- frontend/src/pages/admin/approval/index.tsx → 补全详情 Drawer（ApprovalTimeline + ApprovalActions）
- frontend/src/pages/admin/delegation/index.tsx（委托列表 + 新增 Modal + 取消）
- frontend/src/pages/admin/onboarding/index.tsx → 补全 StatCards + 按钮显隐矩阵
- frontend/src/services/workflow.ts → 补全委托/催办 API

跨模块 Feign 联调：
- 入职 confirm→调 B 组 employee Feign 建档
- 入职 confirm→调 A 组 auth Feign 建号
- 离职生效→发 MQ 通知 D 组考勤
```

##### D（张浩杰）- 账套 + 核算批次 + 工资条前后端

> ⚠️ **跨模块依赖提醒：** 核算批次需要 B 组的员工薪资档案数据（employee_salary_profile），工资条二次验证需要 A 组的 auth/verify 接口。今天先通过 mock 数据跑通核算逻辑和工资条展示，Day 5 联调再接入真实数据。分段计薪的边界条件可以单元测试先行覆盖。

```prompt
@薪资-后端系分.md
@HRMS-API-Contract.md

请开发薪资核算全模块：

后端文件：
- backend/hrms-payroll/pom.xml
- backend/hrms-payroll/src/main/java/com/company/hrms/payroll/controller/SchemeController.java
  └── GET/POST/PUT/DELETE /payroll/schemes（账套 CRUD + SpEL 公式校验）
- backend/hrms-payroll/src/main/java/com/company/hrms/payroll/controller/BatchController.java
  └── POST/GET /payroll/batches（创建/列表）
  └── GET /payroll/batches/{id}（详情 + progress 轮询）
  └── POST /payroll/batches/{id}/calculate（draft→calculating，MQ 异步）
  └── GET /payroll/batches/{id}/details（核算明细）
  └── GET /payroll/batches/{id}/chart-data（图表数据）
  └── PUT /payroll/batches/{id}/details/{detailId}（手工调整）
  └── POST /payroll/batches/{id}/submit（提交审批）
  └── POST /payroll/batches/{id}/distribute（发放确认，幂等）
- backend/hrms-payroll/src/main/java/com/company/hrms/payroll/controller/PayslipController.java
  └── GET /payroll/payslips（HR/财务端列表）
  └── GET /payroll/payslips/{month}（详情）
  └── GET /profile/payslips（门户端列表）
  └── GET /profile/payslips/trend（近 6 月实发趋势）
  └── GET /profile/payslips/{period}（详情，须二次验证→60004）
  └── POST /profile/payslips/verify（二次验证，Redis TTL 30min）
  └── GET /profile/payslips/{period}/pdf（PDF 下载）
- backend/hrms-payroll/src/main/java/com/company/hrms/payroll/controller/CostReportController.java
  └── GET /payroll/cost-report（趋势 + 部门分布）
- backend/hrms-payroll/src/main/java/com/company/hrms/payroll/service/CalculateService.java
  └── MQ 异步核算分片（50人/片）
  └── 分段计薪：边界点{月初,入职日,离职日+1,转正日,调薪生效日,月末+1}
  └── 异常检测：黄牌(请假>15天/加班>50h) 红牌(变动>30%/无档案→阻断)
  └── 老板审批 AD-07：实发≥30万/调薪占比>15%/单人调薪>40%
  └── 个税累计预扣法（5000元/月减除）
- backend/hrms-payroll/src/main/java/com/company/hrms/payroll/service/SchemeService.java
  └── SpEL 公式语法校验

前端文件：
- frontend/src/pages/admin/payroll/schemes/index.tsx（账套列表 + 创建/编辑 Modal + SpEL 公式输入）
- frontend/src/pages/admin/payroll/batches/index.tsx（批次列表 + 状态 Tag + 进度条）
- frontend/src/pages/admin/payroll/batches/detail.tsx（核算明细表格 + 手工调整 + 图表）
- frontend/src/pages/admin/payroll/payslips/index.tsx（工资条列表 + 详情弹窗）
- frontend/src/pages/admin/payroll/cost-report/index.tsx（LineChart 趋势 + 饼图分布）
- frontend/src/pages/portal/payslips/index.tsx（趋势图 + 列表 + 二次验证 Modal + PDF 下载）
- frontend/src/services/payroll.ts（全部 API 调用）

要求：
1. 核算批次状态机严谨：非法操作→50002 拦截
2. 工资条二次验证 Key 与 B 组敏感字段 verify Key 隔离
3. 发放确认幂等：已 distributed 状态不再处理
```

**全链路联调**

| 人员 | 上午（各自修复）| 下午（交叉联调）|
| --- | --- | --- |
| **A（李俊毅）** | 修复联调中发现的 auth/org bug | E2E：入职→登录→权限验证<br>E2E：离职→账号禁用→401<br>⚠️ E2E 依赖 B/C/D 的模块全部就绪 |
| **B（范文路）** | 修复联调中发现的 employee bug | E2E：手机号变更→同步 auth<br>E2E：敏感字段二次验证 + 审计日志<br>⚠️ 手机号变更 E2E 依赖 A 的 auth + C 的审批 |
| **C（郭策）** | 修复联调中发现的 workflow bug | E2E：转正/调岗/离职→员工状态变更<br>E2E：审批委托→代审生效<br>⚠️ 离职 E2E 依赖 D 的考勤联动 |
| **D（张浩杰）** | 修复联调中发现的 attendance/payroll bug | E2E：考勤月汇总→薪资核算联动<br>E2E：工资条二次验证 + PDF 下载<br>⚠️ 核算联动依赖 B 的薪资档案数据|

> **联调清单：**
> - [ ] 入职 → 审批 → 建档 → 开号 → 登录 → 考勤入组（A+B+C+D）
> - [ ] 手机号变更 → 审批 → 同步账号（A+B）
> - [ ] 部门合并 → 员工归属 → 调岗（A+B+C）
> - [ ] 离职 → 账号禁用 → 移出考勤组 → 薪资结算（A+B+C+D）
> - [ ] 考勤 → 月汇总 → 算薪 → 工资条（D）
> - [ ] SYS_ADMIN 薪资菜单不可见 + API 403（A+B+D）
> - [ ] DEPT_MANAGER 仅看本部门员工数据（A+B）

#### 第五天提示词

##### 全员 - 交叉联调 Bug 修复

```prompt
请进行跨模块联调，按以下顺序逐条验证并修复发现的问题：

E2E-01 入职→登录→打卡（A+B+C+D）：
1. C 组创建入职申请→提交审批→审批通过
2. C 组确认入职→调 B 组 Feign 建档→调 A 组 Feign 建号
3. 新员工用随机密码登录 A 组 auth→首次改密
4. 员工进入门户→D 组考勤打卡→打卡成功
5. 验证：员工档案存在、工号格式正确、考勤组已关联

E2E-02 手机号变更→同步账号（A+B）：
1. B 组 portal 提交手机号变更→创建审批实例
2. HR 审批通过→update employee.mobile
3. A 组 auth 同步 username
4. 新手机号登录→成功，旧手机号登录→失败

E2E-03 离职→账号禁用→薪资结算（A+B+C+D）：
1. 员工提交离职申请（C）→HR 审批
2. HR 发起正式离职→离职生效 Job 执行
3. A 组账号禁用→旧 Token 401
4. D 组考勤月汇总锁定→薪资核算至离职日

修复优先级：
- 🔴 阻塞链路的问题（跨模块调用失败、状态机卡住）立即修复
- 🟡 数据不一致问题（字段映射错误、计算偏差）上午修完
- 🟢 前端展示问题（样式、文案）记录后延
```

##### A（李俊毅）- auth/org 联调修复

```prompt
@HRMS-API-Contract.md

请修复联调中发现的 auth 和 org 模块问题：

联调检查项：
1. POST /internal/users 建号 → 确认返回 userId，employee_id 关联正确
2. PUT /internal/users/{id}/status=0 → 该用户登录返回 401
3. PUT /internal/users/{id}/username → 新 username 可登录
4. GET /departments/tree → 返回完整部门树 + headcount 准确
5. 部门合并后 → 原部门 deleted=1，员工归入目标部门
6. 工作台 KPI 数据与各模块实际数据对比一致

修复范围：
- JWT Filter 未捕获的异常
- DataScope 未正确注入 SQL
- 部门树缓存未及时失效
- 工号生成锁 Key 未释放（Redis SETNX 死锁）
```

##### B（范文路）- employee 联调修复

```prompt
@hrms-employee-后端系分.md
@HRMS-API-Contract.md

请修复联调中发现的 employee 模块问题：

联调检查项：
1. 花名册 DataScope：DEPT_MANAGER 仅看本部门，EMPLOYEE→403
2. 白名单编辑：PUT departmentId→20003，PUT mobile→20003
3. 敏感字段二次验证：DEPT_MANAGER 查看下属身份证→脱敏
4. 手机号变更全链路：申请→审批→同步 auth→新号登录
5. 入职建档：confirm 后 employee 表数据完整，user_id 已回写
6. 审计日志：敏感字段查看记录 operation_log 有数据

修复范围：
- 敏感字段 AOP 拦截遗漏
- 手机号变更审批回调超时
- 白名单字段前后端不一致
```

##### C（郭策）- workflow 联调修复

```prompt
@人员C-后端系分.md
@HRMS-API-Contract.md

请修复联调中发现的 workflow 模块问题：

联调检查项：
1. 入职全链路：draft→submit→approve→confirm→onboarded
2. 入职 confirm→Feign 调 B 组建档成功→Feign 调 A 组建号成功
3. 转正 PASS→employee 状态从 PROBATION 变 REGULAR
4. 调岗 newDepartmentId=原部门→30004 兜底
5. 离职 Job→状态 RESIGNED→账号禁用→移出考勤组
6. 审批委托→ACTIVE 期内的任务 actual_assignee_id 正确
7. 催办 MQ→48h 延迟消息投递

修复范围：
- 状态机非法转换未拦截
- Feign 调用超时未回滚
- MQ 消息体字段遗漏
- 委托解析未覆盖边界日期
```

##### D（张浩杰）- attendance/payroll 联调修复

```prompt
@考勤请假-后端系分.md
@薪资-后端系分.md
@HRMS-API-Contract.md

请修复联调中发现的考勤和薪资模块问题：

联调检查项：
1. 打卡判定：09:00→NORMAL, 09:10→LATE, 09:30→ABSENT_HALF（边界值）
2. 补卡配额：当月补卡 2 次后第 3 次→422:40002
3. 月锁定后补卡→422:40001
4. 请假余额预扣→审批通过确认→驳回恢复
5. 核算批次状态机：draft→calculating→pending_confirm→approving→approved→distributed
6. 分段计薪：月中入职/转正/调薪组合场景
7. 工资条二次验证：未验证→60004，验证通过→30min 免验证
8. 成本报表：趋势图数据与核算明细一致

修复范围：
- Redis 幂等键 TTL 设置
- MQ 消费失败重试
- 分段计薪边界条件遗漏
- SpEL 公式校验不完整
```

**安全 + 回归 + 验收**

| 时间 | A（李俊毅）| B（范文路）| C（郭策）| D（张浩杰）|
| --- | --- | --- | --- | --- |
| **上午** | SEC-01~20 安全用例逐一验证 | 花名册/编辑/Portal P0 回归 | 入职/转调离 P0 回归 | 考勤/薪资 P0 回归 |
| **下午** | 修复所有 P0 Bug + 验收签字 | 修复所有 P0 Bug + 验收签字 | 修复所有 P0 Bug + 验收签字 | 修复所有 P0 Bug + 验收签字 |
| **依赖** | ✅ 独立回归自己的模块 | ✅ 独立回归自己的模块 | ✅ 独立回归自己的模块 | ✅ 独立回归自己的模块 |

#### 第六天提示词

##### 全员 - 安全测试与回归

```prompt
请按以下顺序完成安全测试和回归验证：

上午：安全测试
逐条验证 SEC-01~09，发现的安全缺陷立即修复：

SEC-01：EMPLOYEE 调用 GET /system/users → 期望 403
SEC-02：SYS_ADMIN 调用 GET /payroll/batches → 期望 403（双拦截）
SEC-03：离职生效后，旧 Token 请求任意 API → 期望 401
SEC-04：篡改 JWT 中的 roles 字段 → 签名失败 → 401
SEC-05：登出后使用旧 Token → 黑名单 → 401
SEC-06：未二次验证直接 GET /profile/payslips/{period} → 60004
SEC-07：搜索接口传 username=' OR 1=1-- → 参数绑定，无注入
SEC-08：EMPLOYEE 调用 GET /departments/tree → 期望 403
SEC-09：EMPLOYEE 调用 GET /employees?departmentId=其他部门 → DataScope 仅本人

下午：P0 回归 + Bug 修复
1. 各人回归自己模块的 P0 核心用例
2. 修复当日上午发现的 Bug
3. 修复联调阶段遗留的 🟡 问题

验收签出条件：
- [ ] SEC-01~09 全部通过
- [ ] 无 P0/P1 未关闭缺陷
- [ ] 跨模块 E2E 5 条全部通过
- [ ] 花名册→详情→编辑→Portal 全链路正常
- [ ] 入职→审批→建档→登录→打卡 全链路正常
- [ ] 考勤月汇总→核算批次→工资条 全链路正常
```

##### A（李俊毅）- 安全测试 + 权限回归

```prompt
请完成 A 组安全测试和功能回归：

SEC 必测：
1. EMPLOYEE→GET /system/users → 403（@PreAuthorize 拦截）
2. SYS_ADMIN→GET /payroll/batches → 403（后端双拦截）
3. SYS_ADMIN→前端薪资菜单 → 不可见（access 权限码过滤）
4. 离职后旧 Token 请求 → 401（user:last-active 已过期 + status=0）
5. 篡改 JWT payload（修改 roles）→ 签名失败 401
6. 登出后旧 Token → 黑名单 401
7. SQL 注入 keyword="' OR 1=1--" → 参数绑定安全

P0 回归：
1. 登录→Token→Profile→按角色跳转
2. Token 过期→自动 refresh→请求重放（临时设 TTL=10s 验证）
3. 部门树 CRUD + 合并
4. 职位序列级联 M/P/S
5. 工作台 KPI 渲染
6. 用户管理+角色管理+权限分配 Tree
7. userId:permissions 缓存变更即时生效
```

##### B（范文路）- 安全测试 + 员工回归

```prompt
请完成 B 组安全测试和功能回归：

SEC 必测：
1. EMPLOYEE→查看他人档案 → 20002（DataScope SELF）
2. DEPT_MANAGER→查看下属身份证 → idNumber=null（FieldPermission）
3. 前端绕过：抓包 PUT 携带 departmentId → 20003
4. 敏感字段明文不在浏览器持久化

P0 回归：
1. 花名册分页 + 组合筛选 + StatusTag 颜色
2. 白名单编辑/非白名单校验
3. 敏感字段二次验证 + 审计日志
4. 薪资档案 HR/FINANCE 可见，SYS_ADMIN 不可见
5. Portal 我的档案编辑邮箱/地址
6. 手机号变更全链路：提交→审批→同步→新号登录
7. 入职建档数据完整性
```

##### C（郭策）- 安全测试 + 流程回归

```prompt
请完成 C 组安全测试和功能回归：

SEC 必测：
1. EMPLOYEE→POST /transfers → 403
2. 审批人操作非本人待办 → 20002
3. 非 assignee 拒绝操作
4. 参数校验：REJECT 无 comment → 10001

P0 回归：
1. 入职全链路：draft→submit→审批→confirm→onboarded
2. 转正 PASS/EXTEND/FAIL 三分支
3. 调岗部门变更约束 30004 + 三节点审批
4. 离职双通道全流程
5. 审批工作台待办/已办/我发起 Tab 切换
6. 委托 ACTIVE 生效/取消
7. 催办 MQ 消息投递
```

##### D（张浩杰）- 安全测试 + 考勤薪资回归

```prompt
请完成 D 组安全测试和功能回归：

SEC 必测：
1. SYS_ADMIN→GET /payroll/payslips → 403
2. 未二次验证→GET /profile/payslips/{period} → 60004
3. 二次验证过期后→重新验证
4. HR_STAFF 可看全公司工资条，EMPLOYEE 仅本人

P0 回归：
1. 考勤组 CRUD + 工作日/节假日配置
2. 打卡判定 6 场景（NORMAL/LATE/ABSENT_HALF/EARLY_LEAVE）
3. 补卡配额 ≤2次/月 + 月锁定后禁止补卡
4. 请假余额预扣/审批确认/驳回恢复
5. 加班倍率 1.5/2.0/3.0 + 二审阈值
6. 账套 SpEL 公式校验
7. 核算批次状态机完整流转
8. 工资条二次验证 + PDF 下载
9. 成本报表趋势/部门分布数据正确
```

---

## 快速判断顺序的原则

```
1️⃣ 被依赖的模块先做（common → auth → org → employee → workflow → attendance → payroll）
2️⃣ 有跨模块调用的一开始就约定接口（Sprint 0 契约评审）
3️⃣ 前后端同步开发：后端先出 API 文档（OpenAPI），前端用 Mock 并行
4️⃣ 先做主干再做分支：登录 → 列表 → 详情 → 编辑
5️⃣ 先做 P0 再做 P1/P2：核心链路先跑通，辅助功能后补
```

## 各模块对外承诺的接口（Sprint 0 必须冻结）

| 提供方 | 接口 | 调用方 | 冻结时间 |
| --- | --- | --- | --- |
| hrms-auth | `POST /internal/users` | hrms-employee、hrms-workflow | Day 1 |
| hrms-auth | `PUT /internal/users/{id}/status` | hrms-employee | Day 1 |
| hrms-auth | `PUT /internal/users/{id}/username` | hrms-employee | Day 1 |
| hrms-org | `GET /departments/tree` | 所有前端页面 | Day 3 |
| hrms-employee | Feign 建档接口 | hrms-workflow | Day 3 |
| hrms-workflow | 审批回调 MQ | hrms-attendance | Day 4 |
| hrms-attendance | 月汇总数据 | hrms-payroll | Day 4 |

## 已知注意事项

- 遇到需要跨模块决定的问题（如接口字段名、状态码、联调时序），停下手上的工作，先问组长/产品做决定，不要自行猜测
- 所有人在 Day 3 晚上前必须确保自己的模块在 Swagger 中可调通
- Day 4 晚上开始全员集中联调，不要等 Day 5 才开始碰头
