package com.company.hrms.module.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 运行时参数：默认取自 {@link AiProperties}，管理员可在线调整并尽量持久化到 Redis。
 * 分块参数仅影响后续新上传文档。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRuntimeSettings {

    public static final String REDIS_KEY = "hrms:ai:runtime-settings";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    private volatile int chunkSize;
    private volatile int chunkOverlap;
    private volatile int topK;
    private volatile double temperature;
    private volatile String chatModel;
    private volatile boolean enableThinking;

    @PostConstruct
    public void init() {
        resetFromProperties();
        loadFromRedis();
    }

    public void resetFromProperties() {
        AiProperties.Knowledge k = properties.getKnowledge();
        AiProperties.Rag r = properties.getRag();
        AiProperties.Chat c = properties.getChat();
        this.chunkSize = k.getChunkSize();
        this.chunkOverlap = k.getChunkOverlap();
        this.topK = r.getTopK();
        this.temperature = c.getTemperature();
        this.chatModel = c.getModel();
        this.enableThinking = c.isEnableThinking();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(chunkSize, chunkOverlap, topK, temperature, chatModel, enableThinking,
                properties.getEmbedding().getModel(),
                properties.getEmbedding().getDimensions(),
                properties.hasApiKey());
    }

    public synchronized Snapshot update(UpdateRequest req) {
        if (req.getChunkSize() != null) {
            int v = req.getChunkSize();
            if (v < 100 || v > 4000) {
                throw new com.company.hrms.common.exception.BusinessException(10001, "分块大小需在 100～4000");
            }
            this.chunkSize = v;
        }
        if (req.getChunkOverlap() != null) {
            int v = req.getChunkOverlap();
            if (v < 0 || v >= chunkSize) {
                throw new com.company.hrms.common.exception.BusinessException(10001, "分块重叠需 ≥0 且小于分块大小");
            }
            this.chunkOverlap = v;
        }
        if (req.getTopK() != null) {
            int v = req.getTopK();
            if (v < 1 || v > 20) {
                throw new com.company.hrms.common.exception.BusinessException(10001, "检索条数 topK 需在 1～20");
            }
            this.topK = v;
        }
        if (req.getTemperature() != null) {
            double v = req.getTemperature();
            if (v < 0 || v > 2) {
                throw new com.company.hrms.common.exception.BusinessException(10001, "temperature 需在 0～2");
            }
            this.temperature = v;
        }
        if (req.getChatModel() != null && !req.getChatModel().isBlank()) {
            this.chatModel = req.getChatModel().trim();
        }
        if (req.getEnableThinking() != null) {
            this.enableThinking = req.getEnableThinking();
        }
        persist();
        return snapshot();
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public int getTopK() {
        return topK;
    }

    public double getTemperature() {
        return temperature;
    }

    public String getChatModel() {
        return chatModel;
    }

    public boolean isEnableThinking() {
        return enableThinking;
    }

    private void loadFromRedis() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            String json = redis.opsForValue().get(REDIS_KEY);
            if (json == null || json.isBlank()) {
                return;
            }
            PersistPayload p = objectMapper.readValue(json, PersistPayload.class);
            if (p.chunkSize != null) this.chunkSize = p.chunkSize;
            if (p.chunkOverlap != null) this.chunkOverlap = p.chunkOverlap;
            if (p.topK != null) this.topK = p.topK;
            if (p.temperature != null) this.temperature = p.temperature;
            if (p.chatModel != null && !p.chatModel.isBlank()) this.chatModel = p.chatModel;
            if (p.enableThinking != null) this.enableThinking = p.enableThinking;
            log.info("Loaded AI runtime settings from Redis");
        } catch (Exception e) {
            log.warn("Load AI runtime settings from Redis failed: {}", e.getMessage());
        }
    }

    private void persist() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            PersistPayload p = new PersistPayload();
            p.chunkSize = chunkSize;
            p.chunkOverlap = chunkOverlap;
            p.topK = topK;
            p.temperature = temperature;
            p.chatModel = chatModel;
            p.enableThinking = enableThinking;
            redis.opsForValue().set(REDIS_KEY, objectMapper.writeValueAsString(p));
        } catch (Exception e) {
            log.warn("Persist AI runtime settings failed: {}", e.getMessage());
        }
    }

    @Data
    public static class Snapshot {
        private final int chunkSize;
        private final int chunkOverlap;
        private final int topK;
        private final double temperature;
        private final String chatModel;
        private final boolean enableThinking;
        private final String embeddingModel;
        private final int embeddingDimensions;
        private final boolean apiKeyConfigured;
    }

    @Data
    public static class UpdateRequest {
        private Integer chunkSize;
        private Integer chunkOverlap;
        private Integer topK;
        private Double temperature;
        private String chatModel;
        private Boolean enableThinking;
    }

    @Data
    private static class PersistPayload {
        private Integer chunkSize;
        private Integer chunkOverlap;
        private Integer topK;
        private Double temperature;
        private String chatModel;
        private Boolean enableThinking;
    }
}
