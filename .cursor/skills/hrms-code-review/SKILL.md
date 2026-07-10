---
name: hrms-code-review
description: >-
  HRMS 代码审查：安全合规、稳定性、业务规则、模块边界、避免冗余。
  在 review PR、审查 diff、合并前检查、用户要求 code review 时使用。
---

# HRMS 代码审查

审查目标：**安全合规** → **稳定可靠** → **业务正确** → **简洁无冗余**。

交叉引用：[hrms-business-rules](../hrms-business-rules/SKILL.md)、[hrms-api-convention](../hrms-api-convention/SKILL.md)。

## 审查流程

1. **范围**：确认改动属于提交者负责的 Maven 模块 / 前端目录
2. **契约**：API/字段是否与 openapi、系分附录 H 一致
3. **安全**：权限、敏感数据、注入、越权
4. **稳定**：事务、幂等、异常、并发
5. **冗余**：是否过度抽象、重复代码、超范围改动
6. **输出**：按下方格式给出结论

## 输出格式（必须）

```markdown
## 审查结论
- 总体：✅ 可合并 / ⚠️ 修改后合并 / ❌ 需重做
- 风险等级：低 / 中 / 高

## 问题清单
| 级别 | 位置 | 问题 | 建议 |
|------|------|------|------|
| 🔴 必须修 | file:line | ... | ... |
| 🟡 建议 | ... | ... | ... |
| 🟢 可选 | ... | ... | ... |

## 通过项（简要）
- ...
```

级别定义：
- 🔴 **必须修**：安全漏洞、越权、数据丢失、与 PRD 冲突
- 🟡 **建议**：稳定性隐患、可维护性、缺校验
- 🟢 **可选**：命名、小优化

## 1. 安全合规（PRD §11.2）

| 检查项 | 通过标准 |
|--------|----------|
| 鉴权 | 除 `/auth/login` 外均需 JWT；401/403 正确 |
| RBAC | 角色码用 `SYS_ADMIN`/`HR_STAFF` 等系分枚举，非硬编码魔法字符串散落 |
| 数据权限 | 列表/详情有 `@DataScope`；主管不能看其他部门 |
| **薪资隔离** | `SYS_ADMIN` 不能访问账套/批次/他人工资条（菜单+API 双检） |
| 字段权限 | 身份证/银行卡/薪资按角色裁剪；前端 `FieldGuard` |
| 敏感存储 | 身份证/银行卡 AES-256，禁止明文落库 |
| 审计 | 薪资查看、敏感字段解密、批量导出写 `operation_log` |
| SQL | MyBatis 参数绑定，**禁止** `${}` 拼接用户输入 |
| 密码 | BCrypt；8 位+大小写+数字；不在日志/响应泄露 |
| 工资条 | 详情须二次验证（`60004`）；Redis TTL 30min |
| 前端 | Token 不 URL 传递；无敏感信息 console.log |

## 2. 稳定性

| 检查项 | 通过标准 |
|--------|----------|
| 事务 | 多表写操作 `@Transactional`；跨模块调用的边界清晰 |
| 幂等 | 审批 action、打卡、算薪触发有幂等键或状态校验（`60001`） |
| 乐观锁 | 并发更新用 `version` 字段 |
| 错误处理 | 业务拒绝用约定 `code`（附录 K），不吞异常 |
| 空值 | NPE 防护：Optional/判空，尤其 FK、`employee_id` |
| 分页 | `pageSize` 上限 100；禁止一次拉全表 |
| 外部依赖 | Redis/MySQL 失败有明确错误，不 silent fail |
| 定时任务 | 离职生效、日汇总等 Job 可重复执行或幂等 |

## 3. 业务规则（必查三条）

见 [hrms-business-rules](../hrms-business-rules/SKILL.md)：

- [ ] 手机号未绕过 `MOBILE_CHANGE` 直改
- [ ] 离职未跳过员工申请
- [ ] 入职未用 `POST /employees` 直建
- [ ] FK 用 `employee_id` 非 `emp_no`
- [ ] 算薪前考勤月已锁定（若触及薪资模块）

## 4. 模块边界与契约

- [ ] 未修改他人 Maven 模块（除非 PR 说明联调必要）
- [ ] 未跨模块直接注入他人 Mapper
- [ ] 新 API 已更新 `openapi.yaml` + 附录 H（或 Issue 跟踪）
- [ ] 路径前缀 `/api/v1`；响应含 `traceId`
- [ ] 前端页面路由与 API 未混淆（`/portal/*` vs `/profile/*`）

## 5. 冗余与范围（微项目原则）

**拒绝合并若出现：**

- 与本次需求无关的重构、格式化整库
- 重复工具类（先搜 `hrms-common` 是否已有）
- 过度抽象：单处使用的 Interface/Factory/策略模式
- 重复 DTO 字段映射（应用 MapStruct 或单处 converter，但不为 1 个接口建框架）
- 注释解释显而易见的代码
- 引入 PRD 未要求的新依赖（Flowable、Gateway 等）

**允许：**

- 本模块内清晰分层 controller/service/mapper
- 复用系分已有组件名（`FieldGuard`、`ApprovalTimeline`）

## 6. 按层快速扫描

### 后端 Java

```
Controller → 只做校验+调用，无业务逻辑堆叠
Service    → 事务边界、业务规则
Mapper     → 无业务判断；XML 参数 #{}
Entity     → 与表一致；敏感字段不 toString 泄露
```

### 前端 TSX

```
pages/     → 组装 UI，逻辑下沉 hooks/services
services/  → 纯 API 调用，无 DOM 操作
access.ts  → 权限与菜单一致
stores/    → 仅全局态，列表数据优先 TanStack Query
```

## 7. 合并门禁

**全部满足才可 ✅：**

- 无 🔴 项
- 触及权限/薪资/审批的改动，自测越权用例
- `mvn compile` / `npm run lint` 通过（若 CI 有则看 CI）
- 数据库变更有 Flyway 脚本

详细清单见 [review-checklist.md](review-checklist.md)。
