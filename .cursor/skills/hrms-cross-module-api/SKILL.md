---
name: hrms-cross-module-api
description: >-
  HRMS 跨模块 Service 接口契约（Java 直接调用）：员工服务（同学 B）、审批引擎（同学 C）、MQ 事件消息体。
  在 attendnace/payroll 模块调用 employee/workflow 模块时使用；也用于 Mock 模拟和联调。
---

# HRMS 跨模块接口契约

权威来源：`HRMS-Backend-System-Design.md` §2.2、`考勤请假-后端系分.md` §1.1。

## 模块依赖关系

```
hrms-common  ←  hrms-auth  ←  hrms-org
                                    ↓
hrms-employee  ←  hrms-workflow(审批引擎)
       ↓                   ↓
hrms-attendance(考勤)    (加依赖后可用)
hrms-payroll(薪资)
```

| 调用方 | 被调用方 | 方式 | 用途 |
|--------|---------|------|------|
| `hrms-attendance` | `hrms-employee` | 直接注入 Service（已有 Maven 依赖） | 查员工列表/详情 |
| `hrms-attendance` | `hrms-workflow` | **需加 Maven 依赖**，直接注入 Service | 创建/查询审批实例 |
| `hrms-workflow` → `hrms-attendance` | MQ `hrms.approval.notify` | 审批完成事件通知 | 请假/加班/补卡状态变更 |
| `hrms-workflow` → `hrms-attendance` | MQ `hrms.employee.event` | 离职联动移交 | 考勤移出 |

---

## 1. 员工服务接口（同学 B 提供）

### 1.1 Maven 依赖

`hrms-attendance` / `hrms-payroll` 的 `pom.xml` 已有：

```xml
<dependency>
    <groupId>com.company.hrms</groupId>
    <artifactId>hrms-employee</artifactId>
</dependency>
```

无需额外配置，直接 `@Autowired` / `@RequiredArgsConstructor` 注入 Service。

### 1.2 接口定义

包路径：`com.company.hrms.module.employee.service`

```java
public interface EmployeeService {

    /**
     * 批量查询员工基本信息（按 employeeId 列表）
     * 用于：打卡记录回显、考勤统计、月汇总
     */
    List<EmployeeBasicDTO> getByIds(Collection<Long> employeeIds);

    /**
     * 根据部门 ID 列表查询员工（含子部门）
     * 用于：考勤组物化 —— 考勤组选择部门适用范围时，调用此接口获取所有关联员工
     */
    List<EmployeeBasicDTO> getEmployeesByDepartmentIds(Collection<Long> departmentIds);

    /**
     * 根据岗位 ID 列表查询员工
     * 用于：考勤组物化 —— 考勤组选择岗位适用范围时
     */
    List<EmployeeBasicDTO> getEmployeesByPositionIds(Collection<Long> positionIds);

    /**
     * 按姓名/工号模糊搜索员工
     * 用于：请假页面选择员工（交接人、审批人选择等）
     */
    List<EmployeeBasicDTO> search(String keyword);

    /**
     * 查询员工详情（含入职日期等敏感信息）
     * 用于：年假计算（需 onboardDate 算工龄）
     */
    EmployeeDetailDTO getDetailById(Long employeeId);
}
```

### 1.3 DTO 定义

包路径：`com.company.hrms.module.employee.dto`

```java
@Data
public class EmployeeBasicDTO {
    private Long employeeId;
    private String employeeName;       // 姓名
    private String departmentName;     // 部门名称
    private String empNo;              // 工号（搜索接口必返）
}

@Data
public class EmployeeDetailDTO {
    private Long employeeId;
    private String employeeName;
    private String departmentName;
    private String empNo;
    private LocalDate onboardDate;     // 入职日期（年假计算用）
    private String employmentStatus;   // 在职状态
}
```

### 1.4 使用场景速查

| 调用方方法 | 调用员工服务方法 | 返回数据要求 |
|-----------|----------------|-------------|
| 考勤组物化（适用范围 → 员工列表） | `getEmployeesByDepartmentIds` / `getEmployeesByPositionIds` | employeeId, employeeName |
| 打卡记录/月汇总回显 | `getByIds` | employeeId, employeeName, departmentName |
| 请假选择交接人 | `search(keyword)` | employeeId, employeeName, departmentName, empNo |
| 年假计算 | `getDetailById` | employeeId, onboardDate |

---

## 2. 审批引擎接口（同学 C 提供）

### 2.1 Maven 依赖

`hrms-attendance` / `hrms-payroll` 需在 `pom.xml` **新增**：

```xml
<dependency>
    <groupId>com.company.hrms</groupId>
    <artifactId>hrms-workflow</artifactId>
</dependency>
```

### 2.2 接口定义

包路径：`com.company.hrms.approval`（引擎类所在包，见总系分 §2.2.12 项目结构）

```java
public interface ApprovalEngineService {

    /**
     * 创建审批实例
     *
     * @param request 审批创建请求
     * @return 审批实例 ID 与初始状态
     */
    CreateApprovalResult createInstance(CreateApprovalRequest request);

    /**
     * 查询审批实例当前状态
     *
     * @param instanceId 审批实例 ID
     * @return 实例状态 + 当前节点
     */
    ApprovalStatusDTO getInstanceStatus(Long instanceId);

    /**
     * 撤销审批实例（仅发起人且第一级节点可撤回）
     *
     * @param instanceId 审批实例 ID
     * @param operatorId 操作人 employeeId
     * @return 是否成功
     */
    boolean withdrawInstance(Long instanceId, Long operatorId);
}
```

