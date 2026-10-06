package com.aischool.server.controller;

import com.aischool.server.common.ApiResponse;
import com.aischool.server.common.BizException;
import com.aischool.server.entity.SysConfig;
import com.aischool.server.mapper.SysConfigMapper;
import com.aischool.server.service.auth.PermissionService;
import com.aischool.server.service.notify.ProbeService;
import com.aischool.server.service.notify.WeeklyReportService;
import com.aischool.server.service.notify.WecomService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端：群机器人与告警（批29）——企微 webhook 配置/测试、探活巡检、体检周报。
 */
@RestController
@RequestMapping("/api/admin/notify")
@RequiredArgsConstructor
public class AdminNotifyController {

    private final SysConfigMapper sysConfigMapper;
    private final WecomService wecomService;
    private final ProbeService probeService;
    private final WeeklyReportService weeklyReportService;
    private final PermissionService permissionService;

    private void checkAdmin() {
        permissionService.checkAdminAccess("只有管理员可操作系统管理");
    }

    private String cfg(String key) {
        SysConfig c = sysConfigMapper.selectById(key);
        return c == null || c.getCfgValue() == null ? "" : c.getCfgValue().trim();
    }

    private void set(String key, String value) {
        // 批35 起新键无种子行（push_*），update 0 行时补 insert
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
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("wecomWebhookUrl", cfg("wecom_webhook_url"));
        m.put("wecomEnabled", "1".equals(cfg("wecom_enabled")));
        m.put("wecomPushApprovals", "1".equals(cfg("wecom_push_approvals")));
        m.put("alertEnabled", "1".equals(cfg("alert_enabled")));
        m.put("alertIntervalMin", num(cfg("alert_interval_min"), 5));
        m.put("weeklyReportEnabled", "1".equals(cfg("weekly_report_enabled")));
        m.put("weeklyReportDay", num(cfg("weekly_report_day"), 5));
        m.put("wecomActive", wecomService.enabled());
        // 批35 厂商推送预留（AppKey 注册到位、开发方接 SDK 后读取此处即用）
        m.put("pushProvider", cfg("push_provider"));
        m.put("pushJpushAppkey", cfg("push_jpush_appkey"));
        m.put("pushJpushMasterSecret", cfg("push_jpush_master_secret"));
        m.put("pushGetuiAppid", cfg("push_getui_appid"));
        m.put("pushGetuiAppkey", cfg("push_getui_appkey"));
        m.put("pushGetuiMasterSecret", cfg("push_getui_master_secret"));
        return ApiResponse.ok(m);
    }

    @PutMapping("/config")
    public ApiResponse<Void> setConfig(@Validated @RequestBody NotifyCfgReq req) {
        checkAdmin();
        if (req.getWecomEnabled() && (req.getWecomWebhookUrl() == null
                || !req.getWecomWebhookUrl().startsWith("https://qyapi.weixin.qq.com/"))) {
            throw new BizException(400, "启用前请填写企业微信群机器人 webhook 地址（https://qyapi.weixin.qq.com/ 开头）");
        }
        set("wecom_webhook_url", req.getWecomWebhookUrl() == null ? "" : req.getWecomWebhookUrl().trim());
        set("wecom_enabled", Boolean.TRUE.equals(req.getWecomEnabled()) ? "1" : "0");
        set("wecom_push_approvals", Boolean.TRUE.equals(req.getWecomPushApprovals()) ? "1" : "0");
        set("alert_enabled", Boolean.TRUE.equals(req.getAlertEnabled()) ? "1" : "0");
        set("alert_interval_min", String.valueOf(Math.max(1, Math.min(1440,
                req.getAlertIntervalMin() == null ? 5 : req.getAlertIntervalMin()))));
        set("weekly_report_enabled", Boolean.TRUE.equals(req.getWeeklyReportEnabled()) ? "1" : "0");
        set("weekly_report_day", String.valueOf(Math.max(1, Math.min(7,
                req.getWeeklyReportDay() == null ? 5 : req.getWeeklyReportDay()))));
        // 厂商推送预留
        String provider = req.getPushProvider() == null ? "" : req.getPushProvider().trim();
        if (!provider.isEmpty() && !provider.equals("JPUSH") && !provider.equals("GETUI")) {
            throw new BizException(400, "推送供应商仅支持极光（JPUSH）/个推（GETUI）");
        }
        set("push_provider", provider);
        set("push_jpush_appkey", req.getPushJpushAppkey() == null ? "" : req.getPushJpushAppkey().trim());
        set("push_jpush_master_secret", req.getPushJpushMasterSecret() == null ? "" : req.getPushJpushMasterSecret().trim());
        set("push_getui_appid", req.getPushGetuiAppid() == null ? "" : req.getPushGetuiAppid().trim());
        set("push_getui_appkey", req.getPushGetuiAppkey() == null ? "" : req.getPushGetuiAppkey().trim());
        set("push_getui_master_secret", req.getPushGetuiMasterSecret() == null ? "" : req.getPushGetuiMasterSecret().trim());
        return ApiResponse.ok();
    }

    /** 发送测试消息到指定 URL（不受启用闸：先测通再启用） */
    @PostMapping("/wecom-test")
    public ApiResponse<Map<String, Object>> wecomTest(@Validated @RequestBody UrlReq req) {
        checkAdmin();
        String result = wecomService.test(req.getUrl().trim());
        return ApiResponse.ok(Map.of("result", result));
    }

    /** 立即巡检一次（返回各检查项状态；定时器同款逻辑） */
    @PostMapping("/probe-run")
    public ApiResponse<List<Map<String, Object>>> probeRun() {
        checkAdmin();
        return ApiResponse.ok(probeService.runProbe());
    }

    /** 立即出体检周报（推送+返回全文预览） */
    @PostMapping("/weekly-run")
    public ApiResponse<Map<String, Object>> weeklyRun() {
        checkAdmin();
        return ApiResponse.ok(Map.of("markdown", weeklyReportService.runReport()));
    }

    private static int num(String v, int dft) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return dft;
        }
    }

    @Data
    public static class NotifyCfgReq {
        private String wecomWebhookUrl;
        private Boolean wecomEnabled;
        private Boolean wecomPushApprovals;
        private Boolean alertEnabled;
        private Integer alertIntervalMin;
        private Boolean weeklyReportEnabled;
        private Integer weeklyReportDay;
        private String pushProvider;
        private String pushJpushAppkey;
        private String pushJpushMasterSecret;
        private String pushGetuiAppid;
        private String pushGetuiAppkey;
        private String pushGetuiMasterSecret;
    }

    @Data
    public static class UrlReq {
        @NotBlank(message = "url 不能为空")
        private String url;
    }
}
