package com.company.hrms.module.employee.feign;

import com.company.hrms.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * hrms-org Feign 接口
 * 查询部门、职位基础数据
 */
@FeignClient(name = "hrms-org", path = "/api/v1")
public interface OrgFeignClient {

    /**
     * 查询部门详情
     */
    @GetMapping("/departments/{id}")
    Result<?> getDepartment(@PathVariable("id") Long id);

    /**
     * 查询职位详情
     */
    @GetMapping("/positions/{id}")
    Result<?> getPosition(@PathVariable("id") Long id);
}
