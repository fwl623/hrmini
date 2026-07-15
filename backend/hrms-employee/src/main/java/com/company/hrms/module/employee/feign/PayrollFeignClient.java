package com.company.hrms.module.employee.feign;

import com.company.hrms.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * hrms-payroll Feign 接口
 * 工资条 PDF 下载
 */
@FeignClient(name = "hrms-payroll", path = "/api/v1")
public interface PayrollFeignClient {

    /**
     * 获取工资条 PDF 二进制流
     * GET /api/v1/profile/payslips/{period}/pdf
     */
    @GetMapping("/profile/payslips/{period}/pdf")
    Result<byte[]> getPayslipPdf(@PathVariable("period") String period);
}
