package com.company.hrms.employee.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 员工编辑请求参数（HR管理端）
 * <p>
 * PUT /api/v1/employees/{id}
 * 仅允许更新白名单字段，其它字段（如 departmentId、positionId、mobile）拒绝并返回 20003。
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

    /** 紧急联系人姓名 */
    private String emergencyContact;

    /** 紧急联系人电话 */
    private String emergencyPhone;

    /** 工作地点 */
    private String workLocation;
}
