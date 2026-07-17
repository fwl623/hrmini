package com.company.hrms;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.company.hrms")
// 仅注册带 @Mapper 的接口；勿扫整个 com.company.hrms（会把 AuthService 等业务接口当成 Mapper）
@MapperScan(basePackages = "com.company.hrms", annotationClass = Mapper.class)
@EnableScheduling
public class HrmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrmsApplication.class, args);
    }
}
