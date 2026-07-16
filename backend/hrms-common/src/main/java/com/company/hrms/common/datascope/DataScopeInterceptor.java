package com.company.hrms.common.datascope;

import com.company.hrms.common.enums.DataScopeType;
import com.company.hrms.common.security.LoginUser;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 数据权限 MyBatis 拦截器骨架。
 * <p>
 * 解析路径：{@code @DataScope.value()} → AUTO 则用 LoginUser.dataScope，否则用注解强制值；
 * 再按 DEPT_TREE/SELF 等拼 SQL 写入 {@link DataScopeContext}。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class DataScopeInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // TODO Sprint1: 解析 @DataScope → resolve() → 拼 SQL 片段写入 DataScopeContext
        // 安全：deptId/employeeId 只用预编译参数或白名单数字，禁止把用户输入拼进 ${}
        try {
            return invocation.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    /**
     * AUTO → 用户角色范围；显式 ALL/SELF/... → 以注解为准（业务强制收紧时用）。
     */
    static DataScopeType resolve(DataScopeType annotated, LoginUser user) {
        if (annotated == null || annotated == DataScopeType.AUTO) {
            return user == null || user.getDataScope() == null
                    ? DataScopeType.SELF
                    : user.getDataScope();
        }
        return annotated;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }
}
