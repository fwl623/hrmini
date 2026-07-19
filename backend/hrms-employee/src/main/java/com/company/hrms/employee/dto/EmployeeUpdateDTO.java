package com.company.hrms.employee.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 员工编辑请求参数（HR管理端）
 * <p>
 * PUT /api/v1/employees/{id}
 * 仅允许更新白名单字段；流程字段（departmentId / mobile 等）即使传入也会被校验并返回 20003。
 * 流程字段必须出现在本 DTO 中，否则 Jackson 会静默丢弃，无法触发白名单拦截。
 * </p>
 */
@Data
public class EmployeeUpdateDTO {

    /** 姓名 */
    private String name;

    /** 性别 MALE / FEMALE */
    private String gender;

    /** 邮箱 */
    private String email;

    /** 生日 */
    private LocalDate birthday;

    /** 现居地址 */
    private String residenceAddress;

    /** 户籍地址 */
    private String householdAddress;

    /** 紧急联系人姓名 */
    private String emergencyContact;

    /** 紧急联系人电话 */
    private String emergencyPhone;

    // workLocation 不可直接 PUT（PRD：须走调岗），仅用于 rejectFlowFields 拦截
    private String workLocation;

    // ---- 流程字段：不可直接 PUT，仅用于 rejectFlowFields 拦截 → 20003 ----
    private Long departmentId;
    private Long positionId;
    private String grade;
    private Long managerId;
    private String mobile;
    private String idNumber;
}
