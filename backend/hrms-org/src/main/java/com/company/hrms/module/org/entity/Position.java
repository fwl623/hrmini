package com.company.hrms.module.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.hrms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("position")
public class Position extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** M / P / S */
    private String sequence;
    @TableField("department_id")
    private Long departmentId;
    @TableField("rank_min")
    private String rankMin;
    @TableField("rank_max")
    private String rankMax;
    @TableField("default_probation_months")
    private Integer defaultProbationMonths;
    @TableField("is_standard")
    private Integer isStandard;
    private String description;
}
