# 人资管理系统 · 前后端 API 契约文档

> **文档版本**：v1.7.0  
> **生效日期**：2026-07-15  
> **PRD 来源**：[人资管理系统-PRD.md](../人资管理系统-PRD.md) v1.0（2026-07-07）  
> **配套系分**：[HRMS-Backend-System-Design(2).md](HRMS-Backend-System-Design(2).md) v1.7.1 · [HRMS-Frontend-System-Design(1).md](HRMS-Frontend-System-Design(1).md) v1.8.1 · [李俊毅-后端系分.md](李俊毅-后端系分.md) v2.0.0 · [李俊毅-前端系分.md](李俊毅-前端系分.md) v2.0.0 · [hrms-employee-后端系分.md](hrms-employee-后端系分.md) v1.3.0 · [前端系分_格式化.md](前端系分_格式化.md) v1.0.0 · [薪资-后端系分.md](薪资-后端系分.md) v1.0.0 · [薪资-前端系分.md](薪资-前端系分.md) v1.0.0 · [HRMS-Workflow-Backend-Design.md](HRMS-Workflow-Backend-Design.md) · [HRMS-Workflow-Frontend-Design.md](HRMS-Workflow-Frontend-Design.md)  
> **机器可读**：Sprint 0 输出 `hrms-server/openapi.yaml`（与本契约保持 semver 同步）

---

## 1. 文档定位

### 1.1 用途

本文档是 HRMS V1.0 **前后端接口的唯一契约锚点**：

- **业务规则**以 PRD 为准；
- **接口路径、枚举、错误码、权限范围**以本文档为准；
- **实现细节**（表结构、状态机、组件）见后端/前端系分；
- **联调与 Mock**以本文档 + OpenAPI 为准，路径零偏差。

### 1.2 文档层级

```
人资管理系统-PRD.md          ← 业务需求、流程、原型
        ↓
HRMS-API-Contract.md         ← 本文档（契约锚点）
        ↓
├── HRMS-Backend-System-Design(2).md   ← 后端详设
├── HRMS-Frontend-System-Design(1).md  ← 前端详设
└── hrms-server/openapi.yaml           ← 机器可读契约
```

### 1.3 系统边界（V1.0）

| 包含 | 不包含 |
| --- | --- |
| 组织、员工档案、入转调离 | OA 对接 |
| 考勤、请假、加班 | 银行代发/回盘 |
| 薪资核算、工资条 | 移动端 App |
| 审批中心、个人中心 | Flowable BPM |
| Excel 数据迁移 | — |

---

## 2. 全局约定

### 2.1 基础信息

| 项 | 约定 |
| --- | --- |
| Base URL | `/api/v1` |
| 开发环境 | 后端 `http://localhost:8080/api/v1` · 前端 `http://localhost:8000` |
| 鉴权 | `Authorization: Bearer <access_token>` |
| Token 有效期 | `access_token` **2 小时**，`refresh_token` **7 天** |
| 无操作登出 | 30 分钟无用户操作自动登出（**前端三层联动**：idleDetector 监听用户事件 + tokenRefresher 预判刷新前检查空闲状态 + 401 拦截器排队重放兜底；**后端兜底**：JwtAuthFilter 校验 `user:last-active:{userId}` Redis Key TTL，归零则返回 401） |
| 密码规则 | 8 位以上，需包含大小写字母+数字，**90 天**强制更换（首次登录强制改密） |
| 链路追踪 | 请求头 `X-Trace-Id: <uuid>`（可选，响应回传 `traceId`） |
| Content-Type | `application/json`（文件上传 `multipart/form-data`） |

