package com.company.hrms.module.auth.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.module.auth.dto.WorkbenchSummaryVO;
import com.company.hrms.module.auth.service.WorkbenchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端工作台聚合接口。KPI 查询失败时 Service 内降级为 0/空列表，不抛 500。
 */
@RestController
@RequestMapping("/workbench")
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    public WorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping("/summary")
    public Result<WorkbenchSummaryVO> summary() {
        return Result.success(workbenchService.summary());
    }
}
