package com.company.hrms.module.auth.service;

import com.company.hrms.module.auth.dto.AnalyticsOverviewVO;

public interface AnalyticsService {

    /**
     * @param from 含当日 yyyy-MM-dd
     * @param to   含当日 yyyy-MM-dd
     */
    AnalyticsOverviewVO overview(String from, String to);
}
