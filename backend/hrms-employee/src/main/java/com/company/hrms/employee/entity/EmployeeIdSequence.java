package com.company.hrms.employee.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工号序列表实体（Redis 降级兜底方案）
 * <p>
 * 对应表: employee_id_sequence_deprecated (DDL #27)
 * 当 Redis 不可用时，回退到此表获取工号序号。
 * 表名虽含 deprecated 但不可删除。
 * </p>
 */
@Data
public class EmployeeIdSequence {

    /** 主键ID */
    private Long id;

    /** 年份 */
    private String year;

    /** 部门编码 */
    private String deptCode;

    /** 当前序号 */
    private Integer currentVal;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
