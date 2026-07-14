---
name: hrms-security-review
description: >-
  HRMS 专项安全审查：越权、敏感数据、审计、注入、Token。
  在审查权限/薪资/员工档案/登录相关代码，或用户提到安全、合规时使用。
---

# HRMS 安全专项审查

> 通用审查流程见 [hrms-code-review](../hrms-code-review/SKILL.md)。本 Skill 聚焦 **安全与合规**。

## 威胁模型（微项目范围）

| 威胁 | 防护要求 |
|------|----------|
| 未授权访问 | JWT + RBAC |
| 水平越权 | DataScope SELF/DEPT_TREE |
| 垂直越权 | 角色 + 权限码 |
| 薪资泄露 | SYS_ADMIN 双拦截；FieldPermission |
| 敏感数据泄露 | AES 存储；响应裁剪；审计日志 |
| 注入 | `#{}` 参数绑定；JSR-303 校验 |
| 会话劫持 | HTTPS；Token 黑名单登出；30min 超时 |

## 必测用例（审查时要求提供或自测说明）

### 权限

1. **EMPLOYEE** 调 `GET /employees` → 仅本人或 403
2. **DEPT_MANAGER** 调其他部门员工 id → 403
3. **SYS_ADMIN** 调 `GET /payroll/batches` → 403
4. **FINANCE** 调组织删除 → 403

### 敏感数据

5. 无 `SENSITIVE_VIEW` 权限调 `GET /employees/{id}/sensitive/idNumber` → 403 + 审计
6. 响应 JSON 中 DEPT_MANAGER 看不到下属银行卡全量

### 业务安全

7. 不能直接 PUT 修改 mobile
8. 工资条详情未 verify → `60004`
9. 重复 POST 审批 action → `60001` 幂等

## 代码模式：拒绝 ❌

```java
// ❌ 字符串拼 SQL
@Select("SELECT * FROM employee WHERE name = '${name}'")

// ❌ 无权限检查的详情
public Employee getById(Long id) { return mapper.selectById(id); }

// ❌ 日志打印敏感信息
log.info("user login: {}", password);

// ❌ 返回实体含加密字段明文
return employeeEntity; // 应转 VO + FieldFilter
```

## 代码模式：推荐 ✅

```java
// ✅ 数据权限
@DataScope(type = DEPT_TREE)
public Page<EmployeeVO> list(EmployeeQuery q) { ... }

// ✅ 薪资 API 角色校验
@PreAuthorize("hasAnyRole('HR_STAFF','FINANCE')")
public PayrollBatchVO getBatch(Long id) { ... }

// ✅ 敏感字段审计
@Audit(action = "VIEW_SENSITIVE", resource = "idNumber")
public String getSensitiveField(...) { ... }
```

## 前端安全

- Token 存 localStorage（微项目可接受）；禁止 cookie 明文无 HttpOnly 混用 secrets
- `dangerouslySetInnerHTML` 禁止用于用户输入
- 导出 Excel 须后端鉴权，不只靠前端隐藏按钮

## 输出

仅输出 **🔴 安全项** 和 **🟡 加固建议**；每项注明 CWE/风险一句话（如「水平越权」「敏感信息泄露」）。

无安全问题时明确写：**未发现安全阻塞项**。
