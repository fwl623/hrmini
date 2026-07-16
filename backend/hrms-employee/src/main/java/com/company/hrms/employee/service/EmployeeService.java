package com.company.hrms.employee.service;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.dto.EmployeePageQuery;
import com.company.hrms.employee.dto.EmployeeUpdateDTO;
import com.company.hrms.employee.dto.MobileBindDTO;
import com.company.hrms.employee.dto.PasswordChangeDTO;
import com.company.hrms.employee.dto.ProfileUpdateDTO;
import com.company.hrms.employee.vo.EmployeeDetailVO;
import com.company.hrms.employee.vo.EmployeeListVO;
import com.company.hrms.employee.vo.LoginLogVO;
import com.company.hrms.employee.vo.ProfileVO;

import java.util.List;

public interface EmployeeService {

    /** 花名册分页+高级搜索，DataScope 由拦截器注入 */
    PageResult<EmployeeListVO> pageSearch(EmployeePageQuery query);

    /** 员工详情+脱敏+fieldPermissions */
    EmployeeDetailVO getDetail(Long employeeId);

    /** HR端编辑：白名单校验，非白名单→20003，离职→30003 */
    void update(Long employeeId, EmployeeUpdateDTO dto);

    /** 门户-本人档案 */
    ProfileVO getMyProfile(Long employeeId);

    /** 门户-编辑本人档案（白名单） */
    void updateMyProfile(Long employeeId, ProfileUpdateDTO dto);

    /** HR查询所有待办手机号变更 */
    List<?> listMobileChangeApps();

    /** 员工调岗历史 */
    List<?> getTransferHistory(Long employeeId);

    // 以下由 ProfileController 委托至 hrms-auth
    void changePassword(Long userId, PasswordChangeDTO dto);
    void bindMobile(Long userId, MobileBindDTO dto);
    List<LoginLogVO> listLoginLogs(Long userId);
}
