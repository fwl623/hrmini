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

/**
 * 【转正 REST 接口】路径前缀 {@code /api/v1/regularization/applications}。
 * 试用期结束前 7 天待转正员工扫描、HR 发起转正申请（PASS/EXTEND/FAIL 三分支）、
 * 转正记录分页查询。发起时创建审批实例（部门负责人 → HR）；审批通过后经
 * {@code LifecycleApprovalHandler} 回调 {@code RegularizationService.onApproved}：
 * PASS 改员工状态 10→20 并可调薪，EXTEND 延长试用期，FAIL 通知 HR 走离职流程。
 * 审批操作在 {@code pages/admin/approval/index.tsx} 通过 {@code services/workflow.ts} 完成。
 */
@RestController
@RequestMapping("/regularization/applications")
public class RegularizationController {

    private final RegularizationService regularizationService;

    public RegularizationController(RegularizationService regularizationService) {
        this.regularizationService = regularizationService;
    }

    /**
     * GET /api/v1/regularization/applications/pending
     * 列出待转正员工：试用结束日 ≤ 今天+7（含已逾期），并排除已有 APPROVING 转正申请的员工，
     * 供 HR 在到期前主动发起转正。
     */
    @GetMapping("/pending")
    public Result<List<PendingRegularizationVO>> pending() {
        return Result.success(regularizationService.listPending());
    }

    /**
     * GET /api/v1/regularization/applications
     * 分页查询历史转正申请记录，支持按 status 筛选（APPROVING/COMPLETED/REJECTED 等）。
     * 【实现】{@code RegularizationController.list} → {@code RegularizationService.list} →
     * 查 {@code regularization_application} → 按 status 可选过滤 → 转 VO 内存分页。
     */
    @GetMapping
    public Result<PageResult<LifecycleDtos.RegularizationVO>> list(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(regularizationService.list(page, pageSize, status));
    }

    /**
     * POST /api/v1/regularization/applications
     * HR 为员工发起转正申请，body 含 {@code approvalResult=PASS|EXTEND|FAIL}、
     * 绩效评价及可选调薪/延长月数；创建审批实例进入部门负责人 → HR 两节点流程。
     */
    @PostMapping
    public Result<LifecycleDtos.RegularizationVO> create(@RequestBody LifecycleDtos.RegularizationCreateRequest body) {
        return Result.success(regularizationService.create(body));
    }
}

