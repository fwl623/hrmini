package com.company.hrms.module.org.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * OrgEmployee 动态 SQL（避免注解 {@code <script>} 对 LanguageDriver 的隐式依赖）。
 * IN 列表仅拼接已校验的 Long，无注入风险。
 */
public class OrgEmployeeSqlProvider {

    public String selectNamesByIds(@Param("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "SELECT id, name FROM employee WHERE 1 = 0";
        }
        String inClause = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (inClause.isEmpty()) {
            return "SELECT id, name FROM employee WHERE 1 = 0";
        }
        return "SELECT id, name FROM employee WHERE deleted = 0 AND id IN (" + inClause + ")";
    }
}
