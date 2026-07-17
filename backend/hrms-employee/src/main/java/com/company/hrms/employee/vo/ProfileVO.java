package com.company.hrms.employee.vo;

import lombok.Data;

import java.util.Set;

/**
 * 门户个人信息响应
 * <p>
 * 用于 GET /api/v1/profile/me 接口。
 * 敏感字段（手机号）脱敏展示，仅本人可见。
 * editableFields 标识当前用户可编辑的字段白名单。
 * </p>
 */
@Data
public class ProfileVO {

    /** 员工ID */
    private Long employeeId;

    /** 工号 */
    private String empNo;

    /** 姓名 */
    private String name;

    /** 手机号（脱敏：138****1234）；未绑定时为空 */
    private String mobile;

    /** 是否已绑定手机号（已绑定则不可走 /security/mobile/bind，须走变更申请） */
    private Boolean mobileBound;

    /** 邮箱 */
    private String email;

    /** 部门名称 */
    private String department;

    /** 职位名称 */
    private String position;

    /** 职级 */
    private String grade;

    /** 入职日期 yyyy-MM-dd */
    private String hireDate;

    /** 现居地址 */
    private String residenceAddress;

    /** 紧急联系人姓名 */
    private String emergencyContact;

    /** 紧急联系人电话 */
    private String emergencyPhone;

    /** 可编辑字段白名单（如 ["email","residenceAddress","emergencyContact","emergencyPhone"]） */
    private Set<String> editableFields;
}
