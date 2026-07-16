package com.company.hrms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI（springdoc）：http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hrmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HRMS API")
                        .description("人资管理系统接口（含审批中心 / 入职申请）")
                        .version("0.1.0"));
    }

    /** 开发期：在 Swagger 中统一露出 X-User-Id，便于模拟当前用户 */
    @Bean
    public OperationCustomizer userIdHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name("X-User-Id")
                    .description("开发期当前用户（默认 1002=部门负责人，1001=HR）")
                    .required(false)
                    .schema(new StringSchema()._default("1002")));
            return operation;
        };
    }
}
