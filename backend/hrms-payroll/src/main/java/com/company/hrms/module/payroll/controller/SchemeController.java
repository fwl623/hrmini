package com.company.hrms.module.payroll.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.module.payroll.dto.SchemeCreateDTO;
import com.company.hrms.module.payroll.service.SchemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payroll/schemes")
@RequiredArgsConstructor
public class SchemeController {

    private final SchemeService schemeService;

    @GetMapping
    public Result<Object> list() {
        return Result.success(Map.of("list", schemeService.list(), "total", 0));
    }

    @GetMapping("/{id}")
    public Result<Object> detail(@PathVariable Long id) {
        return Result.success(schemeService.getById(id));
    }

    @PostMapping
    public Result<Map<String, Long>> create(@RequestBody SchemeCreateDTO dto) {
        return Result.success(Map.of("id", schemeService.create(dto)));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SchemeCreateDTO dto) {
        schemeService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        schemeService.delete(id);
        return Result.success();
    }
}
