package com.company.hrms.employee.service.impl;

import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeeNoHistory;
import com.company.hrms.employee.mapper.EmployeeContractMapper;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeeNoHistoryMapper;
import com.company.hrms.employee.mapper.EmployeePersonalMapper;
import com.company.hrms.employee.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 入职建档服务实现
 *
 * 事务内依次完成：
 * 1. 生成工号
 * 2. 写入 employee 主表
 * 3. 写入 employee_personal / employee_contract
 * 4. Feign 调用 A 组 auth 创建系统账号 ← 联调验证
 * 5. 回写 userId
 * 6. 发布 MQ 事件通知下游 ← MQ 就绪后开启
 *
 * ⚠️ 依赖 A 组 (李俊毅) 的 AuthInternalFeignClient — 先约定接口，联调时启用
 * ⚠️ 入职申请数据来自 C 组 (郭策) 的 workflow 模块 — 联调时对接
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private final EmployeeMapper employeeMapper;
    private final EmployeePersonalMapper employeePersonalMapper;
    private final EmployeeContractMapper employeeContractMapper;
    private final EmployeeNoHistoryMapper employeeNoHistoryMapper;

    // ⚠️ 联调时接入工号生成器（EmployeeNoGenerator）
    // private final RedisTemplate<String, String> redisTemplate;

    @Override
    @Transactional
    public Long confirm(Long applicationId, LocalDate actualOnboardDate) {
        // ⚠️ 联调时：根据 applicationId 从 C 组 workflow 模块获取入职申请数据
        log.info("入职建档 confirm: applicationId={}, actualOnboardDate={}", applicationId, actualOnboardDate);

        // 1. 生成工号
        // ⚠️ 联调时接入 Redis INCR + employee_no_history 复用逻辑
        //    EmployeeNoGenerator generate(deptCode) → "2024JS001"
        String empNo = "TMP" + System.currentTimeMillis(); // 临时工号，联调替换

        // 2. 写入 employee
        Employee emp = new Employee();
        emp.setEmployeeNo(empNo);
        emp.setName("（联调填充）");
        emp.setGender("MALE");
        emp.setMobile("");
        emp.setEmail("");
        emp.setDepartmentId(0L);
        emp.setPositionId(0L);
        emp.setHireDate(actualOnboardDate);
        emp.setEmploymentType("fulltime");
        emp.setEmploymentStatus(10);
        emp.setProbationPayRatio(new java.math.BigDecimal("0.80"));
        employeeMapper.insert(emp);
        Long employeeId = emp.getId();

        // 3. 写入 employee_no_history（工号占用）
        EmployeeNoHistory noHistory = new EmployeeNoHistory();
        noHistory.setEmployeeNo(empNo);
        noHistory.setYear(String.valueOf(actualOnboardDate.getYear()));
        noHistory.setDeptCode("JS");
        noHistory.setEmployeeId(employeeId);
        noHistory.setReuseFlag(0);
        employeeNoHistoryMapper.insert(noHistory);

        // ⚠️ 联调时：写入 employee_personal（身份证加密）、employee_contract
        //   EmployeePersonal personal = new EmployeePersonal();
        //   personal.setEmployeeId(employeeId);
        //   personal.setIdNumberEnc(aesEncryptUtil.encrypt(idNumber));
        //   personal.setIdNumberHash(Sha256HashUtil.hash(idNumber));
        //   ...

        // 4. Feign 调用 A 组 auth 创建账号
        // ⚠️ 入职建档调 A 组 auth 建号 — 先约定接口，联调验证
        //   AuthInternalFeignClient.CreateUserRequest req = new ...();
        //   req.setUsername(emp.getMobile());
        //   req.setEmployeeId(employeeId);
        //   req.setRoleCodes(List.of("EMPLOYEE"));
        //   req.setPassword(randomPassword());
        //   Result<CreateUserResponse> resp = authInternalFeignClient.createUser(req);
        //   if (resp.success) { 回写 employee.userId = resp.data.userId;  }

        // 5. 发布 MQ 事件（通知考勤组/薪资模块初始化）
        // ⚠️ MQ 就绪后开启
        //   rabbitTemplate.convertAndSend("hrms.employee.event", eventJson);

        log.info("入职建档完成: employeeId={}, empNo={}", employeeId, empNo);
        return employeeId;
    }
}
