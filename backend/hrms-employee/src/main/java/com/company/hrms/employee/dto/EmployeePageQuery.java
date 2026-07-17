package com.company.hrms.employee.dto;

import com.company.hrms.common.web.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 员工花名册分页搜索参数
 * <p>
 * 用于 GET /api/v1/employees 接口的查询参数绑定。
 * 支持按关键词、部门、职位、在职状态、职级、入职日期范围等多维度筛选。
 * 分页参数继承自 {@link PageParam}，默认第1页每页20条，最大100条。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmployeePageQuery extends PageParam {

    /** 模糊搜索关键词（匹配姓名/工号/手机号） */
    private String keyword;

    /** 部门ID列表，逗号分隔（部门树多选） */
    private String departmentIds;

    /** 职位ID列表，逗号分隔 */
    private String positionIds;

    /** 在职状态筛选，逗号分隔（如 "probation,regular"） */
    private String employmentStatus;

    /** 职级筛选，逗号分隔（如 "P5,P6,P7"） */
    private String gradeLevels;

    /** 入职日期范围-起始 yyyy-MM-dd */
    private LocalDate hireDateFrom;

    /** 入职日期范围-结束 yyyy-MM-dd */
    private LocalDate hireDateTo;
}
