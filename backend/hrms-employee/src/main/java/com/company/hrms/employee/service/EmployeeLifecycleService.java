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

    /** 试用结束前 7 天内的待转正列表 */
    List<PendingRegularizationVO> listPendingRegularization(LocalDate from, LocalDate to);

    /** PASS：10 → 20 */
    void regularizePass(Long employeeId);

    /** EXTEND：延长试用期，更新 probation_end_date */
    void regularizeExtend(Long employeeId, int extendMonths);

    /** 调岗生效：改部门/职位/上级并写历史 */
    void applyTransfer(Long employeeId, TransferEffectDTO dto);

    /** HR 正式离职审批通过：→ 30 待离职 */
    void markPendingResign(Long employeeId, LocalDate resignationDate);

    /** Job 生效：→ 40 已离职（禁用账号等联动 TODO） */
    void effectResign(Long employeeId);
}
