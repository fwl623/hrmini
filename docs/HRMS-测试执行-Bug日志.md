# HRMS 测试执行 Bug 日志

> **执行方式**：Playwright CLI 可视化浏览器（`playwright-cli open --headed --browser=chrome`）  
> **依据**：`docs/HRMS-测试用例全集.md`（165 条）  
> **执行日**：2026-07-19  
> **环境**：`localhost:8000` / `localhost:8080`  
> **密码**：`Admin@12345`  
> **矩阵产物**：`docs/tc-official-matrix.json` / `docs/tc-results.json`

## 最终矩阵（官方 165）

| 指标 | 数量 |
|------|------|
| 官方用例总数 | **165** |
| 已有执行结论（含 SKIPPED） | **165**（NOT_RUN = 0） |
| PASS | **114** |
| FAIL | **14** |
| BLOCKED | **3** |
| SKIPPED | **34** |

说明：SKIPPED 多为需时钟/Job/SMS/多审批人夹具的长链路（如 30min 空闲、90 天改密、入职 confirm 全链路、离职 Job）。BLOCKED 多为同日打卡幂等 `40005` 挡住迟到边界复测。

脚本批次：`pw-smoke-admin-routes` / `pw-batch2-hr` / `pw-batch3-roles` / `pw-batch-e2e-core` / `pw-batch-remaining` / `pw-batch-fill165` / `pw-batch-repair-false`。

---

## Bug 列表（Open）

### BUG-001 【P0】SYS_ADMIN 打开请假列表返回 90001

| 项 | 内容 |
|----|------|
| 用例 | TC-LEAVE-008 / TC-UI-003 |
| 实际 | `GET /leaves/applications` → **500/90001**；HR 正常 |
| 状态 | **Open** |

### BUG-002 【P0】SYS_ADMIN 打开加班列表返回 90001

| 项 | 内容 |
|----|------|
| 用例 | TC-OT-006 / TC-UI-003 |
| 实际 | `GET /overtime/applications` → **500/90001**；HR 正常 |
| 状态 | **Open** |

### BUG-003 【P1】SYS_ADMIN 可直链进入薪资页壳

| 项 | 内容 |
|----|------|
| 用例 | TC-PAY-SEC-001 / TC-SEC-04/05 |
| 实际 | 菜单隐藏 OK；直链页壳可开；API **403** OK |
| 状态 | **Open** |

### BUG-004 【P1】HR 可直链打开「用户管理」页壳

| 项 | 内容 |
|----|------|
| 用例 | TC-SEC-18 |
| 实际 | 页壳可开；API **403/20002** OK |
| 状态 | **Open** |

### BUG-005 【P1】门户侧栏缺少「我的工资条」

| 项 | 内容 |
|----|------|
| 用例 | TC-UI-005 |
| 实际 | 直链 `/portal/payslips` 可用；侧栏无入口 |
| 状态 | **Open** |

### BUG-006 【P3】antd `destroyOnClose` 废弃警告

| 状态 | **Open** |

### BUG-007 【P2】委托冲突弹窗体验

| 项 | 内容 |
|----|------|
| 用例 | TC-DLG-002 |
| 实际 | API **60003** 正确；全局 errorHandler 吞错导致误关弹窗/可能误报成功 |
| 修复 | 创建委托 `skipErrorHandler`；60003 专用提示；已有 ACTIVE 时拦截新建 |
| 状态 | **Fixed** |

### BUG-008 【P0】EMPLOYEE 可成功创建调岗（缺权限拦截）

| 项 | 内容 |
|----|------|
| 用例 | TC-TRF-004 |
| 期望 | 非 HR → **403/20002** |
| 实际 | `POST /transfers` 以员工 Token 返回 **code=0** 成功创建 |
| 根因 | `TransferService` 创建/列表/详情无角色校验 |
| 修复 | `requireHrOrAdmin()`（仅 `HR_STAFF`/`SYS_ADMIN`），业务码 **20002** |
| 状态 | **Fixed** |

