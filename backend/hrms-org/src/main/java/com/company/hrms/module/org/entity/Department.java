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
@TableName("department")
public class Department extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    @TableField("parent_id")
    private Long parentId;
    private String path;
    private Integer level;
    @TableField("head_employee_id")
    private Long headEmployeeId;
    @TableField("sort_order")
    private Integer sortOrder;
    private String description;
}
