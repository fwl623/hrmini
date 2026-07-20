package com.company.hrms.module.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "hrms.ai")
public class AiProperties {

    /** 阿里云百炼 API Key */
    private String apiKey = "";

    /**
     * 聊天 OpenAI 兼容网关。
     * 控制台华北2示例：https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
     */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /**
     * 向量 OpenAI 兼容网关；为空则复用 {@link #baseUrl}。
     */
    private String embeddingBaseUrl = "";

    private Chat chat = new Chat();
    private Embedding embedding = new Embedding();
    private Qdrant qdrant = new Qdrant();

    @Data
    public static class Chat {
        private String model = "deepseek-v4-flash";
        private boolean enableThinking = false;
    }

    @Data
    public static class Embedding {
        private String model = "text-embedding-v4";
        private int dimensions = 1024;
    }

    @Data
    public static class Qdrant {
        private String host = "127.0.0.1";
        private int port = 6333;
        private String collection = "hrms_knowledge";
    }

    public boolean hasApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        // 占位文案不算已配置
        return !apiKey.contains("在此填写")
                && !apiKey.contains("YOUR_")
                && !apiKey.equalsIgnoreCase("sk-xxx");
    }

    public String chatBaseUrl() {
        return trimSlash(baseUrl);
    }

    public String resolveEmbeddingBaseUrl() {
        if (embeddingBaseUrl != null && !embeddingBaseUrl.isBlank()) {
            return trimSlash(embeddingBaseUrl);
        }
        return chatBaseUrl();
    }

    public String qdrantBaseUrl() {
        return "http://" + qdrant.getHost() + ":" + qdrant.getPort();
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
