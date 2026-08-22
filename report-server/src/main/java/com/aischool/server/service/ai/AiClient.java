package com.aischool.server.service.ai;

import com.aischool.server.common.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * OpenAI 兼容 Chat Completions 客户端（与数字人一期环境同源，可随时切换供应商）。
 * api-key 留空 = 未启用 LLM，调用方降级为规则模板草稿。
 */
@Slf4j
@Component
public class AiClient {

    private final ObjectMapper om = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final RestClient client;

    public AiClient(@Value("${aischool.ai.base-url}") String baseUrl,
                    @Value("${aischool.ai.api-key}") String apiKey,
                    @Value("${aischool.ai.model}") String model,
                    @Value("${aischool.ai.timeout-seconds}") int timeoutSeconds) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean enabled() {
        return !baseUrl.isEmpty() && !apiKey.isEmpty() && !model.isEmpty();
    }

    /** 单轮补全；messages = [system, user] */
    public String chat(String system, String user) {
        if (!enabled()) {
            throw new BizException(503, "AI 未配置（aischool.ai.base-url/api-key/model）");
        }
        ObjectNode body = om.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.7);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.add(om.createObjectNode().put("role", "system").put("content", system));
        messages.add(om.createObjectNode().put("role", "user").put("content", user));
        return postChat(body);
    }

    /** 多模态单轮：证书图片识别。dataUrl = "data:image/jpeg;base64,..." */
    public String chatVision(String system, String userText, String dataUrl) {
        if (!enabled()) {
            throw new BizException(503, "AI 未配置（aischool.ai.base-url/api-key/model）");
        }
        ObjectNode body = om.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.1);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.add(om.createObjectNode().put("role", "system").put("content", system));
        ObjectNode userMsg = om.createObjectNode().put("role", "user");
        ArrayNode content = userMsg.putArray("content");
        content.add(om.createObjectNode().put("type", "text").put("text", userText));
        ObjectNode img = om.createObjectNode().put("type", "image_url");
        img.putObject("image_url").put("url", dataUrl);
        content.add(img);
        messages.add(userMsg);
        return postChat(body);
    }

    private String postChat(ObjectNode body) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!url.endsWith("/chat/completions")) {
            url = url + "/chat/completions";
        }
        try {
            JsonNode resp = client.post().uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve().body(JsonNode.class);
            String content = resp == null ? null
                    : resp.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new BizException(502, "AI 返回为空");
            }
            return content.trim();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 调用失败: {}", e.getMessage());
            throw new BizException(502, "AI 调用失败: " + e.getMessage());
        }
    }
}
