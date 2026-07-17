package com.company.hrms.employee.service;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.dto.*;
import com.company.hrms.employee.vo.*;

import java.util.List;

/**
 * 员工档案服务接口
 * <p>
 * 提供员工档案的核心业务操作，包括花名册分页查询、详情查看、白名单编辑、
 * 门户个人档案查看/编辑、密码修改、手机绑定、登录日志查询。
 * </p>
 */
public interface EmployeeService {

    /** 花名册分页+高级搜索（DataScope 由拦截器注入） */
    PageResult<EmployeeListVO> pageSearch(EmployeePageQuery query);

    /** 员工详情+脱敏 */
    EmployeeDetailVO getDetail(Long employeeId);

    /** HR端编辑：白名单校验→20003 / 离职→30003 */
    void update(Long employeeId, EmployeeUpdateDTO dto);

    /** 门户-本人档案（脱敏） */
    ProfileVO getMyProfile(Long employeeId);

    /** 门户-编辑本人档案（仅白名单字段） */
    void updateMyProfile(Long employeeId, ProfileUpdateDTO dto);

    // ===== 账号安全 =====
    void changePassword(Long userId, PasswordChangeDTO dto);

    /**
     * 首次绑定手机号；已有手机号须走 MOBILE_CHANGE 审批，不可直接绑定。
     */
    void bindMobile(Long employeeId, Long userId, MobileBindDTO dto);

    List<LoginLogVO> listLoginLogs(Long userId);
}
