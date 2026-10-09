package com.aischool.server.service.ai;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.SysConfig;
import com.aischool.server.mapper.SysConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;

/**
 * OpenAI Images 协议兼容垫图改图客户端（批40c IP 成长画像·垫图版）。
 * POST {base}/images/edits（multipart）：image=素材库 IP 底图（男小石/女小萌）+ prompt=画面改动英文描述。
 * 与文本 AI（AiClient）两把 key 完全独立：img_base_url/img_api_key/img_model，
 * 配置动态读 t_sys_config（yml 兜底）——管理端「AI 设置 → 图像生成」改完即时生效。
 * img 配置为空 = 未启用，调用方降级（IP 原图占位），不报错。
 * 供应商实测（fluxapi.cloud 聚合）：edits 偶发 upstream_error（限流），固定退避 30s 重试 1 次。
 */
@Slf4j
@Component
public class AiImageClient {

    private final SysConfigMapper sysConfigMapper;
    /** yml 兜底默认（t_sys_config 无值时使用） */
    private final String dftBaseUrl;
    private final String dftApiKey;
    private final String dftModel;
    private final RestClient client;

    public AiImageClient(SysConfigMapper sysConfigMapper,
                         @Value("${aischool.ai.img-base-url}") String baseUrl,
                         @Value("${aischool.ai.img-api-key}") String apiKey,
                         @Value("${aischool.ai.img-model}") String model,
                         @Value("${aischool.ai.img-timeout-seconds}") int timeoutSeconds) {
        this.sysConfigMapper = sysConfigMapper;
        this.dftBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.dftApiKey = apiKey == null ? "" : apiKey.trim();
        this.dftModel = model == null ? "" : model.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        // 改图比文生图更慢（实测 25~60s + 偶发限流重试），读超时给足
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    /** 当前生效配置：t_sys_config 优先，无值回退 yml */
    public record ImgConfig(String baseUrl, String apiKey, String model) {
        public boolean complete() {
            return !baseUrl.isEmpty() && !apiKey.isEmpty() && !model.isEmpty();
        }
    }

    public ImgConfig currentConfig() {
        return new ImgConfig(
                resolve("img_base_url", dftBaseUrl),
                resolve("img_api_key", dftApiKey),
                resolve("img_model", dftModel));
    }

    private String resolve(String key, String dft) {
        SysConfig c = sysConfigMapper.selectById(key);
        String v = c == null || c.getCfgValue() == null ? "" : c.getCfgValue().trim();
        return v.isEmpty() ? dft : v;
    }

    public boolean enabled() {
        return currentConfig().complete();
    }

    /** 底图改一张图，返回图片字节（mime 按 magicOf 判定） */
    public byte[] edit(byte[] baseImage, String prompt) {
        return editWith(baseImage, prompt, requireConfig());
    }

    /** 测试改图（管理端）：用显式传入或已保存配置真实生成一次，返回 dataUri 供页面预览 */
    public String testDataUri(byte[] baseImage, String prompt, ImgConfig cfg) {
        byte[] bytes = editWith(baseImage, prompt, cfg);
        return "data:" + magicOf(bytes) + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private ImgConfig requireConfig() {
        ImgConfig cfg = currentConfig();
        if (!cfg.complete()) {
            throw new BizException(503, "图像 AI 未配置（请在管理端「AI 设置 → 图像生成」填写服务商地址 / API key / 模型名）");
        }
        return cfg;
    }

    /** 限流退避重试：第 1 次失败睡 30s 再试一次（批渲染后台 worker 可等；再失败由调用方降级） */
    private byte[] editWith(byte[] baseImage, String prompt, ImgConfig cfg) {
        String url = normalizeUrl(cfg.baseUrl());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", cfg.model());
        body.add("prompt", prompt);
        body.add("image", new ByteArrayResource(baseImage) {
            @Override
            public String getFilename() {
                return "base.png"; // edits 端点要求文件名带扩展名
            }
        });
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(30_000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BizException(502, "图像 AI 调用被中断");
                }
            }
            try {
                JsonNode resp = client.post().uri(url)
                        .header("Authorization", "Bearer " + cfg.apiKey())
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body)
                        .retrieve().body(JsonNode.class);
                JsonNode data0 = resp == null ? null : resp.path("data").path(0);
                String b64 = data0 == null ? null : data0.path("b64_json").asText(null);
                if (b64 != null && !b64.isBlank()) {
                    return Base64.getDecoder().decode(b64);
                }
                String remote = data0 == null ? null : data0.path("url").asText(null);
                if (remote != null && !remote.isBlank()) {
                    byte[] bytes = client.get().uri(remote).retrieve().body(byte[].class);
                    if (bytes != null && bytes.length > 0) {
                        return bytes;
                    }
                }
                throw new BizException(502, "图像 AI 返回为空（无 b64_json 也无 url）");
            } catch (BizException e) {
                throw e; // 业务性错误（配置/返回空）不重试
            } catch (Exception e) {
                last = new BizException(502, "图像 AI 调用失败: " + e.getMessage());
                log.warn("图像 AI edits 第 {} 次失败: {}", attempt + 1, e.getMessage());
            }
        }
        throw last;
    }

    private static String normalizeUrl(String baseUrl) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return url.endsWith("/images/edits") ? url : url + "/images/edits";
    }

    /** 魔数判 mime：PNG 0x89 50，其余按 JPEG 兜底（主流生图仅此两种） */
    public static String magicOf(byte[] b) {
        if (b != null && b.length > 4 && (b[0] & 0xFF) == 0x89 && (b[1] & 0xFF) == 0x50) {
            return "image/png";
        }
        return "image/jpeg";
    }
}
