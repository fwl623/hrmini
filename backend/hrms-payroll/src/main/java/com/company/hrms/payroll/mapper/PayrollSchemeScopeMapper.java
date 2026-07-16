package com.company.hrms.payroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.payroll.entity.PayrollSchemeScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis-Plus Mapper 接口，操作 {@link PayrollSchemeScope} (账套适用范围／薪酬方案适用范围) 实体。
 *
 * <p>提供账套(薪酬方案)与适用范围(部门/岗位/员工)关联关系的增删查改操作，
 * 继承 {@link BaseMapper} 获得标准的 CRUD 能力，并扩展按账套 ID 查询和删除的自定义方法。</p>
 *
 * @see PayrollSchemeScope
 */
@Mapper
public interface PayrollSchemeScopeMapper extends BaseMapper<PayrollSchemeScope> {

    /**
     * 根据账套 ID 查询所有适用范围记录。
     *
     * <p>查询结果通常包含该账套所绑定的部门、岗位或具体员工的范围关系，
     * 用于在前端展示或计算薪酬发放范围时使用。</p>
     *
     * @param schemeId 账套 ID，不可为 {@code null}
     * @return 匹配的适用范围列表；若不存在则返回空列表
     */
    List<PayrollSchemeScope> selectBySchemeId(@Param("schemeId") Long schemeId);

    /**
     * 根据账套 ID 删除所有适用范围记录。
     *
     * <p>通常在更新账套适用范围时先删后插，以保证范围数据的原子性。
     * 删除操作会级联影响该账套下所有已绑定的范围关系。</p>
     *
     * @param schemeId 账套 ID，不可为 {@code null}
     * @return 实际删除的记录行数
     */
    int deleteBySchemeId(@Param("schemeId") Long schemeId);
}
