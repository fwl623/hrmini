package com.company.hrms.module.employee.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * 工号生成器
 *
 * 规则: YYYY + 部门编码(2位) + 序号(3位)
 * 例: 202401005 → 2024年 + JS部门 + 005号
 *
 * 使用 Redis 自增保证序号唯一性
 * 优先复用 employee_no_history(reuse_flag=1) 中的工号
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeNoGenerator {

    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_KEY_PREFIX = "emp_no:";
    private static final int SEQ_LENGTH = 3;

    /**
     * 生成工号
     *
     * @param deptCode 部门编码（2位）
     * @return 工号，如 "202401005"
     */
    public String generate(String deptCode) {
        String year = String.valueOf(Year.now().getValue());
        String redisKey = REDIS_KEY_PREFIX + year + ":" + deptCode;

        // Redis INCR 获取自增序号
        Long seq = redisTemplate.opsForValue().increment(redisKey);
        if (seq == null) {
            seq = 1L;
            redisTemplate.opsForValue().set(redisKey, "1");
        }

        String seqStr = String.format("%0" + SEQ_LENGTH + "d", seq);
        return year + deptCode + seqStr;
    }
}
