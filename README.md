# HRMini — 人力资源管理系统

基于 PRD 与前后端系分（v1.7 / v1.8）的 **Sprint 0 工程骨架**：Maven **多模块**分工、**单 JAR** 部署，不含业务实现代码。

## 四人分工

| 同学 | 后端模块 | 前端页面（主要） |
|------|----------|------------------|
| **你** | `hrms-common` `hrms-auth` `hrms-org` | `/login`、`/admin/org/*`、权限菜单 |
| **同学 B** | `hrms-employee`（含 portal API） | `/admin/employee/*`、`/portal/*` |
| **同学 C** | `hrms-workflow` | 入转调离页、`/admin/approval/*` |
| **同学 D** | `hrms-attendance` `hrms-payroll` | 考勤/请假/薪资页 |

详见 [backend/README.md](backend/README.md)。

## 环境地址

| 环境 | 前端 | 后端 API |
|------|------|----------|
| **本地开发** | http://localhost:8000 | http://localhost:8080/api/v1 |
| **联调服务器** | http://39.101.67.167 | http://39.101.67.167:8080/api/v1 |

| 仓库 | https://gitee.com/swing-king/hrmini |

## 目录结构

```
HRMini/
├── backend/                 # Maven 多模块，单 JAR 部署
│   ├── hrms-common/         # 公共 — 你
│   ├── hrms-auth/           # 认证 — 你
│   ├── hrms-org/            # 组织 — 你
│   ├── hrms-employee/       # 员工+门户 — B
│   ├── hrms-workflow/       # 流程+审批 — C
│   ├── hrms-attendance/     # 考勤 — D
│   ├── hrms-payroll/        # 薪资 — D
│   └── hrms-app/            # 启动模块
├── frontend/                # Umi Max，/admin/* + /portal/*
├── config/                  # Docker、Nginx、环境变量示例
├── docs/                    # 文档与 DDL 占位
├── scripts/                 # 开发脚本
├── HRMS-Backend-System-Design.md   # v1.7
└── HRMS-Frontend-System-Design.md  # v1.8
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17、Spring Boot 3.2、MyBatis-Plus、MySQL 8、Redis 7、RabbitMQ、Flyway |
| 前端 | React 18、TypeScript、Umi Max 4、Ant Design 5、Zustand |
| 仓库 | Gitee |

## 快速开始

### 1. 基础设施

```powershell
docker compose -f config/docker/docker-compose.yml up -d
```

### 2. 后端（IDEA）

1. **File → Open** → `backend/pom.xml`
2. JDK 17，运行 `com.company.hrms.HrmsApplication`（模块 `hrms-app`）

3. 本地 API：`http://localhost:8080/api/v1`（Sprint 1 起实现业务接口）

### 3. 前端

```powershell
cd frontend
npm install
npm run dev
```

访问 http://localhost:8000 ，路由：`/login`、`/admin/workbench`、`/portal/profile`（占位页）。

### 4. 服务器联调配置

- 后端：复制 `config/env/application-dev-server.yml.example` → `application-dev-server.yml`
- 前端连远程 API：`API_PROXY_TARGET=http://39.101.67.167:8080 npm run dev`
- Nginx 示例：`config/nginx/nginx.conf.example`

### 5. Gitee

```powershell
.\scripts\gitee\setup-remote.ps1 -RemoteUrl "https://gitee.com/swing-king/hrmini.git"
.\scripts\gitee\push.ps1
```

## 后端包结构

各模块内包路径统一为 `com.company.hrms.module.*`，与系分一致。模块职责见 [backend/README.md](backend/README.md) 四人分工表。

## 文档

- [PRD](人资管理系统-PRD.md)
- [后端系分 v1.7](HRMS-Backend-System-Design.md)
- [前端系分 v1.8](HRMS-Frontend-System-Design.md)
- [开发计划](HRMini-Development-Plan.md)
