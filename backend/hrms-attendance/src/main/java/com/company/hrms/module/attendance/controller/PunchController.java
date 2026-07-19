package com.company.hrms.module.attendance.controller;

import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.attendance.dto.PunchDTO;
import com.company.hrms.module.attendance.dto.PunchFixDTO;
import com.company.hrms.module.attendance.dto.PunchRecordVO;
import com.company.hrms.module.attendance.dto.QuotaVO;
import com.company.hrms.module.attendance.dto.TodayPunchVO;
import com.company.hrms.module.attendance.service.PunchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 打卡管理 Controller
 *
 * 涵盖打卡、今日状态、打卡记录、补卡申请与配额
 */
@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class PunchController {

    private final PunchService punchService;

    /**
     * 员工打卡
     * POST /api/v1/attendance/punch
     *
     * @param dto 打卡请求 { type, punchTime, latitude, longitude }
     * @return { punchStatus: NORMAL/LATE/EARLY_LEAVE/ABSENT_HALF }
     */
    @PostMapping("/punch")
    public Result<Map<String, String>> punch(@RequestBody PunchDTO dto) {
        Long employeeId = requireEmployeeId();
        String punchStatus = punchService.punch(employeeId, dto);
        return Result.success(Map.of("punchStatus", punchStatus));
    }

    /**
     * 今日打卡状态
     * GET /api/v1/attendance/punch/today
     *
     * @return { clockedCount, totalCount, lateCount, earlyLeaveCount, absentCount }
     */
    @GetMapping("/punch/today")
    public Result<TodayPunchVO> todayStatus() {
        return Result.success(punchService.getTodayStatus(requireEmployeeId()));
    }

    /**
     * 打卡记录分页
     * GET /api/v1/attendance/punch/records?page=1&size=20&keyword=&dateFrom=&dateTo=
     *
     * @return 分页打卡记录列表
     */
    @GetMapping("/punch/records")
    public Result<PageResult<PunchRecordVO>> records(PageParam pageParam,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String dateFrom,
                                                     @RequestParam(required = false) String dateTo) {
        return Result.success(punchService.pageRecords(pageParam, keyword, dateFrom, dateTo));
    }

    /**
     * 补卡申请
     * POST /api/v1/attendance/punch-fix
     *
     * @param dto 补卡请求 { punchDate, type, punchTime, reason }
     * @return { id, status }
     */
    @PostMapping("/punch-fix")
    public Result<Map<String, Object>> applyFix(@RequestBody PunchFixDTO dto) {
        Map<String, Object> result = punchService.applyFix(requireEmployeeId(), dto);
        return Result.success(result);
    }

    /**
     * 补卡剩余次数
     * GET /api/v1/attendance/punch-fix/quota
     *
     * @return { totalQuota, usedQuota, remainingQuota }
     */
    @GetMapping("/punch-fix/quota")
    public Result<QuotaVO> fixQuota() {
        return Result.success(punchService.getFixQuota(requireEmployeeId()));
    }

    private static Long requireEmployeeId() {
        Long employeeId = SecurityUtils.requireLoginUser().getEmployeeId();
        if (employeeId == null) {
            throw new com.company.hrms.common.exception.BusinessException(
                    com.company.hrms.common.exception.ErrorCode.PARAM_INVALID, "当前账号未关联员工档案");
        }
        return employeeId;
    }

}
