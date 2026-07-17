package com.company.hrms.workflow.controller;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.workflow.dto.LifecycleDtos;
import com.company.hrms.workflow.service.TransferService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public Result<LifecycleDtos.TransferVO> create(@RequestBody LifecycleDtos.TransferCreateRequest body) {
        return Result.success(transferService.create(body));
    }

    @GetMapping
    public Result<PageResult<LifecycleDtos.TransferVO>> list(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(transferService.list(page, pageSize, status));
    }

    @GetMapping("/{id}")
    public Result<LifecycleDtos.TransferVO> detail(@PathVariable("id") Long id) {
        return Result.success(transferService.detail(id));
    }
}
