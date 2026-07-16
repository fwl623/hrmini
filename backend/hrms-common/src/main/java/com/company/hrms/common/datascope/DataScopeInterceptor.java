package com.company.hrms.common.datascope;

import com.company.hrms.common.enums.DataScopeType;
import com.company.hrms.common.security.LoginUser;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
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
 * 数据权限 MyBatis 拦截器：将 {@link DataScopeContext} 中的 SQL 片段追加到查询。
 * <p>
 * 片段由 {@link DataScopeAspect} 在带 {@link DataScope} 的方法上写入；
 * XML 亦可使用 {@code ${@com.company.hrms.common.datascope.DataScopeContext@get()}} 或自行读取 Context。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class DataScopeInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        String fragment = DataScopeContext.get();
        if (fragment == null || fragment.isBlank()) {
            return invocation.proceed();
        }
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];
        BoundSql boundSql = ms.getBoundSql(parameter);
        String newSql = applyFragment(boundSql.getSql(), fragment);
        if (!newSql.equals(boundSql.getSql())) {
            BoundSql newBoundSql = new BoundSql(ms.getConfiguration(), newSql,
                    boundSql.getParameterMappings(), parameter);
            for (String name : boundSql.getParameterMappings().stream()
                    .map(pm -> pm.getProperty()).toList()) {
                if (boundSql.hasAdditionalParameter(name)) {
                    newBoundSql.setAdditionalParameter(name, boundSql.getAdditionalParameter(name));
                }
            }
            // MP 分页等场景额外参数
            copyAdditionalParameters(boundSql, newBoundSql);
            MappedStatement newMs = copyMappedStatement(ms, new BoundSqlSqlSource(newBoundSql));
            args[0] = newMs;
        }
        return invocation.proceed();
    }

    @SuppressWarnings("unchecked")
    private static void copyAdditionalParameters(BoundSql source, BoundSql target) {
        try {
            var field = BoundSql.class.getDeclaredField("additionalParameters");
            field.setAccessible(true);
            Object map = field.get(source);
            if (map instanceof java.util.Map<?, ?> m) {
                m.forEach((k, v) -> {
                    if (k instanceof String key) {
                        target.setAdditionalParameter(key, v);
                    }
                });
            }
        } catch (ReflectiveOperationException ignored) {
            // 无额外参数时可忽略
        }
    }

    /**
     * AUTO → 用户角色范围；显式 ALL/SELF/... → 以注解为准。
     */
    static DataScopeType resolve(DataScopeType annotated, LoginUser user) {
        if (annotated == null || annotated == DataScopeType.AUTO) {
            return user == null || user.getDataScope() == null
                    ? DataScopeType.SELF
                    : user.getDataScope();
        }
        return annotated;
    }

    /**
     * 将 AND ... 片段插到 ORDER BY / LIMIT 之前。
     */
    static String applyFragment(String sql, String fragment) {
        if (sql == null || sql.isBlank() || fragment == null || fragment.isBlank()) {
            return sql;
        }
        String lower = sql.toLowerCase();
        int orderBy = lower.lastIndexOf(" order by ");
        int limit = lower.lastIndexOf(" limit ");
        int cut = -1;
        if (orderBy >= 0 && limit >= 0) {
            cut = Math.min(orderBy, limit);
        } else if (orderBy >= 0) {
            cut = orderBy;
        } else if (limit >= 0) {
            cut = limit;
        }
        if (cut >= 0) {
            return sql.substring(0, cut) + fragment + sql.substring(cut);
        }
        return sql + fragment;
    }

    private static MappedStatement copyMappedStatement(MappedStatement ms, SqlSource sqlSource) {
        MappedStatement.Builder builder = new MappedStatement.Builder(
                ms.getConfiguration(), ms.getId(), sqlSource, ms.getSqlCommandType());
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.timeout(ms.getTimeout());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if (ms.getKeyProperties() != null) {
            builder.keyProperty(String.join(",", ms.getKeyProperties()));
        }
        builder.databaseId(ms.getDatabaseId());
        builder.lang(ms.getLang());
        builder.resultOrdered(ms.isResultOrdered());
        builder.resultSets(ms.getResultSets() == null ? null : String.join(",", ms.getResultSets()));
        builder.resultMaps(ms.getResultMaps());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        builder.cache(ms.getCache());
        return builder.build();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }

    private static final class BoundSqlSqlSource implements SqlSource {
        private final BoundSql boundSql;

        private BoundSqlSqlSource(BoundSql boundSql) {
            this.boundSql = boundSql;
        }

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            return boundSql;
        }
    }
}
