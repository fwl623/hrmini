package com.company.hrms.module.payroll.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.module.payroll.dto.CostReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/payroll")
@RequiredArgsConstructor
public class CostReportController {

    @GetMapping("/cost-report")
    public Result<CostReportVO> report(@RequestParam String periodFrom,
                                       @RequestParam String periodTo,
                                       @RequestParam(required = false) Long departmentId) {
        CostReportVO vo = new CostReportVO();
        vo.setTrend(new ArrayList<>());
        vo.setDeptDistribution(new ArrayList<>());
        return Result.success(vo);
    }
}
