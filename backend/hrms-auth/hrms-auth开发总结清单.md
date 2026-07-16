# hrms-auth 开发总结清单

| 项 | 内容 |
|----|------|
| **文档名称** | hrms-auth 开发总结清单 |
| **版本** | v1.0.0 |
| **撰写人** | 李俊毅 |
| **日期** | 2026-07-16 |
| **模块** | `backend/hrms-auth` |
| **依据** | 《人资管理系统-PRD》§2 / §11.2、李俊毅后端系分、`HRMS-API-Contract.md`、总系分附录 H/K |

---

## 1. 模块定位

`hrms-auth` 负责认证鉴权与 RBAC 基础能力：

- 登录 / 登出 / Token 刷新 / 当前用户信息
- JWT Access Token + Refresh Token（Redis）
- 用户 / 角色 / 权限管理（系统管理端）
- 内部建号接口（供入职等模块调用）
- 与 `hrms-common` 配合：DataScope、FieldPermission、薪资路径双拦截

**包路径：** `com.company.hrms.module.auth`  
**启动方式：** 由 `hrms-app` 聚合启动，本模块无独立启动类。

---

## 2. 工程结构

```
hrms-auth/
├── pom.xml
├── hrms-auth开发总结清单.md          ← 本文档
└── src/main/java/com/company/hrms/module/auth/
    ├── config/          PasswordConfig（BCrypt cost=12）
    ├── constant/        AuthRedisKeys
    ├── controller/      Auth / System / Internal
    ├── dto/             请求响应对象
    ├── entity/          SysUser / SysRole / SysPermission / LoginLog
    ├── filter/          JwtAuthFilter
    ├── mapper/          MyBatis-Plus Mapper
    ├── service/         接口
    └── service/serviceImpl/  实现类
```

**依赖：** `hrms-common` + `spring-security-crypto`（仅 BCrypt，未引入完整 Security 过滤器链）。

---

## 3. 已实现功能清单

### 3.1 认证接口（`/api/v1/auth/*`）

| 方法 | 路径 | 状态 | 说明 |
|------|------|------|------|
| POST | `/auth/login` | ✅ | 签发 Access(2h) + Refresh(7d)；失败锁定；写 login_log |
| POST | `/auth/logout` | ✅ | Token 黑名单；清除 Refresh / last-active |
| POST | `/auth/refresh` | ✅ | Refresh 轮换；旧 Refresh 失效 |
| GET | `/auth/profile` | ✅ | roles / permissions / dataScope / mustChangePassword |
| PUT | `/auth/password` | ✅ | 强度校验；不可与旧密码相同；拉黑当前 Access Token |
| PUT | `/auth/mobile` | ✅ | **仅首次绑定**；已是手机号则拒绝（须走审批） |
| DELETE | `/auth/mobile` | ✅ | 业务拒绝，引导走手机号变更审批 |
| POST | `/auth/verify` | ✅ | 工资条二次验证；Redis TTL 30min |

### 3.2 系统管理（`/api/v1/system/*`，需 SYS_ADMIN）

| 方法 | 路径 | 状态 |
|------|------|------|
| GET/POST/PUT | `/system/users`、`/system/users/{id}` | ✅ |
| GET | `/system/roles` | ✅ |
| PUT | `/system/roles/{id}/permissions` | ✅ |
| GET | `/system/permissions` | ✅ |
| GET | `/system/login-logs` | ✅ |
| GET | `/system/operation-logs` | ❌ 未做 |
| POST | `/system/backup` | ❌ 未做 |
| GET | `/workbench/summary` | ❌ 未做（依赖多模块数据） |

### 3.3 内部接口（`/api/v1/internal/*`）

| 方法 | 路径 | 状态 | 鉴权 |
|------|------|------|------|
| POST | `/internal/users` | ✅ | 请求头 `X-Internal-Token` |
| PUT | `/internal/users/{id}/status` | ✅ | 同上 |
| PUT | `/internal/users/{id}/username` | ✅ | 同上 |

### 3.4 安全策略（对照 PRD §11.2）

| 策略 | 状态 | 实现要点 |
|------|------|----------|
| BCrypt 密码存储 | ✅ | cost=12 |
| 密码 ≥8 位且含大小写+数字 | ✅ | |
| 90 天强制更换 | ✅ | profile / login 返回 mustChangePassword |
| 首次登录强制改密 | ✅ | 建号时 password_changed_at≈created_at |
| 失败 ≥5 次锁定 15 分钟 | ✅ | Redis `hrms:login:fail:{username}` |
| 30 分钟无操作登出 | ✅ | Redis `hrms:user:last-active:{userId}` |
| SYS_ADMIN 不可见薪资全量 | ✅ | JwtAuthFilter 路径双拦截（见下） |

### 3.5 薪资路径双拦截（JwtAuthFilter）

| 路径类型 | 规则 |
|----------|------|
| `/api/v1/payroll/**`、`/api/v1/employees/*/salary/**` | **仅** `HR_STAFF` / `FINANCE` |
| `/api/v1/profile/payslips/**` | **禁止** `SYS_ADMIN`；其余角色可进（本人范围由业务层再收） |

> 普通员工访问管理端 `/payroll/**` 返回 403（已修复早期仅拦 SYS_ADMIN 的漏洞）。

### 3.6 与 common 协同（本阶段已具备）

