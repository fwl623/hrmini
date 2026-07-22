package com.company.hrms.module.payroll.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.web.Result;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.module.org.entity.Department;
import com.company.hrms.module.org.mapper.DepartmentMapper;
import com.company.hrms.module.payroll.dto.CostReportVO;
import com.company.hrms.module.payroll.dto.DeptSalaryReportVO;
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

        // 如果指定了部门筛选，收集该部门及其所有子部门的 ID
        Set<Long> targetDeptIds = null;
        if (departmentId != null) {
            List<Department> allDepts = departmentMapper.selectList(null);
            targetDeptIds = new HashSet<>(collectChildIds(allDepts, departmentId));
        }

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

                // 如果指定了部门筛选，保留该部门及其所有子部门的明细
                if (targetDeptIds != null && !targetDeptIds.contains(deptId)) {
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


    /**
     * 部门薪资分布报表
     * GET /payroll/cost-report/department-salary?deptId=&period=
     */
    @GetMapping("/cost-report/department-salary")
    public Result<DeptSalaryReportVO> departmentSalary(@RequestParam Long deptId,
                                                        @RequestParam String period) {
        Department rootDept = departmentMapper.selectById(deptId);
        if (rootDept == null) {
            return Result.success(emptyReport(deptId, "未知部门", period));
        }
        List<Department> allDepts = departmentMapper.selectList(null);
        List<Long> childIds = collectChildIds(allDepts, deptId);
        List<Employee> employees = employeeMapper.search(null, childIds, null,
                java.util.List.of(10, 20), null, null, null, null);
        if (employees.isEmpty()) {
            return Result.success(emptyReport(deptId, rootDept.getName(), period));
        }
        Set<Long> empIds = employees.stream().map(Employee::getId).collect(java.util.stream.Collectors.toSet());
        PayrollBatch batch = batchMapper.selectOne(
                new LambdaQueryWrapper<PayrollBatch>()
                        .eq(PayrollBatch::getPeriod, period)
                        .in(PayrollBatch::getStatus, "DISTRIBUTED", "APPROVED", "PENDING_CONFIRM")
                        .orderByDesc(PayrollBatch::getId)
                        .last("LIMIT 1"));
        if (batch == null) {
            return Result.success(emptyReport(deptId, rootDept.getName(), period));
        }
        List<PayrollDetail> details = detailMapper.selectByBatchId(batch.getId());
        Map<Long, PayrollDetail> empDetailMap = details.stream()
                .filter(d -> empIds.contains(d.getEmployeeId()))
                .collect(java.util.stream.Collectors.toMap(PayrollDetail::getEmployeeId, d -> d, (a, b) -> a));

        DeptSalaryReportVO vo = new DeptSalaryReportVO();
        vo.setDeptId(deptId);
        vo.setDeptName(rootDept.getName());
        vo.setPeriod(period);

        // ---- 构建部门薪资汇总列表 ----
        List<DeptSalaryReportVO.DeptSalaryItem> children = new ArrayList<>();

        // ① 本部：仅直属根部门的员工（deptId 精确匹配）
        List<Employee> rootDirectEmployees = employees.stream()
                .filter(e -> e.getDepartmentId() != null && e.getDepartmentId().equals(deptId))
                .collect(java.util.stream.Collectors.toList());
        DeptSalaryReportVO.DeptSalaryItem rootItem = new DeptSalaryReportVO.DeptSalaryItem();
        rootItem.setDeptId(deptId);
        rootItem.setDeptName(rootDept.getName() + "(本部)");
        rootItem.setEmployeeCount(rootDirectEmployees.size());
        rootItem.setTotalSalary(rootDirectEmployees.stream().mapToDouble(e -> {
            PayrollDetail d = empDetailMap.get(e.getId());
            return d != null && d.getGrossSalary() != null ? d.getGrossSalary().doubleValue() : 0;
        }).sum());
        rootItem.setTotalActualSalary(rootDirectEmployees.stream().mapToDouble(e -> {
            PayrollDetail d = empDetailMap.get(e.getId());
            return d != null && d.getNetSalary() != null ? d.getNetSalary().doubleValue() : 0;
        }).sum());
        rootItem.setHasDetail(rootDirectEmployees.stream().anyMatch(e -> empDetailMap.containsKey(e.getId())));
        rootItem.setEmployees(buildEmployeeDetails(rootDirectEmployees, empDetailMap));
        children.add(rootItem);

        // ② 直接子部门（仅一层，含其自身下级；不单独展示孙子级）
        List<Department> directChildren = allDepts.stream()
                .filter(d -> d.getParentId() != null && d.getParentId().equals(deptId)
                        && (d.getDeleted() == null || d.getDeleted() == 0))
                .collect(java.util.stream.Collectors.toList());
        for (Department child : directChildren) {
            List<Long> childSubtreeIds = collectChildIds(allDepts, child.getId());
            List<Employee> childEmps = employees.stream()
                    .filter(e -> e.getDepartmentId() != null && childSubtreeIds.contains(e.getDepartmentId()))
                    .collect(java.util.stream.Collectors.toList());
            if (childEmps.isEmpty()) continue;

            DeptSalaryReportVO.DeptSalaryItem item = new DeptSalaryReportVO.DeptSalaryItem();
            item.setDeptId(child.getId());
            item.setDeptName(child.getName());
            item.setEmployeeCount(childEmps.size());
            item.setTotalSalary(childEmps.stream().mapToDouble(e -> {
                PayrollDetail d = empDetailMap.get(e.getId());
                return d != null && d.getGrossSalary() != null ? d.getGrossSalary().doubleValue() : 0;
            }).sum());
            item.setTotalActualSalary(childEmps.stream().mapToDouble(e -> {
                PayrollDetail d = empDetailMap.get(e.getId());
                return d != null && d.getNetSalary() != null ? d.getNetSalary().doubleValue() : 0;
            }).sum());
            item.setEmployees(buildEmployeeDetails(childEmps, empDetailMap));
            item.setHasDetail(childEmps.stream().anyMatch(e -> empDetailMap.containsKey(e.getId())));
            children.add(item);
        }

        // 摘要展示全量总计（本部 + 所有子部门，无重复无遗漏）
        int totalEmp = employees.size();
        double totalSalary = employees.stream().mapToDouble(e -> {
            PayrollDetail d = empDetailMap.get(e.getId());
            return d != null && d.getGrossSalary() != null ? d.getGrossSalary().doubleValue() : 0;
        }).sum();
        double totalActual = employees.stream().mapToDouble(e -> {
            PayrollDetail d = empDetailMap.get(e.getId());
            return d != null && d.getNetSalary() != null ? d.getNetSalary().doubleValue() : 0;
        }).sum();
        vo.setTotalEmployeeCount(totalEmp);
        vo.setTotalSalary(totalSalary);
        vo.setTotalActualSalary(totalActual);
        children.sort((a, b) -> Double.compare(b.getTotalSalary(), a.getTotalSalary()));
        vo.setChildren(children);
        return Result.success(vo);
    }

    /**
     * 构建员工明细列表——仅展示当月存在 PayrollDetail 薪资核算记录的人员
     */
    private List<DeptSalaryReportVO.EmployeeDetail> buildEmployeeDetails(
            List<Employee> empList,
            Map<Long, PayrollDetail> empDetailMap) {
        return empList.stream()
                .filter(e -> empDetailMap.containsKey(e.getId()))
                .map(e -> {
                    PayrollDetail d = empDetailMap.get(e.getId());
                    DeptSalaryReportVO.EmployeeDetail detail = new DeptSalaryReportVO.EmployeeDetail();
                    detail.setEmployeeId(e.getId());
                    detail.setEmployeeName(e.getName());
                    detail.setPositionName(e.getPositionName() != null ? e.getPositionName() : "");
                    detail.setHireDate(e.getHireDate() != null ? e.getHireDate().toString() : "");
                    detail.setGrossSalary(d != null && d.getGrossSalary() != null ? d.getGrossSalary().doubleValue() : 0);
                    detail.setNetSalary(d != null && d.getNetSalary() != null ? d.getNetSalary().doubleValue() : 0);
                    return detail;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取有核算数据的月份列表
     * GET /payroll/cost-report/available-periods
     */
    @GetMapping("/cost-report/available-periods")
    public Result<List<String>> availablePeriods() {
        List<PayrollBatch> batches = batchMapper.selectList(
                new LambdaQueryWrapper<PayrollBatch>()
                        .select(PayrollBatch::getPeriod)
                        .orderByDesc(PayrollBatch::getPeriod));
        List<String> periods = batches.stream()
                .map(PayrollBatch::getPeriod)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        return Result.success(periods);
    }

    private List<Long> collectChildIds(List<Department> allDepts, Long parentId) {
        List<Long> ids = new ArrayList<>();
        ids.add(parentId);
        for (Department dept : allDepts) {
            if (dept.getParentId() != null && dept.getParentId().equals(parentId)
                    && (dept.getDeleted() == null || dept.getDeleted() == 0)) {
                ids.addAll(collectChildIds(allDepts, dept.getId()));
            }
        }
        return ids;
    }

    private DeptSalaryReportVO emptyReport(Long deptId, String deptName, String period) {
        DeptSalaryReportVO vo = new DeptSalaryReportVO();
        vo.setDeptId(deptId);
        vo.setDeptName(deptName);
        vo.setPeriod(period);
        vo.setTotalEmployeeCount(0);
        vo.setTotalSalary(0);
        vo.setTotalActualSalary(0);
        vo.setChildren(Collections.emptyList());
        return vo;
    }

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