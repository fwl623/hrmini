package com.company.hrms.module.org.controller;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.org.dto.CreatePositionRequest;
import com.company.hrms.module.org.dto.PositionVO;
import com.company.hrms.module.org.dto.UpdatePositionRequest;
import com.company.hrms.module.org.service.PositionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public Result<PageResult<PositionVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String sequenceCode) {
        return Result.success(positionService.list(page, pageSize, departmentId, sequenceCode));
    }

    @GetMapping("/{id}")
    public Result<PositionVO> detail(@PathVariable Long id) {
        return Result.success(positionService.getById(id));
    }

    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody CreatePositionRequest request) {
        return Result.success(positionService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdatePositionRequest request) {
        positionService.update(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return Result.success();
    }
}