### 2.3 DTO 定义

```java
@Data
public class CreateApprovalRequest {
    @NotNull
    private String processType;        // LEAVE / MAKEUP / OVERTIME / PAYROLL_BATCH 等（大写枚举）
    @NotNull
    private Long businessId;           // 业务记录主键（如 leave_application.id）
    @NotNull
    private Long applicantId;          // 发起人 employeeId
    @NotBlank
    private String title;              // 审批标题（如"张三 - 年假申请"）
    private String businessSummary;    // 业务摘要（如"年假 3 天，2026-07-14~2026-07-16"）
    private String businessUrl;        // 业务详情页前端路由（可选）
    private Map<String, Object> formData;  // SpEL 条件变量（如 leaveType, days, dailyTotalHours 等）
}

@Data
public class CreateApprovalResult {
    private Long instanceId;           // 审批实例 ID
    private String status;             // PENDING
}

@Data
public class ApprovalStatusDTO {
    private Long instanceId;
    private String status;             // PENDING / APPROVED / REJECTED / CANCELLED
    private String currentNodeLabel;   // 当前节点名称（如"直接上级审批"）
}
```

### 2.4 考勤模块需要传递的 SpEL formData 变量

| processType | formData 字段 | 说明 |
|-------------|--------------|------|
| `LEAVE` | `leaveType`, `days` | 请假类型 + 天数 → 路由审批链 |
| `MAKEUP` | — | 补卡仅 supervisor 审批，无需额外条件 |
| `OVERTIME` | `dailyTotalHours` | 加班小时数 → ≥4h 触发二审 |

---

## 3. MQ 消息契约

### 3.1 审批完成事件（`hrms.approval.notify`）

**Exchange**: `hrms.approval.topic`  
**Routing Key**: `hrms.approval.notify`  
**消费者**: `hrms-attendance` 模块（请假/加班/补卡状态更新）, `hrms-payroll` 模块（批次状态更新）

```java
@Data
public class ApprovalCompletedEvent {
    private String eventType;          // "APPROVAL_COMPLETED"
    private String processType;        // LEAVE / MAKEUP / OVERTIME / PAYROLL_BATCH
    private Long instanceId;           // 审批实例 ID
    private Long businessId;           // 业务主键（如 leave_application.id）
    private String result;             // APPROVED / REJECTED
    private Long applicantId;          // 发起人 employeeId
    private LocalDateTime completedAt; // 审批完成时间
    private String comment;            // 审批意见（REJECTED 时必填）
}
```

JSON 示例：

```json
{
  "eventType": "APPROVAL_COMPLETED",
  "processType": "LEAVE",
  "instanceId": 1,
  "businessId": 100,
  "result": "APPROVED",
  "applicantId": 42,
  "completedAt": "2026-07-15T10:00:00",
  "comment": "同意"
}
```

### 3.2 员工状态变更事件（`hrms.employee.event`）

**消费者**: `hrms-attendance` 模块（离职联动 → 考勤移出）

```java
@Data
public class EmployeeStatusChangeEvent {
    private String eventType;          // "EMPLOYEE_STATUS_CHANGED"
    private Long employeeId;
    private String oldStatus;          // 旧状态
    private String newStatus;          // 新状态（如 RESIGNED）
    private LocalDate effectDate;      // 生效日期（离职日）
    private String triggerSource;      // RESIGNATION（离职）/ RESIGNATION_REQUEST（员工离职）
}
```

JSON 示例：

```json
{
  "eventType": "EMPLOYEE_STATUS_CHANGED",
  "employeeId": 42,
  "oldStatus": "regular",
  "newStatus": "resigned",
  "effectDate": "2026-07-31",
  "triggerSource": "RESIGNATION"
}
```

> 郭策审批引擎触发离职生效 → 发 MQ 消息 → 张浩杰考勤模块消费 → 执行考勤移出（从 `attendance_group_member` 移除）。

---

## 4. 新增接口 / MQ 前 Checklist

- [ ] `hrms-attendance` 的 `pom.xml` 已添加 `hrms-workflow` 依赖
- [ ] 员工服务接口已由同学 B 在 `hrms-employee` 模块实现
- [ ] 审批引擎接口已由同学 C 在 `hrms-workflow` 模块实现
- [ ] RabbitMQ topic `hrms.approval.topic` / queue `hrms.approval.notify` 已创建
- [ ] RabbitMQ topic `hrms.employee.event` / queue `hrms.employee.event` 已创建
- [ ] 审批完成事件的消费者在 `hrms-attendance` 中已实现（请假/加班/补卡各状态处理）
- [ ] 员工状态变更事件的消费者在 `hrms-attendance` 中已实现（离职考勤移出）
- [ ] 跨模块调用自测：Mock 审批引擎 → 提交请假 → 消费 MQ 事件 → 检验状态流转

---

## 5. 联调 Mock 方案

| 阶段 | 方案 | 说明 |
|------|------|------|
| 本地开发 | `@MockBean` / 本地 `if-else` 桩 | Mock 审批引擎返回固定 PENDING，MQ 事件手动触发 |
| 前后端联调 | Apifox Mock + 内嵌审批桩 | 审批创建接口返回 `{ instanceId: 1, status: "PENDING" }` |
| 跨服务联调 | 同学 C 部署真实审批引擎 + RabbitMQ | 请假提交流转完整链路 |
