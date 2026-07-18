package com.company.hrms.common.datascope;

import com.company.hrms.common.enums.DataScopeType;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 捕获 {@link DataScope}，解析范围并写入 {@link DataScopeContext}，供 MyBatis 拦截器追加 WHERE。
 */
@Aspect
@Component
public class DataScopeAspect {

    @Around("@annotation(com.company.hrms.common.datascope.DataScope) || @within(com.company.hrms.common.datascope.DataScope)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        DataScope ann = AnnotationUtils.findAnnotation(method, DataScope.class);
        if (ann == null) {
            ann = AnnotationUtils.findAnnotation(method.getDeclaringClass(), DataScope.class);
        }
        if (ann == null) {
            return pjp.proceed();
        }

        LoginUser user = SecurityUtils.getLoginUser();
        DataScopeType resolved = DataScopeInterceptor.resolve(ann.value(), user);
        String fragment = DataScopeSqlBuilder.build(resolved, user, ann);
        // SELF/DEPT_TREE → AND ...；ALL/PAYROLL/NONE_PAYROLL → ""（明确不过滤）
        DataScopeContext.set(fragment);
        try {
            return pjp.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }
}
