package com.company.hrms.common.file;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地文件上传配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hrms.upload")
public class FileStorageProperties {

    /** 本地存储目录（相对或绝对路径） */
    private String dir = "./data/uploads";

    /** 单文件最大字节数，默认 10MB */
    private long maxSizeBytes = 10L * 1024 * 1024;
}
