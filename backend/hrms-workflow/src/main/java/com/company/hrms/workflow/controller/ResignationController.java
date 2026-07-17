package com.company.hrms.workflow.controller;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.workflow.dto.LifecycleDtos;
import com.company.hrms.workflow.service.ResignationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 离职双通道：门户申请 + HR 正式离职。
 */
@RestController
public class ResignationController {

    private final ResignationService resignationService;

    public ResignationController(ResignationService resignationService) {
        this.resignationService = resignationService;
    }

    // ---------- 门户 SELF ----------

    @PostMapping("/profile/resignation-requests")
    public Result<LifecycleDtos.ResignationRequestVO> createMyRequest(
            @RequestBody LifecycleDtos.ResignationRequestCreate body) {
        return Result.success(resignationService.createMyRequest(body));
    }

    @GetMapping("/profile/resignation-requests")
    public Result<PageResult<LifecycleDtos.ResignationRequestVO>> myRequests(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        return Result.success(resignationService.listMyRequests(page, pageSize));
    }

    @PostMapping("/profile/resignation-requests/{id}/cancel")
    public Result<Void> cancelMyRequest(@PathVariable("id") Long id) {
        resignationService.cancelMyRequest(id);
        return Result.success();
    }

    // ---------- HR ----------

    @GetMapping("/resignation-requests")
    public Result<PageResult<LifecycleDtos.ResignationRequestVO>> listRequests(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(resignationService.listRequests(page, pageSize, status));
    }

    @PostMapping("/resignations")
    public Result<LifecycleDtos.ResignationVO> createResignation(
            @RequestBody LifecycleDtos.ResignationCreateRequest body) {
        return Result.success(resignationService.createResignation(body));
    }

    @GetMapping("/resignations")
    public Result<PageResult<LifecycleDtos.ResignationVO>> listResignations(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(resignationService.listResignations(page, pageSize, status));
    }

    @GetMapping("/resignations/stats")
    public Result<LifecycleDtos.ResignationStatsVO> stats() {
        return Result.success(resignationService.stats());
    }

    @GetMapping("/resignations/{id}")
    public Result<LifecycleDtos.ResignationVO> detail(@PathVariable("id") Long id) {
        return Result.success(resignationService.resignationDetail(id));
    }
}
