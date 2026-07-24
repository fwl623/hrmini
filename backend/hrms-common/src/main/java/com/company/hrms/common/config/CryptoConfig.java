package com.company.hrms.common.config;

import com.company.hrms.common.util.AesEncryptUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 敏感字段加解密 Bean：提供 {@link AesEncryptUtil}。
 * 密钥必须来自配置 {@code hrms.crypto.aes-key}（Base64，解码后 32 字节），禁止硬编码。
 */
@Configuration
public class CryptoConfig {

    /**
     * 密钥必须通过配置提供：hrms.crypto.aes-key（Base64，解码后 32 字节）。
     * 禁止代码内硬编码 fallback。
     */
    @Bean
    public AesEncryptUtil aesEncryptUtil(
            @Value("${hrms.crypto.aes-key}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException(
                    "hrms.crypto.aes-key must be configured (Base64 of 32-byte key)");
        }
        return AesEncryptUtil.fromBase64Key(base64Key);
    }
}
