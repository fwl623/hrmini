package com.company.hrms.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 为所有 @RestController 统一加上 /api/v1 前缀；CORS 来源可配置。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${hrms.cors.allowed-origins:http://localhost:8000}")
    private String allowedOrigins;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 业务 Controller 统一加 /api/v1；排除 Spring / springdoc，避免 Swagger 落到 /api/v1/v3/api-docs
        configurer.addPathPrefix("/api/v1", c -> c.isAnnotationPresent(RestController.class)
                && !c.getPackageName().startsWith("org.springframework")
                && !c.getPackageName().startsWith("org.springdoc"));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = StringUtils.tokenizeToStringArray(allowedOrigins, ",");
        registry.addMapping("/api/v1/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
