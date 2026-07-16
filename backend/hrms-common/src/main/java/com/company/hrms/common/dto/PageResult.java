package com.company.hrms.common.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 统一分页结果
 * { list, total, page, pageSize }
 */
@Data
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int page;
    private int pageSize;

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int page, int pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int pageSize) {
        return new PageResult<>(list, total, page, pageSize);
    }

    @SuppressWarnings("unchecked")
    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 0, 1, 20);
    }
}
