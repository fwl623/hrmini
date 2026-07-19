package com.company.hrms.employee.dto;

import lombok.Data;

/**
 * 门户个人信息编辑请求参数
 * <p>
 * PUT /api/v1/profile/me
 * 仅允许编辑白名单字段：email、residenceAddress、emergencyContact、emergencyPhone。
 * mobile、department、position、salary 等字段不可编辑（须走对应流程）。
 * </p>
 */
@Data
public class ProfileUpdateDTO {

    /** 邮箱 */
    private String email;

    /** 现居地址 */
    private String residenceAddress;

    /** 紧急联系人姓名 */
    private String emergencyContact;

    /** 紧急联系人电话 */
    private String emergencyPhone;

    // ---- 流程字段：抓包强行携带时返回 20003 ----
    private Long departmentId;
    private Long positionId;
    private String grade;
    private Long managerId;
    private String mobile;
    private String idNumber;
}
