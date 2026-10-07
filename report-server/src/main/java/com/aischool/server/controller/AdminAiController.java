package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.SysConfig;
import com.aischool.server.mapper.AiTaskMapper;
import com.aischool.server.mapper.SysConfigMapper;
import com.aischool.server.service.ai.AiClient;
import com.aischool.server.service.ai.AiImageClient;
import com.aischool.server.service.auth.PermissionService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端「AI 设置」（批35 学校自治）：OpenAI 兼容单供应商配置，
 * 存 t_sys_config，AiClient 动态读库即时生效；api-key 打码回显只露尾 4 位。
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final SysConfigMapper sysConfigMapper;
    private final AiClient aiClient;
    private final AiImageClient aiImageClient;
    private final AiTaskMapper aiTaskMapper;
    private final PermissionService permissionService;

    private void checkAdmin() {
        permissionService.checkAdminAccess("只有管理员可操作系统管理");
    }

    private String cfg(String key) {
        SysConfig c = sysConfigMapper.selectById(key);
        return c == null || c.getCfgValue() == null ? "" : c.getCfgValue().trim();
    }

    /** 新键无种子行，update 0 行时补 insert（t_sys_config 主键=cfg_key） */
    private void upsert(String key, String value) {
        int n = sysConfigMapper.update(null, new LambdaUpdateWrapper<SysConfig>()
                .eq(SysConfig::getCfgKey, key).set(SysConfig::getCfgValue, value));
        if (n == 0) {
            SysConfig c = new SysConfig();
            c.setCfgKey(key);
            c.setCfgValue(value);
            sysConfigMapper.insert(c);
        }
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        checkAdmin();
        String key = cfg("ai_api_key");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baseUrl", cfg("ai_base_url"));
        m.put("model", cfg("ai_model"));
        m.put("apiKeySet", !key.isEmpty());
        m.put("apiKeyMasked", key.length() > 4 ? "••••••" + key.substring(key.length() - 4) : "");
        m.put("enabled", aiClient.enabled());
        m.put("effectiveModel", aiClient.currentConfig().model());
        // 批36① 图像生成：独立第二把 key，未配置 = 报告成长画像保持虚线占位框
        String imgKey = cfg("img_api_key");
        m.put("imgBaseUrl", cfg("img_base_url"));
        m.put("imgModel", cfg("img_model"));
        m.put("imgApiKeySet", !imgKey.isEmpty());
        m.put("imgApiKeyMasked", imgKey.length() > 4 ? "••••••" + imgKey.substring(imgKey.length() - 4) : "");
        m.put("imgEnabled", aiImageClient.enabled());
        return ApiResponse.ok(m);
    }

    /** api-key 留空 = 保持不变（打码回显不会把密钥带回来）；清空 base-url/model 即停用。文本/图像两组同语义 */
    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> setConfig(@Validated @RequestBody AiCfgReq req) {
        checkAdmin();
        if (req.getBaseUrl() != null) {
            upsert("ai_base_url", req.getBaseUrl().trim());
        }
        if (req.getModel() != null) {
            upsert("ai_model", req.getModel().trim());
        }
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            upsert("ai_api_key", req.getApiKey().trim());
        }
        if (req.getImgBaseUrl() != null) {
            upsert("img_base_url", req.getImgBaseUrl().trim());
        }
        if (req.getImgModel() != null) {
            upsert("img_model", req.getImgModel().trim());
        }
        if (req.getImgApiKey() != null && !req.getImgApiKey().isBlank()) {
            upsert("img_api_key", req.getImgApiKey().trim());
        }
        return config();
    }

    /** 测试连接：字段留空 = 用已保存配置；发一条最小补全验证连通 */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> test(@RequestBody AiCfgReq req) {
        checkAdmin();
        AiClient.AiConfig saved = aiClient.currentConfig();
        String baseUrl = req.getBaseUrl() != null && !req.getBaseUrl().isBlank()
                ? req.getBaseUrl().trim() : saved.baseUrl();
        String apiKey = req.getApiKey() != null && !req.getApiKey().isBlank()
                ? req.getApiKey().trim() : saved.apiKey();
        String model = req.getModel() != null && !req.getModel().isBlank()
                ? req.getModel().trim() : saved.model();
        if (baseUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
            throw new BizException(400, "请先填齐服务商地址 / API key / 模型名（或先保存）再测试");
        }
        String reply = aiClient.testConnection(new AiClient.AiConfig(baseUrl, apiKey, model));
        return ApiResponse.ok(Map.of("result", reply));
    }

    /** AI 用量统计（管理端「AI 设置」内嵌用量区；修复既有缺口：此路径此前无人实现，页签一直空表） */
    @GetMapping("/usage")
    public ApiResponse<Map<String, Object>> usage(@RequestParam(defaultValue = "30") int days) {
        checkAdmin();
        LocalDateTime since = LocalDateTime.now().minusDays(Math.min(Math.max(days, 1), 365));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("byDay", aiTaskMapper.usageByDay(since));
        m.put("byTeacher", aiTaskMapper.usageByTeacher(since));
        return ApiResponse.ok(m);
    }

    /** 测试生图（批36①）：字段留空 = 用已保存配置；固定样例提示词真实生成一张，回 dataUri 预览 */
    @PostMapping("/test-image")
    public ApiResponse<Map<String, Object>> testImage(@RequestBody AiCfgReq req) {
        checkAdmin();
        AiImageClient.ImgConfig saved = aiImageClient.currentConfig();
        String baseUrl = req.getImgBaseUrl() != null && !req.getImgBaseUrl().isBlank()
                ? req.getImgBaseUrl().trim() : saved.baseUrl();
        String apiKey = req.getImgApiKey() != null && !req.getImgApiKey().isBlank()
                ? req.getImgApiKey().trim() : saved.apiKey();
        String model = req.getImgModel() != null && !req.getImgModel().isBlank()
                ? req.getImgModel().trim() : saved.model();
        if (baseUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
            throw new BizException(400, "请先填齐图像服务商地址 / API key / 模型名（或先保存）再测试");
        }
        // 样例 = 报告实际模板（小树+责任担当红旗），测的就是真实出图效果
        String dataUri = aiImageClient.testDataUri(
                "Flat vector decorative illustration. In the center is a young slender sapling with a thin brown"
                        + " trunk and sparse light-green leaves, growing upright toward the sun. Around it: several"
                        + " small plain red flags on poles. Simple flat color blocks, warm green and gold color"
                        + " palette, centered composition, clean plain background. Absolutely no text, no letters,"
                        + " no numbers, no banners, no people, no animals.",
                new AiImageClient.ImgConfig(baseUrl, apiKey, model));
        return ApiResponse.ok(Map.of("image", dataUri));
    }

    @Data
    public static class AiCfgReq {
        private String baseUrl;
        private String apiKey;
        private String model;
        private String imgBaseUrl;
        private String imgApiKey;
        private String imgModel;
    }
}
