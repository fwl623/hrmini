---
name: hrms-frontend-dev
description: >-
  HRMS 前端开发规范：React 18、Umi Max、双布局、路由、access 权限、services 层。
  在新增页面、组件、API 调用、菜单权限时使用。
---

# HRMS 前端开发规范

权威来源：`HRMS-Frontend-System-Design.md` v1.8。

## 技术栈（硬性）

| 类别 | 选型 | 禁止 |
|------|------|------|
| 框架 | React 18 + TypeScript | Vue |
| 应用 | Umi Max 4 | 单独装 react-router-dom |
| UI | Ant Design 5 + ProComponents | — |
| 图表 | @ant-design/charts | moment（用 dayjs） |
| 请求 | Umi request 主 | — |
| 状态 | Zustand（全局）+ TanStack Query（服务端，推荐） | — |

## 目录结构

```
frontend/src/
├── access.ts           # RBAC
├── app.tsx             # request 拦截、401 跳转
├── constants/          # 角色、枚举、状态色
├── services/           # API 封装（按模块分文件）
├── hooks/              # TanStack Query hooks
├── layouts/
│   ├── AdminLayout.tsx # /admin/*
│   └── PortalLayout.tsx# /portal/*
└── pages/
    ├── login/
    ├── admin/          # 管理后台
    └── portal/         # 员工门户
```

## 路由 vs API（勿混淆）

| 类型 | 前缀 | 示例 |
|------|------|------|
| **页面路由** | `/admin/*` `/portal/*` | `/admin/org/departments` |
| **REST API** | `/api/v1/*` | `/departments/tree` |

员工自助：**页面**在 `/portal/profile`；**接口**用 `/profile/me`、`/profile/payslips/*`。

## 双布局与登录跳转

| 角色 | 布局 | 登录后跳转 |
|------|------|------------|
| EMPLOYEE | PortalLayout | `/portal/profile` |
| 其他 | AdminLayout | `/admin/workbench` |

Token 存 localStorage；401 全局拦截回 `/login`。

## access 权限码（示例）

与后端 `permissions` 对齐，在 `access.ts` 暴露：

- `canHr`、`canAdmin`、`canFinance`、`canManagePayroll`（**SYS_ADMIN 无 payroll 菜单**）
- `canPunch`、`canPortal`

菜单 `access` 字段与 PRD §2.2 矩阵一致。

## services 层约定

```typescript
// services/employee.ts
import { request } from '@umijs/max';
import { API_BASE } from '@/constants/roles';

export async function listEmployees(params: EmployeeQuery) {
  return request(`${API_BASE}/employees`, { method: 'GET', params });
}
```

- 所有路径以 `/api/v1` 为前缀（`constants/roles.ts` 中 `API_BASE`）
- 响应解包：检查 `code === 0`，否则 Message.error
- 新页面先在系分 §2.2 查「所需 API」，再写 service

## 页面开发 Checklist

- [ ] 路径与系分 §2.2 一致
- [ ] 表单字段表与系分字段对齐
- [ ] 敏感字段用 `FieldGuard` 组件（Part II §A.3）
- [ ] 状态 Tag 颜色用 `constants` 中 PRD §12.1 token
- [ ] 列表分页 pageSize ≤ 100

## 四人前端分工（主要页面）

| 负责人 | 目录 |
|--------|------|
| 你 | `login`、`admin/org/*`、Layout、access |
| 同学 B | `admin/employee/*`、`portal/*` |
| 同学 C | `admin/onboarding/*`、lifecycle、`admin/approval/*` |
| 同学 D | `admin/attendance/*`、`admin/payroll/*` |
