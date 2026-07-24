package com.company.hrms.module.auth.controller;

import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.exception.ForbiddenException;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.auth.dto.AnalyticsOverviewVO;
import com.company.hrms.module.auth.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人力资源数据概览（仅 SYS_ADMIN / HR_STAFF）。
 * GET /api/v1/analytics/overview?from=2026-06-21&to=2026-07-21
 */
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public Result<AnalyticsOverviewVO> overview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        requireHrOrAdmin();
        return Result.success(analyticsService.overview(from, to));
    }

    private static void requireHrOrAdmin() {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (user.hasRole(RoleCode.SYS_ADMIN.name()) || user.hasRole(RoleCode.HR_STAFF.name())) {
            return;
        }
        throw new ForbiddenException();
    }
}
