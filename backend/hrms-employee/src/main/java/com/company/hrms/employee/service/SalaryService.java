package com.company.hrms.employee.service;

import com.company.hrms.employee.dto.SalaryProfileUpdateDTO;
import com.company.hrms.employee.vo.SalaryProfileVO;

/**
 * 薪资档案服务
 * - 薪资档案读写
 * - 调薪历史记录
 */
public interface SalaryService {

    /** 查询薪资档案 */
    SalaryProfileVO getProfile(Long employeeId);

    /** 更新薪资档案 + 记录调薪历史 */
    void updateProfile(Long employeeId, SalaryProfileUpdateDTO dto);
}