### 2.2 统一响应

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "abc-123",
  "timestamp": 1700000000000
}
```

| 规则 | 说明 |
| --- | --- |
| `code === 0` | 业务成功；`data` 为载荷 |
| `code !== 0` | 业务失败；前端展示 `message` |
| HTTP 401/403 | 未登录/无权限，对应 `20001`/`20002` |

### 2.3 分页

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `page` | number | 页码，从 1 开始 |
| `pageSize` | number | 每页条数，最大 **100** |

```json
{
  "list": [],
  "total": 0,
  "page": 1,
  "pageSize": 20
}
```

### 2.4 枚举约定

| 层级 | 格式 | 示例 |
| --- | --- | --- |
| REST API JSON | **小写 snake_case** | `leave_type: "annual"` |
| 审批 processType | **大写** | `"ONBOARDING"` |
| DB 内部 | **大写** | `ANNUAL` |

### 2.5 主键约定（AD-05）

| 字段 | 用途 |
| --- | --- |
| `employee_id` | 业务主键，全局唯一，**永不复用**；所有 FK 引用此字段 |
| `emp_no` | 展示工号 `YYYY+部门码+序号`；**当年度同部门可复用** |

---

## 3. PRD 溯源矩阵

| PRD 章节 | 业务要点 | 契约章节 |
| --- | --- | --- |
| §2 权限体系 | 5 角色、数据范围、字段权限 | §4、§5 |
| §3 组织架构 | 部门≤5 层、职位序列、部门合并 | §6.2 |
| §4 员工档案 | 工号规则、敏感字段、手机号变更、高级搜索 | §6.3 |
| §5 入转调离 | 入职状态机、转正/调岗(部门必变更)/离职, 审批催办 | §6.4、§8 |
| §6 考勤管理 | 打卡、补卡 2 次/月、请假规则、8 项个人统计指标 | §6.5 |
| §7 薪资管理 | 账套、批次状态、工资条二次验证、核算明细预警 | §6.6 |
| §8 审批中心 | 10 类审批、委托、48h 催办（婚产丧特殊规则） | §6.7、§8 |
| §9 个人中心 | 门户 `/profile/*`、账号安全 | §6.8、§7 |
| §10 技术栈 | Spring Boot / React / Umi | 系分文档 |
| §11 非功能 | 性能、安全(密码规则/token 30min/90天改密) | §2.1、§9 |

---

## 4. 角色与数据权限

### 4.1 角色编码

| PRD 角色 | API `roleCode` | 登录后默认跳转 |
| --- | --- | --- |
| 系统管理员 | `SYS_ADMIN` | `/admin/workbench` |
| HR 专员 | `HR_STAFF` | `/admin/workbench` |
| 部门主管 | `DEPT_MANAGER` | `/admin/workbench` |
| 财务专员 | `FINANCE` | `/admin/workbench` |
| 普通员工 | `EMPLOYEE` | `/portal/profile` |

### 4.2 数据范围（`GET /auth/profile` → `dataScope`）

| 值 | 含义 | 典型角色 |
| --- | --- | --- |
| `ALL` | 全量员工 | HR_STAFF |
| `DEPT_TREE` | 本部门及下属 | DEPT_MANAGER |
| `SELF` | 仅本人 | EMPLOYEE |
| `PAYROLL` | 薪资相关全量 | HR_STAFF、FINANCE |
| `NONE_PAYROLL` | 禁止访问薪资接口 | SYS_ADMIN |

> **契约规则**：`SYS_ADMIN` 对薪资相关接口双拦截（菜单不可见 + API 返回 `20002`）。

### 4.3 字段权限（PRD §2.3）

敏感字段（身份证、银行卡、薪资等）由后端 `FieldPermissionFilter` 裁剪 VO；前端 `FieldGuard` 仅做 UI 门控。**以接口返回的 `fieldPermissions` 为准**。

查看敏感字段：`GET /employees/{id}/sensitive/{field}`（须二次验证，见系分）。

---

## 5. 架构决策（影响契约行为）

| ID | 决策 | 契约影响 |
| --- | --- | --- |
| AD-01 | 自然月 + 考勤锁定 | 算薪前须 LOCKED；否则 `50004` |
| AD-02 | 加班独立模块 | `/overtime/applications` 独立路径 |
| AD-03 | SpEL 表驱动审批 | `processType` 枚举；禁用 BPM |
| AD-04 | （已删除：导入接口无系分归属） | — |
| AD-05 | employee_id / emp_no 分离 | 请求/响应用 `employeeId`，展示用 `empNo` |
| AD-06 | 加班二审 | 单日累计 ≥4h → `OVERTIME` 增加 HR 节点 |
| AD-07 | 老板审批双条件 | `PAYROLL_BATCH` 满足条件时增加 boss 节点 |
| AD-08 | 分段计薪 | 算薪明细含 `segmentCount`、`calcSnapshotJson` |

---

## 6. API 总表

> 所有路径相对 Base URL `/api/v1`。`{id}`、`{type}`、`{field}`、`{period}`、`{month}` 为路径参数。

### 6.1 认证

| 方法 | 路径 | 说明 | PRD |
| --- | --- | --- | --- |
| POST | `/auth/login` | 登录 `{ username, password }` | §2 |
| POST | `/auth/logout` | 登出（Token 黑名单） | — |
| POST | `/auth/refresh` | 刷新 Token | — |
| GET | `/auth/profile` | 用户+角色+权限+dataScope | §2 |
| PUT | `/auth/password` | 改密（登录页/首次改密；门户见 §6.8） | §9.5 |
| PUT/DELETE | `/auth/mobile` | 绑定/解绑手机（别名，见 §7.2） | §9.5 |
| POST | `/auth/verify` | 工资条二次验证（别名，见 §7.1） | §7.4 |

**`GET /auth/profile` 响应核心字段：**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| userId | number | 用户 ID |
| employeeId | number | 员工 ID |
| roles | string[] | 角色 code |
| permissions | string[] | 权限码 |
| dataScope | string | ALL / DEPT_TREE / SELF / PAYROLL / NONE_PAYROLL |
| mustChangePassword | boolean | 首次登录强制改密 |
| passwordExpiredAt | string | 密码过期时间（格式 `YYYY-MM-DD`），用于前端提示改密 |

**`POST /auth/login` 请求与响应示例：**

```json
// Request
{ "username": "202401005", "password": "Abc12345" }
// Response
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "expiresIn": 7200,
  "mustChangePassword": false
}
```

**`GET /departments/tree` 响应示例：**

```json
[
  {
    "id": 1,
    "name": "总公司",
    "code": "HQ",
    "headcount": 120,
    "headcountIncludingSub": 320,
    "manager": "王总",
    "sortOrder": 1,
    "children": [
      {
        "id": 2,
        "name": "技术部",
        "code": "JS",
        "headcount": 45,
        "headcountIncludingSub": 60,
        "manager": "李经理",
        "sortOrder": 1,
        "children": []
      }
    ]
  }
]
```

### 6.2 组织架构

| 方法 | 路径 | 说明 | 权限 | PRD |
| --- | --- | --- | --- | --- |
| GET | `/departments/tree` | 部门树（含人数、负责人） | SYS_ADMIN, HR_STAFF | §3.1 |
| GET | `/departments/{id}/headcount` | 部门人数（含下属） | SYS_ADMIN, HR_STAFF | §3.1.4 |
| GET | `/departments/{id}/can-delete` | 删除前校验（检查是否有员工/子部门未转移） | SYS_ADMIN, HR_STAFF | §3.1.3 |
| POST | `/departments` | 新增部门，字段见下方定义 | SYS_ADMIN, HR_STAFF | §3.1 |
| PUT | `/departments/{id}` | 编辑部门（可移动上级） | SYS_ADMIN, HR_STAFF | §3.1 |
| DELETE | `/departments/{id}` | 删除部门（须先清空+合并） | SYS_ADMIN, HR_STAFF | §3.1 |
| PUT | `/departments/{id}/merge` | 部门合并 `{ targetDepartmentId }` | SYS_ADMIN, HR_STAFF | §3.1.3 |
| GET/POST/PUT/DELETE | `/positions` `/positions/{id}` | 职位 CRUD，字段见下方定义 | SYS_ADMIN, HR_STAFF | §3.2 |

**`POST /departments` 请求体：**

| 字段 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `name` | Y | string | 部门名称，≤64 |
| `deptCode` | Y | string | 2 位字母数字，唯一 |
| `parentId` | N | number | 上级部门 ID，空=根 |
| `headEmployeeId` | N | number | 部门负责人 employee_id |
| `sortOrder` | Y | number | 排序，默认 0 |
| `description` | N | string | ≤256 |

业务逻辑：
1. 校验 `deptCode` 唯一
2. 计算新 `level`（parent.level + 1），超 5 层→错误码 **30001**
3. 生成 `path`（parent.path + id + "/"）
4. 删除 `dept:tree` 缓存

**`PUT /departments/{id}/merge` 请求体：**

| 字段 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `targetDepartmentId` | Y | number | 目标部门 ID |

业务逻辑：
1. 校验目标存在且非自身
2. 批量转移员工、重新挂载子部门
3. 逻辑删除源部门、刷新缓存

**`POST /positions` 请求体：**

| 字段 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `name` | Y | string | 职位名称 |
| `sequenceCode` | Y | string | M/P/S |
| `departmentId` | N | number | 空=全公司通用 |
| `gradeMin` | Y | string | 职级范围最小值，如 P1 |
| `gradeMax` | Y | string | 职级范围最大值，如 P10 |
| `defaultProbationMonths` | Y | number | 默认试用期，默认 3 |
| `isStandard` | Y | boolean | 是否标准职位 |
| `description` | N | string | 描述 |

职级范围校验：`gradeMin`/`gradeMax` 须在序列合法范围内（M1-M5 / P1-P10 / S1-S5），且 `gradeMin` 索引 ≤ `gradeMax` 索引。

### 6.3 员工档案

| 方法 | 路径 | 说明 | PRD |
| --- | --- | --- | --- |
| GET | `/employees` | 花名册分页+高级搜索，详见下方参数定义 | §4.2 |
| GET/PUT | `/employees/{id}` | 档案详情/编辑 | §4.1 |
| GET/PUT | `/employees/{id}/salary` | 薪资档案 | §7.2 |
| GET | `/employees/{id}/sensitive/{field}` | 敏感字段（二次验证） | §2.3 |
| GET | `/employees/mobile-change-applications` | HR 手机号变更待办 | §4.1.2 |
| GET | `/employees/{id}/transfer-history` | 员工调岗历史 | §5.3 |

**`GET /employees` 搜索参数（Query Parameters）：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 否 | 关键词模糊搜索：姓名/工号/手机号 |
| `departmentIds` | string | 否 | 部门 ID 列表，逗号分隔（部门树多选） |
| `positionIds` | string | 否 | 职位 ID 列表，逗号分隔 |
| `employmentStatus` | string | 否 | 在职状态筛选，逗号分隔（如 `probation,regular`） |
| `gradeLevels` | string | 否 | 职级筛选，逗号分隔（如 `P5,P6,P7`） |
| `hireDateFrom` | string | 否 | 入职日期范围-起始（格式 `YYYY-MM-DD`） |
| `hireDateTo` | string | 否 | 入职日期范围-结束（格式 `YYYY-MM-DD`） |
| `page` | number | 否 | 页码，从 1 开始，默认 1 |
| `pageSize` | number | 否 | 每页条数，最大 100，默认 20 |

**`GET /employees` 响应结构：**

```json
{
  "list": [
    {
      "employeeId": 1,
      "empNo": "202401005",
      "name": "张三",
      "department": "技术部",
      "position": "Java开发工程师",
      "grade": "P5",
      "employmentStatus": "regular",
      "hireDate": "2024-01-15"
    }
  ],
  "total": 100,
  "page": 1,
  "pageSize": 20
}
```

> 返回字段根据当前用户角色权限进行脱敏处理。数据权限由 `DataScopeInterceptor` 在 SQL 层注入。

**`GET /employees/{id}` 响应结构：**

```json
{
  "employeeId": 1001,
  "empNo": "202401005",
  "name": "张三",
  "gender": "MALE",
  "mobile": "138****1234",
  "email": "zhangsan@example.com",
  "departmentId": 10,
  "department": "技术部",
  "positionId": 20,
  "position": "Java开发工程师",
  "grade": "P5",
  "managerId": 1000,
  "manager": "李四",
  "workLocation": "杭州",
  "employmentType": "fulltime",
  "employmentStatus": "regular",
  "hireDate": "2024-01-15",
  "probationEndDate": "2024-07-15",
  "personalInfo": {
    "idCard": "3301**********1234",
    "birthday": "1995-06-15",
    "householdAddress": "浙江省杭州市...",
    "residenceAddress": "浙江省杭州市...",
    "emergencyContact": "王五",
    "emergencyPhone": "139****5678"
  },
  "salaryInfo": null,
  "fieldPermissions": ["employee:view:idNumber"]
}
```

> **说明**：`salaryInfo` 仅 HR_STAFF / FINANCE 可见；`idCard` 仅 HR_STAFF 查看完整值；手机号对部门主管和本人可见，对其他角色脱敏。`fieldPermissions` 为当前用户的字段权限码列表。

**`PUT /employees/{id}` — 编辑档案：**

请求体（`fields` 包裹，仅填写需修改的字段）：

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `name` | N | string | ≤32 |
| `gender` | N | string | MALE / FEMALE |
| `email` | N | string | 邮箱格式 |
| `birthday` | N | string | YYYY-MM-DD |
| `residenceAddress` | N | string | ≤256 |
| `emergencyContact` | N | string | ≤64 |
| `emergencyPhone` | N | string | ≤16 |

请求示例：
```json
{ "fields": { "email": "newemail@example.com", "residenceAddress": "新地址" } }
```

响应体：
```json
{ "updatedFields": ["email", "residenceAddress"] }
```

> **规则**：仅白名单字段允许直接更新；编辑非白名单字段（departmentId / positionId / grade / mobile / idNumber 等）返回 `20003`（字段权限不足），前端引导走对应审批流程。

**`GET/PUT /employees/{id}/salary` — 薪资档案：**

`PUT` 请求体：

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `schemeId` | Y | number | 适用账套 ID |
| `baseSalary` | Y | number | 基本工资 |
| `allowanceBaseJson` | N | string | 津贴基数 JSON |
| `ssBase` | Y | number | 社保基数 |
| `hfBase` | Y | number | 公积金基数 |
| `performanceBase` | N | number | 绩效基数 |
| `probationRatio` | Y | number | 试用期比例 0.80–1.00 |

**`GET /employees/{id}/sensitive/{field}` — 敏感字段查看：**

`{field}` 取值：`idNumber` / `bankAccount`。须先 `POST /auth/verify` 二次验证（Redis `hrms:payslip:verified:{userId}` TTL 30min），查看完整值记 `operation_log` 审计日志。

### 6.4 入转调离

| 方法 | 路径 | 说明 | 权限 | PRD |
| --- | --- | --- | --- | --- |
| GET | `/onboarding/applications` | 入职申请列表 | HR_STAFF | §5.1 |
| GET | `/onboarding/applications/stats` | 统计卡片（draft/pending/approved_pending/onboarded） | HR_STAFF | §5.1 |
| POST/PUT/DELETE | `/onboarding/applications` `/onboarding/applications/{id}` | 草稿 CRUD，请求体见下方定义 | HR_STAFF | §5.1 |
| POST | `/onboarding/applications/{id}/submit` | 提交审批 | HR_STAFF | §5.1 |
| POST | `/onboarding/applications/{id}/withdraw` | HR 撤回（仅第一级） | HR_STAFF | §5.1 |
| POST | `/onboarding/applications/{id}/confirm` | 确认入职 | HR_STAFF | §5.1 |
| POST | `/onboarding/applications/{id}/abandon` | 标记放弃 | HR_STAFF | §5.1 |
| GET | `/regularization/applications/pending` | 待转正列表（试用结束前 7 天） | HR_STAFF | §5.2 |
| GET/POST | `/regularization/applications` | 转正列表/发起，请求体见下方定义 | HR_STAFF | §5.2 |
| POST/GET | `/transfers` | 调岗申请，请求体见下方定义。**约束：部门必须变更**，否则 `30004`。职位/职级/汇报人/薪资为可选变更项，薪资调整需额外审批。 | HR_STAFF | §5.3 |
| GET | `/transfers/{id}` | 调岗详情 | HR_STAFF | §5.3 |
| POST/GET | `/resignations` | HR 发起正式离职，请求体见下方定义 | HR_STAFF | §5.4 |
| GET | `/resignations/stats` | 离职统计 | HR_STAFF | §5.4 |
| GET | `/resignations/{id}` | 离职详情 | HR_STAFF | §5.4 |
| GET/POST | `/resignation-requests` | HR 管理员工离职申请 | HR_STAFF | §5.4 |

**`POST /onboarding/applications` 请求体（入职申请表单）：**

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `name` | Y | string | 姓名 |
| `gender` | Y | string | MALE / FEMALE |
| `mobile` | Y | string | 11 位手机号（登录账号），唯一 |
| `email` | Y | string | 邮箱格式 |
| `idNumber` | Y | string | 18 位身份证号 |
| `expectedOnboardDate` | Y | string(date) | 预计入职日，≥今天 |
| `departmentId` | Y | number | 部门 ID，深度≤5 |
| `positionId` | Y | number | 职位 ID |
| `employmentType` | Y | string | `fulltime` / `parttime` / `intern` |
| `probationMonths` | Y | number | 试用期（月），默认取职位配置 |
| `probationSalaryRatio` | Y | number | 试用薪资比例 0.80–1.00 |
| `managerId` | N | number | 直属上级，默认部门负责人 |
| `baseSalary` | Y | number | 约定薪资，超职级→触发 HR 二审 |

**`POST /regularization/applications` 请求体（转正申请）：**

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `employeeId` | Y | number | 员工 ID |
| `performanceEvaluation` | Y | string | 试用期表现评价 |
| `salaryAdjustment` | N | number | 调薪金额（有值→额外审批） |
| `approvalResult` | Y | string | PASS / EXTEND / FAIL |

**`POST /transfers` 请求体（调岗申请）：**

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `employeeId` | Y | number | 员工 ID，状态须为 probation/regular |
| `newDepartmentId` | Y | number | 新部门 ID，必须变更，否则 `30004` |
| `newPositionId` | N | number | 新职位 ID |
| `newJobLevel` | N | string | 新职级 |
| `newManagerId` | N | number | 新汇报人 |
| `salaryAdjustment` | N | number | 调薪金额（有值→额外审批） |
| `effectiveDate` | Y | string(date) | 生效日期 |
| `reason` | Y | string | 调岗原因 |

**`POST /resignations` 请求体（HR 正式离职）：**

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `employeeId` | Y | number | 员工 ID |
| `requestId` | Y | number | 关联已批准的员工离职申请 ID |
| `resignationDate` | Y | string(date) | 离职日，≥今天 |
| `reasonCategory` | Y | string | VOLUNTARY / INVOLUNTARY / NEGOTIATED |
| `resignationType` | Y | string | `resignation` / `dismissal` / `contract_expiry` / `other` |
| `reasonDetail` | N | string | 详细说明 |
| `handoverEmployeeId` | Y | number | 交接人 employee_id |

### 6.5 考勤 · 请假 · 加班

| 方法 | 路径 | 说明 | PRD |
| --- | --- | --- | --- |
| POST | `/attendance/punch` | 打卡 | §6.2 |
| GET | `/attendance/punch/today` | 今日打卡状态 | §6.2 |
| GET | `/attendance/punch/records` | 打卡记录 | §6.2 |
| POST | `/attendance/punch-fix` | 补卡申请 | §6.2.3 |
| GET | `/attendance/punch-fix/quota` | 补卡剩余次数 | §6.2.3 |
| GET/PUT | `/attendance/monthly-summary` | 月汇总/锁定 | §6.4、AD-01 |
| GET/POST/PUT/DELETE | `/attendance/groups` `/attendance/groups/{id}` | 考勤组 CRUD，请求/响应体字段见下方定义 | §6.1 |
| GET/PUT | `/attendance/workdays` | 工作日设置 | §6.1.2 |
| GET/POST/PUT/DELETE | `/attendance/holidays` | 节假日 | §6.1.2 |
| GET | `/attendance/statistics/personal` | 个人统计/日历，返回字段见下方定义 | §6.4 |
| GET | `/attendance/statistics/department` | 部门统计 | §6.4 |
| GET | `/leaves/balances` | 假期余额 | §6.3.2 |
| GET/POST | `/leaves/applications` | 请假申请/记录 | §6.3 |
| GET | `/leaves/calc-days` | 预览请假天数 | §6.3.3 |
| PUT | `/leaves/applications/{id}/cancel` | 撤销请假（**管理端**） | §6.3 |
| GET/POST | `/overtime/applications` | 加班申请/记录 | AD-02 |

**`POST /overtime/applications` 请求体：**

| 参数名 | 类型 | 必填 | 说明 | 校验规则 |
| --- | --- | --- | --- | --- |
| `overtimeDate` | string(date) | Y | 加班日期 | 不能是未来日期 |
| `startTime` | string | Y | 开始时间 | HH:mm 格式 |
| `endTime` | string | Y | 结束时间 | HH:mm 格式，须晚于 startTime |
| `reason` | string | Y | 加班原因 | ≤256 字符 |
| `hours` | number | N | 加班时长 | 系统自动计算 |

**考勤组字段定义（PRD §6.1.1）：**

`POST/PUT /attendance/groups` 请求体：

```json
{
  "name": "标准工时组",
  "applicableScope": { "departmentIds": [2, 3], "positionIds": [], "employeeIds": [] },
  "shiftType": "fixed",
  "onDuty": "09:00",
  "offDuty": "18:00",
  "restStart": "12:00",
  "restEnd": "13:00",
  "flexibleRange": { "earliest": null, "latest": null },
  "lateThreshold": 15,
  "earlyLeaveThreshold": 15
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | 考勤组名称，如"标准工时组" |
| `applicableScope` | object | 是 | 适用人员范围，可按部门/职位/个人指定 |
| `applicableScope.departmentIds` | number[] | 否 | 适用部门 ID 列表 |
| `applicableScope.positionIds` | number[] | 否 | 适用职位 ID 列表 |
| `applicableScope.employeeIds` | number[] | 否 | 适用员工 ID 列表 |
| `shiftType` | string | 是 | 班次类型：`fixed`（固定班）/ `flexible`（弹性班）/ `schedule`（排班制） |
| `onDuty` | string | 是 | 上班时间，格式 `HH:mm` |
| `offDuty` | string | 是 | 下班时间，格式 `HH:mm` |
| `restStart` | string | 否 | 中午休息开始，默认 `12:00`，格式 `HH:mm` |
| `restEnd` | string | 否 | 中午休息结束，默认 `13:00`，格式 `HH:mm` |
| `flexibleRange.earliest` | string | 否 | 弹性班最早打卡时间，格式 `HH:mm` |
| `flexibleRange.latest` | string | 否 | 弹性班最晚打卡时间，格式 `HH:mm` |
| `lateThreshold` | number | 是 | 迟到阈值（分钟），默认 15 |
| `earlyLeaveThreshold` | number | 是 | 早退阈值（分钟），默认 15 |

**`GET /attendance/statistics/personal` 响应结构（个人维度 8 项指标）：**

```json
{
  "employeeId": 1,
  "period": "2026-07",
  "shouldAttendDays": 23,
  "actualAttendDays": 21.5,
  "lateCount": 1,
  "earlyLeaveCount": 0,
  "absentDays": 0,
  "leaveDays": 1.5,
  "overtimeHours": 3.0,
  "annualBalance": 5.0
}
```

| 指标 | 说明 | PRD |
| --- | --- | --- |
| `shouldAttendDays` | 应出勤天数：当月工作日 | §6.4.1 |
| `actualAttendDays` | 实际出勤天数：有打卡记录的天数 | §6.4.1 |
| `lateCount` | 迟到次数 | §6.4.1 |
| `earlyLeaveCount` | 早退次数 | §6.4.1 |
| `absentDays` | 旷工天数 | §6.4.1 |
| `leaveDays` | 请假天数：各类请假汇总 | §6.4.1 |
| `overtimeHours` | 加班时长：已审批的加班时长 | §6.4.1 |
| `annualBalance` | 年假余额：剩余可用天数 | §6.4.1 |

### 6.6 薪资

| 方法 | 路径 | 说明 | 权限 | PRD |
| --- | --- | --- | --- | --- |
| GET/POST/PUT/DELETE | `/payroll/schemes` `/payroll/schemes/{id}` | 账套 CRUD，请求体见下方定义 | HR_STAFF, FINANCE | §7.1 |
| POST/GET | `/payroll/batches` | 创建/核算批次列表 | HR_STAFF, FINANCE | §7.3 |
| GET | `/payroll/batches/{id}` | 批次详情/状态轮询（含 `progress`） | HR_STAFF, FINANCE | §7.3 |
| DELETE | `/payroll/batches/{id}` | 删除批次（仅 draft 状态） | HR_STAFF | §7.3 |
| POST | `/payroll/batches/{id}/calculate` | 触发异步计算 | HR_STAFF | §7.3 |
| GET | `/payroll/batches/{id}/details` | 核算明细（含异常标记），响应见下方定义 | HR_STAFF, FINANCE | §7.3 |
| GET | `/payroll/batches/{id}/chart-data` | 图表数据 | HR_STAFF, FINANCE | §7.3.4 |
| PUT | `/payroll/batches/{id}/details/{detailId}` | 手工调整 | HR_STAFF | §7.3 |
| POST | `/payroll/batches/{id}/submit` | 提交审批 | HR_STAFF | §7.3 |
| POST | `/payroll/batches/{id}/distribute` | 发放确认 | HR_STAFF | §7.3 |
| GET | `/payroll/payslips` | 工资条列表（HR/财务），响应见下方定义 | HR_STAFF, FINANCE | §7.4 |
| GET | `/payroll/payslips/{month}` | 工资条详情（HR/财务），响应见下方定义 | HR_STAFF, FINANCE | §7.4 |
| GET | `/payroll/cost-report` | 成本报表 | HR_STAFF, FINANCE | §7 |

**账套请求体（`POST/PUT /payroll/schemes`）：**

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `name` | Y | string | 账套名称，2-50 字符 |
| `scope` | Y | object | 适用范围 `{ departmentIds[], positionIds[], jobLevels[] }` |
| `effectiveDate` | Y | string(date) | 生效日期 |
| `status` | N | string | ENABLED / DISABLED，默认 ENABLED |
| `items` | Y | array | 工资项目列表，至少 1 项 |

`items[]` 工资项目定义：

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `itemCode` | Y | string | 项目编码，唯一 |
| `itemName` | Y | string | 项目名称 |
| `itemType` | Y | string | `fixed` / `variable` / `attendance_deduct` / `social` / `fund` / `tax` |
| `calcRule` | N | string | SpEL 公式（variable 类型时必填） |
| `baseField` | N | string | ssBase / hfBase / performanceBase（social/fund 类型时必填） |
| `ratio` | N | number | 社保公积金比例 |
| `sortOrder` | N | number | 排序号，默认 0 |

**批次创建（`POST /payroll/batches`）请求体：** `{ "period": "2026-07" }`（账期 YYYY-MM，不可重复）

**批次列表（`GET /payroll/batches`）参数：** `?period=&page=&pageSize=`

**批次详情（`GET /payroll/batches/{id}`）响应：**

```json
{
  "id": 1,
  "period": "2026-07",
  "status": "calculating",
  "totalCount": 50,
  "successCount": 48,
  "anomalyCount": 2,
  "progress": 96
}
```

**核算明细（`GET /payroll/batches/{id}/details`）响应：**

```json
{
  "list": [
    {
      "employeeId": 1,
      "employeeName": "张三",
      "grossSalary": 15600.00,
      "netSalary": 12980.00,
      "calcStatus": "SUCCESS",
      "anomalyFlags": ["LEAVE_HIGH"],
      "manualAdjusted": false,
      "segmentCount": 1
    }
  ],
  "total": 50,
  "page": 1,
  "pageSize": 20
}
```

> 异常标记：`LEAVE_HIGH`（请假>15天🟡）、`OVERTIME_HIGH`（加班>50h🟡）、`SALARY_CHANGE_HIGH`（环比变动>30%🔴）、`NO_SALARY_PROFILE`（无薪资档案🔴阻断）

**手工调整（`PUT /payroll/batches/{id}/details/{detailId}`）请求体：**

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `itemCode` | Y | string | 调整项目编码 |
| `adjustAmount` | Y | number | 调整金额（可为负） |
| `reason` | Y | string | 调整原因，≤256 |

**图表数据（`GET /payroll/batches/{id}/chart-data`）响应：**

```json
{
  "costTrend": [{ "period": "2026-01", "grossTotal": 500000 }],
  "deptDistribution": [{ "deptName": "技术部", "grossTotal": 200000 }]
}
```

**工资条列表（`GET /payroll/payslips`）响应：**

```json
{
  "list": [
    {
      "employeeId": 1,
      "employeeName": "张三",
      "period": "2026-07",
      "grossSalary": 15600.00,
      "netSalary": 12980.00,
      "status": "distributed"
    }
  ],
  "total": 50
}
```

参数：`?period=&departmentId=&page=&pageSize=`

**工资条详情（`GET /payroll/payslips/{month}`）响应：**

```json
{
  "period": "2026-07",
  "employee": { "name": "张三", "employeeNo": "202401005", "department": "技术部" },
  "earnings": [
    { "name": "基本工资", "amount": 10000.00 },
    { "name": "岗位津贴", "amount": 2000.00 },
    { "name": "绩效奖金", "amount": 3600.00 }
  ],
  "grossSalary": 15600.00,
  "deductions": [
    { "name": "养老保险", "amount": -640.00 },
    { "name": "医疗保险", "amount": -160.00 },
    { "name": "失业保险", "amount": -40.00 },
    { "name": "住房公积金", "amount": -960.00 },
    { "name": "个人所得税", "amount": -320.00 },
    { "name": "事假扣款", "amount": -500.00 }
  ],
  "totalDeduction": 2620.00,
  "netSalary": 12980.00
}
```

**成本报表（`GET /payroll/cost-report`）参数：** `?periodFrom=&periodTo=&departmentId=`

```json
{
  "trend": [{ "period": "2026-01", "grossTotal": 500000, "netTotal": 400000 }],
  "deptDistribution": [{ "deptName": "技术部", "grossTotal": 200000, "netTotal": 160000 }]
}
```

### 6.7 审批中心

> 审批引擎为**表驱动 SpEL 路由，禁用 BPM**（AD-03）。审批中心不负责各业务自身状态机，职责：待办分发、详情聚合、审批操作、委托、超时催办。

| 方法 | 路径 | 说明 | 权限 | PRD |
| --- | --- | --- | --- | --- |
| GET | `/approvals/tasks/stats` | 待办统计（含 `overdueCount` 超时数量） | 有审批权限角色 | §8.2 |
| GET | `/approvals/tasks` | 待办列表，可筛 type/status/keyword，响应见下方定义 | 有审批权限角色 | §8.2 |
| GET | `/approvals/tasks/{id}` | 审批详情（含 task/instance/businessDetail/timeline/actions），响应见下方定义 | 有审批权限角色 | §8.2 |
| POST | `/approvals/tasks/{id}/action` | 审批操作 `{ action, comment, targetUserId? }`，操作规则见下方定义 | 有审批权限角色 | §8.2 |
| POST | `/approvals/tasks/{id}/remind` | 催办：向当前审批人发送催办通知 | 有审批权限角色 | §5.1.4 |
| POST | `/approvals/instances/{id}/withdraw` | 撤回实例（仅发起人且第一级可撤回） | 发起人 | §8 |
| GET | `/approvals/instances` | 我发起的审批实例列表 | 所有角色 | §8 |
| GET/POST/PUT/DELETE | `/approvals/delegations` | 委托 CRUD，请求体见下方定义 | 有审批权限角色 | §8.3 |

**`GET /approvals/tasks` 查询参数：** `?processType=&status=&keyword=&page=&pageSize=`

**`GET /approvals/tasks` 响应示例（待办列表）：**

```json
{
  "list": [
    {
      "taskId": 101,
      "instanceId": 1,
      "processType": "ONBOARDING",
      "title": "张三入职审批",
      "applicantName": "HR李四",
      "applicantDept": "HR部",
      "businessNo": "OA-2026-001",
      "businessSummary": "技术部-Java开发工程师",
      "currentNodeLabel": "部门负责人审批",
      "createTime": "2026-07-10 09:00:00",
      "dueAt": "2026-07-12 09:00:00",
      "status": "pending"
    }
  ],
  "total": 5,
  "page": 1,
  "pageSize": 20
}
```

**`GET /approvals/tasks/{id}` 响应结构（审批详情）：**

```json
{
  "task": {
    "id": 101,
    "status": "pending",
    "currentNodeLabel": "部门负责人审批",
    "dueAt": "2026-07-12 09:00:00"
  },
  "instance": {
    "processType": "ONBOARDING",
    "businessNo": "OA-2026-001",
    "initiator": "HR李四",
    "createdAt": "2026-07-10 09:00:00"
  },
  "businessDetail": {},
  "timeline": [
    {
      "node": "提交申请",
      "assignee": "HR李四",
      "action": "SUBMIT",
      "comment": null,
      "time": "2026-07-10 09:00:00"
    }
  ],
  "actions": ["APPROVE", "REJECT", "FORWARD"]
}
```

> `businessDetail` 按 `processType` 动态渲染，内容由各业务模块的 DetailAdapter 提供。

**`POST /approvals/tasks/{id}/action` 请求体：**

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `action` | Y | string | APPROVE / REJECT / FORWARD |
| `comment` | N | string | REJECT 时必填 |
| `targetUserId` | N | number | FORWARD 时必填，转交目标用户 ID |

**`POST /approvals/delegations` 请求体（委托表单）：**

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `delegateUserId` | Y | number | 被委托人，≠本人 |
| `startDate` | Y | string(date) | 委托开始日期 |
| `endDate` | Y | string(date) | 委托结束日期 |
| `reason` | N | string | 委托原因 |

> 委托规则：同一 delegator 仅一条 ACTIVE 委托生效；代审记录含 `on_behalf_of_id` / `display_text`。

**`GET /approvals/tasks/stats` 响应示例：**

```json
{
  "pending": 12,
  "approvedToday": 3,
  "overdueCount": 1
}
```

### 6.8 个人中心（员工门户）

> 所有 `/profile/*` 接口强制 `@DataScope(SELF)`。

| 方法 | 路径 | 说明 | PRD |
| --- | --- | --- | --- |
| GET/PUT | `/profile/me` | 我的档案 | §9.1 |
| GET | `/profile/attendance/calendar` | 考勤日历 | §9.2 |
| POST | `/profile/attendance/punch` | 打卡（代理 `/attendance/punch`） | §9.2 |
| POST | `/profile/attendance/punch-fix` | 补卡（代理 `/attendance/punch-fix`） | §9.2 |
| GET/POST | `/profile/leave/applications` | 请假申请/记录 | §9.3 |
| PUT | `/profile/leave/applications/{id}/cancel` | 撤销请假（**门户**） | §6.3 |
| GET | `/profile/payslips` | 工资条列表摘要 | §9.4 |
| GET | `/profile/payslips/trend` | 近 6 月实发趋势 | §9.4 |
| GET | `/profile/payslips/{period}` | 工资条详情（须先验证） | §7.4 |
| GET | `/profile/payslips/{period}/pdf` | 工资条 PDF 下载（须先验证） | §7.4 |
| POST | `/profile/payslips/verify` | 工资条二次验证 | §7.4 |
| PUT | `/profile/security/password` | 修改密码 | §9.5 |
| POST | `/profile/security/mobile/bind` | 首次绑定手机 | §9.5 |
| DELETE | `/profile/security/mobile` | 解绑手机 | §9.5 |
| GET | `/profile/security/login-logs` | 本人登录日志 | §9.5 |
| POST/GET | `/profile/mobile-change-applications` | 手机号变更申请 | §4.1.2 |
| POST | `/profile/mobile-change-applications/{id}/cancel` | 撤销变更申请 | §4.1.2 |
| GET/POST | `/profile/overtime/applications` | 加班申请/列表（门户规范路径） | AD-02 |
| POST/GET | `/profile/resignation-requests` | 员工离职申请 | §5.4 |
| POST | `/profile/resignation-requests/{id}/cancel` | 撤销离职申请 | §5.4 |

**`PUT /profile/me` 请求体：**

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `email` | N | string | 邮箱 |
| `residenceAddress` | N | string | 现居地址 |
| `emergencyContact` | N | string | 紧急联系人 |
| `emergencyPhone` | N | string | 紧急联系电话 |

**`PUT /profile/security/password` 请求体：** `{ "oldPassword": "...", "newPassword": "...", "confirmPassword": "..." }`

**`POST /profile/mobile-change-applications` 请求体：** `{ "newMobile": "138...", "smsCode": "123456", "reason": "..." }`

**`POST /profile/resignation-requests` 请求体（员工离职申请）：**

| 字段 | 必填 | 类型 | 说明 |
|------|:----:|------|------|
| `expectedResignDate` | Y | string(date) | 期望离职日，≥今天 |
| `reasonCategory` | Y | string | VOLUNTARY / INVOLUNTARY / NEGOTIATED |
| `resignationType` | Y | string | `resignation` / `dismissal` / `contract_expiry` / `other` |
| `reasonDetail` | N | string | 详细说明，≤512 |

> 员工加班：门户规范路径 `GET/POST /profile/overtime/applications`（强制 `@DataScope(SELF)`），与管理端 `GET/POST /overtime/applications` 同服务。

### 6.9 数据迁移 · 系统

| 方法 | 路径 | 说明 | 权限 | PRD |
| --- | --- | --- | --- | --- |
| GET/POST/PUT | `/system/users` `/system/users/{id}` | 用户管理，请求体见下方定义 | SYS_ADMIN | §2 |
| GET/PUT | `/system/roles` `/system/roles/{id}/permissions` | 角色管理/权限分配 | SYS_ADMIN | §2 |
| GET | `/system/operation-logs` | 操作审计日志 | SYS_ADMIN | §11.2 |
| GET | `/system/login-logs` | 登录日志（管理端全量） | SYS_ADMIN | §11.2 |
| POST | `/system/backup` | 数据备份 | SYS_ADMIN | §2 |


**`GET /workbench/summary` 响应结构：**

```json
{
  "totalEmployees": 156,
  "newHiresThisMonth": 5,
  "pendingApprovals": 12,
  "attendanceAnomalies": 3,
  "todayPunchRate": 0.92,
  "departmentStats": [
    { "deptName": "技术部", "headcount": 45 }
  ]
}
```

**`POST /system/users` 请求体：**

| 字段 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `username` | Y | string | 手机号 |
| `employeeId` | Y | number | 员工 ID |
| `roleIds` | Y | number[] | 角色 ID 列表 |
| `password` | N | string | 不传则随机生成+首次改密 |

**`GET /system/roles` 响应结构：**

```json
[
  {
    "id": 1,
    "code": "HR_STAFF",
    "name": "HR专员",
    "dataScope": "ALL",
    "permissionIds": [1, 2, 3, 5, 8]
  }
]
```

**`PUT /system/roles/{id}/permissions` 请求体：**

```json
{ "permissionIds": [1, 2, 3, 5, 8, 10, 12] }
```

---

## 7. 特殊路径规则

### 7.1 工资条二次验证

| 路径 | 角色 | 说明 |
| --- | --- | --- |
| `POST /profile/payslips/verify` | **规范路径** | 请求体 `{ verifyType: "PASSWORD"\|"SMS", verifyCode }` |
| `POST /auth/verify` | 同服务别名 | OpenAPI 以规范路径为准 |

- 验证成功后 Redis `hrms:payslip:verified:{userId}` TTL **30min**
- `GET /profile/payslips/{period}` 须先验证，否则 `60004`
- `GET /profile/payslips` 列表摘要无需验证

### 7.2 账号安全

| 路径 | 角色 | 说明 |
| --- | --- | --- |
| `PUT /profile/security/password` | **门户规范路径** | `{ oldPassword, newPassword }` |
| `PUT /auth/password` | 同服务别名 | 登录页首次改密 |
| `POST /profile/security/mobile/bind` | **门户规范路径** | `{ mobile, smsCode }` |
| `DELETE /profile/security/mobile` | **门户规范路径** | 解绑（短信验证） |
| `PUT/DELETE /auth/mobile` | 同服务别名 | — |
| `GET /profile/security/login-logs` | 门户 | 仅本人 |
| `GET /system/login-logs` | 管理端 | SYS_ADMIN 全量 |

> **变更手机号**不走解绑/绑定，须 `POST /profile/mobile-change-applications` 走 `MOBILE_CHANGE` 审批。

### 7.3 请假撤销（管理端 vs 门户）

| 场景 | 方法 | 路径 |
| --- | --- | --- |
| 管理端 / HR / 主管 | **PUT** | `/leaves/applications/{id}/cancel` |
| 员工门户 | **PUT** | `/profile/leave/applications/{id}/cancel` |

---

## 8. 审批 processType 与 PRD 对应

| processType | PRD 业务 | 审批链（概要） |
| --- | --- | --- |
| `ONBOARDING` | §5.1 入职 | 部门负责人 → HR 负责人（条件） |
| `REGULARIZATION` | §5.2 转正 | 部门负责人 → HR 负责人 |
| `TRANSFER` | §5.3 调岗 | 原部门 → 新部门 → HR 负责人 |
| `RESIGNATION` | §5.4 离职 | 部门负责人 → HR 负责人 |
| `RESIGNATION_REQUEST` | §5.4 员工离职申请 | 直接上级 → HR |
| `MOBILE_CHANGE` | §4.1.2 手机号变更 | HR |
| `LEAVE` | §6.3.4 请假 | 动态规则（按类型+天数）:<br>年假/调休≤3天→直接上级；>3天→直接上级→部门负责人<br>病假/事假≤1天→直接上级；>1天→直接上级→部门负责人<br>**婚假/产假/丧假→直接上级→HR备案（无需二审）** |
| `MAKEUP` | §6.2.3 补卡 | 直接上级 |
| `OVERTIME` | AD-06 加班 | 直接上级 → HR 负责人（≥4h） |
| `PAYROLL_BATCH` | §7.3 薪资批次 | 财务 → 老板（AD-07 条件） |

**审批状态（API）：** `pending` · `approved` · `rejected` · `cancelled`

---

## 9. 业务枚举

### 9.1 在职状态

| API 值 | PRD | DB |
| --- | --- | --- |
| `probation` | 试用期 | 10 |
| `regular` | 正式 | 20 |
| `pending_resign` | 待离职 | 30 |
| `resigned` | 已离职 | 40 |

### 9.2 入职状态

| API 值 | PRD 业务状态 | DB |
| --- | --- | --- |
| `draft` | 草稿 | DRAFT |
| `pending` | 审批中 | APPROVING |
| `approved_pending` | 已批准待入职 | APPROVED |
| `rejected` | 已拒绝 | REJECTED |
| `onboarded` | 已入职 | ONBOARDED |
| `abandoned` | 已放弃 | ABANDONED |

### 9.3 请假类型

| API 值 | PRD |
| --- | --- |
| `annual` | 年假 |
| `sick` | 病假 |
| `personal` | 事假 |
| `marriage` | 婚假 |
| `maternity` | 产假 |
| `bereavement` | 丧假 |
| `compensatory` | 调休 |

### 9.4 录用类型

| API 值 | PRD |
| --- | --- |
| `fulltime` | 全职 |
| `parttime` | 兼职 |
| `intern` | 实习 |

### 9.5 薪资批次状态

| API 值 | PRD |
| --- | --- |
| `draft` | 草稿 |
| `calculating` | 计算中 |
| `pending_confirm` | 待确认 |
| `approving` | 审批中 |
| `approved` | 已通过 |
| `distributed` | 已发放 |
| `rejected` | 已驳回 |

### 9.6 班制类型

| API 值 | PRD |
| --- | --- |
| `fixed` | 固定班 |
| `flexible` | 弹性班 |
| `schedule` | 排班制 |

### 9.7 工资项目类型

| API 值 | PRD §7.1.2 |
| --- | --- |
| `fixed` | 固定收入 |
| `variable` | 变动收入 |
| `attendance_deduct` | 考勤扣款 |
| `social` | 社保扣除 |
| `fund` | 公积金扣除 |
| `tax` | 个税 |

### 9.8 离职类型

| API 值 | PRD §5.4.3 |
| --- | --- |
| `resignation` | 辞职 |
| `dismissal` | 辞退 |
| `contract_expiry` | 合同到期不续签 |
| `other` | 其他 |

### 9.9 性别

| API / DB 值 | 说明 |
|:-----------:|------|
| `MALE` | 男 |
| `FEMALE` | 女 |

### 9.10 手机号变更申请状态

| API 值 | DB 值 | 说明 |
|:------:|:-----:|------|
| `pending` | PENDING | 审批中 |
| `approved` | APPROVED | 已通过 |
| `rejected` | REJECTED | 已驳回 |
| `cancelled` | CANCELLED | 已撤销 |

### 9.11 离职原因分类

| API 值 | 说明 |
|:------:|------|
| `VOLUNTARY` | 主动辞职 |
| `INVOLUNTARY` | 被动辞退 |
| `NEGOTIATED` | 协商解除 |

---

## 10. 错误码

### 10.1 HTTP 状态码

| HTTP | 场景 |
| --- | --- |
| 200 | 正常响应（含业务失败时 `code !== 0`） |
| 400 | 参数校验失败 |
| 401 | 未登录 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 冲突（重复提交、乐观锁） |
| 422 | 业务规则拒绝 |
| 500 | 系统异常 |

### 10.2 业务错误码（`code` 字段）

| code | 说明 | PRD/场景 |
| --- | --- | --- |
| 0 | 成功 | — |
| 10001 | 参数校验失败 | — |
| 20001 | 未登录或 Token 过期 | §11.2 |
| 20002 | 无权限 | §2 |
| 20003 | 字段权限不足 | §2.3 |
| 30001 | 部门层级超过 5 层 | §3.1.3 |
| 30002 | 部门合并前尚有员工 | §3.1.3 |
| 30003 | 员工状态不允许此操作 | §5 |
| 30004 | 调岗部门未变更 | §5.3.2 |
| 30005 | 数据已过期（乐观锁冲突） | — |
| 40001 | 考勤月已锁定 | AD-01 |
| 40002 | 补卡次数超限（2 次/月） | §6.2.3 |
| 40003 | 请假余额不足 | §6.3.2 |
| 40004 | 不在打卡有效范围（GPS/IP） | §6.2.1 |
| 50001 | 算薪批次已存在 | §7.3 |
| 50002 | 算薪进行中 | §7.3 |
| 50003 | 员工无薪资档案 | §7.3.3 |
| 50004 | 考勤数据未锁定 | AD-01 |
| 50005 | 工资条尚未发放或不可查看 | §7.4 |
| 60001 | 审批已处理 | §8 |
| 60002 | 审批超时/不可撤销 | §8 |
| 60003 | 委托规则冲突 | §8.3 |
| 60004 | 工资条二次验证未通过 | §7.4 |
| 90001 | 系统内部错误 | — |

---

## 11. 页面路由 ↔ API 映射

> **约定**：页面路由（前端）与 API 路径（后端）分离。管理端 `/admin/*`，员工门户 `/portal/*`。

### 11.1 管理后台（AdminLayout）

| 页面路径 | 主要 API |
| --- | --- |
| `/login` | `POST /auth/login` `GET /auth/profile` |
| `/admin/workbench` | `GET /workbench/summary` `GET /approvals/tasks/stats` |
| `/admin/org/departments` | `/departments/*` |
| `/admin/org/positions` | `/positions/*` |
| `/admin/employee/list` | `GET /employees` |
| `/admin/employee/:id` | `GET/PUT /employees/{id}` |
| `/admin/onboarding/*` | `/onboarding/applications/*` |
| `/admin/lifecycle/*` | `/regularization/*` `/transfers` `/resignations` |
| `/admin/attendance/*` | `/attendance/*` |
| `/admin/leave/list` | `/leaves/*` |
| `/admin/overtime/list` | `/overtime/applications` |
| `/admin/payroll/*` | `/payroll/*` |
| `/admin/approval/*` | `/approvals/*` |
| `/admin/system/*` | `/system/*` |

### 11.2 员工门户（PortalLayout）

| 页面路径 | 主要 API |
| --- | --- |
| `/portal/profile` | `GET/PUT /profile/me` `POST /profile/mobile-change-applications` |
| `/portal/attendance` | `/profile/attendance/*` |
| `/portal/leave` | `/profile/leave/*` |
| `/portal/overtime` | `/profile/overtime/applications`（SELF） |
| `/portal/salary` | `/profile/payslips/*` |
| `/portal/resignation/apply` | `/profile/resignation-requests` |
| `/portal/security` | `/profile/security/*` |

---

## 12. Mock 与联调

| 项 | 约定 |
| --- | --- |
| Mock 工具 | Apifox，从 `openapi.yaml` 导入 |
| 前端环境变量 | `UMI_APP_API_BASE` 指向 Apifox Mock |
| 切换时机 | 联调周切换至 `http://localhost:8080/api/v1` |
| 回归 | Apifox 用例集覆盖 §6 各模块主路径 |
| 破坏性变更 | OpenAPI semver **major** 升级，同步更新本文档版本 |

---

## 13.另： 变更管理

### 13.1 变更流程

1. PRD 变更 → PD 确认 → 更新本文档 → 同步 OpenAPI → 通知前后端
2. 仅技术优化 → 直接更新本文档 + OpenAPI，在 §13.2 记录
3. **禁止**仅改系分而不更新本文档

### 13.2 变更记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0.0 | 2026-07-11 | 初版：基于 PRD v1.0，对齐后端 v1.7.1 / 前端 v1.8.1 契约 |
| v1.1.0 | 2026-07-11 | 与 PRD 对齐修订：新增部门合并、审批催办接口；补充员工搜索参数、考勤统计返回字段、考勤组字段定义、离职类型枚举；统一工资条路径为 `/payroll/payslips`；注明调岗约束；补充 JSON 示例；补充 token/密码规则；补充请假审批规则；统一门户加班路径。**更新人：李俊毅** |
| v1.2.0 | 2026-07-11 | 补充调岗详情、离职详情、员工调岗历史、工资条 PDF 下载四个后端系分已有而契约缺失的接口。**更新人：张浩杰** |
| v1.3.0 | 2026-07-15 | 个人统计响应扁平化、撤销请假方法修正、加班申请参数补充。**更新人：张浩杰** |
| v1.4.0 | 2026-07-15 | Token 有效期 30min→2h、无操作超时机制补充、组织架构/系统管理接口补充权限及字段定义。**更新人：张浩杰** |
| v1.5.0 | 2026-07-15 | 员工详情/编辑/薪资档案补充请求响应体、性别枚举、手机号变更状态枚举。**更新人：张浩杰** |
| v1.6.0 | 2026-07-15 | 薪资模块全量接口补充权限列、账套/批次/工资条字段定义、清理误放示例。**更新人：张浩杰** |
| v1.7.0 | 2026-07-15 | 入转调离补充审批权限列+请求体字段定义、审批中心补充接口/详情结构/委托字段/操作规则、离职原因分类枚举。**更新人：张浩杰** |

**v1.1.0 修改完成总结：**

| # | 类别 | 修改内容 | 涉及章节 |
|:---:|------|---------|:--------:|
| 1 | 新增接口 | 部门合并 `POST /departments/{id}/merge` | §6.2 |
| 2 | 新增接口 | 审批催办 `POST /approvals/tasks/{id}/remind` | §6.7 |
| 3 | 补充参数 | 员工搜索 9 个 Query Parameters + 响应示例 | §6.3 |
| 4 | 补充参数 | 考勤组 9 个字段定义 + 请求/响应体结构 | §6.5 |
| 5 | 补充参数 | 考勤统计个人 8 项指标 + 响应示例 | §6.5 |
| 6 | 补充参数 | 审批待办 `dueAt` 超时字段 | §6.7 |
| 7 | 补充枚举 | 离职类型枚举 4 类 | §9.8 |
| 8 | 补充枚举 | 新增错误码 `30004`（调岗部门未变更）| §10.2 |
| 9 | 补充示例 | login、dept tree、approval tasks、payslip JSON 示例 | §6.1, §6.7 |
| 10 | 补充规则 | Token 30min/密码规则/90 天改密 | §2.1 |
| 11 | 补充规则 | 请假婚产丧特殊审批规则 | §8 |
| 12 | 补充规则 | 调岗"部门必须变更"约束 | §6.4 |
| 13 | 路径统一 | 工资条 `/payslips` → `/payroll/payslips` | §6.6 |
| 14 | 路径统一 | 门户加班新增 `/profile/overtime/applications` | §6.8, §11.2 |
| 15 | 结构优化 | 更新 §3 溯源矩阵 | §3 |

**v1.2.0 修改完成总结：**

| # | 类别 | 修改内容 | 涉及章节 |
|:---:|------|---------|:--------:|
| 1 | 新增接口 | 调岗详情 `GET /transfers/{id}` | §6.4 |
| 2 | 新增接口 | 离职详情 `GET /resignations/{id}` | §6.4 |
| 3 | 新增接口 | 员工调岗历史 `GET /employees/{id}/transfer-history` | §6.3 |
| 4 | 新增接口 | 工资条 PDF 下载 `GET /profile/payslips/{period}/pdf` | §6.8 |

**v1.3.0 修改完成总结：**

| # | 类别 | 修改内容 | 涉及章节 |
|:---:|------|---------|:--------:|
| 1 | 响应结构调整 | 个人统计接口响应从嵌套 `metrics` 改为扁平结构；字段名 `absenteeismDays` → `absentDays`、`annualLeaveBalance` → `annualBalance`（与后端/前端系分对齐） | §6.5 |
| 2 | 方法修正 | 员工门户撤销请假方法 `POST` → `PUT`（与后端/前端系分对齐） | §6.8, §7.3 |
| 3 | 补充参数定义 | 加班申请 `POST /overtime/applications` 补充请求参数字段定义 | §6.5 |

**v1.4.0 修改完成总结（对齐李俊毅系分 v2.0.0）：**

| # | 类别 | 修改内容 | 涉及章节 |
|:---:|------|---------|:--------:|
| 1 | 参数修正 | Token 有效期 `access_token` 30 分钟（`expiresIn: 1800`）→ **2 小时（`expiresIn: 7200`）**，与后端系分双 Token 设计对齐 | §2.1, §6.1 |
| 2 | 规则补充 | 无操作超时机制从简略描述扩充为**前端三层联动**（idleDetector + tokenRefresher + 401 拦截器）+ **后端 Redis `user:last-active` 兜底**，与系分保持一致 | §2.1 |
| 3 | 补充权限 | 组织架构、数据迁移·系统模块所有接口补充**角色权限**列 | §6.2, §6.9 |
| 4 | 补充字段定义 | 新增部门 `POST /departments` 请求体（6 字段） | §6.2 |
| 5 | 补充字段定义 | 部门合并 `PUT /departments/{id}/merge` 请求体 | §6.2 |
| 6 | 补充字段定义 | 新增职位 `POST /positions` 请求体（8 字段 + 职级校验规则） | §6.2 |
| 7 | 补充字段定义 | 创建用户 `POST /system/users` 请求体（4 字段） | §6.9 |
| 8 | 补充响应结构 | 工作台 `GET /workbench/summary` 补充完整 JSON 响应示例 | §6.9 |
| 9 | 补充响应结构 | 角色列表 `GET /system/roles` 补充完整响应示例 | §6.9 |

**v1.5.0 修改完成总结（对齐同学 B/范文路 系分）：**

| # | 类别 | 修改内容 | 涉及章节 |
|:---:|------|---------|:--------:|
| 1 | 补充响应结构 | 员工详情 `GET /employees/{id}` 补充完整 JSON 响应示例（含 `personalInfo`、`salaryInfo`、`fieldPermissions`） | §6.3 |
| 2 | 补充字段定义 | 编辑档案 `PUT /employees/{id}` 请求体（7 个白名单字段 + `fields` 包裹格式 + `updatedFields` 响应） | §6.3 |
| 3 | 补充字段定义 | 薪资档案 `GET/PUT /employees/{id}/salary` 补充请求体（7 个字段） | §6.3 |
| 4 | 补充规则 | 敏感字段查看 `GET /employees/{id}/sensitive/{field}` 补充二次验证说明 + 审计日志要求 | §6.3 |
| 5 | 补充字段定义 | 个人中心编辑 `PUT /profile/me` 补充请求体（4 个白名单字段） | §6.8 |
| 6 | 补充字段定义 | 修改密码 `PUT /profile/security/password`、手机号变更申请 `POST /profile/mobile-change-applications` 补充请求体示例 | §6.8 |
| 7 | 新增枚举 | 性别枚举 `MALE`/`FEMALE` | §9.9 |
| 8 | 新增枚举 | 手机号变更申请状态枚举（`pending`/`approved`/`rejected`/`cancelled`） | §9.10 |

**v1.6.0 修改完成总结（对齐张浩杰薪资系分 v1.0.0）：**

| # | 类别 | 修改内容 | 涉及章节 |
|:---:|------|---------|:--------:|
| 1 | 补充权限 | 薪资模块全部接口补充**角色权限**列（HR_STAFF, FINANCE） | §6.6 |
| 2 | 新增接口 | 删除批次 `DELETE /payroll/batches/{id}`（仅 draft 状态） | §6.6 |
| 3 | 补充字段定义 | 账套 `POST/PUT /payroll/schemes` 请求体（8 个字段 + `items[]` 工资项目 7 个子字段） | §6.6 |
| 4 | 补充字段定义 | 批次创建 `POST /payroll/batches` 请求体、批次列表/批次详情/核算明细完整响应 JSON | §6.6 |
| 5 | 补充字段定义 | 手工调整 `PUT /payroll/batches/{id}/details/{detailId}` 请求体（3 字段） | §6.6 |
| 6 | 补充响应结构 | 工资条列表/详情响应统一为系分格式（`grossSalary`/`netSalary` 命名、`earnings[]`/`deductions[]` 分类） | §6.6 |
| 7 | 补充响应结构 | 成本报表 `GET /payroll/cost-report` 参数定义 + 完整 JSON 响应示例 | §6.6 |
| 8 | 补充示例 | 图表数据 `GET /payroll/batches/{id}/chart-data` 完整 JSON 响应示例 | §6.6 |
| 9 | 清理 | 移除误放在 §6.7 审批中心中的工资条响应示例（已移至 §6.6） | §6.7 |

**v1.7.0 修改完成总结（对齐郭策 Workflow 系分）：**

| # | 类别 | 修改内容 | 涉及章节 |
|:---:|------|---------|:--------:|
| 1 | 补充权限 | 入转调离、审批中心全部接口补充**角色权限**列 | §6.4, §6.7 |
| 2 | 补充字段定义 | 入职申请 `POST /onboarding/applications` 请求体（13 个字段） | §6.4 |
| 3 | 补充字段定义 | 转正申请 `POST /regularization/applications` 请求体（4 字段） | §6.4 |
| 4 | 补充字段定义 | 调岗申请 `POST /transfers` 请求体（8 字段 + 部门必须变更规则） | §6.4 |
| 5 | 补充字段定义 | HR 正式离职 `POST /resignations` 请求体（7 字段） | §6.4 |
| 6 | 新增接口 | 审批中心 `GET /approvals/instances`（我发起的） | §6.7 |
| 7 | 补充响应结构 | 审批详情 `GET /approvals/tasks/{id}` 完整 JSON（task/instance/timeline/actions） | §6.7 |
| 8 | 补充字段定义 | 审批操作 `POST .../action` 规则（comment REJECT 必填、targetUserId FORWARD 必填） | §6.7 |
| 9 | 补充字段定义 | 委托 `POST /approvals/delegations` 请求体（4 字段） | §6.7 |
| 10 | 补充响应结构 | 待办统计 `GET /approvals/tasks/stats` 完整响应 JSON | §6.7 |
| 11 | 补充参数 | 待办列表 `GET /approvals/tasks` 补充查询参数（processType/status/keyword） | §6.7 |
| 12 | 补充字段定义 | 员工门户离职申请 `POST /profile/resignation-requests` 请求体（4 字段） | §6.8 |
| 13 | 新增枚举 | 离职原因分类 `reasonCategory`（VOLUNTARY / INVOLUNTARY / NEGOTIATED） | §9.11 |

---

## 15. 待办事项

> 目前无待办事项。所有已发现的与 PRD 对齐问题均已在 v1.1.0 中解决。

---

## 14. 参考资料（原 §14）

| 文档 | 路径 |
| --- | --- |
| PRD | [人资管理系统-PRD.md](../人资管理系统-PRD.md) |
| 后端总系分 | [HRMS-Backend-System-Design(2).md](HRMS-Backend-System-Design(2).md) |
| 前端总系分 | [HRMS-Frontend-System-Design(1).md](HRMS-Frontend-System-Design(1).md) |
| 后端系分（李俊毅） | [李俊毅-后端系分.md](李俊毅-后端系分.md) v2.0.0 |
| 前端系分（李俊毅） | [李俊毅-前端系分.md](李俊毅-前端系分.md) v2.0.0 |
| 后端系分（范文路） | [hrms-employee-后端系分.md](hrms-employee-后端系分.md) v1.3.0 |
| 前端系分（同学 B） | [前端系分_格式化.md](前端系分_格式化.md) v1.0.0 |
| 后端系分（张浩杰） | [薪资-后端系分.md](薪资-后端系分.md) v1.0.0 |
| 前端系分（张浩杰） | [薪资-前端系分.md](薪资-前端系分.md) v1.0.0 |
| 后端系分（郭策） | [HRMS-Workflow-Backend-Design.md](HRMS-Workflow-Backend-Design.md) |
| 前端系分（郭策） | [HRMS-Workflow-Frontend-Design.md](HRMS-Workflow-Frontend-Design.md) |
| OpenAPI | `hrms-server/openapi.yaml`（Sprint 0） |
| 代码仓库 | https://gitee.com/swing-king/hrmini |

---

*本文档为 HRMS V1.0 前后端联调的唯一契约锚点。与系分冲突时，以本文档 API 路径、枚举、错误码为准；与 PRD 冲突时，以 PRD 业务规则为准并发起契约变更。*
