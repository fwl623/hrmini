---
name: hrms-business-rules
description: >-
  HRMS 关键业务规则（PRD 强制）：权限矩阵、入转调离、手机号变更、离职双阶段、薪资可见性。
  在实现业务逻辑、写测试用例、评审 PR 时使用，避免与 PRD 冲突。
---

# HRMS 业务规则（PRD 强制）

来源：`人资管理系统-PRD.md` + 系分 v1.7/v1.8。实现与 PRD 冲突时 **以 PRD 为准**。

## 角色与数据权限（PRD §2）

| 角色 code | 数据范围 | 薪资全量 |
|-----------|----------|----------|
| SYS_ADMIN | ALL | **否** |
| HR_STAFF | ALL | 是 |
| DEPT_MANAGER | 本部门树 | 否 |
| FINANCE | PAYROLL | 是（薪资域） |
| EMPLOYEE | SELF | 仅本人工资条 |

- 系统管理员：**无**薪资管理菜单，薪资 API 双拦截
- 部门主管：可看本部门员工，**不可**看薪资字段

## 三条易错规则

### 1. 手机号不可直接改（PRD §4.1.2）

- 员工/HR **不能** PUT 直接改 `mobile`
- 须走 `MOBILE_CHANGE` 审批 → `POST /profile/mobile-change-applications`
- HR 待办：`GET /employees/mobile-change-applications`

### 2. 离职双阶段（PRD §5.4.1）

```
员工 POST /profile/resignation-requests（RESIGNATION_REQUEST）
  → 审批通过
HR POST /resignations（正式离职）
  → 定时任务生效
```

HR **不能**跳过员工申请直接办离职（除非系分允许的 HR 发起场景见 §5.4）。

### 3. 入职不走员工 CRUD

- **禁止** `POST /employees` 创建在职员工
- 统一：`/onboarding/applications` → 审批 → `confirm` → 生成档案

## 员工主键（AD-05）

| 字段 | 规则 |
|------|------|
| employee_id | 业务主键，**永不复用** |
| emp_no | 展示工号，`YYYY+部门码+序号`，同年同部门可复用 |

所有 FK 引用 `employee_id`，不引用 `emp_no`。

## 考勤与算薪（AD-01）

- 自然月核算；算薪前须 **考勤月锁定**（`attendance_month_lock`）
- 未锁定算薪 → 错误码 `50004`

## 工资条二次验证

- 员工查看详情：`POST /profile/payslips/verify` → Redis TTL 30min
- 未验证访问详情 → `60004`

## 审批

- 9 类 processType 统一走 `/approvals/tasks`
- 48h SLA 催办（可 Phase 4+ MQ）
- 委托：同时仅 1 条有效规则

## 实现前自检

- [ ] 该功能 PRD 哪一节？系分 §2.2 哪条？
- [ ] 是否触及 SYS_ADMIN 薪资禁区？
- [ ] 是否需 DataScope / FieldPermission？
- [ ] 状态枚举 API 小写 vs DB 大写（附录 I 映射）？
