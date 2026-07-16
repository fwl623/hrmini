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

    /** 薪资档案查看 */
    SalaryProfileVO getSalaryProfile(Long employeeId);

    /** 薪资档案更新 */
    void updateSalaryProfile(Long employeeId, SalaryProfileUpdateDTO dto);

    /** 敏感字段查看（密码二次验证 + AES解密 + 记审计日志） */
    SensitiveFieldVO getSensitiveField(Long employeeId, String field, String password);

    /** 门户-本人档案 */
    ProfileVO getMyProfile(Long employeeId);

    /** 门户-编辑本人档案 */
    void updateMyProfile(Long employeeId, ProfileUpdateDTO dto);

    /** HR手机号变更待办 */
    List<?> listMobileChangeApps();

    /** 调岗历史 */
    List<?> getTransferHistory(Long employeeId);

    // 以下委托至 hrms-auth
    void changePassword(Long userId, PasswordChangeDTO dto);
    void bindMobile(Long userId, MobileBindDTO dto);
    List<LoginLogVO> listLoginLogs(Long userId);
}
