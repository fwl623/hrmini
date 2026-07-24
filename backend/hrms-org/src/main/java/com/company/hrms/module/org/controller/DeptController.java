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

/**
 * 部门组织 API：树、详情、人数、可否删除、CRUD、合并。
 * <p>
 * 鉴权在 {@link com.company.hrms.module.org.service.DeptService} 内通过 {@code OrgAccessGuard}：
 * 读开放给部门主管；写仅 HR/管理员。路径 {@code /api/v1/departments}。
 */
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    /** 全量部门树（含人数/负责人，Redis 缓存约 5 分钟） */
    @GetMapping("/tree")
    public Result<List<DeptTreeNodeVO>> tree() {
        return Result.success(deptService.getTree());
    }

    @GetMapping("/{id}")
    public Result<DeptTreeNodeVO> detail(@PathVariable Long id) {
        return Result.success(deptService.getById(id));
    }

    /** 直属人数 + 含下级人数 */
    @GetMapping("/{id}/headcount")
    public Result<DeptHeadcountVO> headcount(@PathVariable Long id) {
        return Result.success(deptService.headcount(id));
    }

    /** 删除前校验：无子部门、无在职/待离职员工、无职位 */
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

    /** 合并到目标部门：员工/子部门迁移，源部门隐藏 */
    @PutMapping("/{id}/merge")
    public Result<Void> merge(@PathVariable Long id, @Valid @RequestBody MergeDeptRequest request) {
        deptService.merge(id, request);
        return Result.success();
    }
}