| 能力 | 位置 | 说明 |
|------|------|------|
| DataScope | Aspect + Interceptor + SqlBuilder | 业务方法标 `@DataScope` 后生效 |
| FieldPermission | `FieldPermissionFilter.filter` | **须显式调用**，注解不自动生效 |
| JWT | `JwtTokenProvider` | permissions 不进 Token，由 Filter 加载 |

---

## 4. 种子数据与联调账号

**Flyway：** `V2__seed_auth_admin.sql`（位于 `hrms-app` 迁移目录）

| 项 | 值 |
|----|-----|
| 管理员手机号 | `13800000000` |
| 初始密码 | `Admin@12345` |
| 角色 | `SYS_ADMIN` |
| 权限种子 | 工作台/组织/员工/系统及部分 API 码（考勤薪资审批码待后续补） |

**内部调用示例：**

```http
POST /api/v1/internal/users
X-Internal-Token: hrms-dev-internal-token-change-me
Content-Type: application/json

{ "username": "13900000001", "employeeId": 1, "roleCodes": ["EMPLOYEE"] }
```

**生产注意：**

- `hrms.sms.dev-enabled=false`
- 用环境变量覆盖 `HRMS_INTERNAL_TOKEN`、JWT secret、AES key

---

## 5. Redis Key 约定

| Key | 用途 | TTL |
|-----|------|-----|
| `hrms:login:fail:{username}` | 登录失败计数 | 锁定窗口 900s |
| `hrms:token:blacklist:{jti}` | Access 黑名单 | Access 剩余有效期 |
| `hrms:refresh:{userId}` | Refresh 白名单 | 7d |
| `hrms:refresh:token:{token}` | Refresh → userId | 7d |
| `hrms:user:last-active:{userId}` | 无操作超时 | 30min |
| `hrms:user:perms:{userId}` | 权限码缓存 | 2h |
| `hrms:payslip:verified:{userId}` | 工资条二次验证 | 30min |

---

## 6. 未实现 / 延期项

| 项 | 原因 |
|----|------|
| 操作审计日志 API + `@OperationLog` | 排 Sprint 后期；表已有 |
| 数据备份 `/system/backup` | 运维能力，后续补 |
| 工作台汇总 `/workbench/summary` | 依赖员工/审批/考勤数据 |
| 完整权限码（考勤/薪资/审批菜单） | 各业务模块就绪后增量种子 |
| 真实短信网关 | 当前仅开发码；生产关闭 |
| `/profile/security/*` 规范路径 | 门户侧由 employee/portal 承接或做别名 |

---

## 7. 需其他模块配合

| 模块 | 配合事项 |
|------|----------|
| **hrms-employee** | 花名册/详情加 `@DataScope`；敏感 VO 调 `FieldPermissionFilter`；门户 `/profile/*` |
| **hrms-org** | 部门 `path`/`deleted` 正确，保证 `DEPT_TREE` SQL 可用 |
| **hrms-payroll** | 校验 `payslip:verified`；管理端 API 落在 `/payroll/**` |
| **hrms-workflow** | 入职确认调 `/internal/users`；离职调 status 禁用 |
| **前端** | 菜单按 permissions 过滤；401 刷新；idle 检测；SYS_ADMIN 无薪资菜单 |
| **运维** | 生产密钥与短信开关 |

---

## 8. 已知待改进（技术债）

### 高优先级

1. 统一角色校验注解（避免各处手写 `hasRole`）
2. auth 直查 `employee` 表取 `deptId` → 改为跨模块 Service，避免表耦合
3. `@DataScope` 方法内所有 Mapper 均被追加 WHERE → 约定用法或收窄拦截范围
4. 补齐操作审计 / 备份 / 工作台

### 中优先级

5. 错误码 `40901`/`42201` 同步进 API 契约，或改回附录 K 风格  
6. 扩展权限种子；FieldPermission 少用 `id` 字段回退猜 employeeId  
7. DataScope + 分页 BoundSql 联调验证 count  
8. `JwtAuthFilter` 依赖 `AuthServiceImpl` 具体类 → 改为接口  

### 低优先级

9. yml 中 `internal`/`sms` 双写收敛  
10. 多角色用户主 `data_scope`（`FIELD` 排序）边界用例测试  

---

## 9. 与 PRD 关系说明

- **符合 PRD：** 五角色、密码策略、锁定、无操作超时、SYS_ADMIN 薪资不可见、手机号不可随意直改。  
- **未超出 PRD 的新业务：** 无额外业务域；Refresh Token、Internal Token、开发短信码属系分/工程落地。  
- **本模块单独能保证：** 可登录、可拦路径、可配用户角色。  
- **产品级权限完整：** 依赖员工/组织/薪资/审批/前端共同落地。

---

## 10. 联调自检（建议）

1. `POST /auth/login`（`13800000000` / `Admin@12345`）→ 拿到 Token  
2. `GET /auth/profile` → 含 `SYS_ADMIN`、`NONE_PAYROLL`  
3. EMPLOYEE Token 调 `GET /payroll/**` → **403**  
4. EMPLOYEE Token 调 `GET /profile/payslips` → 不因 Filter 被拒（业务可空）  
5. 无 `X-Internal-Token` 调 `/internal/users` → **403**  
6. 错误密码 5 次 → 锁定 422  

---

## 11. 版本记录

| 版本 | 日期 | 说明 | 作者 |
|------|------|------|------|
| v1.0.0 | 2026-07-16 | 首版：认证主链路、系统用户角色、薪资路径拦截、总结与待办 | 李俊毅 |

---

*本文档随 `hrms-auth` 迭代更新；重大变更请升版本号并补充版本记录。*
