package com.company.hrms.common.web;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应：{ list, total, page, pageSize }
 */
public class PageResult<T> implements Serializable {

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

    public static <T> PageResult<T> of(List<T> list, long total, PageParam param) {
        return of(list, total, param.getPage(), param.getPageSize());
    }

    public static <T> PageResult<T> empty(PageParam param) {
        return of(Collections.emptyList(), 0, param);
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

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
}