### BUG-009 【P1】白名单外字段 PUT 返回 90001 而非 20003

| 项 | 内容 |
|----|------|
| 用例 | TC-EMP-004 / TC-SEC-10 |
| 期望 | `departmentId`/`mobile` → **20003** |
| 实际 | 部分场景 **90001** |
| 根因 | ① `EmployeeUpdateDTO` 无流程字段，Jackson 静默丢弃 → 无法拦截；② 仅改流程字段时 `updatePersonal` 空 insert 触发 `id_number_enc NOT NULL` → 90001 |
| 修复 | DTO 显式声明流程字段 + `rejectFlowFields`；无个人字段变更时跳过 personal 写库 |
| 状态 | **Fixed** |

### BUG-010 【P1】敏感字段 / 补卡配额 / 个人统计 90001

| 项 | 内容 |
|----|------|
| 用例 | TC-EMP-008 / TC-PUNCH-005 / TC-STAT-001 / TC-STAT-003 |
| 实际 | 相关 API 返回 **90001** |
| 根因 | 缺 Header/必填参数未映射 → 落到全局 90001；统计只认 `period` 不认 `month`；补卡配额 Redis 异常直炸；敏感字段占位密文解密失败抛 SYSTEM_ERROR |
| 修复 | 全局补 `Missing*Exception→10001`；统计兼容 `month` + 部门主管默认本部门；配额 Redis 回落 DB；敏感 Header 可选 + 解密失败改业务码；种子写入真实 AES 密文 |
| 状态 | **Fixed** |

### BUG-011 【P2】病假>1 天无证明仍可提交

| 项 | 内容 |
|----|------|
| 用例 | TC-LEAVE-006 |
| 期望 | 拒绝 |
| 实际 | `POST /leaves/applications` sick 2 天 **code=0** |
| 状态 | **Open** |

### BUG-012 【P1】考勤组编辑 / 用户启停 / ≥4h 加班 等 90001

| 项 | 内容 |
|----|------|
| 用例 | TC-ATT-GRP-005 / TC-SYS-USER-003 / TC-OT-003 |
| 实际 | 多处业务写操作触发 **90001**（需对照服务端日志） |
| 状态 | **Open** |

---

## SKIPPED / BLOCKED 摘要（非缺陷）

| 类型 | 代表用例 | 原因 |
|------|----------|------|
| SKIPPED | AUTH-003/004/006，ONB-003/004/006，E2E-02~05，MOB-002，RES-003/004，SEC-21 | 需新用户/SMS/Job/长时间夹具 |
| BLOCKED | PUNCH 迟到边界复测 | 同日幂等 **40005** |
| BLOCKED | APPR-003 | 当时无待办可测拒绝意见 |

---

## 已通过高优先级摘录

登录/登出黑名单、记住用户名、部门树 CRUD 冒烟、职位级联、花名册 DataScope、入职草稿提交撤回、手机号冲突 30007、打卡 NORMAL、补卡月锁 40001、调岗同部门 30004、委托 60003、工资条未验证 60004、SYS_ADMIN 薪资 API 403、菜单隐藏薪资、Portal 各页冒烟、审批 Tab/统计等。

---

## 建议修复优先级

1. **P0**：BUG-001/002/008  
2. **P1**：BUG-003/004/005/009/010/012  
3. **P2**：BUG-007/011  
4. **P3**：BUG-006  

---

## 证据 / 产物

| 文件 | 说明 |
|------|------|
| `docs/tc-official-matrix.json` | 官方 165 结论矩阵 |
| `docs/tc-results.json` | 原始执行结果（含别名 ID） |
| `docs/evidence-leave-admin-error.png` | Admin 请假 90001 |
| `docs/evidence-delegation-conflict.png` | 委托 60003 |
| `docs/evidence-portal-leave.png` | 门户侧栏 |
| `scripts/pw-batch-fill165.js` | 补齐 165 批次 |
