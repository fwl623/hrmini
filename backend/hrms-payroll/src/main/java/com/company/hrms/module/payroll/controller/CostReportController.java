package com.company.hrms.module.payroll.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.web.Result;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.module.org.entity.Department;
import com.company.hrms.module.org.mapper.DepartmentMapper;
import com.company.hrms.module.payroll.dto.CostReportVO;
import com.company.hrms.payroll.entity.PayrollBatch;
import com.company.hrms.payroll.entity.PayrollDetail;
import com.company.hrms.payroll.mapper.PayrollBatchMapper;
import com.company.hrms.payroll.mapper.PayrollDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/payroll")
@RequiredArgsConstructor
public class CostReportController {

    private final PayrollBatchMapper batchMapper;
    private final PayrollDetailMapper detailMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;

    /**
     * 成本报表
     * GET /payroll/cost-report?periodFrom=&periodTo=&departmentId=
     * <p>
     * 按时间范围查询已发放批次的明细，按员工部门聚合薪资数据。
     * 返回 trend（各月份趋势）和 deptDistribution（各部门分布）。
     */
    @GetMapping("/cost-report")
    public Result<CostReportVO> report(@RequestParam String periodFrom,
                                        @RequestParam String periodTo,
                                        @RequestParam(required = false) Long departmentId) {
        // 查询已发放的批次（按账期升序排列）
        List<PayrollBatch> batches = batchMapper.selectList(
                new LambdaQueryWrapper<PayrollBatch>()
                        .eq(PayrollBatch::getStatus, "DISTRIBUTED")
                        .ge(PayrollBatch::getPeriod, periodFrom)
                        .le(PayrollBatch::getPeriod, periodTo)
                        .orderByAsc(PayrollBatch::getPeriod));

        // 用于 trend 的聚合：账期 -> 汇总
        // 用于 deptDistribution 的聚合：部门ID -> 汇总
        Map<String, List<PayrollDetail>> periodMap = new LinkedHashMap<>();
        Map<Long, List<PayrollDetail>> deptMap = new HashMap<>();

        for (PayrollBatch batch : batches) {
            List<PayrollDetail> details = detailMapper.selectByBatchId(batch.getId());

            for (PayrollDetail detail : details) {
                // 解析员工部门
                Employee emp = employeeMapper.selectById(detail.getEmployeeId());
                Long deptId = (emp != null && emp.getDepartmentId() != null)
                        ? emp.getDepartmentId() : 0L;

                // 如果指定了部门筛选，仅保留该部门的明细
                if (departmentId != null && !deptId.equals(departmentId)) {
                    continue;
                }

                // 按账期分组（trend）
                periodMap.computeIfAbsent(batch.getPeriod(), k -> new ArrayList<>()).add(detail);

                // 按部门分组（deptDistribution）
                deptMap.computeIfAbsent(deptId, k -> new ArrayList<>()).add(detail);
            }
        }

        // 构建 trend
        List<CostReportVO.CostTrendItem> trendList = new ArrayList<>();
        for (Map.Entry<String, List<PayrollDetail>> entry : periodMap.entrySet()) {
            CostReportVO.CostTrendItem item = new CostReportVO.CostTrendItem();
            item.setPeriod(entry.getKey());
            item.setGrossTotal(sumGross(entry.getValue()));
            item.setNetTotal(sumNet(entry.getValue()));
            trendList.add(item);
        }

        // 构建 deptDistribution
        List<CostReportVO.DeptDistItem> deptList = new ArrayList<>();
        for (Map.Entry<Long, List<PayrollDetail>> entry : deptMap.entrySet()) {
            CostReportVO.DeptDistItem item = new CostReportVO.DeptDistItem();
            Long deptId = entry.getKey();
            if (deptId != null && deptId != 0L) {
                Department dept = departmentMapper.selectById(deptId);
                item.setDeptName(dept != null ? dept.getName() : "未知部门");
            } else {
                item.setDeptName("未知部门");
            }
            item.setGrossTotal(sumGross(entry.getValue()));
            item.setNetTotal(sumNet(entry.getValue()));
            deptList.add(item);
        }

        CostReportVO vo = new CostReportVO();
        vo.setTrend(trendList);
        vo.setDeptDistribution(deptList);
        return Result.success(vo);
    }

    // ==================== 私有方法 ====================

    private double sumGross(List<PayrollDetail> details) {
        return details.stream()
                .filter(d -> d.getGrossSalary() != null)
                .mapToDouble(d -> d.getGrossSalary().doubleValue())
                .sum();
    }

    private double sumNet(List<PayrollDetail> details) {
        return details.stream()
                .filter(d -> d.getNetSalary() != null)
                .mapToDouble(d -> d.getNetSalary().doubleValue())
                .sum();
    }
}
