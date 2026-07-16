package com.company.hrms.employee.service.impl;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.util.AesEncryptUtil;
import com.company.hrms.common.util.Sha256HashUtil;
import com.company.hrms.employee.entity.*;
import com.company.hrms.employee.feign.AuthInternalFeignClient;
import com.company.hrms.employee.mapper.*;
import com.company.hrms.employee.service.OnboardingIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 入职建档集成服务实现
 * <p>
 * 事务边界：@Transactional 保证全部步骤原子性。
 * auth 建号失败时整个事务回滚，employee 及相关数据不会落库。
 * <p>
 * ⚠️ 跨模块调用以注释桩形式存在，联调时启用：
 *   - 工号生成（EmployeeNoGenerator / Redis）
 *   - A 组 auth 建号（AuthInternalFeignClient）
 *   - MQ 事件发布（RabbitTemplate）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingIntegrationServiceImpl implements OnboardingIntegrationService {

    private final EmployeeMapper employeeMapper;
    private final EmployeePersonalMapper employeePersonalMapper;
    private final EmployeeContractMapper employeeContractMapper;
    private final EmployeeBankMapper employeeBankMapper;
    private final EmployeeNoHistoryMapper employeeNoHistoryMapper;
    private final AesEncryptUtil aesEncryptUtil;

    // ⚠️ 联调时启用
    // private final AuthInternalFeignClient authInternalFeignClient;
    // private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long confirm(Long applicationId, LocalDate actualOnboardDate) {
        log.info("入职建档 confirm 开始: applicationId={}, actualOnboardDate={}", applicationId, actualOnboardDate);

        // ===== 1. 生成工号 =====
        // ⚠️ 联调时：根据申请数据获取部门编码，调用 EmployeeNoGenerator.generate(deptCode)
        String empNo = generateEmpNo(actualOnboardDate.getYear(), "JS");
        log.info("工号生成: empNo={}", empNo);

        // ===== 2. 写入 employee 主表 =====
        // ⚠️ 联调时：从 C 组 onboarding_application 表获取申请人数据
        Employee emp = new Employee();
        emp.setEmployeeNo(empNo);
        emp.setName("（联调填充）");
        emp.setGender("MALE");
        emp.setMobile("");               // 从申请数据取
        emp.setEmail("");                // 从申请数据取
        emp.setDepartmentId(0L);         // 从申请数据取
        emp.setPositionId(0L);           // 从申请数据取
        emp.setHireDate(actualOnboardDate);
        emp.setEmploymentType("fulltime");
        emp.setEmploymentStatus(10);     // 试用期
        emp.setProbationPayRatio(new java.math.BigDecimal("0.80"));
        employeeMapper.insert(emp);
        Long employeeId = emp.getId();

        // ===== 3. 写入员工扩展表 =====
        // 3a. employee_personal（身份证 AES 加密）
        // EmployeePersonal personal = new EmployeePersonal();
        // personal.setEmployeeId(employeeId);
        // personal.setIdNumberEnc(aesEncryptUtil.encrypt(申请数据中的身份证号));
        // personal.setIdNumberHash(Sha256HashUtil.hash(申请数据中的身份证号));
        // employeePersonalMapper.insert(personal);

        // 3b. employee_contract
        // EmployeeContract contract = new EmployeeContract();
        // contract.setEmployeeId(employeeId);
        // contract.setContractType("FIXED");
        // ...

        // 3c. employee_bank（银行卡 AES 加密）
        // EmployeeBank bank = new EmployeeBank();
        // bank.setEmployeeId(employeeId);
        // bank.setBankAccountEnc(aesEncryptUtil.encrypt(卡号));
        // bank.setBankAccountTail(卡号后四位);
        // employeeBankMapper.insert(bank);

        // ===== 4. 写入 employee_no_history（工号占用） =====
        EmployeeNoHistory noHistory = new EmployeeNoHistory();
        noHistory.setEmployeeNo(empNo);
        noHistory.setYear(String.valueOf(actualOnboardDate.getYear()));
        noHistory.setDeptCode("JS");     // 从申请数据取部门编码
        noHistory.setEmployeeId(employeeId);
        noHistory.setReuseFlag(0);
        employeeNoHistoryMapper.insert(noHistory);

        // ===== 5. Feign 调用 A 组 auth 创建系统账号 =====
        // ⚠️ 联调时启用，建号失败抛异常触发事务回滚
        // try {
        //     AuthInternalFeignClient.CreateUserRequest req = new AuthInternalFeignClient.CreateUserRequest();
        //     req.setUsername(emp.getMobile());          // 手机号作为登录名
        //     req.setEmployeeId(employeeId);
        //     req.setRoleCodes(List.of("EMPLOYEE"));
        //     req.setPassword(generateRandomPassword());  // 随机密码，首次登录强制改密
        //     Result<AuthInternalFeignClient.CreateUserResponse> resp = authInternalFeignClient.createUser(req);
        //     if (resp.getCode() != 0 || resp.getData() == null) {
        //         throw new BusinessException(ErrorCode.SYSTEM_ERROR, "A组auth建号失败，事务回滚");
        //     }
        //     // 回写 employee.userId
        //     Employee update = new Employee();
        //     update.setId(employeeId);
        //     update.setUserId(resp.getData().getUserId());
        //     employeeMapper.updateById(update);
        // } catch (Exception e) {
        //     log.error("A组auth建号失败: employeeId={}", employeeId, e);
        //     throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建系统账号失败，事务已回滚");
        // }

        // ===== 6. 发布 MQ 事件（通知考勤组分配、薪资档案初始化） =====
        // ⚠️ MQ 就绪后开启
        // String eventJson = String.format(
        //     "{\"eventType\":\"EMPLOYEE_CREATED\",\"employeeId\":%d,\"name\":\"%s\",\"departmentId\":%d,\"employmentStatus\":10}",
        //     employeeId, emp.getName(), emp.getDepartmentId());
        // rabbitTemplate.convertAndSend("hrms.employee.event", eventJson);
        // log.info("MQ 事件已发布: {}", eventJson);

        log.info("入职建档完成: employeeId={}, empNo={}", employeeId, empNo);
        return employeeId;
    }

    /**
     * 工号生成（临时实现）
     * ⚠️ 联调时替换为 EmployeeNoGenerator（Redis INCR + employee_no_history 复用）
     */
    private String generateEmpNo(int year, String deptCode) {
        return year + deptCode + String.format("%03d", System.currentTimeMillis() % 1000);
    }

    /**
     * 随机密码生成
     * ⚠️ 联调时使用 AuthServiceImpl 中的密码策略
     */
    private String generateRandomPassword() {
        return "Hrms@" + UUID.randomUUID().toString().substring(0, 8);
    }
}
