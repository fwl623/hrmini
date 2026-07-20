package com.company.hrms.employee.service;

import com.company.hrms.employee.dto.SalaryProfileUpdateDTO;
import com.company.hrms.employee.vo.SalaryHistoryVO;
import com.company.hrms.employee.vo.SalaryProfileVO;

import java.util.List;

/**
 * 薪资档案服务接口
 * <p>
 * 提供员工薪资档案的读写操作。
 * 更新时自动记录调薪历史到 employee_salary_history 表。
 * SYS_ADMIN 角色访问被拦截（403）。
 * </p>
 */
public interface SalaryService {

    /** 查询薪资档案（无档案→50003） */
    SalaryProfileVO getProfile(Long employeeId);

    /** 更新薪资档案 + 记录调薪历史 */
    void updateProfile(Long employeeId, SalaryProfileUpdateDTO dto);

    /** 调薪历史列表（按生效日倒序） */
    List<SalaryHistoryVO> listHistory(Long employeeId);
}
