package com.aischool.server.service.ai;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.SysConfig;
import com.aischool.server.mapper.SysConfigMapper;
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
 * 批35 学校自治：配置动态读 t_sys_config（ai_base_url/ai_api_key/ai_model），
 * yml 值仅作兜底默认——管理端「AI 设置」改完即时生效，无需重启容器。
 * api-key 为空 = 未启用 LLM，调用方降级为规则模板草稿。
 */
@Slf4j
@Component
public class AiClient {

    private final ObjectMapper om = new ObjectMapper();
    private final SysConfigMapper sysConfigMapper;
    /** yml 兜底默认（t_sys_config 无值时使用） */
    private final String dftBaseUrl;
    private final String dftApiKey;
    private final String dftModel;
    private final RestClient client;

    public AiClient(SysConfigMapper sysConfigMapper,
                    @Value("${aischool.ai.base-url}") String baseUrl,
                    @Value("${aischool.ai.api-key}") String apiKey,
                    @Value("${aischool.ai.model}") String model,
                    @Value("${aischool.ai.timeout-seconds}") int timeoutSeconds) {
        this.sysConfigMapper = sysConfigMapper;
        this.dftBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.dftApiKey = apiKey == null ? "" : apiKey.trim();
        this.dftModel = model == null ? "" : model.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    /** 当前生效配置：t_sys_config 优先，无值回退 yml */
    public record AiConfig(String baseUrl, String apiKey, String model) {
        public boolean complete() {
            return !baseUrl.isEmpty() && !apiKey.isEmpty() && !model.isEmpty();
        }
    }

    public AiConfig currentConfig() {
        return new AiConfig(
                resolve("ai_base_url", dftBaseUrl),
                resolve("ai_api_key", dftApiKey),
                resolve("ai_model", dftModel));
    }

    private String resolve(String key, String dft) {
        SysConfig c = sysConfigMapper.selectById(key);
        String v = c == null || c.getCfgValue() == null ? "" : c.getCfgValue().trim();
        return v.isEmpty() ? dft : v;
    }

    public boolean enabled() {
        return currentConfig().complete();
    }

    /** 补全结果：内容 + 供应商返回的 token 用量（响应缺 usage 字段时为 0） */
    public record ChatResult(String content, int promptTokens, int completionTokens) {}

    /** 单轮补全；messages = [system, user] */
    public String chat(String system, String user) {
        return chatWithUsage(system, user).content();
    }

    /** 同 chat()，额外带回 token 用量（供任务落库做用量统计） */
    public ChatResult chatWithUsage(String system, String user) {
        AiConfig cfg = requireConfig();
        ObjectNode body = om.createObjectNode();
        body.put("model", cfg.model());
        body.put("temperature", 0.7);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.add(om.createObjectNode().put("role", "system").put("content", system));
        messages.add(om.createObjectNode().put("role", "user").put("content", user));
        return postChat(cfg, body);
    }

    /** 多模态单轮：证书图片识别。dataUrl = "data:image/jpeg;base64,..." */
    public String chatVision(String system, String userText, String dataUrl) {
        AiConfig cfg = requireConfig();
        ObjectNode body = om.createObjectNode();
        body.put("model", cfg.model());
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
        return postChat(cfg, body).content();
    }

    /** 测试连接（管理端「AI 设置」）：用显式传入配置发一条最小补全，返回模型回话 */
    public String testConnection(AiConfig cfg) {
        return postChat(cfg, minimalBody(cfg.model())).content();
    }

    private ObjectNode minimalBody(String model) {
        ObjectNode body = om.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 8);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.add(om.createObjectNode().put("role", "user").put("content", "回复「连接成功」四个字"));
        return body;
    }

    private AiConfig requireConfig() {
        AiConfig cfg = currentConfig();
        if (!cfg.complete()) {
            throw new BizException(503, "AI 未配置（请在管理端「系统管理 → AI 设置」填写服务商地址 / API key / 模型名）");
        }
        return cfg;
    }

    private ChatResult postChat(AiConfig cfg, ObjectNode body) {
        String url = cfg.baseUrl().endsWith("/") ? cfg.baseUrl().substring(0, cfg.baseUrl().length() - 1) : cfg.baseUrl();
        if (!url.endsWith("/chat/completions")) {
            url = url + "/chat/completions";
        }
        try {
            JsonNode resp = client.post().uri(url)
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve().body(JsonNode.class);
            String content = resp == null ? null
                    : resp.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new BizException(502, "AI 返回为空");
            }
            JsonNode usage = resp.path("usage");
            return new ChatResult(content.trim(),
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 调用失败: {}", e.getMessage());
            throw new BizException(502, "AI 调用失败: " + e.getMessage());
        }
    }
}
