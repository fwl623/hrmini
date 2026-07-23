package com.company.hrms.module.attendance.controller;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.util.ExcelExportUtil;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.attendance.dto.MonthlySummaryExportVO;
import com.company.hrms.module.attendance.dto.MonthlySummaryLockDTO;
import com.company.hrms.module.attendance.dto.MonthlySummaryVO;
import com.company.hrms.module.attendance.auth.AttendanceAccessGuard;
import com.company.hrms.module.attendance.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 月考勤汇总 Controller
 */
@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class MonthlySummaryController {

    private final SummaryService summaryService;

    /**
     * 月汇总查看
     * GET /api/v1/attendance/monthly-summary?period=2026-07&page=1&pageSize=20
     */
    @GetMapping("/monthly-summary")
    public Result<MonthlySummaryVO> get(PageParam pageParam,
                                        @RequestParam String period) {
        AttendanceAccessGuard.requireHrStaff();
        return Result.success(summaryService.getMonthlySummary(pageParam, period));
    }

    /**
     * 导出月汇总
     * GET /api/v1/attendance/monthly-summary/export?period=2026-07
     */
    @GetMapping("/monthly-summary/export-excel")
    public ResponseEntity<byte[]> export(@RequestParam String period) {
        AttendanceAccessGuard.requireHrStaff();
        List<MonthlySummaryExportVO> list = summaryService.exportMonthlySummary(period);
        byte[] bytes = ExcelExportUtil.generateExcelBytes("月考勤汇总", list, MonthlySummaryExportVO.class);
        String fileName = URLEncoder.encode("考勤月报-" + period, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + fileName + ".xlsx")
                .body(bytes);
    }

    /**
     * 手动生成月汇总
     * POST /api/v1/attendance/monthly-summary/generate?period=2026-07
     */
    @PostMapping("/monthly-summary/generate")
    public Result<Void> generate(@RequestParam String period) {
        AttendanceAccessGuard.requireHrStaff();
        summaryService.generateMonthlySummary(period);
        return Result.success();
    }

    /**
     * 月汇总锁定/解锁
     * PUT /api/v1/attendance/monthly-summary
     */
    @PutMapping("/monthly-summary")
    public Result<Void> updateLock(@RequestBody MonthlySummaryLockDTO dto) {
        AttendanceAccessGuard.requireHrStaff();
        Long operatorId = SecurityUtils.getCurrentUser().getEmployeeId();
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "当前账号未绑定员工");
        }
        summaryService.updateLock(dto.getPeriod(), dto.getLocked(), operatorId);
        return Result.success();
    }
}
