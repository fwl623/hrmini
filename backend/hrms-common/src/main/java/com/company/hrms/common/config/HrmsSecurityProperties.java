package com.company.hrms.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内部调用 Token、短信联调码等安全相关配置（前缀 {@code hrms.*}）。
 * JWT 密钥等见 application.yml 中 {@code hrms.jwt} / {@code hrms.crypto}。
 */
@Component
@ConfigurationProperties(prefix = "hrms")
public class HrmsSecurityProperties {

    private final Internal internal = new Internal();
    private final Sms sms = new Sms();

    public Internal getInternal() {
        return internal;
    }

    public Sms getSms() {
        return sms;
    }

    public static class Internal {
        /** 模块间调用头 X-Internal-Token 的共享密钥 */
        private String token = "hrms-dev-internal-token-change-me";

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    public static class Sms {
        /** 为 true 时允许开发验证码（仅本地/联调） */
        private boolean devEnabled = true;
        private String devCode = "123456";

        public boolean isDevEnabled() {
            return devEnabled;
        }

        public void setDevEnabled(boolean devEnabled) {
            this.devEnabled = devEnabled;
        }

        public String getDevCode() {
            return devCode;
        }

        public void setDevCode(String devCode) {
            this.devCode = devCode;
        }
    }
}
