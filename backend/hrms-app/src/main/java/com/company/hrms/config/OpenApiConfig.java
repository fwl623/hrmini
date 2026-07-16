package com.company.hrms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置
 *
 * 访问 Swagger UI：http://localhost:8080/swagger-ui/index.html
 * 访问 OpenAPI JSON：http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HRMS 人资管理系统 API")
                        .version("v1.0.0")
                        .description("考勤、请假、加班、薪资一体化管理接口")
                        .contact(new Contact()
                                .name("HRMS Team")));
    }
}
