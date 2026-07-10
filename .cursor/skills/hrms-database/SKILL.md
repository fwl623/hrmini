---
name: hrms-database
description: >-
  HRMS 数据库规范：Flyway 迁移、表命名、公共字段、索引、敏感字段存储。
  在编写 DDL、entity、Mapper SQL 时使用。
---

# HRMS 数据库规范

权威来源：系分 **附录 B/F**（表清单）、**附录 J.3**（字段字典）。

## 迁移

- 脚本目录：`docs/db/`（团队共享）或 `hrms-app/src/main/resources/db/migration/`
- 命名：`V{version}__{description}.sql`（Flyway）
- **禁止**手工改生产库；所有变更走 SQL 文件 + Git

## 表命名

- **无前缀**：`department`、`employee`、`payroll_batch`
- 弃用：`org_*`、`emp_*`、`wf_*`、`att_*`、`pay_*`（旧 B 文档）
- **不建**闭包表 `org_department_closure`；用 `department.path`

## 公共字段（建议每张业务表）

```sql
id BIGINT PRIMARY KEY AUTO_INCREMENT,
create_time DATETIME NOT NULL,
update_time DATETIME NOT NULL,
create_by BIGINT,
update_by BIGINT,
is_deleted TINYINT DEFAULT 0,
version INT DEFAULT 0  -- 乐观锁
```

## 核心表关系

```
department → position → employee
employee → employee_salary_profile / employee_contract
approval_process_def → approval_instance → approval_task
payroll_scheme → payroll_batch → payroll_detail
```

## 敏感字段

| 字段 | 存储 |
|------|------|
| 身份证号 | AES-256-GCM + SHA-256 哈希列检索 |
| 银行卡 | AES-256-GCM，后四位明文展示 |
| 手机号 | 明文（登录账号），唯一索引 |

## 索引要点

- `department(path)` — 子树查询
- `employee(employee_id)` — 所有 FK 指向此列
- 列表查询按系分 §A.4.3 补组合索引

## 枚举存储

- API JSON 与 DB 可能不同（如入职状态，见附录 I）
- Service 层做 API↔DB 映射，**不在前端硬编码 DB 值**

## DDL Checklist

- [ ] 表名在附录 F 映射中登记
- [ ] FK 引用 `employee_id`
- [ ] 部门 `level` ≤ 5 约束在 Service + DB CHECK（如有）
- [ ] 种子数据：5 角色、管理员账号、示例部门（`docs/db/seed.sql`）
