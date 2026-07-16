package com.company.hrms.common.dto;

import lombok.Data;

/**
 * 分页查询参数基类
 */
@Data
public class PageParam {

    private int page = 1;
    private int pageSize = 20;

    public int getPage() {
        return Math.max(page, 1);
    }

    public int getPageSize() {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}
