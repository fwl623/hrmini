package com.company.hrms.common.roster;

/**
 * 花名册只读查询 SPI（实现位于 hrms-employee）。
 * 实现内须走 DataScope，禁止跨数据权限全表拉取。
 */
public interface RosterQueryService {

    RosterQueryResult search(RosterQueryRequest request);
}
