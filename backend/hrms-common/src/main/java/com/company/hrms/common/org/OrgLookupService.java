package com.company.hrms.common.org;

import java.util.List;
import java.util.Optional;

/**
 * 组织只读查询 SPI（实现位于 hrms-org）。
 * 供 AI 按部门名解析、查人数等，避免 AI 模块硬依赖 org Mapper。
 */
public interface OrgLookupService {

    /**
     * 按名称提示模糊匹配部门（去「部门/部」等后缀；精确优先于包含）。
     */
    List<DeptBriefDTO> resolveDepartmentsByNameHint(String hint, int limit);

    /** 各部门人数（按直属人数倒序，最多 limit 条）。 */
    List<DeptBriefDTO> listTopDepartmentHeadcounts(int limit);

    /** 单部门人数（直属 + 含下级）。 */
    Optional<DeptBriefDTO> getDepartmentHeadcount(long departmentId);
}
