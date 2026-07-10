# HRMS Backend

**模块化单体**：Maven 多模块分工开发，**打包成一个 JAR** 部署（系分「单模块」指运行时，不是说不分 Maven 模块）。

## 四人分工 ↔ Maven 模块

| 同学 | 负责域 | Maven 模块 | 包路径 |
|------|--------|------------|--------|
| **你** | 权限 + 组织 + 公共 | `hrms-common` `hrms-auth` `hrms-org` | `common/*` `module/auth` `module/org` |
| **同学 B** | 员工 + 个人中心 | `hrms-employee` | `module/employee` `module/portal` |
| **同学 C** | 入转调离 + 审批 | `hrms-workflow` | `module/onboarding` `module/lifecycle` `module/approval` |
| **同学 D** | 考勤 + 薪资 | `hrms-attendance` `hrms-payroll` | `module/attendance` `module/leave` `module/overtime` `module/payroll` |
| 全员 | 启动聚合 | `hrms-app` | 仅启动类与 `application*.yml` |

## 结构

```
backend/
├── openapi.yaml
├── hrms-common/
├── hrms-auth/          ← 你
├── hrms-org/           ← 你
├── hrms-employee/      ← B
├── hrms-workflow/      ← C
├── hrms-attendance/    ← D
├── hrms-payroll/       ← D
└── hrms-app/           ← 启动（不要写业务代码）
```

## 协作约定

1. **只改自己模块**，跨模块调用走 Service 接口，不直接改别人 Mapper
2. **`hrms-common` 变更**需你 review（Result、DataScope 等）
3. **依赖顺序**：common → auth → org → employee → workflow / attendance → payroll → app
4. 每人前后端全链路：后端写自己模块 API，前端写对应 `pages/admin/*` 或 `pages/portal/*`

## 运行

```bash
cd backend
mvn -pl hrms-app -am spring-boot:run
```

IDEA 打开 `backend/pom.xml`，运行 `com.company.hrms.HrmsApplication`。
