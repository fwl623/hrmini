package com.company.hrms.module.ai.client;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.module.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Qdrant HTTP API（6333）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantClient {

    private final AiProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void ensureCollection() {
        try {
            String collection = properties.getQdrant().getCollection();
            RestClient client = qdrant();
            try {
                client.get().uri("/collections/{name}", collection).retrieve().toBodilessEntity();
                log.info("Qdrant collection exists: {}", collection);
                return;
            } catch (Exception ignored) {
                // create below
            }
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode vectors = body.putObject("vectors");
            vectors.put("size", properties.getEmbedding().getDimensions());
            vectors.put("distance", "Cosine");
            client.put()
                    .uri("/collections/{name}", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Created Qdrant collection: {}", collection);
        } catch (Exception e) {
            log.warn("Qdrant ensureCollection skipped/failed (service may start without Qdrant): {}", e.getMessage());
        }
    }

    public void upsertPoints(List<Point> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode arr = body.putArray("points");
            for (Point p : points) {
                ObjectNode node = arr.addObject();
                node.put("id", p.id());
                ArrayNode vector = node.putArray("vector");
                for (float v : p.vector()) {
                    vector.add(v);
                }
                ObjectNode payload = node.putObject("payload");
                payload.put("docId", p.docId());
                payload.put("title", p.title());
                payload.put("content", p.content());
                payload.put("chunkIndex", p.chunkIndex());
                payload.put("enabled", p.enabled());
            }
            qdrant().put()
                    .uri("/collections/{name}/points?wait=true", properties.getQdrant().getCollection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Qdrant upsert failed", e);
            throw new BusinessException(90001, "写入 Qdrant 失败: " + e.getMessage());
        }
    }

    public void deleteByDocId(long docId) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode filter = body.putObject("filter");
            ArrayNode must = filter.putArray("must");
            ObjectNode matchCond = must.addObject();
            matchCond.put("key", "docId");
            matchCond.putObject("match").put("value", docId);

            qdrant().post()
                    .uri("/collections/{name}/points/delete?wait=true", properties.getQdrant().getCollection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Qdrant deleteByDocId failed", e);
            throw new BusinessException(90001, "删除 Qdrant 向量失败: " + e.getMessage());
        }
    }

    public void setEnabledByDocId(long docId, boolean enabled) {
        // 检索时按 enabled 过滤；启停通过 scroll+set payload 较重，V1 用删除再检索侧过滤 MySQL enabled
        // 这里用 set payload 批量更新
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode filter = body.putObject("filter");
            ArrayNode must = filter.putArray("must");
            ObjectNode matchCond = must.addObject();
            matchCond.put("key", "docId");
            matchCond.putObject("match").put("value", docId);
            ObjectNode payload = body.putObject("payload");
            payload.put("enabled", enabled);

            qdrant().post()
                    .uri("/collections/{name}/points/payload?wait=true", properties.getQdrant().getCollection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Qdrant setEnabledByDocId failed: {}", e.getMessage());
        }
    }

    public List<SearchHit> search(float[] vector, int topK) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode vec = body.putArray("vector");
            for (float v : vector) {
                vec.add(v);
            }
            body.put("limit", topK);
            body.put("with_payload", true);
            ObjectNode filter = body.putObject("filter");
            ArrayNode must = filter.putArray("must");
            ObjectNode matchCond = must.addObject();
            matchCond.put("key", "enabled");
            matchCond.putObject("match").put("value", true);

            String raw = qdrant().post()
                    .uri("/collections/{name}/points/search", properties.getQdrant().getCollection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            JsonNode result = root.path("result");
            List<SearchHit> hits = new ArrayList<>();
            if (result.isArray()) {
                for (JsonNode item : result) {
                    JsonNode payload = item.path("payload");
                    hits.add(new SearchHit(
                            payload.path("docId").asLong(),
                            payload.path("title").asText(""),
                            payload.path("content").asText(""),
                            item.path("score").asDouble(0)
                    ));
                }
            }
            return hits;
        } catch (Exception e) {
            log.error("Qdrant search failed", e);
            throw new BusinessException(90001, "Qdrant 检索失败: " + e.getMessage());
        }
    }

    public static String newPointId() {
        return UUID.randomUUID().toString();
    }

    private RestClient qdrant() {
        return restClientBuilder.baseUrl(properties.qdrantBaseUrl()).build();
    }

    public record Point(String id, long docId, String title, String content, int chunkIndex, boolean enabled, float[] vector) {
    }

    public record SearchHit(long docId, String title, String content, double score) {
    }
}
