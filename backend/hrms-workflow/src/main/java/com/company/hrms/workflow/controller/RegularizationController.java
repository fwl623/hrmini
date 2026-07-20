package com.company.hrms.workflow.controller;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.employee.vo.PendingRegularizationVO;
import com.company.hrms.workflow.dto.LifecycleDtos;
import com.company.hrms.workflow.service.RegularizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/regularization/applications")
public class RegularizationController {

    private final RegularizationService regularizationService;

    public RegularizationController(RegularizationService regularizationService) {
        this.regularizationService = regularizationService;
    }

    /** 待转正：试用结束日 ≤ 今天+7（含已逾期） */
    @GetMapping("/pending")
    public Result<List<PendingRegularizationVO>> pending() {
        return Result.success(regularizationService.listPending());
    }

    @GetMapping
    public Result<PageResult<LifecycleDtos.RegularizationVO>> list(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(regularizationService.list(page, pageSize, status));
    }

    @PostMapping
    public Result<LifecycleDtos.RegularizationVO> create(@RequestBody LifecycleDtos.RegularizationCreateRequest body) {
        return Result.success(regularizationService.create(body));
    }
}
