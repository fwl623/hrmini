# HRMS Frontend

React 18 + Umi Max + Ant Design 5 骨架（系分 v1.8）。

## 路由（占位，Sprint 1 起实现页面）

| 路径 | 布局 | 说明 |
|------|------|------|
| `/login` | 无 | 登录 |
| `/admin/*` | AdminLayout | 管理后台 |
| `/portal/*` | PortalLayout | 员工门户 |

## 开发

```powershell
npm install
npm run dev
```

- 本地：http://localhost:8000
- API 代理：默认 `http://localhost:8080`
- 连远程后端：`$env:API_PROXY_TARGET="http://39.101.67.167:8080"; npm run dev`

目录约定见 [HRMS-Frontend-System-Design.md](../HRMS-Frontend-System-Design.md) §2.1.3。
