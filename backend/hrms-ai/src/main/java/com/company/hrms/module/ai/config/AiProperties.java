package com.company.hrms.module.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 配置绑定（前缀 hrms.ai）：API Key、聊天/向量网关、模型名、Qdrant 连接等。
 */
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
    private Knowledge knowledge = new Knowledge();
    private Rag rag = new Rag();

    /** 对话模型配置。 */
    @Data
    public static class Chat {
        private String model = "deepseek-v4-flash";
        private boolean enableThinking = false;
        /** 采样温度 0～2，默认 0.3 偏稳妥 */
        private double temperature = 0.3;
    }

    /** 知识库分块默认值（可被运行时设置覆盖）。 */
    @Data
    public static class Knowledge {
        private int chunkSize = 500;
        private int chunkOverlap = 50;
        private int embedBatchSize = 8;
    }

    /** RAG 检索默认值。 */
    @Data
    public static class Rag {
        private int topK = 5;
    }

    /** 向量模型配置。 */
    @Data
    public static class Embedding {
        private String model = "text-embedding-v4";
        private int dimensions = 1024;
    }

    /** Qdrant 向量库连接配置。 */
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
