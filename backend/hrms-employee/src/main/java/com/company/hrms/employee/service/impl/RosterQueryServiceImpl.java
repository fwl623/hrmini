package com.company.hrms.employee.service.impl;

import com.company.hrms.common.roster.EmployeeBriefDTO;
import com.company.hrms.common.roster.RosterQueryRequest;
import com.company.hrms.common.roster.RosterQueryResult;
import com.company.hrms.common.roster.RosterQueryService;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.auth.EmployeeAccessGuard;
import com.company.hrms.employee.dto.EmployeePageQuery;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.employee.vo.EmployeeListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 花名册只读 SPI：供 AI 查数卡片拉取名单（含 DataScope）。
 */
@Service
@RequiredArgsConstructor
public class RosterQueryServiceImpl implements RosterQueryService {

    private final EmployeeService employeeService;

    @Override
    public RosterQueryResult search(RosterQueryRequest request) {
        EmployeeAccessGuard.requireRosterRead();
        RosterQueryResult result = new RosterQueryResult();
        if (request == null) {
            return result;
        }
        int limit = Math.max(1, Math.min(request.getLimit() <= 0 ? 20 : request.getLimit(), 50));

        EmployeePageQuery query = new EmployeePageQuery();
        query.setPage(1);
        query.setPageSize(limit);
        if (StringUtils.hasText(request.getKeyword())) {
            query.setKeyword(request.getKeyword().trim());
        }
        if (request.getDepartmentIds() != null && !request.getDepartmentIds().isEmpty()) {
            query.setDepartmentIds(request.getDepartmentIds().stream()
                    .filter(id -> id != null && id > 0)
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }
        // 在职为主：试用 + 正式 + 待离职
        query.setEmploymentStatus("probation,regular,pending_resign");

        PageResult<EmployeeListVO> page = employeeService.pageSearch(query);
        if (page == null) {
            return result;
        }
        result.setTotal(page.getTotal());
        List<EmployeeListVO> list = page.getList();
        if (list != null) {
            int n = 0;
            for (EmployeeListVO vo : list) {
                if (vo == null || n >= limit) {
                    break;
                }
                EmployeeBriefDTO brief = new EmployeeBriefDTO();
                brief.setEmployeeId(vo.getEmployeeId());
                brief.setEmpNo(vo.getEmpNo());
                brief.setName(vo.getName());
                brief.setDepartment(vo.getDepartment());
                brief.setPosition(vo.getPosition());
                brief.setGrade(vo.getGrade());
                brief.setEmploymentStatus(vo.getEmploymentStatus());
                result.getItems().add(brief);
                n++;
            }
        }
        return result;
    }
}
