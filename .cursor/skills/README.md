# HRMini Cursor Skills

项目级开发规范，供 Cursor Agent 在编码时自动参考。

| Skill | 说明 |
|-------|------|
| `hrms-dev` | 总览与索引（优先阅读） |
| `hrms-api-convention` | REST 接口、响应体、错误码、OpenAPI |
| `hrms-backend-dev` | 后端模块、分层、JDK17、Maven |
| `hrms-frontend-dev` | Umi Max、双布局、services、access |
| `hrms-business-rules` | PRD 强制业务规则 |
| `hrms-database` | Flyway、表命名、敏感字段 |
| `hrms-code-review` | 合并前审查：安全、稳定、无冗余 |
| `hrms-cross-module-api` | **跨模块 Service 契约：员工/审批/MQ 消息体（新增）** |
| `hrms-security-review` | 权限/薪资/敏感数据专项安全 |

## 使用方式

**Review 场景：**
- 「帮我 review 这段代码」→ `hrms-code-review`
- 「检查有没有越权漏洞」→ `hrms-security-review` + `hrms-business-rules`

在 Cursor 对话中提及任务类型即可触发，例如：

- 「按接口规范加一个登录 API」→ `hrms-api-convention` + `hrms-backend-dev`
- 「做员工列表页」→ `hrms-frontend-dev` + `hrms-api-convention`
- 「写部门表 DDL」→ `hrms-database`
- 「跨模块调用审批引擎/员工服务」→ `hrms-cross-module-api`
- 「写考勤模块请假审批联动」→ `hrms-cross-module-api` + `hrms-business-rules`

或在 Agent 输入 `@hrms-api-convention` 显式引用。
