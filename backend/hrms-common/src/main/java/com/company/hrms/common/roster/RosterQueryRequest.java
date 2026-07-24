package com.company.hrms.common.roster;

import lombok.Data;

import java.util.List;

/**
 * 花名册只读查询（AI / 工作台等跨模块调用）。
 */
@Data
public class RosterQueryRequest {
    /** 部门 ID 过滤（可空） */
    private List<Long> departmentIds;
    /** 姓名/工号/手机号关键词（可空） */
    private String keyword;
    /** 最多返回条数，建议 ≤20 */
    private int limit = 20;
}
