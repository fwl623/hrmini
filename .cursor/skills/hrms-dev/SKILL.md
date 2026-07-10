---
name: hrms-dev
description: >-
  HRMS 项目总览与 Skill 索引。在 HRMS 任意开发任务开始时使用，确定应遵循的规范与文档版本。
---

# HRMS 开发总览

## 文档优先级

1. **PRD**：`人资管理系统-PRD.md` — 业务规则最终来源
2. **后端系分** v1.7：`HRMS-Backend-System-Design.md`（附录 H/K 为 API/错误码权威）
3. **前端系分** v1.8：`HRMS-Frontend-System-Design.md`
4. **开发计划**：`HRMini-Development-Plan.md`

## 环境

| 环境 | 前端 | 后端 |
|------|------|------|
| 本地 | localhost:8000 | localhost:8080/api/v1 |
| 联调 | 39.101.67.167 | 39.101.67.167:8080/api/v1 |

## 专项 Skill（按任务选用）

| Skill | 何时用 |
|-------|--------|
| [hrms-api-convention](../hrms-api-convention/SKILL.md) | 新增/改接口、openapi、Apifox |
| [hrms-backend-dev](../hrms-backend-dev/SKILL.md) | Java 后端、Maven 模块、分层 |
| [hrms-frontend-dev](../hrms-frontend-dev/SKILL.md) | React 页面、路由、services |
| [hrms-business-rules](../hrms-business-rules/SKILL.md) | 业务逻辑、PRD 规则、评审 |
| [hrms-database](../hrms-database/SKILL.md) | DDL、Flyway、表设计 |
| [hrms-code-review](../hrms-code-review/SKILL.md) | PR/合并前审查：安全、稳定、冗余 |
| [hrms-security-review](../hrms-security-review/SKILL.md) | 权限/薪资/敏感数据专项安全审查 |

## 四人分工（全栈）

| 同学 | 后端模块 | 前端 |
|------|----------|------|
| 你 | common、auth、org | login、org、权限 |
| B | employee、portal | employee、portal |
| C | workflow | 入转调离、审批 |
| D | attendance、payroll | 考勤、薪资 |

## 编码原则

- JDK **17**；Spring Boot 3；禁止 Vue
- 最小改动；只改自己模块
- 先更 openapi/系分契约，再写实现
- 微项目：可运行优先，避免过度抽象
