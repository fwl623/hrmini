package com.company.hrms.common.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页请求参数：page 从 1 起，pageSize 默认 20、最大 100。
 */
public class PageParam {

    @Min(1)
    private int page = 1;

    @Min(1)
    @Max(1000)
    private int pageSize = 20;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    /** MyBatis-Plus 分页 offset（0-based）。 */
    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}
