package com.company.hrms.module.ai.client;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.module.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 阿里云百炼 OpenAI 兼容：Chat Completions（流式）+ Embeddings。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BailianClient {

    private final AiProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public void requireApiKey() {
        if (!properties.hasApiKey()) {
            throw new BusinessException(10001, "未配置百炼 API Key（hrms.ai.api-key / DASHSCOPE_API_KEY）");
        }
    }

    public List<float[]> embed(List<String> texts) {
        requireApiKey();
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", properties.getEmbedding().getModel());
            body.put("dimensions", properties.getEmbedding().getDimensions());
            ArrayNode input = body.putArray("input");
            texts.forEach(input::add);

            String raw = embeddingClient()
                    .post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            List<float[]> vectors = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    JsonNode emb = item.path("embedding");
                    float[] vec = new float[emb.size()];
                    for (int i = 0; i < emb.size(); i++) {
                        vec[i] = (float) emb.get(i).asDouble();
                    }
                    vectors.add(vec);
                }
            }
            if (vectors.size() != texts.size()) {
                throw new BusinessException(90001, "Embedding 返回数量与输入不一致");
            }
            return vectors;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Bailian embedding failed", e);
            throw new BusinessException(90001, "调用百炼 Embedding 失败: " + e.getMessage());
        }
    }

    public float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    /**
     * 流式对话：将每个 content delta 交给 onDelta；返回完整拼接文本。
     */
    public String chatStream(String systemPrompt, String userPrompt, Consumer<String> onDelta) {
        requireApiKey();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", properties.getChat().getModel());
            body.put("stream", true);
            body.put("enable_thinking", properties.getChat().isEnableThinking());
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            StringBuilder full = new StringBuilder();
            chatClient()
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(objectMapper.writeValueAsString(body))
                    .exchange((req, res) -> {
                        if (res.getStatusCode().isError()) {
                            String err = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            throw new BusinessException(90001, "百炼对话失败: " + err);
                        }
                        try (InputStream in = res.getBody();
                             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank()) {
                                    continue;
                                }
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String data = line.substring(5).trim();
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                JsonNode root = objectMapper.readTree(data);
                                JsonNode delta = root.path("choices").path(0).path("delta");
                                String content = delta.path("content").asText(null);
                                if (content != null && !content.isEmpty()) {
                                    full.append(content);
                                    if (onDelta != null) {
                                        onDelta.accept(content);
                                    }
                                }
                            }
                        }
                        return null;
                    });
            return full.toString();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Bailian chat stream failed", e);
            throw new BusinessException(90001, "调用百炼对话失败: " + e.getMessage());
        }
    }

    private RestClient chatClient() {
        return restClientBuilder
                .baseUrl(properties.chatBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    private RestClient embeddingClient() {
        return restClientBuilder
                .baseUrl(properties.resolveEmbeddingBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }
}
