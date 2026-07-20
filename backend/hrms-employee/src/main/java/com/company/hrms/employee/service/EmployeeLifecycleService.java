package com.company.hrms.employee.service;

import com.company.hrms.employee.dto.TransferEffectDTO;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.vo.PendingRegularizationVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工生命周期能力：供 hrms-workflow 转正/调岗/离职调用。
 */
public interface EmployeeLifecycleService {

    Employee requireEmployee(Long employeeId);

    /**
     * 解析部门负责人审批人 userId：优先沿部门树找 head_employee，其次员工直属上级，
     * 再回退任意 DEPT_MANAGER 角色用户。
     */
    Long resolveDeptManagerUserId(Long employeeId);

    /**
     * 按目标部门解析负责人 userId（调岗「新部门接收」节点）：
     * 沿部门树找 head_employee，再回退 DEPT_MANAGER。
     */
    Long resolveDeptHeadUserIdByDeptId(Long departmentId);

    /**
     * 按部门解析负责人审批人 userId（入职候选人尚无 employeeId 时使用）。
     * 沿部门树找 head_employee，再回退任意 DEPT_MANAGER。
     */
    Long resolveDeptManagerUserIdByDepartment(Long departmentId);

    /**
     * 解析部门负责人员工 ID（作入职默认直属上级）；找不到返回 null。
     */
    Long resolveDeptHeadEmployeeId(Long departmentId);

    /**
     * 解析 HR 审批人 userId：优先非 excludeUserId 的 HR_STAFF，否则任意 HR_STAFF。
     */
    Long resolveHrApproverUserId(Long excludeUserId);

    /**
     * 解析财务审批人 userId（调岗含调薪）：仅 FINANCE_MANAGER（财务经理），不含普通财务专员。
     */
    Long resolveFinanceApproverUserId(Long excludeUserId);

    /** 试用结束日前 N 天内（含已逾期）的待转正列表；from 可为空表示不限下限 */
    List<PendingRegularizationVO> listPendingRegularization(LocalDate from, LocalDate to);

    /** PASS：10 → 20；可选同步调薪 */
    void regularizePass(Long employeeId);

    /** PASS 且有调薪：更新薪资档案并写 history */
    void applyRegularizationSalary(Long employeeId, java.math.BigDecimal newBaseSalary, Long operatorId);

    /** EXTEND：延长试用期，更新 probation_end_date */
    void regularizeExtend(Long employeeId, int extendMonths);

    /** 调岗生效：改部门/职位/上级并写历史 */
    void applyTransfer(Long employeeId, TransferEffectDTO dto);

    /** HR 正式离职审批通过：→ 30 待离职 */
    void markPendingResign(Long employeeId, LocalDate resignationDate);

    /** Job 生效：→ 40 已离职（禁用账号、释放工号、发状态事件） */
    void effectResign(Long employeeId);
}
