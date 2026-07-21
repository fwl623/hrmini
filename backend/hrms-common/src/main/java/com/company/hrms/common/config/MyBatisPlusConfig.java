package com.company.hrms.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.company.hrms.common.datascope.DataScopeInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.Map;

/**
 * MyBatis-Plus 配置
 */
@Configuration
public class MyBatisPlusConfig implements ApplicationListener<ContextRefreshedEvent> {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /** 显式挂载 DataScope 插件，避免漏注册导致行级越权 */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        DataScopeInterceptor dataScopeInterceptor =
                event.getApplicationContext().getBean(DataScopeInterceptor.class);
        Map<String, SqlSessionFactory> factories =
                event.getApplicationContext().getBeansOfType(SqlSessionFactory.class);
        for (SqlSessionFactory factory : factories.values()) {
            var conf = factory.getConfiguration();
            boolean already = conf.getInterceptors().stream()
                    .anyMatch(i -> i instanceof DataScopeInterceptor);
            if (!already) {
                conf.addInterceptor(dataScopeInterceptor);
            }
        }
    }
}
