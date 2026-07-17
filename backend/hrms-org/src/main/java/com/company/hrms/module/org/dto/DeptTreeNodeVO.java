package com.company.hrms.module.org.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DeptTreeNodeVO {

    private Long id;
    private String name;
    /** 契约树响应字段名为 code */
    private String code;
    private Long parentId;
    /** 层级 1~5，前端校验新增子部门用 */
    private Integer level;
    private Long headEmployeeId;
    private String description;
    private Integer headcount;
    private Integer headcountIncludingSub;
    private String manager;
    private Integer sortOrder;
    private List<DeptTreeNodeVO> children = new ArrayList<>();
}
