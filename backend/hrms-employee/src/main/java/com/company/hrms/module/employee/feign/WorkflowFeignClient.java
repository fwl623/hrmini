package com.company.hrms.module.employee.feign;

import com.company.hrms.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * hrms-workflow Feign 接口
 * 跨模块查询调岗/离职单据详情
 */
@FeignClient(name = "hrms-workflow", path = "/api/v1")
public interface WorkflowFeignClient {

    /**
     * 查询调岗申请详情
     */
    @GetMapping("/transfers/{id}")
    Result<?> getTransferById(@PathVariable("id") Long id);

    /**
     * 查询离职申请详情
     */
    @GetMapping("/resignations/{id}")
    Result<?> getResignById(@PathVariable("id") Long id);
}
