---
name: hrms-api-convention
description: >-
  HRMS REST API 接口规范：路径、响应体、分页、鉴权、错误码、OpenAPI 同步。
  在新增/修改 Controller、编写 openapi.yaml、Apifox 契约、前后端联调时使用。
---

# HRMS 接口规范

权威来源：`HRMS-Backend-System-Design.md` **附录 H**（路径）、**附录 K**（错误码）。

## 基础约定

| 项 | 值 |
|----|-----|
| Base URL | `/api/v1` |
| 鉴权 | `Authorization: Bearer {accessToken}` |
| Content-Type | `application/json`（文件上传除外） |

## 统一响应

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "uuid",
  "timestamp": 1700000000000
}
```

- `code === 0` 表示成功；业务失败仍 HTTP 200，用 `code` 区分（部分 401/403 用 HTTP 状态）
- 分页 `data`：`{ list, total, page, pageSize }`
- 查询参数：`page`（从 1）、`pageSize`（默认 20，**最大 100**）

## 路径命名

- 资源复数、kebab-case：`/departments`、`/onboarding/applications`
- 动作子路径：`POST /approvals/tasks/{id}/action`
- 员工自助 API 统一 `/profile/*`（与页面路由 `/portal/*` 分离）
- 禁止新增 `/api/` 无版本前缀；禁止 `/workflow/*` 等已废弃路径（见系分 §2.3.2）

## HTTP 与业务码

| HTTP | 场景 |
|------|------|
| 200 | 成功或业务失败（看 code） |
| 401 | 未登录 / Token 过期 → `20001` |
| 403 | 无权限 → `20002` / `20003` |
| 404 | 资源不存在 |
| 409 | 冲突（重复提交、乐观锁） |
| 422 | 业务拒绝（部门超 5 层、补卡超限等） |
| 500 | 未捕获异常 → `90001` |

常用业务码见 [error-codes.md](error-codes.md)。

## 新增接口 Checklist

- [ ] 路径已在附录 H 或经团队确认后**同步更新附录 H + openapi.yaml**
- [ ] Controller 在对应 Maven 模块的 `module/*` 包下
- [ ] 返回 `Result<T>`（放在 `hrms-common`）
- [ ] 需数据权限的查询加 `@DataScope`
- [ ] 薪资相关 API 校验角色（**SYS_ADMIN 不可访问薪资全量**）
- [ ] Apifox 集合补充正常 + 403 + 422 分支

## OpenAPI

- 文件：`backend/openapi.yaml`
- 新接口先写契约再写实现（Mock 并行）
- 破坏性变更升 OpenAPI `info.version` major
