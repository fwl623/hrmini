package com.company.hrms.module.org.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.module.org.dto.CreateDeptRequest;
import com.company.hrms.module.org.dto.DeptCanDeleteVO;
import com.company.hrms.module.org.dto.DeptHeadcountVO;
import com.company.hrms.module.org.dto.DeptTreeNodeVO;
import com.company.hrms.module.org.dto.MergeDeptRequest;
import com.company.hrms.module.org.dto.UpdateDeptRequest;
import com.company.hrms.module.org.service.DeptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @GetMapping("/tree")
    public Result<List<DeptTreeNodeVO>> tree() {
        return Result.success(deptService.getTree());
    }

    @GetMapping("/{id}")
    public Result<DeptTreeNodeVO> detail(@PathVariable Long id) {
        return Result.success(deptService.getById(id));
    }

    @GetMapping("/{id}/headcount")
    public Result<DeptHeadcountVO> headcount(@PathVariable Long id) {
        return Result.success(deptService.headcount(id));
    }

    @GetMapping("/{id}/can-delete")
    public Result<DeptCanDeleteVO> canDelete(@PathVariable Long id) {
        return Result.success(deptService.canDelete(id));
    }

    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody CreateDeptRequest request) {
        return Result.success(deptService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateDeptRequest request) {
        deptService.update(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/merge")
    public Result<Void> merge(@PathVariable Long id, @Valid @RequestBody MergeDeptRequest request) {
        deptService.merge(id, request);
        return Result.success();
    }
}
