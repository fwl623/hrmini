package com.company.hrms.employee.service;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.dto.*;
import com.company.hrms.employee.vo.*;

import java.util.List;

public interface EmployeeService {

    /** 花名册分页+高级搜索 */
    PageResult<EmployeeListVO> pageSearch(EmployeePageQuery query);

    /** 员工详情+脱敏 */
    EmployeeDetailVO getDetail(Long employeeId);

    /** HR编辑：白名单校验 → 20003 / 离职 → 30003 */
    void update(Long employeeId, EmployeeUpdateDTO dto);

    /** 门户-本人档案 */
    ProfileVO getMyProfile(Long employeeId);

    /** 门户-编辑本人档案 */
    void updateMyProfile(Long employeeId, ProfileUpdateDTO dto);

    // 以下委托至 hrms-auth
    void changePassword(Long userId, PasswordChangeDTO dto);
    void bindMobile(Long userId, MobileBindDTO dto);
    List<LoginLogVO> listLoginLogs(Long userId);
}
