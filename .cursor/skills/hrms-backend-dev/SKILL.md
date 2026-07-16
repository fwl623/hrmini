---
name: hrms-backend-dev
description: >-
  HRMS 后端开发规范：JDK 17、Maven 多模块、包结构、分层、模块边界与四人分工。
  在编写 Spring Boot、Controller、Service、Mapper、Flyway 时使用。
---

# HRMS 后端开发规范

## 技术栈（硬性）

| 项 | 版本/选型 |
|----|-----------|
| JDK | **17**（Spring Boot 3 必须） |
| Spring Boot | 3.2.x |
| ORM | MyBatis-Plus + XML（复杂 SQL） |
| DB | MySQL 8、Flyway 迁移 |
| 缓存 | Redis 7 |
| MQ | RabbitMQ 3.12（Phase 4+ 可接入） |

## 工程结构

```
backend/
├── openapi.yaml
├── hrms-common/      # Result、异常、DataScope、工具
├── hrms-auth/        # 认证 JWT、RBAC
├── hrms-org/         # 部门、职位
├── hrms-employee/    # 员工、portal API
├── hrms-workflow/    # 入转调离、审批
├── hrms-attendance/  # 考勤、请假、加班
├── hrms-payroll/     # 薪资
└── hrms-app/         # 仅启动类 + application*.yml
```

包路径：`com.company.hrms.module.{domain}`；引擎类在 `com.company.hrms.approval`、`payroll`、`job`。

## 四人模块归属

| 负责人 | Maven 模块 |
|--------|------------|
| 你 | common、auth、org |
| 同学 B | employee（含 portal） |
| 同学 C | workflow |
| 同学 D | attendance、payroll |

**只改自己模块**；跨模块通过 Service 接口调用，禁止直接引用他人 Mapper。

## 分层约定（每个 module 包内）

```
controller/   ← REST，仅参数校验 + 调用 Service
service/      ← 业务逻辑、事务边界
mapper/       ← MyBatis-Plus Mapper
entity/       ← 与表对应
dto/          ← 请求/响应 VO
```

- Controller 路径遵循 [hrms-api-convention](../hrms-api-convention/SKILL.md)
- 表名无前缀（`department`、`employee`，非 `org_department`）
- FK 只用 `employee_id`，**不引用 emp_no**
- 部门树用 `parent_id` + `path`，**不用闭包表**

## 权限三层

1. **RBAC**：角色 `SYS_ADMIN` / `HR_STAFF` / `DEPT_MANAGER` / `FINANCE` / `EMPLOYEE`
2. **DataScope**：`ALL` / `DEPT_TREE` / `SELF` / `PAYROLL` / `NONE_PAYROLL`
3. **FieldPermission**：敏感字段裁剪（身份证、银行卡等）

`SYS_ADMIN` 对薪资 API **双拦截**（`DS_NONE_PAYROLL`）。

## 关键架构决策（AD）

- AD-01：自然月 + 考勤锁定后再算薪
- AD-03：SpEL 表驱动审批，**不用 Flowable**
- AD-05：`employee_id` 永不复用；`emp_no` 同年同部门可复用
- AD-08：分段计薪 `ProratedPayrollService`

## 编码原则

- 最小改动；匹配现有模块边界
- `hrms-common` 公共类变更需组长 review
- 敏感字段 AES-256；查看记 `operation_log`
- 新表 DDL 放 `docs/db/` 或 `hrms-app/src/main/resources/db/migration/`

## 工程约定

- **Mapper**：直接继承 `BaseMapper<Entity>`，默认自带 CRUD；复杂 SQL 用 `@Select` 注解或 XML，避免手写重复的 insert/update/delete
- **日志**：Service 类加 `@Slf4j` 注解，直接用 `log.info()` / `log.warn()` / `log.error()` 记录关键操作和异常
- **Lombok**：优先使用 `@Data`（实体/DTO）、`@RequiredArgsConstructor`（构造器注入）、`@Slf4j`（日志），减少模板代码
- **DTO/VO**：放在 `dto/` 包，Controller 入参用 DTO，出参用 VO，与 Entity 解耦
